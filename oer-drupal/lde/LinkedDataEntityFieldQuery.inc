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
    $host = variable_get('lde_api_host');
    $term = str_replace(
      ' ', '+', $this->metaData['term']
    );
    $page = pager_find_page();

    $results_per_page = variable_get('edoweb_storage_results_per_page', 10);
    $from = array_key_exists('start', $this->range)
      ? $this->range['start']
      : $page * $results_per_page;
    $size = array_key_exists('length', $this->range)
      ? $this->range['length']
      : $results_per_page;

    $http_url = sprintf(
      'http://%s/oer?q=%s&from=%s&size=%s',
      $host, urlencode($term), $from, $size
    );

    if (array_key_exists('bundle', $this->entityConditions)) {
      $target_bundles = $this->entityConditions['bundle']['value'];
      if (!is_array($target_bundles)) {
        $target_bundles = array($target_bundles);
      }
      // Map target bundles to rdf types
      $target_types = array();
      foreach ($target_bundles as $target_bundle) {
        $rdf_mapping = rdf_mapping_load(LDE_ENTITY_TYPE, $target_bundle);
        $target_types = array_merge($rdf_mapping['rdftype'], $target_types);
      }
      if (!empty($target_types)) {
        $http_url .= '&t=' . implode(',', $target_types);
      }
    }

    $http_options = array(
      'method' => 'GET',
      'headers' => array('Content-Type' => 'application/json'),
    );
    $http_response = lde_http_request($http_url, $http_options);

    if (200 != $http_response->code || !isset($http_response->data)) {
      drupal_set_message("Unable to execute query", 'error');
      return FALSE;
    }
    $rdf_jsonld = json_decode($http_response->data);
    $options = array(
      'format' => 'application/ntriples'
    );
    $rdf_ntriples = jsonld_to_rdf($rdf_jsonld, $options);
    $entities = array();
    foreach ($rdf_jsonld as $match) {
      $uri = current($match->{'@graph'})->{'@id'};
      $entity = entity_create(LDE_ENTITY_TYPE, array(
        'uri' => $uri,
      ));
      lde_from_rdf($entity, $rdf_ntriples, 'ntriples');
      $entities[] = $entity;
    }
    return $entities;
  }
}
