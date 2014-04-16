(function($) {
  $(document).ready(function() {
    var map = L.map('oerworldmap').setView([0, 0], 2);
    var user_lang = navigator.language || navigator.userLanguage;

    var type_facets = {
      'http://schema.org/Organization' : 'Organization',
      'http://schema.org/Person' : 'Person',
    };

    var country_facets = {};
    var markers = [];
    var proxy_url = Drupal.settings.oerworldmap.proxyUrl;

    var bounding_box = [
      map.getBounds().getNorth() + ',' + map.getBounds().getWest(),
      map.getBounds().getNorth() + ',' + map.getBounds().getEast(),
      map.getBounds().getSouth() + ',' + map.getBounds().getEast(),
      map.getBounds().getSouth() + ',' + map.getBounds().getWest()
    ].join('+');

    var request_url = "http://"
      + Drupal.settings.oerworldmap.apiUrl
      + "/oer?q=*"
      + "&t=http://schema.org/Person,http://schema.org/Organization"
      + "&location=" + bounding_box
      + "&size=1000"
      + "&callback=?";

    L.tileLayer('http://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: 'Map data &copy; <a href="http://openstreetmap.org">OpenStreetMap</a> contributors, <a href="http://creativecommons.org/licenses/by-sa/2.0/">CC-BY-SA</a>',
      maxZoom: 18
    }).addTo(map);

    var buildControl = function (map) {
        var control = $('<form />');

        var type_filter = $('<fieldset><legend>Types</legend></fieldset>').append($('<ul />'));
        $.each(type_facets, function (uri, label) {
          var checkbox = $('<input type="checkbox" />').val(uri).bind('click', apply_filters);
          type_filter.append($('<label><span style="padding-left: 0.5em;">' + label + '</span></label>').prepend(checkbox));
        });
        control.append(type_filter);

        var country_filter = $('<fieldset><legend>Countries</legend></fieldset>')
          .append($('<ul />'));
        $.each(country_facets, function (uri, label) {
          var checkbox = $('<input type="checkbox" />').val(uri).bind('click', apply_filters);
          country_filter.append($('<label><span style="padding-left: 0.5em;">' + label + '</span></label>').prepend(checkbox));
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

    var OerIcon = L.Icon.extend({
      options: {
          iconSize:     [26, 33],
          iconAnchor:   [13, 33],
      }
    });
    var organizationIcon = new OerIcon({
      iconUrl: Drupal.settings.oerworldmap.basePath + '/img/marker-icon-organization.png'
    });
    var personIcon = new OerIcon({
      iconUrl: Drupal.settings.oerworldmap.basePath + '/img/marker-icon-person.png'
    });

    $.getJSON(request_url , function(result) {
      $.each(result, function(i, match) {
        var marker = L.marker();
        var popup = L.popup({'maxHeight': 500, 'maxWidth': 400});
        marker.bindPopup(popup);
        if (match['@graph']) $.each(match['@graph'], function (j, resource) {
          if (resource.latitude && resource.longitude) {
            var coordinates = new L.LatLng(resource.latitude[0], resource.longitude[0]);
            marker.setLatLng(coordinates);
          } else if (resource['@type'] in type_facets) {
            if (resource['@type'] == "http://schema.org/Person") {
              marker.setIcon(personIcon);
            } else {
              marker.setIcon(organizationIcon);
            }
            marker.on('click', function(e) {
              popup.setContent('<div class="ajax-progress"><div class="throbber">&nbsp;</div></div>');
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
            var country_uri = resource['addressCountry'][0];
            if (!(country_uri in country_facets)) {
              var label;
              $.get(proxy_url + country_uri, function(result) {
                parser = new DOMParser();
                data = parser.parseFromString( result, "text/xml" );
                var rdf = $.rdf().load(data, {});
                rdf.prefix('gn', 'http://www.geonames.org/ontology#');
                rdf.where('<' + country_uri + '> gn:officialName ?name').each(function() {
                  if (this.name.lang == user_lang) {
                    label = this.name.value.substring(1, this.name.value.length - 1);
                    return false;
                  }
                });
                if (!label) rdf.where('<' + country_uri + '> gn:alternateName ?name').each(function() {
                  if (this.name.lang == user_lang) {
                    label = this.name.value.substring(1, this.name.value.length - 1);
                    return false;
                  }
                });
                if (!label) rdf.where('<' + country_uri + '> gn:officialName ?name').each(function() {
                  if (this.name.lang == undefined) {
                    label = this.name.value;
                    return false;
                  }
                });
                $('span:contains("' + country_uri + '")').text(label);
                country_facets[country_uri] = label;
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
