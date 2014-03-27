(function($) {
  String.prototype.endsWith = function(suffix) {
    return this.indexOf(suffix, this.length - suffix.length) !== -1;
  };
  $(document).ready(function() {
    var map = L.map('oerworldmap').setView([0, 0], 1);

    var type_facets = {
      'http://schema.org/Organization' : 'Organization',
      'http://schema.org/Person' : 'Person',
      'http://schema.org/Service' : 'Service',
      'http://schema.org/Project' : 'Project'
    };
    var country_facets = {};

    var bounding_box = [
      map.getBounds().getNorth() + ',' + map.getBounds().getWest(),
      map.getBounds().getNorth() + ',' + map.getBounds().getEast(),
      map.getBounds().getSouth() + ',' + map.getBounds().getEast(),
      map.getBounds().getSouth() + ',' + map.getBounds().getWest()
    ].join('+');
    var requestUrl = "http://"
      + Drupal.settings.oerworldmap.apiUrl
      + "/oer?q=*"
      + "&location="
      + bounding_box
      + "&callback=?";

    L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>',
      maxZoom: 18
    }).addTo(map);

    var buildControl = function (map) {
        var control = $('<form />');

        var type_filter = $('<ul />');
        type_filter.prepend($('<h3>Types</h3>'));
        $.each(type_facets, function (uri, label) {
          var checkbox = $('<input type="checkbox" />').val(uri).bind('click', apply_filters);
          type_filter.append($('<label><span>' + label + '</span></label>').prepend(checkbox));
        });
        control.append(type_filter);

        var country_filter = $('<ul style="columns: 3; -webkit-columns: 3; -moz-columns: 3;" />');
        country_filter.prepend($('<h3>Countries</h3>'));
        $.each(country_facets, function (uri, label) {
          var checkbox = $('<input type="checkbox" />').val(uri).bind('click', apply_filters);
          country_filter.append($('<label><span>' + label + '</span></label>').prepend(checkbox));
        });
        control.append(country_filter);

        function apply_filters(e) {
            var selected_types = [];
            type_filter.find('input[type="checkbox"]:checked').each(function() {
              selected_types.push($(this).val());
            });
            var selected_countries = [];
            country_filter.find('input[type="checkbox"]:checked').each(function() {
              selected_countries.push($(this).val());
            });
            $.each(markers, function(i, marker) {
              var type_match = ((selected_types.indexOf(marker.oer_type) > -1)
                || (selected_types.length == 0));
              var country_match = ((selected_countries.indexOf(marker.oer_country) > -1)
                || (selected_countries.length == 0));
              if (type_match && country_match) {
                if (!map.hasLayer(marker)) {
                  map.addLayer(marker);
                }
              } else {
                if (map.hasLayer(marker)) {
                  map.removeLayer(marker);
                }
              }
            });
        }

        return control;
    }

    var proxy_url = Drupal.settings.oerworldmap.proxyUrl;
    var markers = [];
    $.getJSON(requestUrl , function(result) {
      $.each(result, function(i, match) {
        var marker = L.marker();
        var popup = L.popup({'maxHeight': 500, 'maxWidth': 400, 'autoPan': false});
        marker.bindPopup(popup);
        if (match['@graph']) $.each(match['@graph'], function (j, resource) {
          if (resource.latitude && resource.longitude) {
            var coordinates = new L.LatLng(resource.latitude[0], resource.longitude[0]);
            marker.setLatLng(coordinates);
          } else if (resource['@type'] in type_facets) {
            marker.on('click', function(e) {
              entity_render_view('lde', encodeURIComponent(encodeURIComponent(resource['@id']))).onload = function () {
                if (this.status == 200) {
                  var entity_view = $(this.responseText);
                  Drupal.attachBehaviors(entity_view);
                  popup.setContent(entity_view[0]);
                  marker.off('click', e);
                }
              };
            });
            marker.oer_type = resource['@type'];
          } else if (resource['addressCountry']) {
            //FIXME: hardcoded trailing slash until proper uri is
            //available
            var country_uri = resource['addressCountry'][0] + '/';
            if (!(country_uri in country_facets)) {
              $.get(proxy_url + country_uri, function(result) {
                parser = new DOMParser();
                data = parser.parseFromString( result, "text/xml" );
                var rdf = $.rdf().load(data, {});
                rdf.prefix('gn', 'http://www.geonames.org/ontology#');
                rdf.where('<' + country_uri + '> gn:officialName ?name').each(function() {
                  // Default labels have no language tag
                  if (this.name.lang == undefined) {
                    $('span:contains("' + country_uri + '")').text(this.name.value.toString());
                    country_facets[country_uri] = this.name.value.toString();
                  }
                });
              });
              country_facets[country_uri] = country_uri;
            }
            marker.oer_country = country_uri;
          }
        });
        if (marker.getLatLng()) {
          marker.addTo(map);
          markers.push(marker);
        }
      });
      $('#oerworldmap').after(buildControl(map));
    });
  });
})(jQuery);
