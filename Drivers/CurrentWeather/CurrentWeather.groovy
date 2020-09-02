/**
 *  Current Temp
 *
 *  Summary:
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
        input name: "station", type: "text", title: "Weather Station Code", required: false, defaultValue: null
        input "logEnable", "bool", title: "Enable logging", required: true, defaultValue: false
    }
}

def updated()  {
    poll()
}

def configure() {
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

    if (state.lastStation != station) {
        state.stationIdentifier = null
    }

    if (station != null) {
        if(logEnable) log.debug "Station '${station}' found in device configuration."
        state.stationIdentifier = station
    }

    state.lastStation = station

    try {

        // If we don't already know the station ID, we must retrieve it. We should only have to do this once.

        def id = findStationIdentifier()

        if (id != null) {
            if(logEnable) log.debug "HERE: ${id}"

            def params = [
                uri: "https://api.weather.gov/stations/${id}/observations",
                headers: ['User-Agent':'Hubitat Weather Device', Acccept: 'application/json']
            ]

            if(logEnable) log.debug "Getting temperature for station: ${id}"
            if(logEnable) log.debug "Getting temperature: GET ${params.uri}"

            httpGet(params) {resp ->

                def result = parseGeoJSON(resp.data)

                logResponse(params.uri, resp)

                // The results are sorted with the most recent reading first, so we'll pick that up.
                // Sometimes, the temperature value isn't filled in, in which case we move on to the next.

                for (feature in result.features) {
                    if(feature.properties.temperature.value != null) {
                        state.currentTemperature = convertToTempScale(feature.properties.temperature)
                        state.currentWeather = feature.properties.textDescription
                        state.icon = feature.properties.icon

                        if(logEnable) log.debug "Sending events"

                        def roundedTemp = roundToTenth(state.currentTemperature)

                        log.info "Information for station ${id}: weather - ${state.currentWeather} temperature - ${roundedTemp}"

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

// Find the station identifier, which is a code that is used to represent
// a specific NWS weather station in the US.
//
def findStationIdentifier() {

    if (state.stationIdentifier != null) {
        return state.stationIdentifier
    }

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
        def result = parseGeoJSON(resp.data)

        logResponse(params1.uri, resp)

        // Each location is only found within one grid, so if we get a response, we have the answer.

        if(result.properties.gridId != null) {
            gridId = result.properties.gridId
            gridX = result.properties.gridX
            gridY = result.properties.gridY
        }
    }

    // Once we have a grid, we need to find the weather stations.

    def stationId = null

    if (gridId != null)
    {
        def params2 = [
            uri: "https://api.weather.gov/gridpoints/${gridId}/${gridX},${gridY}/stations",
            headers: ['User-Agent':'Hubitat Weather Device', Acccept: 'application/json']
        ]

        if(logEnable) log.debug "Getting stations: GET ${params2.uri}"

        httpGet(params2) {resp ->

            def result = parseGeoJSON(resp.data)

            logResponse(params2.uri, resp)

            // To make things simple, I'll just pick the first station I find in the list.
            // I have no idea how the list is ordered, or how big a grid is. This may not
            // be a smart choice, but it seems to work for my locations.

            for (feature in result.features) {
                if(feature.properties.stationIdentifier != null) {
                    state.stationIdentifier = feature.properties.stationIdentifier
                    if(logEnable) log.debug "Found Weather Station: ${state.stationIdentifier}"
                    stationId = feature.properties.stationIdentifier
                    break
                }
            }
        }
    }

    return stationId
}

// The NWS returns the Content-Type header as 'application/geo+json' which isn't
// something Groovy's HTTP stack handles automatically, so we need to get the body
// as text and parse it.
def parseGeoJSON(data) {
    def json = data.getText()
    def slurper = new groovy.json.JsonSlurper()
    return slurper.parseText(json)
}


// Round to the nearest tenth. 
// Useful to get more significant digits on the reading
//
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

def logResponse(uri, resp) {
    if (logEnable) log.debug "Got response from: ${uri}: Status code: ${resp.status} Content Type: ${resp.contentType}"
}