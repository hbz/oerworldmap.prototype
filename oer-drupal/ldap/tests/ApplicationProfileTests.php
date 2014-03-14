<?php

define('TEST_LDE_TYPE', 'test');
define('FIELD_CARDINALITY_UNLIMITED', -1);

require_once __DIR__ . '/../ApplicationProfile.inc';

class ApplicationProfileTests extends PHPUnit_Framework_TestCase {

  protected function setUp() {
    $ap_uri = 'file://' . __DIR__ . '/ApplicationProfile.ttl#ap';
    //$ap_uri = 'file://' . __DIR__ . '/../../../oerap.ttl#ap';
    ApplicationProfile::add($ap_uri, 'turtle', TEST_LDE_TYPE, 'text_long');
  }

  public function test_get_fields() {
    $fields = ApplicationProfile::get(TEST_LDE_TYPE)->getFields();
    $expexted = array(
      array(
        'field_name' => TEST_LDE_TYPE . '_type',
        'type' => 'text_long',
        'cardinality' => -1,
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_name',
        'type' => 'text',
        'cardinality' => 1,
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_email',
        'type' => 'text',
        'cardinality' => 1,
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_title',
        'type' => 'text',
        'cardinality' => 1,
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_creator',
        'type' => 'text_long',
        'cardinality' => -1,
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_part_of',
        'type' => 'text_long',
        'cardinality' => -1,
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_property_without_title',
        'type' => 'text',
        'cardinality' => 1,
      ),
    );
    $this->assertEquals($expexted, $fields);
  }

  public function test_get_bundles() {
    $bundles = ApplicationProfile::get(TEST_LDE_TYPE)->getBundles();
    $expected = array(
      'Person' => array(
        'label' => 'Person',
        'admin' => array(
          'path' => 'admin/structure/' . TEST_LDE_TYPE . '/Person/manage',
          'access arguments' => array(
            'administer ' . TEST_LDE_TYPE . ' entities',
          ),
        ),
      ),
      'Document' => array(
        'label' => 'Dokument',
        'admin' => array(
          'path' => 'admin/structure/' . TEST_LDE_TYPE . '/Document/manage',
          'access arguments' => array(
            'administer ' . TEST_LDE_TYPE . ' entities',
          ),
        ),
      ),
      'ClassWithoutLabel' => array(
        'admin' => array(
          'path' => 'admin/structure/' . TEST_LDE_TYPE . '/ClassWithoutLabel/manage',
          'access arguments' => array(
            'administer ' . TEST_LDE_TYPE . ' entities',
          ),
        ),
      ),
    );
    $this->assertEquals($expected, $bundles);
  }

  public function test_get_field_instances() {
    $instances = ApplicationProfile::get(TEST_LDE_TYPE)->getFieldInstances();
    $expected = array(
      array(
        'field_name' => TEST_LDE_TYPE . '_email',
        'bundle' => 'Person',
        'entity_type' => TEST_LDE_TYPE,
        'label' => 'E-Mail',
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_part_of',
        'bundle' => 'Document',
        'entity_type' => TEST_LDE_TYPE,
        'label' => 'Teil von',
        'settings' => array(
          'handler_settings' => array(
            'target_bundles' => array(
              'Document' => array(
                'lookup' => true,
              ),
            ),
          ),
        ),
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_name',
        'bundle' => 'Person',
        'entity_type' => TEST_LDE_TYPE,
        'label' => 'Name',
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_title',
        'bundle' => 'Document',
        'entity_type' => TEST_LDE_TYPE,
        'label' => 'Titel',
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_creator',
        'bundle' => 'Document',
        'entity_type' => TEST_LDE_TYPE,
        'label' => 'Autor',
        'settings' => array(
          'handler_settings' => array(
            'target_bundles' => array(
              'Person' => array(
                'lookup' => true,
              ),
            ),
          ),
        ),
      ),
      array(
        'field_name' => TEST_LDE_TYPE . '_property_without_title',
        'bundle' => 'ClassWithoutLabel',
        'entity_type' => TEST_LDE_TYPE,
      ),
    );
    $this->assertEquals($expected, $instances);
  }

  public function test_get_rdf_mappings() {
    $mappings = ApplicationProfile::get(TEST_LDE_TYPE)->getRdfMappings();
    $expected = array(
      array(
        'type' => TEST_LDE_TYPE,
        'bundle' => 'Person',
        'mapping' => array(
          'rdftype' => array(
            'http://xmlns.com/foaf/0.1/Person',
          ),
          'test_email' => array(
            'predicates' => array(
              'http://xmlns.com/foaf/0.1/email',
            )
          ),
          'test_name' => array(
            'predicates' => array(
              'http://xmlns.com/foaf/0.1/name',
            ),
          ),
        ),
      ),
      array(
        'type' => TEST_LDE_TYPE,
        'bundle' => 'Document',
        'mapping' => array(
          'rdftype' => array(
            'http://xmlns.com/foaf/0.1/Document',
          ),
          'test_part_of' => array(
            'predicates' => array(
              'http://purl.org/dc/terms/isPartOf',
            ),
            'type' => 'rel',
          ),
          'test_title' => array(
            'predicates' => array(
              'http://purl.org/dc/terms/title',
            ),
          ),
          'test_creator' => array(
            'predicates' => array(
              'http://purl.org/dc/terms/creator',
            ),
            'type' => 'rel',
          ),
        ),
      ),
    );
    $this->assertEquals($expected, $mappings);
  }

  public function test_get_top_bundles() {
    $top_bundles = ApplicationProfile::get(TEST_LDE_TYPE)->getTopBundles();
    $expected = array('Person', 'Document');
    $this->assertEquals($expected, $top_bundles);
  }

}
