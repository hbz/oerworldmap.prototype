(function($) {

  Drupal.behaviors.ldf = {
    attach: function (context, settings) {
      $(context).find('fieldset.ldf_field_type').find('a.fieldset-title').each(function(i, element) {
        var link = $(element).closest('fieldset').children('div.fieldset-wrapper').children('input[type=hidden]').get(0);
        if (link) {
          var throbber = $('<div class="ajax-progress"><div class="throbber">&nbsp;</div></div>')
          $(element).after(throbber);
          var encoded_uri = encodeURIComponent(encodeURIComponent(link.value));
          entity_render_view('lde', encoded_uri).onload = function () {
            if (this.status == 200 && this.responseText) {
              var entity_view = $(this.responseText);
              $(element).text($.trim(entity_view.children('h2').text()));
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
      $(context).find('input.action-button').each(function(i, element) {
        var target = $(element).closest('fieldset').children('legend').children('span');
        $(element).css('float', 'right').detach().appendTo(target);
      });
      $(context).find('input.ldf-input').each(function(i, element) {
        var submit_button = $(element).parent().next('input[type="submit"]');
        $(element).keypress(function (e) {
          if ((e.which && e.which == 13) || (e.keyCode && e.keyCode == 13)) {
            submit_button.click();
            return false;
          } else {
            return true;
          }
        });
      });
    }
  };

})(jQuery);

