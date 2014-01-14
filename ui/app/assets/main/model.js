/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(['webjars!knockout', './router', 'commons/settings', 'plugins/tutorial/tutorial', 'widgets/log/log', 'services/build', './keyboard', './omnisearch',
        './navigation', './panel'],
    function(ko, router, settings, Tutorial, log, build, keyboard, omnisearch,
        navigation, panel) {

  // Model for the whole app view; created in two parts
  // so that this first part is available during construction
  // of the second part.
  return {
    plugins: null, // filled in by init
    router: router,
    tutorial: new Tutorial(),
    settings: settings,
    snap: {
      // TODO this needs to be removed after it's no longer used
      // in application.scala.html
      testCallBinding: function(a,b,c,d){
      },
      activeWidget: ko.observable(""),
      pageTitle: ko.observable(),
      // TODO load last value from somewhere until we get a message from the iframe
      signedIn: ko.observable(false),
      app: {
        name: ko.observable(window.serverAppModel.name ? window.serverAppModel.name : window.serverAppModel.id),
        hasAkka: ko.observable(false),
        hasPlay: ko.observable(false),
        hasConsole: ko.observable(false)
      }
    },
    // make this available in knockout bindings
    omnisearch: omnisearch,
    navigation: navigation,
    panel: panel,
    logModel: new log.Log(),
    // This is the initialization of the application...
    init: function(plugins) {
      var self = this;
      self.widgets = [];
      self.plugins = plugins;

      var openSearch = function(e, ctx) {
        omnisearch.openSearch();
        return true;
      };

      var globalKeybindings = [
        [ 'ctrl-k', openSearch, { preventDefault: true } ]
      ];

      // scope '' is global scope
      keyboard.installBindingsInScope('', globalKeybindings);

      // TODO - initialize plugins in a better way perhaps...
      $.each(self.plugins.list, function(idx,plugin) {
        self.router.registerRoutes(plugin.routes);
        $.each(plugin.widgets, function(idx, widget) {
          self.widgets.push(widget);
        });
      });
      self.router.init();
      ko.applyBindings(self, window.body);
    }
  };
});
