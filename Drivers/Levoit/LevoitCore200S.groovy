metadata {
    definition(
        name: "Levoit Core200S Air Purifier",
        namespace: "NiklasGustafsson",
        author: "Niklas Gustafsson",
        description: "Supports controlling the Levoit 200S / 300S air purifiers",
        category: "My Apps",
        iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
        iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
        iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
        documentationLink: "https://github.com/dcmeglio/hubitat-bond/blob/master/README.md")
        {
            capability "Switch"
            capability "FanControl"
            capability "Actuator"

            attribute "filter", "number";                              // Filter status (0-100%)
            attribute "mode", "string";                                // Purifier mode 
            
            attribute "info", "string";                               // HTML

            command "setDisplay", [[name:"Display*", type: "ENUM", description: "Display", constraints: ["on", "off"] ] ]
            command "setSpeed", [[name:"Speed*", type: "ENUM", description: "Speed", constraints: ["off", "low", "medium", "high"] ] ]
            command "setMode",  [[name:"Mode*", type: "ENUM", description: "Mode", constraints: ["manual", "sleep"] ] ]
            command "toggle"
        }

    preferences {
		input("refreshInterval", "number", title: "<font style='font-size:12px; color:#1a77c9'>Refresh Interval</font>", description: "<font style='font-size:12px; font-style: italic'>Poll VeSync status every N seconds</font>", required: true, defaultValue: 30)
        input("debugOutput", "bool", title: "Enable debug logging?", defaultValue: false, required: false)
    }
}

def installed() {
	logDebug "Installed with settings: ${settings}"
    updated();
}

def updated() {
	logDebug "Updated with settings: ${settings}"

    state.clear()
    unschedule()
	initialize()

    runIn(3, update)

    // Turn off debug log in 30 minutes
    if (settings?.debugOutput) runIn(1800, logDebugOff);
}

def uninstalled() {
	logDebug "Uninstalled app"
}

def initialize() {
	logDebug "initializing"
}

def on() {
    logDebug "on()"
	handlePower(true)
    device.sendEvent(name: "switch", value: "on")

	if (state.speed != null) {
        setSpeed(state.speed)
	}
    else {
        setSpeed("low")
    }

    if (state.mode != null) {
        setMode(state.mode)
    }
    else {
        update()
    }
}

def off() {
    logDebug "off()"
	handlePower(false)
    device.sendEvent(name: "switch", value: "off")
}

def toggle() {
    logDebug "toggle()"
	if (device.currentValue("switch") == "on")
		off()
	else
		on()
}

def cycleSpeed() {
    logDebug "cycleSpeed()"

    def speed = (state.speed == "low") ? "medium" : ( (state.speed == "medium") ? "high" : "low")
    
    if (state.switch == "off")
    {
        on()
    }
    setSpeed(speed)
}

def setSpeed(speed) {
    logDebug "setSpeed(${speed})"
    if (speed == "off") {
        off()
    }
    else if (speed == "sleep") {
        setMode(speed)
        device.sendEvent(name: "speed", value: "on")
    }
    else if (state.mode == "manual") {
        handleSpeed(speed)
        state.speed = speed
        device.sendEvent(name: "speed", value: speed)
    }
}

def setMode(mode) {
    logDebug "setMode(${mode})"
    handleMode(mode)
    state.mode = mode
	device.sendEvent(name: "mode", value: mode)
}

def setDisplay(displayOn) {
    logDebug "setDisplay(${displayOn})"
    handleDisplayOn(displayOn)
}

def mapSpeedToInteger(speed) {
    return (speed == "low") ? 1 : ( (speed == "medium") ? 2 : 3)
}

def mapIntegerStringToSpeed(speed) {
    return (speed == "1") ? "low" : ( (speed == "2") ? "medium" : "high")
}

def mapIntegerToSpeed(speed) {
    return (speed == 1) ? "low" : ( (speed == 2) ? "medium" : "high")
}

def logDebug(msg) {
    if (settings?.debugOutput) {
		log.debug msg
	}
}

void logDebugOff() {
  //
  // runIn() callback to disable "Debug" logging after 30 minutes
  // Cannot be private
  //
  if (settings?.debugOutput) device.updateSetting("debugOutput", [type: "bool", value: false]);
}

def handlePower(on) {

    def result = false

    parent.sendBypassRequest(device, [
                data: [ enabled: on, id: 0 ],
                "method": "setSwitch",
                "source": "APP" ]) { resp ->
			if (checkHttpResponse("handleOn", resp))
			{
                def operation = on ? "ON" : "OFF"
                logDebug "turned ${operation}()"
				result = true
			}
		}
    return result
}

def handleSpeed(speed) {

    def result = false

    parent.sendBypassRequest(device, [
                data: [ level: mapSpeedToInteger(speed), id: 0, type: "wind" ],
                "method": "setLevel",
                "source": "APP"
            ]) { resp ->
			if (checkHttpResponse("handleSpeed", resp))
			{
                logDebug "Set speed"
				result = true
			}
		}
    return result
}

def handleMode(mode) {

    def result = false

    parent.sendBypassRequest(device, [
                data: [ "mode": mode ],
                "method": "setPurifierMode",
                "source": "APP"
            ]) { resp ->
			if (checkHttpResponse("handleMode", resp))
			{
                logDebug "Set mode"
				result = true
			}
		}
    return result
}

def update() {

    logDebug "update()"

    def result = false

    parent.sendBypassRequest(device,  [
                "method": "getPurifierStatus",
                "source": "APP"
            ]) { resp ->
			if (checkHttpResponse("update", resp))
			{
                handleUpdateStatus(resp, null)
			}
		}
    return result
}

private void handleUpdateStatus(resp, data)
{
    logDebug "handleUpdateStatus()"

	try
	{
        if (checkHttpResponse("handleUpdateStatus", resp))
        {
            def status = resp.data.result

            logDebug status

            state.speed = mapIntegerToSpeed(status.result.level)
            state.mode = status.result.mode

            device.sendEvent(name: "switch", value: status.result.enabled ? "on" : "off")
            device.sendEvent(name: "speed", value: state.speed)
            device.sendEvent(name: "mode", value: status.result.mode)
            device.sendEvent(name: "filter", value: status.result.filter_life)

            def html = "Filter: ${status.result.filter_life}%"
            device.sendEvent(name: "info", value: html)
        }
	}
	catch (e)
	{
        logError e.getMessage()
//		checkHttpResponse("getDevices", e.getMessage())
	}
}
def handleDisplayOn(displayOn) 
{
    logDebug "handleDisplayOn()"

    def result = false

    parent.sendBypassRequest(device, [
                data: [ "state": (displayOn == "on")],
                "method": "setDisplay",
                "source": "APP"
            ]) { resp ->
			if (checkHttpResponse("handleDisplayOn", resp))
			{
                logDebug "Set display"
				result = true
			}
		}
    return result
}


def checkHttpResponse(action, resp) {
	if (resp.status == 200 || resp.status == 201 || resp.status == 204)
		return true
	else if (resp.status == 400 || resp.status == 401 || resp.status == 404 || resp.status == 409 || resp.status == 500)
	{
		log.error "${action}: ${resp.status} - ${resp.getData()}"
		return false
	}
	else
	{
		log.error "${action}: unexpected HTTP response: ${resp.status}"
		return false
	}
}
