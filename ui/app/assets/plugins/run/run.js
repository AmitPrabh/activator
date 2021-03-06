/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/plugins",
  "services/sbt",
  "services/inspect/connection",
  'widgets/echoInstaller/echoInstaller',
  "widgets/layout/layout",
  "services/monitoring/monitoringSolutions",
  "text!./run.html",
  'widgets/modals/modals',
  'services/monitoring/newreliccontroller',
  "css!./run",
  "css!widgets/buttons/switch",
  "css!widgets/buttons/button",
  "css!widgets/menu/menu",
  "css!widgets/buttons/select"
], function(
  plugins,
  sbt,
  connection,
  echoInstaller,
  layout,
  monitoringSolutions,
  tpl,
  modals
) {

  var subPlugin = ko.observable();
  var currentPlugin;
  var inspects = ko.observable();
  var sbtExecCommand = function(cmd){
    sbt.tasks.requestExecution(cmd);
  }
  var mainRunAction = function() {
    if (sbt.tasks.pendingTasks.run()){
      sbt.tasks.actions.stopRun();
    } else {
      if (sbt.app.settings.automaticResetInspect()) {
        connection.reset();
      }
      sbt.tasks.actions.run();
    }
  }
  var mainRunName = ko.computed(function() {
    if (sbt.tasks.pendingTasks.run()) {
      return sbt.tasks.pendingTasks.stoppingRun() ? "Stopping" : "Stop";
    } else {
      return "Run";
    }
  });

  var runEnabled = ko.computed(function() {
    if (sbt.tasks.applicationReady()) {
      return !sbt.tasks.pendingTasks.stoppingRun();
    } else {
      return false;
    }
  });

  var displayMains = ko.computed(function() {
    return (sbt.tasks.applicationReady() && sbt.app.currentMainClass() && !sbt.tasks.isPlayApplication());
  });

  var runDisabled = ko.computed(function() { return !runEnabled(); });
  var playUrl = sbt.tasks.playApplicationUrl;
  var displayPlayUrl = ko.computed(function() {
    if ((playUrl() !== null) && !sbt.tasks.pendingTasks.stoppingRun() && sbt.tasks.pendingTasks.run()) {
      return true;
    } else {
      return false;
    }
  });

  var toggleInspect = function() {
    var toActivate = monitoringSolutions.inspectActivated() ? monitoringSolutions.NO_MONITORING : monitoringSolutions.INSPECT;
    monitoringSolutions.monitoringSolution(toActivate);
  }

  monitoringSolutions.monitoringSolution.subscribe(function(solution) {
    if (!monitoringSolutions.inspectActivated() && window.location.hash.indexOf("#run/system") !== 0) {
      window.location.hash = "run/system";
    }
    if(monitoringSolutions.inspectActivated()) {
      sbt.tasks.actions.kill();
      echoInstaller(function() {});
    } else {
      sbt.tasks.actions.kill();
    }
  });

  var inspectActivatedAndAvailable = ko.computed(function() {
    return monitoringSolutions.inspectActivated() && sbt.tasks.applicationReady() && sbt.tasks.inspectSupported();
  });

  // Start an empty request for echo
  connection.filters.active([]);

  function resetData(){
    connection.reset();
  }

  var State = {
    subPlugin: subPlugin,
    sbtExecCommand: sbtExecCommand,
    inspects: inspects,
    sbt: sbt,
    stats: connection.stats,
    rerunOnBuild: sbt.app.settings.rerunOnBuild,
    automaticResetInspect: sbt.app.settings.automaticResetInspect,
    showLogDebug: sbt.app.settings.showLogDebug,
    monitoringSolutions: monitoringSolutions,
    inspectActivated: monitoringSolutions.inspectActivated,
    toggleInspect: toggleInspect,
    inspectActivatedAndAvailable: inspectActivatedAndAvailable,
    mainRunAction: mainRunAction,
    mainRunName: mainRunName,
    customCommands: sbt.app.customCommands,
    runEnabled: runEnabled,
    runDisabled: runDisabled,
    displayMains: displayMains,
    displayPlayUrl: displayPlayUrl,
    playUrl: playUrl,
    resetData: resetData
  }

  // Subplugins titles
  var subPlugins = {
    system:         "Stdout",
    actors:         "Actors",
    requests:       "Requests",
    actorIssues:    "Actor Issues"
  }

  return {
    render: function(url) {
      layout.renderPlugin(ko.bindhtml(tpl, State))
    },
    route: plugins.route('run', function(url, breadcrumb, plugin) {
      subPlugin(plugin.render());
      currentPlugin = plugin;
      breadcrumb([['run/', "Run"],['run/'+url.parameters[0], subPlugins[url.parameters[0]]]]);
    }, "run/system"),

    keyboard: function(key, meta, e) {
      if (currentPlugin.keyboard) {
        currentPlugin.keyboard(key, meta, e);
      }
    }
  }
});
