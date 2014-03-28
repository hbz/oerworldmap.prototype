<?php

/**
 * Implements EntityFieldQuery to execute against API
 *
 */
class LinkedDataEntityFieldQuery extends EntityFieldQuery {

  public function __construct() {
    $this->entityCondition('entity_type', LDE_ENTITY_TYPE);
    $this->pager();
  }

  public function execute() {
    $endpoint = $this->metaData['endpoint'];
    $term = $this->metaData['term'];
    $queryParam = $this->metaData['queryParam'];
    $offsetParam = $this->metaData['offsetParam'];
    $limitParam = $this->metaData['limitParam'];
    $resultFormat = $this->metaData['resultFormat'];
    $typeParam = isset($this->metaData['typeParam'])
      ? $this->metaData['typeParam'] : null;
    $additionalParam = isset($this->metaData['additionalParam'])
      ? $this->metaData['additionalParam'] : null;
    $resultFormat = $this->metaData['resultFormat'];
    $page = pager_find_page();

    $results_per_page = variable_get('edoweb_storage_results_per_page', 10);
    $from = array_key_exists('start', $this->range)
      ? $this->range['start']
      : $page * $results_per_page;
    $size = array_key_exists('length', $this->range)
      ? $this->range['length']
      : $results_per_page;

    $http_url = sprintf(
      '%s?%s=%s&%s=%s&%s=%s',
      $endpoint, $queryParam, urlencode($term), $offsetParam, $from, $limitParam, $size
    );

    $target_types = array();
    if (array_key_exists('bundle', $this->entityConditions)) {
      $target_bundles = $this->entityConditions['bundle']['value'];
      if (!is_array($target_bundles)) {
        $target_bundles = array($target_bundles);
      }
      // Map target bundles to rdf types
      foreach ($target_bundles as $target_bundle) {
        $rdf_mapping = rdf_mapping_load(LDE_ENTITY_TYPE, $target_bundle);
        $target_types = array_merge($rdf_mapping['rdftype'], $target_types);
        if (isset($this->metaData['target_bundles'][$target_bundle]['constraints'])) {
          foreach ($this->metaData['target_bundles'][$target_bundle]['constraints'] as $property => $class) {
            $field = new LibRDF_URINode($property);
            $value = new LibRDF_URINode($class);
            // TODO: find a more configurable solution
            $http_url .= "&{$field->getLocalPart()}={$value->getLocalPart()}";
          }
        }
      }
      if (!empty($target_types) && isset($typeParam)) {
        $http_url .= "&$typeParam=" . implode(',', $target_types);
      }
    }

    if ($additionalParam) foreach ($additionalParam as $param) {
      $http_url .= "&$param";
    }

    $http_options = array(
      'method' => 'GET',
      'headers' => array('Accept' => $resultFormat),
    );
    $http_response = lde_http_request($http_url, $http_options);

    if (200 != $http_response->code || !isset($http_response->data)) {
      drupal_set_message("Unable to execute query $http_url, server responded {$http_response->error} and code {$http_response->code}", 'error');
      return FALSE;
    }

    $result_rdf = new LibRDF_Model(new LibRDF_Storage());
    if ('application/json' == $resultFormat) {
      $rdf_jsonld = json_decode($http_response->data);
      $options = array(
        'format' => 'application/ntriples'
      );
      $rdf_ntriples = jsonld_to_rdf($rdf_jsonld, $options);
      $rdf_parser = new LibRDF_Parser('ntriples');
      $result_rdf->loadStatementsFromString($rdf_parser, $rdf_ntriples);
    } else if ('application/rdf+xml' == $resultFormat) {
      $rdf_xml = $http_response->data;
      $rdf_parser = new LibRDF_Parser('rdfxml');
      $result_rdf->loadStatementsFromString($rdf_parser, $rdf_xml);
    }

    $filter_string = '';
    if (!empty($target_types)) {
      $target_uris = array();
      foreach ($target_types as $target_type) {
        $target_uris[] = "<$target_type>";
      }
      $filter_string = 'FILTER(?type=' . implode('|| ?type=', $target_uris) . ')';
    }

    $query_string = "
      SELECT ?uri WHERE {
        ?uri a ?type .
        $filter_string
      }
    ";

    $query = new LibRDF_Query($query_string, null, 'sparql');
    $rdf_entities = $query->execute($result_rdf);
    $entities = array();
    foreach ($rdf_entities as $rdf_entity) {
      $uri = $rdf_entity['uri']->getValue();
      $entity = entity_create(LDE_ENTITY_TYPE, array(
        'uri' => $uri,
      ));
      if (lde_from_rdf($entity, "$result_rdf", 'rdfxml')) {
        $entities[$uri] = $entity;
      }
    }

    return $entities;

  }

}
