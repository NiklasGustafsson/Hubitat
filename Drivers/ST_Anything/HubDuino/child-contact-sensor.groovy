/**
 *  Child Contact Sensor
 *
 *  https://raw.githubusercontent.com/DanielOgorchock/ST_Anything/master/HubDuino/Drivers/child-contact-sensor.groovy
 *
 *  Copyright 2017 Daniel Ogorchock
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Change History:
 *
 *    Date        Who            What
 *    ----        ---            ----
 *    2017-04-10  Dan Ogorchock  Original Creation
 *    2017-08-23  Allan (vseven) Added a generateEvent routine that gets info from the parent device.  This routine runs each time the value is updated which can lead to other modifications of the device.
 *    2018-06-02  Dan Ogorchock  Revised/Simplified for Hubitat Composite Driver Model
 *    2018-09-22  Dan Ogorchock  Added preference for debug logging
 *    2019-07-01  Dan Ogorchock  Added importUrl
 *    2020-01-25  Dan Ogorchock  Remove custom lastUpdated attribute & general code cleanup
 *
 * 
 */
metadata {
	definition (name: "Child Contact Sensor", namespace: "ogiewon", author: "Dan Ogorchock", importUrl: "https://raw.githubusercontent.com/DanielOgorchock/ST_Anything/master/HubDuino/Drivers/child-contact-sensor.groovy") {
		capability "Contact Sensor"
		capability "Sensor"
	}

    preferences {
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "openDelay", type: "number", title: "Contact open delay in seconds", defaultValue: 0
	}
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def reportOpen() {
    if (state.delayedOpen) {
        state.delayedOpen = false
        if (state.lastReportedEvent != "open") {
            if (logEnable) log.debug("Reporting delayed 'open' event") 
            state.lastReportedEvent = "open"
            sendEvent(name: "contact", value: "open")
        }
    }
    else if (logEnable) {
        log.debug("Delayed 'open' event already canceled")
    }
}

def parse(String description) {
    if (logEnable) log.debug "parse(${description}) called"
	def parts = description.split(" ")
    def name  = parts.length>0?parts[0].trim():null
    def value = parts.length>1?parts[1].trim():null
    
    if (name && value) {
        
        if (logEnable && state.delayedOpen) log.debug("Canceling a delayed 'open' event") 
        
        state.delayedOpen = false

        if (state.lastReportedEvent != value) {
            
            // Update device

            if (value == 'open') {
                if (openDelay > 0) {
                    if (logEnable) log.debug("Delay 'open' event for " + openDelay.toString() + " s") 
                    state.delayedOpen = true
                    runIn(openDelay, reportOpen)
                }
                else {
                    if (logEnable) log.debug("Reporting non-delayed '" + value + "' event") 
                    state.lastReportedEvent = value
                    sendEvent(name: name, value: value)
                }
            } 
            else {
                if (logEnable) log.debug("Reporting '" + value + "' event") 
                state.lastReportedEvent = value
                sendEvent(name: name, value: value)
            }
        }
        else {
            if (logEnable) log.debug("Skip reporting repeated '" + value + "' event") 
        }
    }
    else {
    	log.error "Missing either name or value.  Cannot parse!"
    }
}

def installed() {
    updated()
}

def updated() {
    state.delayedOpen = false
    if (logEnable) runIn(1800,logsOff)
}