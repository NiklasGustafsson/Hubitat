/**
 *  Current Temp
 *
 *  Design Usage:
 *  Retrieve current temperature data from the US National Weather Service. For use with Hubitat dashboards.
 *
 *  Since this is my first Hubitat virtual device, I peeked at the 'Pollen Forecaster' device authored by Bryan Turcotte (@bptworld) when I started this.
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
 *  Changes:
 *
 * v0.1.0 - 08/05/2020 - Original version
 *
 */

def setVersion() {
    appName = "Current Temperature"
    version = "v0.1.0" 
    dwInfo = "${appName}:${version}"
    sendEvent(name: "dwDriverInfo", value: dwInfo, displayed: true)
}

def updateVersion()  {
    log.info "In updateVersion"
    setVersion()
}

metadata {
    definition (name: "Current Temperature", namespace: "NiklasGustafsson", author: "Niklas Gustafsson", importUrl: "https://raw.githubusercontent.com/NiklasGustafsson/Hubitat/master/Drivers/CurrentWeather/CurrentWeather.groovy") {
        capability "Actuator"
        capability "Sensor"
        capability "Polling"

        attribute "currentTemperature", "number"
        attribute "currentWeather", "string"
        attribute "location", "string"
        
        attribute "Weather", "string"
        
        attribute "dwDriverInfo", "string"
        command "updateVersion"
    }

    preferences {
        input name: "about", type: "paragraph", title: "Current Weather", description: "Retrieve data from the National Weather Service. For use with Hubitat dashboards. Only locations in the United States are currently supported."
        input "logEnable", "bool", title: "Enable logging", required: true, defaultValue: false
    }
}

def updated()  {
    poll()
}

def uninstalled()  {
    unschedule()
}

def installed()  {
    runEvery1Hour(poll)
    poll()
}

def poll()  {
    if(logEnable) log.debug "In poll..."

    try {

        // If we don't already know the station ID, we must retrieve it. We should only have to do this once.

        if (state.stationIdentifier == null)
        {
            // The first step is to take the lat/long and turn it into a grid id.

            lat = location.latitude
            lng = location.longitude

            def params1 = [
                uri: "https://api.weather.gov/points/${lat},${lng}",
                headers: ['User-Agent':'Hubitat Weather Device', Acccept: 'application/json']
            ]

            if(logEnable) log.debug "Getting gridpoints: GET ${params1.uri}"

            gridId = ''
            gridX = -1
            gridY = -1

            httpGet(params1) {resp ->
                // NWS sends things back as 'applcation/geo+json' which isn't something the HTTP
                // stack can automatically parse, so we have to do it on our own.
                json = resp.data.getText()
                def slurper = new groovy.json.JsonSlurper()
                def result = slurper.parseText(json)

                if(logEnable) log.debug "Got response from: ${params1.uri}"
                if(logEnable) log.debug "Status code: ${resp.status}"
                if(logEnable) log.debug "Content Type: ${resp.contentType}"

                // Each location is only found within one grid, so if we get a response, we have the answer.

                if(result.properties.gridId != null) {
                    gridId = result.properties.gridId
                    gridX = result.properties.gridX
                    gridY = result.properties.gridY
                }
            }

            foundStation = false

            // Once we have a grid, we need to find the weather stations.

            if (gridId != null)
            {
                def params2 = [
                    uri: "https://api.weather.gov/gridpoints/${gridId}/${gridX},${gridY}/stations",
                    headers: ['User-Agent':'Hubitat Weather Device', Acccept: 'application/json']
                ]

                if(logEnable) log.debug "Getting stations: GET ${params2.uri}"

                httpGet(params2) {resp ->
                    // NWS sends things back as 'applcation/geo+json' which isn't something the HTTP
                    // stack can automatically parse, so we have to do it on our own.
                    json = resp.data.getText()
                    def slurper = new groovy.json.JsonSlurper()
                    def result = slurper.parseText(json)

                    if(logEnable) log.debug "Got response from: ${params2.uri}"
                    if(logEnable) log.debug "Status code: ${resp.status}"
                    if(logEnable) log.debug "Content Type: ${resp.contentType}"

                    // To make things simple, we'll pick the first station we find in the list.

                    for (feature in result.features) {
                        if(feature.properties.stationIdentifier != null) {
                            state.stationIdentifier = feature.properties.stationIdentifier
                            if(logEnable) log.debug "Found Weather Station: ${state.stationIdentifier}"
                            break
                        }
                    }
                }
            }
        }


        if (state.stationIdentifier != null) {
            def params3 = [
                uri: "https://api.weather.gov/stations/${state.stationIdentifier}/observations",
                headers: ['User-Agent':'Hubitat Weather Device', Acccept: 'application/json']
            ]

            if(logEnable) log.debug "Getting temperature for station: ${state.stationIdentifier}"
            if(logEnable) log.debug "Getting temperature: GET ${params3.uri}"

            httpGet(params3) {resp ->

                // NWS sends things back as 'applcation/geo+json' which isn't something the HTTP
                // stack can automatically parse, so we have to do it on our own.
                json = resp.data.getText()
                def slurper = new groovy.json.JsonSlurper()
                def result = slurper.parseText(json)

                if(logEnable) log.debug "Got response from: ${params3.uri}"
                if(logEnable) log.debug "Status code: ${resp.status}"
                if(logEnable) log.debug "Content Type: ${resp.contentType}"

                // The results are sorted with the most recent reading first, so we'll pick that up.
                // Sometimes, the temperature value isn't filled in, in which case we move on to the next.

                for (feature in result.features) {
                    if(feature.properties.temperature.value != null) {
                        state.currentTemperature = convertToTempScale(feature.properties.temperature)
                        state.currentWeather = feature.properties.textDescription
                        state.icon = feature.properties.icon

                        if(logEnable) log.debug "Sending events"

                        def roundedTemp = roundToTenth(state.currentTemperature)

                        log.info "Information for station ${state.stationIdentifier}: weather - ${state.currentWeather} temperature - ${roundedTemp}"

                        sendEvent(name: "currentWeather", value: state.currentWeather, displayed: true)
                        sendEvent(name: "currentTemperature", value: roundedTemp, displayed: true)
                        break
                    }
                }
            }
        }
    }
    catch (SocketTimeoutException e) {
        if(logEnable) log.debug "Connection to nws.gov API timed out."
        sendEvent(name: "error", value: "Connection timed out while retrieving data from API", displayed: true)
    }
    catch (e) {
        if(logEnable) log.debug "Could not retrieve weather data: $e"
        sendEvent(name: "error", value: "Could not retrieve data from API", displayed: true)
    }

    tileMap()
}

def configure() {
    poll()
}

// Round to the nearest tenth. Useful to get more significant digits on the reading
def roundToTenth(x) {
    return ((float)Math.round(x*10.0))/10.0
}

def convertToTempScale(temp) {
    x = temp.value
    isCelsius = temp.unitCode == "unit:degC"

    if (location.temperatureScale == "C") {
        return isCelsius ? x : (x - 32.0) / 1.8
    }
    else {
        return isCelsius ? x * 1.8 + 32.0 : x
    }
}

def tileMap()  {
    if(logEnable) log.debug "In tileMap..."
    def roundedTemp = roundToTenth(state.currentTemperature)
    state.appDataToday = "<table width='100%'>"
    state.appDataToday+= "<tr><td><div style=''font-size:.80em;halign=center;'></div></td></tr>"
    state.appDataToday+= "<tr><td><div style=''font-size:.80em;halign=center;'><img src='${state.icon}'/></div></td></tr>"
    state.appDataToday+= "<tr><td><div style='font-size:.80em;halign=center;'>${state.currentWeather}</div></td></tr>"
    state.appDataToday+= "<tr><td><div style='font-size:.80em;halign=center;'>${roundedTemp}&nbsp;&#186;${location.temperatureScale}</div></td></tr>"
    state.appDataToday+= "</table>"
    sendEvent(name: "Weather", value: state.appDataToday, displayed: true)
}
