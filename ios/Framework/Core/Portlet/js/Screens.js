const log = console.log;
console.log = message => {
	log(message);
	if (window.Screens) {
		window.Screens.postMessage("screensinternal.consoleMessage", message);
	}
};

var screens = {
	screensScripts_: [],
	addScreensScript: function (screensScript) {
		this.screensScripts_.push(screensScript);
	},

	reloadScripts: function () {
		this.screensScripts_.forEach(fn => fn());
	},

	postMessage: function (namespace, message) {
		if (window.cordova) {
			document.addEventListener("deviceready", () => {
				cordova.exec(null, null, "ScreensBridgePlugin", "postMessage", [
					namespace,
					message
				]);
			});
		} else if (window.webkit) {
			window.webkit.messageHandlers.screensDefault.postMessage([
				namespace,
				message
			]);
		}
	}
};

window.Screens = Object.create(screens);

if (window.Liferay) {
	window.Liferay.on("endNavigate", () => {
		window.Screens.reloadScripts();
	});

	// Attach a proxy to the Liferay object so we can inject our custom session
	window.Liferay = new Proxy(window.Liferay, {
		set: function (target, name, value) {
			if (name === "Session") {
				target[name] = new Liferay.SessionBase({
					autoExtend: true,
					sessionLength: 5 * 60,
					warningLength: 60
				});
			}

			target[name] = value;
		}
	});
}
