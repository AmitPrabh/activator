/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(function() {

  function notCompatible(err){
    setTimeout(function() {
      document.write("<div style='margin:50px;text-align:center;'><h1>Please update your browser</h1><p>"+err+"</p></div>");
      document.execCommand('Stop'); // Kill all javascript things
    },100)
  }

  // From http://stackoverflow.com/questions/5916900/detect-version-of-browser
  function get_browser(){
    var ua=navigator.userAgent,tem,M=ua.match(/(opera|chrome|safari|firefox|msie|trident(?=\/))\/?\s*(\d+)/i) || [];
    if(/trident/i.test(M[1])){
        tem=/\brv[ :]+(\d+)/g.exec(ua) || [];
        return 'IE '+(tem[1]||'');
        }
    if(M[1]==='Chrome'){
        tem=ua.match(/\bOPR\/(\d+)/)
        if(tem!=null)   {return 'Opera '+tem[1];}
        }
    M=M[2]? [M[1], M[2]]: [navigator.appName, navigator.appVersion, '-?'];
    if((tem=ua.match(/version\/(\d+)/i))!=null) {M.splice(1,1,tem[1]);}
    return M[0];
  }
  function get_browser_version(){
    var ua=navigator.userAgent,tem,M=ua.match(/(opera|chrome|safari|firefox|msie|trident(?=\/))\/?\s*(\d+)/i) || [];
    if(/trident/i.test(M[1])){
        tem=/\brv[ :]+(\d+)/g.exec(ua) || [];
        return 'IE '+(tem[1]||'');
        }
    if(M[1]==='Chrome'){
        tem=ua.match(/\bOPR\/(\d+)/)
        if(tem!=null)   {return 'Opera '+tem[1];}
        }
    M=M[2]? [M[1], M[2]]: [navigator.appName, navigator.appVersion, '-?'];
    if((tem=ua.match(/version\/(\d+)/i))!=null) {M.splice(1,1,tem[1]);}
    return M[1];
  }

  // need websockets
  if (!('MozWebSocket' in window || 'WebSocket' in window))
    notCompatible("This browser doesn't support websockets.");

  // needs IE11
  if (get_browser() == "IE" && get_browser_version() < 11) {
    notCompatible("Activator works poorly in IE 10 and earlier.");
  }

  return {
    browser: get_browser(),
    version: get_browser_version()
  }

});
