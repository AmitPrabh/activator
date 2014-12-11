/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(['commons/utils',
  'commons/websocket',
  'commons/settings',
  './monitoringSolutions'
], function(utils, websocket, settings, monitoringSolutions) {

  var nodeName = settings.observable("appDynamics.nodeName", "activator-"+new Date().getTime());
  var tierName = settings.observable("appDynamics.tierName", "development");
  var hostName = settings.observable("appDynamics.hostName", "");
  var port = settings.observable("appDynamics.port", 443);
  var sslEnabled = settings.observable("appDynamics.sslEnabled", true);
  var accountName = settings.observable("appDynamics.accountName", "");
  var accessKey = settings.observable("appDynamics.accessKey", "");
  var available = ko.observable(false);
  var projectEnabled = ko.observable(false);

  function adMessage(type) {
    return { request: 'AppDynamicsRequest', type: type };
  }

  function adMessageWith(type,attributes) {
    return jQuery.extend(adMessage(type), attributes);
  }

  var validNodeName = /^[0-9a-z@\._-]{1,40}$/i;
  var validTierName = /^[0-9a-z@\._-]{1,40}$/i;
  var validUsername = /^.{1,40}$/i;
  var validPassword = /^[0-9a-z@\.,-\/#!$%\^&\*;:{}=\-_`~()]{1,40}$/i;
  var validPort = {
    test: function(v) {
      var n = Number(v);
      return (n > 0) && (n < 65536);
    }
  };
  var validAccountName = validNodeName;
  var validAccessKey = /^[0-9a-z]{12}$/i;
  var validHostName = /^[0-9a-z][0-9a-z\.\-$*_]{1,128}/i;

  var configured = ko.computed(function () {
    return (validNodeName.test(nodeName()) &&
      validTierName.test(tierName()) &&
      validPort.test(port()) &&
      validAccountName.test(accountName()) &&
      validAccessKey.test(accessKey()) &&
      validHostName.test(hostName()));
  });

  var stream = monitoringSolutions.stream.matchOnAttribute('subtype', 'appdynamics');

  stream.map(function (response) {
    var event = response.event;
    if (event.type === "availableResponse") {
      debug && console.log("setting available to: ",event.result);
      available(event.result);
    } else if (event.type === "provisioned") {
      debug && console.log("AppDynamics provisioned");
      send(adMessage("isAvailable"));
    } else if (event.type === "deprovisioned") {
      debug && console.log("AppDynamics de-provisioned");
      send(adMessage("isAvailable"));
    } else if (event.type === "projectEnabledResponse") {
      debug && console.log("Setting projectEnabled to: " + event.result);
      projectEnabled(event.result);
      if (event.result) {
        monitoringSolutions.addAppDynamics();
      }
    }
  });

  var send = function (msg){
    websocket.send(msg);
  }

  var provision = function (username, password) {
    send(adMessageWith("provision",{username: username, password: password}))
  };

  var deprovision = function() {
    send(adMessage("deprovision"));
  };

  var setObserveProvision = function (callback) {
    monitoringSolutions.provisioningProgress.set(callback);
  };

  var unsetObserveProvision = function () {
    monitoringSolutions.provisioningProgress.reset();
  }

  var nodeNameSaved = ko.computed(function() {
    var name = nodeName();
    return validNodeName.test(name);
  });

  var tierNameSaved = ko.computed(function() {
    var name = tierName();
    return validTierName.test(name);
  });

  var enableProject = function () {
    projectEnabled(true);

    send(adMessageWith("generateFiles", {
      location: serverAppModel.location,
      applicationName: "n/a",
      nodeName: nodeName(),
      tierName: tierName(),
      accountName: accountName(),
      accessKey: accessKey(),
      hostName: hostName(),
      port: port(),
      sslEnabled: sslEnabled()
    }));
  };

  var init = function() {
    debug && console.log("Making initial request to check AD availability");
    send(adMessage("isAvailable"));
    send(adMessage("isProjectEnabled"))
  };

  init();

  return {
    validNodeName: validNodeName,
    validTierName: validTierName,
    validUsername: validUsername,
    validPassword: validPassword,
    validPort: validPort,
    validAccountName: validAccountName,
    validAccessKey: validAccessKey,
    validHostName: validHostName,
    hostName: hostName,
    port: port,
    sslEnabled: sslEnabled,
    accountName: accountName,
    accessKey: accessKey,
    nodeName: nodeName,
    tierName: tierName,
    configured: configured,
    available: available,
    provision: provision,
    deprovision: deprovision,
    setObserveProvision: setObserveProvision,
    unsetObserveProvision: unsetObserveProvision,
    nodeNameSaved: nodeNameSaved,
    tierNameSaved: tierNameSaved,
    enableProject: enableProject,
    projectEnabled: projectEnabled
  };
});
