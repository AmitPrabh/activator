/**
 * Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
require.config({
  baseUrl:  '/public',
});

var vendors = [
  'lib/jquery/jquery',
  'lib/knockout/knockout',
  'css',
  'text',
  'lib/ace/src/ace'
]

var commons = [
  'commons/templates',
  'commons/effects',
  'commons/utils',
  'commons/settings',
  'commons/stream'
]

var services = [
  'services/sbt'
]

var core = [
  'main/view',
  'main/router',
  'commons/websocket'
]

require(vendors, function($, ko) {
  window.ko = ko; // it's used on every page...
  require(commons, function() {
    require(services, function(WS, sbt) {
      require(core, function(view, router, WS) {

        view.render();
        router.load(window.location.hash);

        WS.connect();

      })
    })
  })
})
