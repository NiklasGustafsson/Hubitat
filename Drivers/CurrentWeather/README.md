## Current Weather Virtual Device

This virtual device retrieve the current weather information from a nearby weather station, using the National Weather Service public APIs.

The weather information returns temperature and the NWS summary of the weather, along with the NWS icon representing it, which can then be used to display the information in a dashboard.

I believe that the NWS will only have data for US locations.

## Sinopé Thermostat Driver

The code was copied from scoulombe79's work on Sinopé and tailored to my specific scenario.

This driver allows setting the outdoor temperature with a 'setLevel()' command. See also the 'Sinopé Outdoor Temperature' app in this repo. They go together -- the app collects (presumably outdoor) temperature readings and sends them to each configured Sinopé thermostat. This has been tested on both the 3kW and the 4kW versions of the Sinopé baseboard thermostat.
