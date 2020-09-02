/**
 *
 *  Summary:
 *
 *  Monitors temperature sensors that are presumably located outdoors, averages their readings, and sends the result
 *  to a set of Sinopé thermostats.
 * 
 *  This app requires a special version of the thermostat driver that allows the outdoor temperature to be set, which it
 *  accomplishes using the 'Switch Level' capability.
 *
 *  The application subscribes to temperature events and also polls every ten minutes, just in case.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 * MIT License
 * 
 * Copyright (c) 2020 Niklas Gustafsson
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *
 *  Changes:
 *
 *  1.0.0 - 2010-08-28 Initial version
 *
 */

def setVersion(){
    state.name = "Sinopé Outdoor Temperature"
	state.version = "1.0.0"
}

definition(
	name: "Sinopé Outdoor Temperature",
	namespace: "NiklasGustafsson",
	author: "Niklas Gustafsson",
	description: "Get the temperature from a device and set the outdoor temp on Sinopé.",
	category: "Convenience",
	iconUrl: "",
	iconX2Url: "",
	iconX3Url: "",
)

preferences {
     page name: "mainPage", title: "", install: true, uninstall: true
} 

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    subscribe(inputSensors, "temperature", deviceHandler)
    runIn(1, poller)
    runEvery10Minutes(poller)
}

def mainPage() {
    dynamicPage(name: "mainPage") {
    	installCheck()

        section("Temperature Sensors") {
            input "inputSensors", "capability.temperatureMeasurement", title: "Select the temerature sensors", required: true, multiple: true
        } 

        section("Thermostats") {
            input "thermostats", "capability.thermostat", title: "Select the thermostats", required: true, multiple: true
        } 

        section("Preferences") {
            input("logEnable", "bool", title: "Enable logging", required: false, defaultValue: false)
        } 
	}
}

def installCheck(){   
    display()
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit 'Done' to finish installing '${app.label}'"}
  	}
  	else{
    	log.info "${app.label} installed correctly"
  	}
}

def setDefaults() {
	if(logEnable == null){logEnable = false}
}

def getFormat(type, myText="") {			// Modified from @Stephack Code   
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def display() {
    setVersion()
    section (getFormat("title", "${state.name}")) {
		paragraph getFormat("line")
	}
}

void poller()
{
    if(logEnable) log.debug "In poller()"
    def avgTemp = (float)0.0

    inputSensors.each{sensor ->
        def temp = Float.parseFloat(sensor.currentState("temperature").value)
        if(logEnable) log.debug "temperature: ${temp}"
        avgTemp += (float)temp
    }

    avgTemp = avgTemp / (float)inputSensors.size()

    thermostats.each{therm ->
        therm.setLevel(avgTemp)
    }
}

def deviceHandler(evt) 
{
    if(logEnable) log.debug "In deviceHandler()"
    // Some temperature reading changed, but we'll poll all of them, 
    // since that's the easiest to average them.
    poller()
}