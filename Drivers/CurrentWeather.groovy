/**
 *  Current Temp
 *
 *  Design Usage:
 *  Retrieve current temperature data from the US National Weather Service. For use with Hubitat dashboards.
 *
 *  Copyright 2020 Niklas Gustafsson 
 *
 *  Insipired by 'Pollen Forecaster' by Bryan Turcotte (@bptworld)
 *  
 * ------------------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *
 * v0.1.0 - 08/05/2020 - Original version
 *
 */

def setVersion()
{
    appName = "Current Temperature"
	version = "v0.1.0" 
    dwInfo = "${appName}:${version}"
    sendEvent(name: "dwDriverInfo", value: dwInfo, displayed: true)
}

def updateVersion() 
{
    log.info "In updateVersion"
    setVersion()
}

metadata {
	definition (name: "Current Temperature", namespace: "NiklasGustafsson", author: "Niklas Gustafsson", importUrl: "") {
        capability "Actuator"
		capability "Sensor"
		capability "Polling"

		attribute "currentTemperature", "number"
		attribute "currentWeather", "string"
		attribute "location", "string"
		
		attribute "todayTile", "string"
        
        attribute "dwDriverInfo", "string"
        command "updateVersion"
	}

	preferences {
		input name: "about", type: "paragraph", title: "Current Weather", description: "Retrieve data from the National Weather Service. For use with Hubitat dashboards. Only locations in the United States are currently supported."
		input "logEnable", "bool", title: "Enable logging", required: true, defaultValue: false
	}
}

def installed() 
{
	runEvery1Hour(poll)
	poll()
}

def updated() 
{
	poll()
}

def uninstalled() 
{
	unschedule()
}

def poll() 
{
	if(logEnable) log.debug "In poll..."

	try 
	{

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

					result.features.each {feature ->

						if(!foundStation && feature.properties.stationIdentifier != null) {
							foundStation = true
							state.stationIdentifier = feature.properties.stationIdentifier
							if(logEnable) log.debug "Found Weather Station: ${state.stationIdentifier}"
						}
					}
				}
			}
		}


		if (state.stationIdentifier != null) 
		{
			def params3 = [
				uri: "https://api.weather.gov/stations/${state.stationIdentifier}/observations",
				headers: ['User-Agent':'Hubitat Weather Device', Acccept: 'application/json']
			]

			if(logEnable) log.debug "Getting temperature for station: ${state.stationIdentifier}"
			if(logEnable) log.debug "Getting temperature: GET ${params3.uri}"

			httpGet(params3) {resp ->
				foundFeature = false

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

				result.features.each {feature ->
					if(!foundFeature && feature.properties.temperature.value != null) {
						// TODO: deal with C vs. F
						foundFeature = true
						state.currentTemperature = convertToTempScale(feature.properties.temperature)
						state.currentWeather = feature.properties.textDescription
						state.icon = feature.properties.icon

						if(logEnable) log.debug "Sending events"

						def roundedTemp = roundToTenth(state.currentTemperature)

						if(logEnable) log.debug "currentWeather: ${state.currentWeather}"
						if(logEnable) log.debug "currentTemperature: ${roundedTemp}"

						sendEvent(name: "currentWeather", value: state.currentWeather, displayed: true)
						sendEvent(name: "currentTemperature", value: roundedTemp, displayed: true)
					}
				}
			}
		}
	}
	catch (SocketTimeoutException e) 
	{
		if(logEnable) log.debug "Connection to nws.gov API timed out."
		sendEvent(name: "error", value: "Connection timed out while retrieving data from API", displayed: true)
	}
	catch (e) 
	{
		if(logEnable) log.debug "Could not retrieve weather data: $e"
		sendEvent(name: "error", value: "Could not retrieve data from API", displayed: true)
	}

	tileMap()
}

def configure() 
{
	poll()
}

// Round to the nearest tenth. Useful to get more significant digits on the reading
def roundToTenth(x)
{
	return ((float)Math.round(x*10.0))/10.0
}

def convertToTempScale(temp)
{
	x = temp.value
	unit = temp.unitCode

	if (location.temperatureScale == "C")
	{
		if (unit == "unit:degC")
		{
			return x
		}
		else
		{
			return (x - 32.0) / 1.8
		}
	}
	else
	{
		if (unit == "unit:degC")
		{
			return x * 1.8 + 32.0
		}
		else
		{
			return x
		}
	}
}

def tileMap() 
{
	if(logEnable) log.debug "In tileMap..."
	def roundedTemp = roundToTenth(state.currentTemperature)
	state.appDataToday = "<table width='100%'>"
	state.appDataToday+= "<tr><td><div style=''font-size:.80em;halign=center;'></div></td></tr>"
	state.appDataToday+= "<tr><td><div style=''font-size:.80em;halign=center;'><img src='${state.icon}'/></div></td></tr>"
	state.appDataToday+= "<tr><td><div style='font-size:.80em;halign=center;'>${state.currentWeather}</div></td></tr>"
	state.appDataToday+= "<tr><td><div style='font-size:.80em;halign=center;'>${roundedTemp}&#186;${location.temperatureScale}</div></td></tr>"
	state.appDataToday+= "</table>"
	sendEvent(name: "todayTile", value: state.appDataToday, displayed: true)
}
