(function($) {
  String.prototype.endsWith = function(suffix) {
    return this.indexOf(suffix, this.length - suffix.length) !== -1;
  };
  $(document).ready(function() {
    var map = L.map('oerworldmap').setView([0, 0], 1);

    var type_facets = [
      'http://schema.org/Organization',
      'http://schema.org/Person',
      'http://schema.org/Service',
      'http://schema.org/Project'
    ];
    var country_facets = [];

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

    var FilterControl = L.Control.extend({
    options: {
        position: 'topright'
    },

    onAdd: function (map) {
        var control = $('<div />');

        var type_filter = $('<ul />');
        type_filter.prepend($('<h3>Types</h3>'));
        $.each(type_facets, function (i, facet) {
          var checkbox = $('<input type="checkbox" />').val(facet).bind('click', apply_filters);
          type_filter.append($('<label><span>' + facet + '</span></label>').prepend(checkbox));
        });
        control.append(type_filter);

        var country_filter = $('<ul />');
        country_filter.prepend($('<h3>Countries</h3>'));
        $.each(country_facets, function (i, facet) {
          var checkbox = $('<input type="checkbox" />').val(facet).bind('click', apply_filters);
          country_filter.append($('<label><span>' + facet + '</span></label>').prepend(checkbox));
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

        return control[0];
    }
    });

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
          } else if (type_facets.indexOf(resource['@type']) > -1) {
            marker.on('click', function(e) {
              entity_render_view('lde', encodeURIComponent(encodeURIComponent(resource['@id']))).onload = function () {
                if (this.status == 200) {
                  var entity_view = $(this.responseText);
                  popup.setContent(this.responseText);
                  // FIXME: Why are behaviours not attached?
                  Drupal.attachBehaviors(entity_view);
                  marker.off('click', e);
                }
              };
            });
            marker.oer_type = resource['@type'];
          } else if (country = resource['http://schema.org/addressCountry']) {
            if (country_facets.indexOf(country[0]) == -1) {
              country_facets.push(country[0]);
            }
            marker.oer_country = country[0];
          }
        });
        if (marker.getLatLng()) {
          marker.addTo(map);
          markers.push(marker);
        }
      });
      map.addControl(new FilterControl());
    });
  });
})(jQuery);
