/*

MIT License

Copyright (c) Ian Luo

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/

metadata {
  definition(
    name: "Levoit LV600S Humidifier",
    namespace: "NiklasGustafsson",
    author: "Ian Luo",
    description: "Supports controlling the Levoit LV600S air purifier",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    documentationLink: "https://github.com/dcmeglio/hubitat-bond/blob/master/README.md") {
    capability "Switch"
    capability "SwitchLevel"
    capability "RelativeHumidityMeasurement"
    capability "Actuator"

    attribute "target_humidity", "number";
    attribute "mode", "string";
    attribute "warm_enabled", "boolean";
    attribute "warm_level", "number";
    attribute "automatic_stop", "boolean";
    attribute "display", "boolean";
    attribute "lacks_water", "boolean";
    attribute "water_tank_lifted", "boolean";

    command "setTargetHumidity", [
      [name: "TargetHumidity*", type: "NUMBER", description: "TargetHumidity (30 - 80)"]
    ]
    command "setDisplay", [
      [name: "Display*", type: "ENUM", description: "Display", constraints: ["on", "off"]]
    ]
    command "setAutomaticStop", [
      [name: "AutomaticStop*", type: "ENUM", description: "Automatic Stop Enabled", constraints: ["on", "off"]]
    ]
    command "setMode", [
      [name: "Mode*", type: "ENUM", description: "Mode", constraints: ["manual", "sleep", "auto"]]
    ]
    command "setMistLevel", [
      [name: "MistLevel", type: "NUMBER", description: "Mist level (1-9)"]
    ]
    command "setWarmLevel", [
      [name: "WarmLevel*", type: "NUMBER", description: "Warm Level (1-3)"]
    ]
    command "setWarmEnabled", [
      [name: "WarmEnabled*", type: "ENUM", description: "Warm Mist Enabled", constraints: ["on", "off"]]
    ]

    command "toggle"
    command "update"
  }

  preferences {
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
  handleEvent("switch", "on")

  if (state.mist_level != null) {
    setMistLevel(state.mist_level)
  } else {
    setMistLevel(1)
  }

  if (state.target_humidity != null) {
    setTargetHumidity(state.target_humidity)
  }

  if (state.mode != null) {
    setMode(state.mode)
  } else {
    update()
  }

  if (state.warm_enabled && state.warm_level != null) {
    setWarmLevel(state.warm_level)
  }

  if (state.automatic_stop != null) {
    setAutomaticStop(state.automatic_stop ? "on" : "off")
  }

  if (state.display != null) {
    setDisplay(state.display ? "on" : "off")
  } else {
    setDisplay("on")
  }
}

def off() {
  logDebug "off()"
  handlePower(false)
  handleEvent("switch", "off")
}

def toggle() {
  logDebug "toggle()"
  if (device.currentValue("switch") == "on")
    off()
  else
    on()
}

def setLevel(value) {
  logDebug "setLevel ${value}"
  mist_level = convertRange(value, 0, 100, 1, 9, true)
  setMode("manual") // always manual if setLevel() cmd was called
  setMistLevel(mist_level)
}

def setMistLevel(mist_level) {
  logDebug "setMistLevel(${mist_level})"
  handleMistLevel(mist_level)
  state.mist_level = mist_level
  handleEvent("mist_level", mist_level)
  device.sendEvent(name: "mist_level", value: mist_level)
}

def setTargetHumidity(target_humidity) {
  logDebug "setTargetHumidity(${target_humidity})"
  setMode("auto")
  handleTargetHumidity(target_humidity)
  state.target_humidity = target_humidity
  handleEvent("target_humidity", target_humidity)
}

def setMode(mode) {
  logDebug "setMode(${mode})"
  handleMode(mode)
  state.mode = mode
  handleEvent("mode", mode)
}


def setWarmEnabled(warm_enabled) {
  logDebug "setWarmEnabled(${enabled})"
  def enabled = warm_enabled == "on"

  if (enabled) {
    if (state.warm_level == null || state.warm_level < 1) {
      setWarmLevel(1)
    } else {
      setWarmEnabled(state.warm_level)
    }
  } else {
    setWarmLevel(0)
  }
  state.warm_enabled = enabled
  device.sendEvent(name: "warm_enabled", value: enabled)
}


def setWarmLevel(warm_level) {
  logDebug "setWarmLevel(${warm_level})"
  handleWarmLevel(warm_level)
  state.warm_level = warm_level
  device.sendEvent(name: "warm_level", value: warm_level)

  if (warm_level > 0) {
    state.warm_enabled = true
    device.sendEvent(name: "warm_enabled", value: true)
  } else {
    state.warm_enabled = false
    device.sendEvent(name: "warm_enabled", value: false)
  }
}

def setAutomaticStop(automatic_stop) {
  logDebug "setAutomaticStop(${automatic_stop})"
  def enabled = automatic_stop == "on"

  handleAutomaticStop(enabled)
  state.automatic_stop = enabled
  handleEvent("automatic_stop", enabled)
}

def setDisplay(display_on) {
  logDebug "setDisplay(${display_on})"
  def enabled = display_on == "on"

  handleDisplay(enabled)
  state.display = enabled
  handleEvent("display", enabled)
}

def logDebug(msg) {
  if (settings?.debugOutput) {
    log.debug msg
  }
}

def logError(msg) {
  log.error msg
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
    data: [enabled: on, id: 0],
    "method": "setSwitch",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleOn", resp)) {
        def operation = on ? "ON" : "OFF"
        logDebug "turned ${operation}()"
        result = true
      }
  }
  return result
}

def handleMistLevel(mist_level) {

  def result = false

  parent.sendBypassRequest(device, [
    data: [level: mist_level, id: 0, type: "mist"],
    "method": "setVirtualLevel",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleMistLevel", resp)) {
        logDebug "Set mist level"
        result = true
      }
  }
  return result
}

def handleWarmLevel(warm_level) {

  def result = false

  parent.sendBypassRequest(device, [
    data: [level: warm_level, id: 0, type: "warm"],
    "method": "setLevel",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleWarmLevel", resp)) {
        logDebug "Set warm level"
        result = true
      }
  }
  return result
}

def handleMode(mode) {
  def result = false
  def serialized_mode = mode
  if (mode == "auto") {
    serialized_mode = "humidity"
  }

  parent.sendBypassRequest(device, [
    data: ["mode": serialized_mode],
    "method": "setHumidityMode",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleMode", resp)) {
        logDebug "Set mode ${mode}"
        result = true
      }
  }
}

def handleAutomaticStop(automatic_stop) {
  def result = false

  parent.sendBypassRequest(device, [
    data: ["enabled": automatic_stop],
    "method": "setAutomaticStop",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleAutomaticStop", resp)) {
        logDebug "Set automatic_stop ${automatic_stop}"
        result = true
      }

    return result
  }
}

def handleDisplay(displayOn) {
  def result = false

  parent.sendBypassRequest(device, [
    data: ["state": displayOn],
    "method": "setDisplay",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleDisplay", resp)) {
        logDebug "Set display ${displayOn}"
        result = true
      }

    return result
  }
}

def handleTargetHumidity(target_humidity) {
  logDebug "handleTargetHumidity(${target_humidity})"
  def result = false

  parent.sendBypassRequest(device, [
    data: ["target_humidity": target_humidity],
    "method": "setTargetHumidity",
    "source": "APP"
  ]) {
    resp ->
      if (checkHttpResponse("handleTargetHumidity", resp)) {
        logDebug "Successfully set target humidity ${target_humidity}"
        result = true
      }
  }
  return result
}

def update(status, nl) {
  logDebug "update ${status} ${nl}"
  update()
}

def update() {

  logDebug "update()"

  def result = null

  parent.sendBypassRequest(device, [
    "method": "getHumidifierStatus",
    "source": "APP",
    "data": [: ]
  ]) {
    resp ->
      if (checkHttpResponse("update", resp)) {

        def response = resp.data.result.result

        logDebug "update: ${response}"

        handleEvent("switch", response.enabled ? "on" : "off")
        handleEvent("level", convertRange(response.mist_virtual_level, 1, 9, 0, 100, true))

        state.humidity = response.humidity
        handleEvent("humidity", response.humidity)

        state.mist_level = response.mist_level
        handleEvent("mist_level", response.mist_level)

        state.target_humidity = response.configuration.auto_target_humidity
        handleEvent("target_humidity", response.configuration.auto_target_humidity)

        state.mode = response.mode
        handleEvent("mode", response.mode)

        state.warm_enabled = response.warm_enabled
        handleEvent("warm_enabled", response.warm_enabled)

        state.warm_level = response.warm_level
        handleEvent("warm_level", response.warm_level)

        state.automatic_stop = response.automatic_stop_reach_target
        handleEvent("automatic_stop", response.automatic_stop_reach_target)

        state.display = response.display
        handleEvent("display", response.display)

        state.lacks_water = response.water_lacks
        handleEvent("lacks_water", response.water_lacks)

        state.water_tank_lifted = response.water_tank_lifted
        handleEvent("water_tank_lifted", response.water_tank_lifted)
      }
  }
  return result
}

private void handleEvent(name, val) {
  logDebug "handleEvent(${name}, ${val})"
  device.sendEvent(name: name, value: val)
}

private BigDecimal convertRange(BigDecimal val, BigDecimal inMin, BigDecimal inMax, BigDecimal outMin, BigDecimal outMax, Boolean returnInt = true) {
  // Let make sure ranges are correct
  assert(inMin <= inMax);
  assert(outMin <= outMax);

  // Restrain input value
  if (val < inMin) val = inMin;
  else if (val > inMax) val = inMax;

  val = ((val - inMin) * (outMax - outMin)) / (inMax - inMin) + outMin;
  if (returnInt) {
    // If integer is required we use the Float round because the BigDecimal one is not supported/not working on Hubitat
    val = val.toFloat().round().toBigDecimal();
  }

  return (val);
}

def checkHttpResponse(action, resp) {
  if (resp.status == 200 || resp.status == 201 || resp.status == 204)
    return true
  else if (resp.status == 400 || resp.status == 401 || resp.status == 404 || resp.status == 409 || resp.status == 500) {
    log.error "${action}: ${resp.status} - ${resp.getData()}"
    return false
  } else {
    log.error "${action}: unexpected HTTP response: ${resp.status}"
    return false
  }
}
