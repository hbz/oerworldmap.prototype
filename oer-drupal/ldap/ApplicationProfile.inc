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
    $RDF = new LibRDF_NS('http://www.w3.org/1999/02/22-rdf-syntax-ns#');
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
      if ($property['type']->isEqual($OWL->DatatypeProperty)) {
        try {
          $range = $this->applicationProfileRDF->getTarget(
            $property['uri'], $RDFS->range
          );
          if ($range->isEqual($RDF->HTML)) {
            $field['type'] = 'text_long';
          } else {
            $field['type'] = 'text';
          }
        } catch (LibRDF_LookupError $e) {
          $field['type'] = 'text';
        }
      } else {
        $field['type'] = $this->applicationProfileReferenceFieldType;
      }
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
    $EX = new LibRDF_NS('http://example.org/');

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
      try {
        $instance_label_list = $this->applicationProfileRDF->getTarget(
          $class['uri'], $EX->labelProperties
        );
        $instance_label_fields = $this->applicationProfileRDF
          ->getListAsArray($instance_label_list);
        foreach ($instance_label_fields as $instance_label_field) {
          $bundle['instance labels'][] = $this->getFieldName(
            $instance_label_field
          );
        }
      } catch (LibRDF_LookupError $e) {
        // No instance label set, ignore
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
    $EX = new LibRDF_NS('http://example.org/');

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

    // Construct instances
    $instances = array();
    foreach ($defined_instances as $defined_instance) {

      $field_name = $this->getFieldName($defined_instance['property']);
      $bundle = $this->getBundleName($defined_instance['class']);
      $instance = array(
        'field_name' => $field_name,
        'bundle' => $bundle,
        'entity_type' => $this->applicationProfileEntityType,
      );

      // Set label
      try {
        $label = $this->applicationProfileRDF->getTarget(
          $defined_instance['property'], $RDFS->label
        );
        $instance['label'] = $label->getValue();
      } catch (LibRDF_LookupError $e) {
        // No label set, ignore
      }

      // Set display style
      try {
        $display = $this->applicationProfileRDF->getTarget(
          $defined_instance['property'], $EX->value
        );
        if ($display->isEqual($EX->uri)) {
          $instance['display']['default']['type'] = 'ldf_field_format_link';
        } else if ($display->isEqual($EX->image)) {
          $instance['display']['default']['type'] = 'ldf_field_format_image';
        }
      } catch (LibRDF_LookupError $e) {
        // No display settings, ignore
      }

      // Add target_bundles defined by rdfs:range of properties
      $ranges = $this->applicationProfileRDF->getTargets(
        $defined_instance['property'], $RDFS->range
      );
      $allowed_values = array();
      foreach ($ranges as $range) {
        $classLists = $this->applicationProfileRDF->getTargets(
          $range, $OWL->oneOf
        );
        foreach ($classLists as $classList) {
          $allowed_values += $this->applicationProfileRDF
                                  ->getListAsArray($classList);
        }
        if ($range instanceof LibRDF_URINode) {
          $allowed_values[] = $range;
        }
      }

      // Add target_bundles and cardinalities defined
      // by owl:Restrictions on classes
      $restrictions = $this->applicationProfileRDF->getSources(
        $OWL->onProperty, $defined_instance['property']
      );
      $cardinality = 1;
      $required = 1;
      foreach ($restrictions as $restriction) {
        // Target Bundles
        $classLists = $this->applicationProfileRDF->getTargets(
          $restriction, $OWL->allValuesFrom
        );
        foreach ($classLists as $classList) {
          $allowed_values[] = $classList;
        }
        // Cardinality
        try {
          $cardinality_property = $this->applicationProfileRDF->getTarget(
            $restriction, $OWL->cardinality
          );
          $cardinality = $cardinality_property->getValue();
        } catch (LibRDF_LookupError $e) {
          // No cardinality set, ignore
        }
        try {
          $min_cardinality_property = $this->applicationProfileRDF->getTarget(
            $restriction, $OWL->minCardinality
          );
          $min_cardinality = $min_cardinality_property->getValue();
          if ($min_cardinality == 0) {
            $required = 0;
          }
        } catch (LibRDF_LookupError $e) {
          // No cardinality set, ignore
        }
        try {
          $max_cardinality_property = $this->applicationProfileRDF->getTarget(
            $restriction, $OWL->maxCardinality
          );
          $max_cardinality = $max_cardinality_property->getValue();
          $cardinality = $max_cardinality;
        } catch (LibRDF_LookupError $e) {
          // No cardinality set, ignore
        }
      }

      // Set cardinalities on instance level
      $instance['settings']['handler_settings']['cardinality'] = $cardinality;
      $instance['settings']['handler_settings']['required'] = $required;

      // Set target bundles
      foreach ($allowed_values as $allowed_value) {
        // Only allow bundles from the AP as targets
        if (!$allowed_value->getNamespace()->isEqual(
             $this->applicationProfileURI->getNamespace())
        ) continue;
        $target_bundle = $this->getBundleName($allowed_value);

        // Allow linking
        $endpoints = $this->getEndpoints($target_bundle);
        if (!empty($endpoints)) {
          $instance['settings']['handler_settings']['target_bundles']
            [$target_bundle]['lookup'] = $endpoints;
        }

        // Non top-level bundles are embedded within their parent.
        if (!in_array($target_bundle, $this->getTopBundles())) {
          $instance['settings']['handler_settings']['target_bundles']
            [$target_bundle]['embed'] = true;
          $instance['settings']['handler_settings']['target_bundles']
            [$target_bundle]['create'] = true;
        }

        // Add further constraints
        $constraints = $this->getConstraints($target_bundle);
        if (!empty($constraints)) {
          $instance['settings']['handler_settings']['target_bundles']
            [$target_bundle]['constraints'] = $constraints;
        }
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

  public function getEndpoints($target_bundle = null) {
    $EX = new LibRDF_NS('http://example.org/');

    $query_string = "
      SELECT DISTINCT * WHERE {
        ?endpoint a {$EX->endpoint} ;
          {$EX->queryParam} ?queryParam ;
          {$EX->limitParam} ?limitParam ;
          {$EX->offsetParam} ?offsetParam ;
          {$EX->resultFormat} ?resultFormat ;
      }
    ";

    $query = new LibRDF_Query($query_string, null, 'sparql');
    $defined_endpoints = $query->execute($this->applicationProfileRDF);

    // Construct endpoints
    $endpoints = array();
    foreach ($defined_endpoints as $defined_endpoint) {
      $endpoint = array(
        'queryParam' => $defined_endpoint['queryParam']->getValue(),
        'limitParam' => $defined_endpoint['limitParam']->getValue(),
        'offsetParam' => $defined_endpoint['offsetParam']->getValue(),
        'resultFormat' => $defined_endpoint['resultFormat']->getValue(),
      );
      try {
        $type_param = $this->applicationProfileRDF->getTarget(
          $defined_endpoint['endpoint'], $EX->typeParam
        );
        $endpoint['typeParam'] = $type_param->getValue();
      } catch (LibRDF_LookupError $e) {
        // No type param set, ignore
      }
      $additional_params = $this->applicationProfileRDF->getTargets(
        $defined_endpoint['endpoint'], $EX->additionalParam
      );
      foreach ($additional_params as $additional_param) {
        $endpoint['additionalParam'][] = $additional_param->getValue();
      }
      $available_bundles = $this->applicationProfileRDF->getTargets(
        $defined_endpoint['endpoint'], $EX->supplies
      );
      foreach ($available_bundles as $available_bundle) {
        $bundle_name = $this->getBundleName($available_bundle);
        $endpoint['supplies'][] = $this->getBundleName($available_bundle);
      }
      if (!$target_bundle || in_array($target_bundle, $endpoint['supplies'])) {
        $endpoints[$defined_endpoint['endpoint']->getValue()] = $endpoint;
      }
    }
    return $endpoints;
  }

  public function getConstraints($target_bundle) {
    $RDFS = new LibRDF_NS('http://www.w3.org/2000/01/rdf-schema#');
    $OWL = new LibRDF_NS('http://www.w3.org/2002/07/owl#');
    $class = $this->getClass($target_bundle);
    $query_string = "
      SELECT * WHERE {
        {$class} {$RDFS->subClassOf} [
          {$OWL->onProperty} ?property ;
          {$OWL->hasValue} ?value
        ]
      }
    ";
    $query = new LibRDF_Query($query_string, null, 'sparql');
    $defined_constraints = $query->execute($this->applicationProfileRDF);
    $constraints = array();
    foreach ($defined_constraints as $defined_constraint) {
      $constraints[$defined_constraint['property']->getValue()]
        = $defined_constraint['value']->getValue();
    }
    return $constraints;
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

    // Keep track of deletions
    $batch_size = 0;

    // Update fields
    $installed_fields = field_info_fields();
    foreach ($this->getFields() as $field) {
      $installed_field = $installed_fields[$field['field_name']];
      try {
        if ($installed_field && ($installed_field['type'] != $field['type'])) {
          field_delete_field($field['field_name']);
          $batch_size++;
          field_create_field($field);
        } else if ($installed_field) {
          field_update_field($field);
        } else {
          field_create_field($field);
        }
        unset($installed_fields[$field['field_name']]);
      } catch (FieldException $e) {
        drupal_set_message($field['field_name'] . ':' . $e->getMessage(), 'error');
      }
    }
    // Delete remaining fields
    foreach ($installed_fields as $removed_field) {
      field_delete_field($removed_field['field_name']);
      $batch_size++;
    }

    // Update field instances
    $installed_instances = field_info_instances(
      $this->applicationProfileEntityType
    );
    foreach ($this->getFieldInstances() as $field_instance) {
      $installed_instance = $installed_instances
        [$field_instance['bundle']][$field_instance['field_name']];
      $op = (null == $installed_instance)
        ? 'field_create_instance' : 'field_update_instance';
      try {
        $op($field_instance);
        unset($installed_instances[$field_instance['bundle']]
          [$field_instance['field_name']]);
      } catch (FieldException $e) {
        drupal_set_message($e->getMessage(), 'error');
      }
    }
    // Delete remaining instances
    foreach ($installed_instances as $bundle => $removed_instances) {
      foreach ($removed_instances as $removed_instance) {
        field_delete_instance($removed_instance);
        $batch_size++;
      }
    }

    // Clear deleted fields and instances
    field_purge_batch($batch_size);

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
