<?php

/**
 * A linked data entity
 *
 */
class LinkedDataEntity extends Entity {

  /**
   * Constructs the URI of this entity
   *
   * @return array The URI of this entity
   */
  public function defaultUri() {
    return array(
      'path' => 'oerworldmap/resource/' . $this->identifier()
    );
  }

}
