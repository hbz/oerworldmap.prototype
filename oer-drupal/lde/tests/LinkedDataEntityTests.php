<?php

require_once __DIR__ . '/../LinkedDataEntity.inc';

class LinkedDataEntityTests extends PHPUnit_Framework_TestCase {

  protected function setUp() {
  }

  public function test_to_rdf() {
    $entity = new LinkedDataEntity(array());
  }

}
