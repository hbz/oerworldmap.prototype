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
   * Constructs the URI of this entity
   *
   * @return array The URI of this entity
   */
  public function defaultUri() {
    return array(
      'path' => 'oerworldmap/resource/' .
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
