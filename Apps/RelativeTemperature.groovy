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

import java.math.RoundingMode

def setVersion(){
    state.name = "Relative Temperature"
	state.version = "1.0.0"
}

definition(
	name: "Relative Temperature",
	namespace: "NiklasGustafsson",
	author: "Niklas Gustafsson",
	description: "Get temperature readings from two devices and set a virtual thermometer to the difference.",
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
    subscribe(referenceSensor, "temperature", deviceHandler)
    subscribe(relativeSensor, "temperature", deviceHandler)
    runIn(1, poller)
}

def mainPage() {
    dynamicPage(name: "mainPage", title: " ", install: true, uninstall: true) {
    	installCheck()

        section("") {
            input "thisName", "text", title: "<b>Name for this relative temperature measurement:</b>", submitOnChange: true
            if(thisName) {
                app.updateLabel("$thisName")
            }
        }
        
        section("<b>Reference Sensor</b>") {
            input "referenceSensor", "capability.temperatureMeasurement", title: "Select the reference sensor", required: true, multiple: false
        } 

        section("<b>Relative Sensor</b>") {
            input "relativeSensor", "capability.temperatureMeasurement", title: "Select the relative sensor", required: true, multiple: false
        } 

        section("<b>Virtual Sensor</b>") {
            input "virtualSensor", "capability.temperatureMeasurement", title: "Select the virtual thermometer.", required: true, multiple: false
        } 

        section("<b>Preferences</b>") {
            input("interval", "number", title: "Update interval (seconds)", required: false, defaultValue: 3600)
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
		// paragraph getFormat("line")
	}
}

void poller()
{
    if(logEnable) log.debug "In poller()"
    def diff = (float)0.0

    def relTemp = Float.parseFloat(relativeSensor.currentState("temperature").value)
    def refTemp = Float.parseFloat(referenceSensor.currentState("temperature").value)
    diff = (float)((int)((relTemp - refTemp) * 100.0)) / 100.0;

    if(logEnable) log.debug "relative temperature: ${diff}"

    virtualSensor.setTemperature(diff);
    
    runIn(interval, poller)
}

def deviceHandler(evt) 
{
    if(logEnable) log.debug "In deviceHandler()"
    // Some temperature reading changed, but we'll poll all of them, 
    // since that's the easiest to average them.
    poller()
}