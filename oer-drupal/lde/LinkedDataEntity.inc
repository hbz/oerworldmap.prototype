<?php

/**
 * A linked data entity
 *
 */
class LinkedDataEntity extends Entity {

  /**
   * Holds other, related entities
   *
   */
  protected $entity_cache = array();

  /**
   * Constructs the label of this entity
   *
   * @return string The label of this entity
   */
  public function label() {

    $entity_info = $this->entityInfo();
    $bundle_info = $entity_info['bundles'][$this->bundle()];
    $instance_label_fields = isset($bundle_info['instance labels'])
      ? $bundle_info['instance labels']
      : false;

    $output = array();
    if ($instance_label_fields) {
      $wrapper = entity_metadata_wrapper($this->entityType(), $this);
      foreach ($instance_label_fields as $instance_label_field) {
        $values = $wrapper->{$instance_label_field}->raw();
        if (!empty($values)) {
          $values = is_array($values) ? $values : array($values);
          $output = array_merge($output, $values);
        }
      }
    }

    if (empty($output)) {
      return $this->identifier();
    } else {
      return implode(', ', $output);
    }

  }

  /**
   * Constructs the URI of this entity
   *
   * @return array The URI of this entity
   */
  public function defaultUri() {
    return array(
      'path' => 'resource/' .
        urlencode(urlencode($this->identifier()))
    );
  }

  /**
   * Returns related entities.
   *
   * @return array Related entities
   */
  public function getEntityCache() {
    return $this->entity_cache;
  }

  /**
   * Attach related entities
   *
   * @param  array  $entities Related entities
   */
  public function setEntityCache($entities) {
    $this->entity_cache = $entities;
  }

}
