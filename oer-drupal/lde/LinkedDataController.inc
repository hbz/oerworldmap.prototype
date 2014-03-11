<?php

require_once __DIR__ . '/../lib/LibRDF/LibRDF/LibRDF.php';

/**
 * A controller for linked data entities.
 *
 */
class LinkedDataController extends EntityAPIController {

  public function create(array $values = array()) {
    if (!isset($values['uri'])) {
      $values['uri'] = "urn:uuid:" . lde_uuid_v4();
    }
    return parent::create($values);
  }

  public function save($entity, DatabaseTransaction $transaction = NULL) {
    $rdf = $this->toRDF($entity);
    $file_path = drupal_realpath(file_default_scheme() . '://');
    $file_name = "{$entity->identifier()}.rdf";
    file_put_contents("$file_path/$file_name", $rdf);
  }

  public function load($ids = array(), $conditions = array()) {
    $file_path = drupal_realpath(file_default_scheme() . '://');
    $entities = array();
    foreach ($ids as $id) {
      $file_name = "{$id}.rdf";
      if (file_exists("$file_path/$file_name")) {
        $rdf = file_get_contents("$file_path/$file_name");
        $entity = entity_create(LDE_ENTITY_TYPE, array(
          'uri' => $id,
        ));
        $this->fromRDF($entity, $rdf);
        $entities[$id] = $entity;
      }
    }
    return $entities;
  }

  public function buildContent($entity, $view_mode = 'full',
    $langcode = null, $content = array()
  ) {
    switch ($view_mode) {
      case 'edit':
        return entity_form($entity->entityType(), $entity);
      default:
        return parent::buildContent($entity, $view_mode, $langcode, $content);
    }
  }

  /**
   * Serializes the entity to the given RDF format.
   *
   * @param  Entity  $entity The entity to serialize
   * @param  string  $format Optional, defaults to 'rdfxml'.
   * @return string The serialized entity
   */
  public function toRDF($entity, $format = 'rdfxml') {

    $RDF = new LibRDF_NS('http://www.w3.org/1999/02/22-rdf-syntax-ns#');

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
      foreach ($field_mapping['predicates'] as $predicate) {
        $rdf_predicate = new LibRDF_URINode($predicate);
        foreach ($items as $item) {
          $rdf_object = $is_rel
            ? new LibRDF_URINode($item)
            : new LibRDF_LiteralNode($item);
          $rdf_statement = new LibRDF_Statement(
            $rdf_subject, $rdf_predicate, $rdf_object
          );
          $rdf_model->addStatement($rdf_statement);
        }
      }
    }
    $rdf_serializer = new LibRDF_Serializer($format);
    return $rdf_model->serializeStatements($rdf_serializer);

  }

  /**
   * Constructs a new entity based on the provided RDF.
   *
   * @param  Entity    $entity The entity to load data into
   * @param  resource  $rdf_string The RDF-serialized entity
   * @param  string    $format     Optional, defaults to 'rdfxml'.
   * @return LinkedDataEntity $entity
   */
  public function fromRDF($entity, $rdf_string, $format = 'rdfxml') {

    $RDF = new LibRDF_NS('http://www.w3.org/1999/02/22-rdf-syntax-ns#');

    $rdf_subject = new LibRDF_URINode($entity->identifier());
    $rdf_model = new LibRDF_Model(new LibRDF_Storage());
    $rdf_parser = new LibRDF_Parser($format);
    $rdf_model->loadStatementsFromString($rdf_parser, $rdf_string);

    // Find bundle and corresponding mapping based on rdf:type
    $bundles = field_info_bundles(LDE_ENTITY_TYPE);
    foreach ($bundles as $bundle_name => $bundle) {
      $rdf_mapping = $bundle['rdf_mapping'];
      $rdf_types = $rdf_mapping['rdftype'];
      foreach ($rdf_types as $rdf_type) {
        $rdf_class = new LibRDF_URINode($rdf_type);
        $rdf_type_stmt = $rdf_model->findStatements(
          $rdf_subject, $RDF->type, $rdf_class
        );
        if ($rdf_type_stmt->current()) {
          break 2;
        }
      }
    }
    $entity->type = $bundle_name;
    unset($rdf_mapping['rdftype']);

    // Add field data
    $wrapper = entity_metadata_wrapper($entity->entityType(), $entity);
    foreach ($rdf_mapping as $field_name => $field_mapping) {
      foreach ($field_mapping['predicates'] as $predicate) {
        $rdf_predicate = new LibRDF_URINode($predicate);
        foreach ($rdf_model->getTargets($rdf_subject, $rdf_predicate) as $value) {
          if ($value instanceOf LibRDF_LiteralNode) {
            // Fix escaped unicode characters such as \u00E4
            $value = json_decode('"'.$value->getValue().'"');
            if ($wrapper->{$field_name} instanceOf EntityValueWrapper) {
              $wrapper->{$field_name} = $value;
            } else {
              $wrapper->{$field_name}[] = $value;
            }
          } else {
            $value = substr($value, 1, -1);
            if ($wrapper->{$field_name} instanceOf EntityValueWrapper) {
              $wrapper->{$field_name} = $value;
            } else {
              $wrapper->{$field_name}[] = $value;
            }
          }
        }
      }
    }
  }

}
