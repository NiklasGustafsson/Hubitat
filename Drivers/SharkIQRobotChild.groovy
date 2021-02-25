metadata {
	definition (name: "Shark IQ Robot Room Child", namespace: "cstevens", author: "Niklas Gustafsson") {
		capability "Switch"
	}

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable", [value:"false",type:"bool"])
}

def installed() {
    updated()
}

def updated() {
    if (logEnable) runIn(1800,logsOff)
}

def on() {
    getParent().cleanSpecificRoom(device.displayName)
    eventSender("switch", "on", true)
}
 
def off() {
    getParent().off()
    sendOffEvent()
}

// This is here because the parent device should notify all child devices
// that it's being turned off. To avoid causing recursion, the parent must not
// call off(), just the event signaller.
def sendOffEvent() {
    logging("d", "sendOffEvent called by parent device")
    eventSender("switch", "off", true)
}

def logging(String status, String description) {
    if (logEnable && status == "d"){ log.debug(description) }
    else if (status == "i"){ log.info(description) }
    else if (status == "w"){ log.warn(description) }
    else if (status == "e"){ log.error(description) }
}

def eventSender(String name, String value, Boolean display)
{
    sendEvent(name: "$name", value: "$value", display: "$display", displayed: "$display")
}