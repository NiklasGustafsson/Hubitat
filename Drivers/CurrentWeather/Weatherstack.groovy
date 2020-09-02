
/**
 *
 *    Weatherstack Driver
 *
 *    This virtual device gets the current temperature for the hub's location by making API calls to Weatherstack.
 *
 *    NOTE: The Weatherstack 'Free' tier only allows for updating weather information more or less once an hour. 
 *    The quota is actually 1,000 API calls per month, which comes out to a call every 45 minutes or so.
 *
 *    Author: NiklasGustafsson
 *
 *    Date: 2020-08-28
 *
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
 */
preferences
{
    input("WeatherstackKey", "string", title: "Weatherstack API Key", description: "The API key used with Weatherstack.com to get the outdoor temperature.")
    input("trace", "bool", title: "Trace", description:"Set it to true to enable tracing")
}

metadata
{
    definition(name: "Weatherstack", namespace: "NiklasGustafsson", author: "Niklas Gustafsson", ocfDeviceType: "oic.d.thermostat") {
        capability "Sensor"
        capability "Temperature Measurement"
    }
}

def installed()
{
    if (settings.trace)
        log.trace "Weatherstack >> installed()"

    initialize()
}

def updated()
{
    if (settings.trace)
        log.trace "Weatherstack >> updated()"

    if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000)
    {
        state.updatedLastRanAt = now()

        if (settings.trace)
            log.trace "Weatherstack >> updated() => Device is now updated"

        try
        {
            unschedule()
        }
        catch (e)
        {
        }

        runIn(1, refresh)
        // By refreshing every hour, you can stay under the Free Plan limits
        // on Weatherstack
        runEvery1Hour(refresh)
    }
}

void initialize()
{
    if (settings.trace)
        log.trace "Weatherstack >> initialize()"

    runIn(1, refresh)
    // By refreshing every hour, you can stay under the Free Plan limits
    // on Weatherstack
    runEvery1Hour(refresh)
    state.outdoorTemp = -47.11
}

def ping()
{
    refresh()
}

def uninstalled()
{
    try
    {
        unschedule()
    }
    catch (e)
    {
    }
}

def getTemperatureValue(value, doRounding = false)
{
    def scale = state?.scale

    if (value != null)
    {
        double celsius = (Integer.parseInt(value, 16) / 100).toDouble()

        if (scale == "C")
        {
            if (doRounding)
            {
                def tempValueString = String.format('%2.1f', celsius)

                if (tempValueString.matches(".*([.,][456])"))
                    tempValueString = String.format('%2d.5', celsius.intValue())
                else if (tempValueString.matches(".*([.,][789])"))
                {
                    celsius = celsius.intValue() + 1
                    tempValueString = String.format('%2d.0', celsius.intValue())
                }
                else
                    tempValueString = String.format('%2d.0', celsius.intValue())

                return tempValueString.toDouble().round(1)
            }
            else
                return celsius.round(1)
        }
        else
            return Math.round(celsiusToFahrenheit(celsius))
    }
}

def refresh()
{
    def cmds = []

    if (settings.trace)
        log.trace "Weatherstack >> refresh()"

    float outdoorTemp = getOutdoorTemperature()

    def scale = getTemperatureScale()
    def degreesDouble = outdoorTemp as Float
    String tempValueString

    if (scale == "C")
        tempValueString = String.format('%2.1f', degreesDouble)
    else
        tempValueString = String.format('%2d', degreesDouble.intValue())

    sendEvent(name: "temperature", value: tempValueString, unit: scale)
}

private getOutdoorTemperature()
{
    try {

        // If we don't already know the station ID, we must retrieve it. We should only have to do this once.

        def zip = location.zipCode
        def key = settings.WeatherstackKey

        if (zip != null && key != null) {

            def params = [
                uri: "http://api.weatherstack.com/current?access_key=${key}&query=${zip}",
            ]

            if(settings.trace) log.trace "Getting temperature for zip code: ${zip}"
            if(settings.trace) log.trace "Getting temperature: GET ${params.uri}"

            httpGet(params) {resp ->

                def result = resp.data
                
                if(settings.trace) log.trace "Result: ${result}"
                state.outdoorTemp = (float)result.current.temperature
            }
        }
    }
    catch (SocketTimeoutException e) {
        if (settings.trace) log.trace "Connection to Weatherstack API timed out."
        sendEvent(name: "error", value: "Connection timed out while retrieving data from API", displayed: true)
    }
    catch (e) {
        if (settings.trace) log.trace "Could not retrieve weather data: $e"
        sendEvent(name: "error", value: "Could not retrieve data from API", displayed: true)
    } 

    return state.outdoorTemp       
}