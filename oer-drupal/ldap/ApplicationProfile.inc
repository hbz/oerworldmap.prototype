<?php

require_once __DIR__ . '/../lib/LibRDF/LibRDF/LibRDF.php';

/**
 * Provides Drupal fields, bundles and field instances based on OWL
 * ontologies.
 *
 */
class ApplicationProfile {

  /**
   * The URI of the application profile
   *
   * @var LibRDF_URINode
   */
  protected $applicationProfileURI;

  /**
   * The RDF graph (model) containing the ontologie
   *
   * @var LibRDF_Model
   */
  protected $applicationProfileRDF;

  /**
   * The drupal entity type
   *
   * @var string
   */
  protected $applicationProfileEntityType;

  /**
   * Instances created by the factory
   *
   * @var array Defaults to array().
   */
  private static $instances = array();

  /**
   * Create a new ApplicationProfile
   *
   * @param  string  $applicationProfileURI
   * @param  string  $applicationProfileFormat
   * @param  string  $applicationProfileEntityType
   */
  private function __construct(
    $applicationProfileURI,
    $applicationProfileFormat,
    $applicationProfileEntityType,
    $applicationProfileReferenceFieldType
  ) {
    $this->applicationProfileURI = new LibRDF_URINode($applicationProfileURI);
    $this->applicationProfileRDF = new LibRDF_Model(new LibRDF_Storage());
    $rdf_parser = new LibRDF_Parser($applicationProfileFormat);
    $this->applicationProfileRDF->loadStatementsFromURI(
      $rdf_parser, $applicationProfileURI
    );
    $this->applicationProfileEntityType = $applicationProfileEntityType;
    $this->applicationProfileReferenceFieldType = $applicationProfileReferenceFieldType;
  }

  /**
   * Get the entity type described by this application profile
   *
   * @return string The name of the entity type
   */
  public function getEntityType() {
    return $this->applicationProfileEntityType;
  }

  /**
   * Get an array of field definitions suitable to be passed to
   * Drupal's field_create_field.
   *
   * @return array The fields defined in the application profile
   */
  public function getFields() {
    $RDFS = new LibRDF_NS('http://www.w3.org/2000/01/rdf-schema#');
    $OWL = new LibRDF_NS('http://www.w3.org/2002/07/owl#');

    $query_string = "
      SELECT ?uri, ?type WHERE {
        ?uri {$RDFS->isDefinedBy} {$this->applicationProfileURI} .
        ?uri a ?type .
        FILTER(?type={$OWL->DatatypeProperty} || ?type={$OWL->ObjectProperty}) .
      }
    ";
    $query = new LibRDF_Query($query_string, null, 'sparql');
    $properties = $query->execute($this->applicationProfileRDF);
    $fields = array();
    foreach ($properties as $property) {
      $field = array(
        'field_name' => $this->getFieldName($property['uri']),
      );
      $field['type'] = $property['type']->isEqual($OWL->ObjectProperty)
        ? $this->applicationProfileReferenceFieldType : 'text';
      // TODO: handle cardinalities on instance level
      $field['cardinality'] = $property['type']->isEqual($OWL->ObjectProperty)
        ? FIELD_CARDINALITY_UNLIMITED : 1;
      $fields[] = $field;
    }
    return $fields;
  }

  /**
   * Map property URI to Drupal-compliant field name
   *
   * @param  LibRDF_URINode  $property_uri
   * @return string The field name
   */
  public function getFieldName(LibRDF_URINode $property_uri) {
    return decamelize(
      $this->applicationProfileEntityType . '_' . $property_uri->getLocalPart()
    );
  }

  /**
   * Map Drupal field name to property URI
   *
   * @param  mixed  $field_name
   * @return LibRDF_URINode The property URI
   */
  public function getProperty($field_name) {
    $bare = substr($field_name, strlen($this->applicationProfileEntityType) + 1);
    $AP = $this->applicationProfileURI->getNamespace();
    return $AP->{lcfirst(camelize($bare))};
  }

  /**
   * Map Drupal bundle name to class URI
   *
   * @param  bool  $bundle 
   * @return LibRDF_URINode The class URI
   */
  public function getClass($bundle) {
    $AP = $this->applicationProfileURI->getNamespace();
    return $AP->$bundle;
  }

  /**
   * Get an array of bundle definitions suitable to be used in
   * Drupal's hook_alter_entity_info
   *
   * @return array The bundles defined in the application profile
   */
  public function getBundles() {
    $RDFS = new LibRDF_NS('http://www.w3.org/2000/01/rdf-schema#');
    $OWL = new LibRDF_NS('http://www.w3.org/2002/07/owl#');

    $query_string = "
      SELECT ?uri WHERE {
        ?uri {$RDFS->isDefinedBy} {$this->applicationProfileURI} .
        ?uri a {$OWL->Class} .
      }
    ";
    $query = new LibRDF_Query($query_string, null, 'sparql');
    $classes = $query->execute($this->applicationProfileRDF);
    $bundles = array();
    foreach ($classes as $class) {
      $bundle_name = $this->getBundleName($class['uri']);
      $admin_path = sprintf('admin/structure/%s/%s/manage',
        $this->applicationProfileEntityType,
        $bundle_name
      );
      $access_arguments = sprintf('administer %s entities',
        $this->applicationProfileEntityType
      );
      $bundle = array(
        'admin' => array(
          'path' => $admin_path,
          'access arguments' => array($access_arguments),
        ),
      );
      try {
        $label = $this->applicationProfileRDF->getTarget(
          $class['uri'], $RDFS->label
        );
        $bundle['label'] = $label->getValue();
      } catch (LibRDF_LookupError $e) {
        // No label set, ignore
      }
      if (isset($class['label'])) {
        $bundle['label'] = $class['label']->getValue();
      }
      $bundles[$bundle_name] = $bundle;
    }
    return $bundles;
  }

  /**
   * Get the main bundles of the AP, i.e. those that can be created
   * autonomously.
   *
   * @return array The names of the top bundles
   */
  public function getTopBundles() {
    $RDF = new LibRDF_NS('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
    $RDFS = new LibRDF_NS('http://www.w3.org/2000/01/rdf-schema#');
    $OWL = new LibRDF_NS('http://www.w3.org/2002/07/owl#');

    $query_string = "
      SELECT ?list WHERE {
        _:type {$RDFS->subPropertyOf} {$RDF->type} .
        _:type {$RDFS->range} [
          a {$OWL->Class} ;
          {$OWL->oneOf} ?list ;
        ] .
      }
    ";
    $query = new LibRDF_Query($query_string, null, 'sparql');
    $classes = $query->execute($this->applicationProfileRDF);

    $top_bundles = array();
    foreach ($classes as $class) {
      $bundle_list = $this->applicationProfileRDF->getListAsArray($class['list']);
      foreach ($bundle_list as $bundle) {
        $top_bundles[] = $this->getBundleName($bundle);
      }
    }

    return $top_bundles;

  }

  /**
   * Map class URI to Drupal-compliant bundle name
   *
   * @param  LibRDF_URINode  $class_uri
   * @return string The bundle name
   */
  public function getBundleName(LibRDF_URINode $class_uri) {
    return $class_uri->getLocalPart();
  }

  /**
   * Get an array of field instance definitions suitable to be passed to
   * Drupal's field_create_instance.
   *
   * @return The field instances defined in the application profile
   */
  public function getFieldInstances() {
    $RDFS = new LibRDF_NS('http://www.w3.org/2000/01/rdf-schema#');
    $OWL = new LibRDF_NS('http://www.w3.org/2002/07/owl#');
    $XSD = new LibRDF_NS('http://www.w3.org/2001/XMLSchema#');

    $instances = array();

    $query_string = "
      SELECT DISTINCT ?property, ?class WHERE {
        {
          ?class {$RDFS->subClassOf} _:restriction .
          _:restriction a {$OWL->Restriction} .
          _:restriction {$OWL->onProperty} ?property .
          ?property {$RDFS->isDefinedBy} {$this->applicationProfileURI} .
        } UNION {
          ?property {$RDFS->domain} ?class .
          ?property {$RDFS->isDefinedBy} {$this->applicationProfileURI} .
        }
      }
    ";

    $query = new LibRDF_Query($query_string, null, 'sparql');
    $defined_instances = $query->execute($this->applicationProfileRDF);
    foreach ($defined_instances as $defined_instance) {
      $field_name = $this->getFieldName($defined_instance['property']);
      $bundle = $this->getBundleName($defined_instance['class']);
      $instance = array(
        'field_name' => $field_name,
        'bundle' => $bundle,
        'entity_type' => $this->applicationProfileEntityType,
      );
      try {
        $label = $this->applicationProfileRDF->getTarget(
          $defined_instance['property'], $RDFS->label
        );
        $instance['label'] = $label->getValue();
      } catch (LibRDF_LookupError $e) {
        // No label set, ignore
      }
      try {
        $allowed_values = $this->applicationProfileRDF->getTargets(
          $defined_instance['property'], $RDFS->range
        );
        foreach ($allowed_values as $allowed_value) {
          // Only allow bundles from the AP as targets
          if ((!$allowed_value instanceof LibRDF_URINode) ||
              (!$allowed_value->getNamespace()->isEqual(
               $this->applicationProfileURI->getNamespace()))
          ) continue;
          $target_bundle = $this->getBundleName($allowed_value);
          // Non top-level bundles are embedded within their parent,
          // top-level bundles can be linked to
          if (!in_array($target_bundle, $this->getTopBundles())) {
            $instance['settings']['handler_settings']['target_bundles'][$target_bundle] = array(
              'embed' => true,
              'create' => true,
            );
          } else {
            $instance['settings']['handler_settings']['target_bundles'][$target_bundle] = array (
              'lookup' => true,
            );
          }
        }
      } catch (LibRDF_LookupError $e) {
        // No target bundle set via rdfs:range, ignore
      }
      try {
        $restriction = $this->applicationProfileRDF->getSource(
          $OWL->onProperty, $defined_instance['property']
        );
        $allowed_values = $this->applicationProfileRDF->getTargets(
          $restriction, $OWL->allValuesFrom
        );
        foreach ($allowed_values as $allowed_value) {
          $target_bundle = $this->getBundleName($allowed_value);
          $instance['settings']['handler_settings']['target_bundles'][$target_bundle] = array (
            'create' => true,
            'lookup' => true,
          );
        }
      } catch (LibRDF_LookupError $e) {
        // No target bundle set via owl:Restriction, ignore
      }
      $instances[] = $instance;
    }

    return $instances;

  }

  /**
   * Returns superclasses and superproperties in the application profile
   * as RDF mappings
   *
   */
  public function getRdfMappings() {
    $RDFS = new LibRDF_NS('http://www.w3.org/2000/01/rdf-schema#');
    $OWL = new LibRDF_NS('http://www.w3.org/2002/07/owl#');
    $mappings = array();
    $fields = $this->getFields();
    $field_instances = $this->getFieldInstances();
    foreach (array_keys($this->getBundles()) as $bundle) {
      $mapping = array();
      $mapping['type'] = $this->applicationProfileEntityType;
      $mapping['bundle'] = $bundle;
      $class = $this->getClass($bundle);
      $type_mappings = $this->applicationProfileRDF->getTargets(
        $class, $RDFS->subClassOf
      );
      foreach ($type_mappings as $type_mapping) {
        if ($type_mapping instanceof LibRDF_URINode) {
          $mapping['mapping']['rdftype'][] =
            substr($type_mapping, 1, -1);
        }
      }
      foreach ($field_instances as $field_instance) {
        if ($field_instance['bundle'] == $bundle) {
          foreach ($fields as $field) {
            if ($field_instance['field_name'] == $field['field_name']) break;
          }
          $property = $this->getProperty($field_instance['field_name']);
          $property_mappings = $this->applicationProfileRDF->getTargets(
            $property, $RDFS->subPropertyOf
          );
          foreach ($property_mappings as $property_mapping) {
            $mapping['mapping'][$field_instance['field_name']]['predicates'][]
              = substr($property_mapping, 1, -1);
          }
          if ($field['type'] == $this->applicationProfileReferenceFieldType) {
            $mapping['mapping'][$field_instance['field_name']]['type'] = 'rel';
          }
        }
      }
      if (isset($mapping['mapping'])) {
        $mappings[] = $mapping;
      }
    }
    return $mappings;
  }

  /**
   * Creates fields and field instances defined in the application
   * profile.
   *
   */
  public function install() {
    // Create fields
    foreach ($this->getFields() as $field) {
      field_create_field($field);
    }
    // Create field instances
    foreach ($this->getFieldInstances() as $field_instance) {
      field_create_instance($field_instance);
    }
    // Create RDF mappings
    foreach ($this->getRdfMappings() as $rdf_mapping) {
      rdf_mapping_save($rdf_mapping);
    }
  }

  /**
   * Updates fields and field instances defined in the application
   * profile.
   *
   */
  public function update() {
    // Update fields
    foreach ($this->getFields() as $field) {
      field_update_field($field);
    }
    // Update field instances
    foreach ($this->getFieldInstances() as $field_instance) {
      field_update_instance($field_instance);
    }
    // Update RDF mappings
    foreach ($this->getRdfMappings() as $rdf_mapping) {
      rdf_mapping_save($rdf_mapping);
    }
  }

  /**
   * Deletes fields and field instances defined in the application
   * profile.
   *
   */
  public function uninstall() {
    // Delete RDF mappings
    foreach (array_keys($this->getBundles()) as $bundle) {
      rdf_mapping_delete($this->applicationProfileEntityType, $bundle);
    }
    $batch_size = 1;
    // Delete field instances
    foreach ($this->getFieldInstances() as $field_instance) {
      field_delete_instance($field_instance, true);
      $batch_size++;
    }
    // Delete fields
    foreach ($this->getFields() as $field) {
      field_delete_field($field['field_name']);
      $batch_size++;
    }
    field_purge_batch($batch_size);
  }

  /**
   * Get the application profile for the given entity type
   *
   * @return ApplicationProfile The application profile
   */
  public static function get($entity_type) {
    return self::$instances[$entity_type];
  }

  /**
   * Get all currently defined application profiles
   *
   * @return array The application profiles
   */
  public static function getAll() {
    return self::$instances;
  }

  /**
   * Add an application profile
   *
   * @param  LibRDF_URINode $ap_uri
   * @param  LibRDF_Model   $ap_model
   * @param  string         $ap_type
   */
  public static function add($ap_uri, $ap_model, $ap_type, $ap_link_field) {
    self::$instances[$ap_type] = new ApplicationProfile(
      $ap_uri, $ap_model, $ap_type, $ap_link_field
    );
  }

}

/**
 * Converts a string from camelCase to snake_case.
 *
 * @param  string  $word The string to convert
 * @return string  The string in snake_case
 */
function decamelize($word) {
  return preg_replace(
    '/(^|[a-z])([A-Z])/e',
    'strtolower(strlen("\\1") ? "\\1_\\2" : "\\2")',
    $word
  );
}

/**
 * Converts a string from snake_case to camelCase
 *
 * @param  string  $word The string to convert
 * @return string  The string in camelCase
 */
function camelize($word) {
  return preg_replace('/(^|_)([a-z])/e', 'strtoupper("\\2")', $word);
}
