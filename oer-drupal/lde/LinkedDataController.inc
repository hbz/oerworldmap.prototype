<?php

/**
 * A controller for linked data entities.
 *
 */
class LinkedDataController extends EntityAPIController {

  /**
   * Create a new LinkedDataEntity
   *
   * @param  mixed    The initial values
   * @return LinkedDataEntity
   */
  public function create(array $values = array()) {
    if (!isset($values['uri'])) {
      $values['uri'] = lde_resource_uri();
    }
    return parent::create($values);
  }

  /**
   * Save a LinkedDataEntity to the API
   *
   * @param  mixed                $entity      The entity to save
   * @param  DatabaseTransaction  $transaction Optional, defaults to NULL. 
   */
  public function save($entity, DatabaseTransaction $transaction = NULL)
  {
    return $this->put($entity);
  }

  /**
   * Load LinkedDataEntities from the API
   *
   * @param  array  The URIs of the entities to load
   * @return array  The LinkedDataEntities
   */
  public function load($uris = array(), $conditions = array()) {
    $cached_entities = $this->cacheGet($uris, $conditions);
    $entities = array();
    foreach ($uris as $url_encoded_uri) {
      $uri = urldecode(urldecode($url_encoded_uri));
      if (array_key_exists($uri, $cached_entities)) {
        $entities[$uri] = $cached_entities[$uri];
      } else {
        $entity = entity_create(LDE_ENTITY_TYPE, array(
          'uri' => $uri,
        ));
        if ($this->get($entity)) {
          $entities[$uri] = $entity;
          $this->cacheSet(array($uri => $entities[$uri]));
        }
      }
    }
    return $entities;
  }

  /**
   * Delete LinkedDataEntities from the API
   *
   * @param  array                $uris  The URIs of the entities to delete
   * @param  DatabaseTransaction  $transaction Optional, defaults to NULL. 
   */
  public function delete($uris, DatabaseTransaction $transaction = NULL) {
    foreach ($uris as $url_encoded_uri) {
      $uri = urldecode(urldecode($url_encoded_uri));
      $entity = entity_create(LDE_ENTITY_TYPE, array(
        'uri' => $uri,
      ));
      $status = $this->del($entity);
    }
    return $status;
  }

  /**
   * Serializes the entity to the given RDF format.
   *
   * @param  Entity  $entity The entity to serialize
   * @param  string  $format Optional, defaults to 'rdfxml'.
   * @return string  The serialized entity
   */
  public function toRDF($entity, $format = 'rdfxml') {

    $RDF = new LibRDF_NS('http://www.w3.org/1999/02/22-rdf-syntax-ns#');

    $rdf_parser = new LibRDF_Parser($format);
    $rdf_subject = new LibRDF_URINode($entity->identifier());
    $rdf_model = new LibRDF_Model(new LibRDF_Storage());
    $rdf_mapping = rdf_mapping_load(
      $entity->entityType(), $entity->bundle()
    );

    // Add rdf:type statements
    $rdf_type_mappings = $rdf_mapping['rdftype'];
    foreach ($rdf_type_mappings as $rdf_type_mapping) {
      $class = new LibRDF_URINode($rdf_type_mapping);
      $rdf_type_statement = new LibRDF_Statement(
        $rdf_subject, $RDF->type, $class
      );
      $rdf_model->addStatement($rdf_type_statement);
    }
    unset($rdf_mapping['rdftype']);

    // Add field data
    $wrapper = entity_metadata_wrapper($entity->entityType(), $entity);
    foreach ($rdf_mapping as $field_name => $field_mapping) {
      $field_info_instance = field_info_instance(
        $entity->entityType(), $field_name, $entity->bundle()
      );
      try {
        $items = $wrapper->{$field_name}->value();
      } catch (EntityMetadataWrapperException $e) {
        // Field is not available for the entity
        continue;
      }
      if (is_null($items) || empty($items)) {
        // Field is available for the entity,
        // but does not have value(s).
        continue;
      }
      if (!is_array($items)) $items = array($items);
      $is_rel = (isset($field_mapping['type']) && $field_mapping['type'] == 'rel');
      foreach ($items as $item) {
        $rdf_object = $is_rel
          ? new LibRDF_URINode($item)
          : new LibRDF_LiteralNode($item);
        foreach ($field_mapping['predicates'] as $predicate) {
          $rdf_predicate = new LibRDF_URINode($predicate);
          // Add RDF of embedded entities
          $entity_cache = $entity->getEntityCache();
          $rdf_statement = new LibRDF_Statement(
            $rdf_subject, $rdf_predicate, $rdf_object
          );
          $rdf_model->addStatement($rdf_statement);
        }
        if ($is_rel && isset($entity_cache[$item])) {
          if (isset($field_info_instance['settings']
            ['handler_settings']['target_bundles']
            [$entity_cache[$item]->bundle()]['embed'])
          ) {
            $rdf_model->loadStatementsFromString(
              $rdf_parser, $this->toRDF($entity_cache[$item], $format)
            );
            $entity_cache[$item]->is_new = false;
          }
        }
      }
    }
    $rdf_serializer = new LibRDF_Serializer($format);
    return $rdf_model->serializeStatements($rdf_serializer);

  }

  /**
   * Send PUT request to API
   *
   * @param  LinkedDataEntity  $entity The entity to serialize and PUT
   */
  protected function put($entity) {

    $rdf = $this->toRDF($entity);
    $http_user = variable_get('lde_api_user');
    $http_pass = variable_get('lde_api_password');
    $http_url = $entity->identifier();

    if (!$http_user || !$http_pass) {
      drupal_set_message('Please provide username and password in the configuration');
      return false;
    }

    $http_options = array(
      'method' => 'PUT',
      'headers' => array(
        'Content-Type' => 'application/rdf+xml',
        'Authorization' => 'Basic' .  base64_encode("{$http_user}:{$http_pass}"),
      ),
      'data' => $rdf,
    );

    $http_response = lde_http_request($http_url, $http_options);
    if (200 != $http_response->code) {
      drupal_set_message("Unable to PUT $http_url", 'error');
      return false;
    }

    return true;

  }

  /**
   * Send GET request to API
   *
   * @param  LinkedDataEntity $entity The entity
   * @return LinkedDataEntity The unserialized entity
   */
  protected function get($entity) {
    $http_url = $entity->identifier();
    $http_options = array(
      'method' => 'GET',
      'headers' => array('Accept' => 'application/rdf+xml'),
    );
    $http_response = lde_http_request($http_url, $http_options);
    if (200 != $http_response->code) {
      drupal_set_message("Unable to GET $http_url", 'error');
      return false;
    }
    return lde_from_rdf($entity, $http_response->data, 'rdfxml');
  }

  /**
   * Send DELETE request to API
   *
   * @param  LinkedDataEntity  $entity The entity
   */
  protected function del($entity) {

    $http_user = variable_get('lde_api_user');
    $http_pass = variable_get('lde_api_password');
    $http_url = $entity->identifier();

    if (!$http_user || !$http_pass) {
      drupal_set_message('Please provide username and password in the configuration');
      return false;
    }

    $http_url = $entity->identifier();
    $http_options = array(
      'method' => 'DELETE',
      'headers' => array(
        'Authorization' => 'Basic' .  base64_encode("{$http_user}:{$http_pass}"),
      ),
    );

    $http_response = lde_http_request($http_url, $http_options);
    if (200 != $http_response->code) {
      drupal_set_message("Unable to DELETE $http_url", 'error');
      return false;
    }
    return true;
  }

}

