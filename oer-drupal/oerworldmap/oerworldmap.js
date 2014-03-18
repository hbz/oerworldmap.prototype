(function($) {
  $(document).ready(function() {
    var map = L.map('oerworldmap').setView([51.505, -0.09], 1);
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
    $.getJSON(requestUrl , function(result) {
      $.each(result, function(i, match) {
        var marker = L.marker();
        var popup = $('<div />');
        if (match['@graph']) $.each(match['@graph'], function (j, resource) {
          if (resource.latitude && resource.longitude) {
            var coordinates = new L.LatLng(resource.latitude[0], resource.longitude[0]);
            marker.setLatLng(coordinates);
          } else {
            if (resource.name) {
              popup.append('<h1>' + resource.name[0] + '</h1>');
            }
            if (resource.description) {
              popup.append('<p>' + resource.description[0] + '</p>');
            }
            if (resource.url) {
              popup.append($('<a>' + resource.url[0] + '</a>')
                .attr('href', resource.url[0]));
            }
          }
        });
        if (marker.getLatLng()) {
          marker.bindPopup(popup.get(0));
          marker.addTo(map);
        }
      });
    });
  });
})(jQuery);
