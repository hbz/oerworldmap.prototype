(function($) {

  Drupal.behaviors.ldf = {
    attach: function (context, settings) {
      $(context).find('fieldset.ldf_field_type').find('a.fieldset-title').each(function(i, element) {
        var link = $(element).closest('fieldset').children('div.fieldset-wrapper').children('input[type=hidden]').get(0);
        if (link) {
          var throbber = $('<div class="ajax-progress"><div class="throbber">&nbsp;</div></div>')
          $(element).after(throbber);
          entity_render_view('lde', encodeURIComponent(encodeURIComponent(link.value))).onload = function () {
            if (this.status == 200) {
              console.log($(this.responseText));
              var entity_view = $(this.responseText);
              $(element).text(link);
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

