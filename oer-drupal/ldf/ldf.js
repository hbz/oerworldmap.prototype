(function($) {

  Drupal.behaviors.ldf = {
    attach: function (context, settings) {
      $(context).find('fieldset.ldf_field_type').find('a.fieldset-title').each(function(i, element) {
        var link = $(element).closest('fieldset').children('div.fieldset-wrapper').children('input[type=hidden]').get(0);
        if (link) {
          var throbber = $('<div class="ajax-progress"><div class="throbber">&nbsp;</div></div>')
          $(element).after(throbber);
          entity_render_view('lde', link.value).onload = function () {
            if (this.status == 200) {
              var entity_view = $(this.responseText);
              $(element).text($.trim(entity_view.find('h2').text()));
              var download_link = entity_view.find('div[property="regal:hasData"]').children('a').clone();
              if (download_link.get(0)) {
                var mime_type = entity_view.find('div[property="dc:format"]').text().split('/')[1];
                var icon = $('<img />')
                  .attr('src', Drupal.settings.ldf.basePath + '/' + mime_type + '.svg')
                  .css('height', '1em');
                $(element).siblings(":last").after(download_link.text('Download ').append(icon));
              }
              $(link).replaceWith(entity_view);
              $(element).bind('click', function(event) {
                Drupal.attachBehaviors(entity_view);
                $(element).unbind(event);
              });
            }
            throbber.remove();
          };
        }
      });
    }
  };

})(jQuery);

