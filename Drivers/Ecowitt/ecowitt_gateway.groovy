
Ecowitt WiFi Gateway
chat_bubble_outline
help_outline

niklasgu
arrow_drop_downAccounts
Rooms
Devices
Dashboards
Apps
Settings
Subscriptions
Developer  
Apps Code
Drivers Code
Libraries Code
Bundles
Logs
Ecowitt WiFi Gateway« Drivers code  Import 
Spaces
 
4
 
No wrap
HelpDeleteSave
1
/**
2
 * Driver:     Ecowitt WiFi Gateway
3
 * Author:     Simon Burke (Original author Mirco Caramori - github.com/mircolino)
4
 * Repository: https://github.com/sburke781/ecowitt
5
 * Import URL: https://raw.githubusercontent.com/sburke781/ecowitt/main/ecowitt_gateway.groovy
6
 *
7
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
8
 * in compliance with the License. You may obtain a copy of the License at:
9
 *
10
 * http://www.apache.org/licenses/LICENSE-2.0
11
 *
12
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
13
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
14
 * for the specific language governing permissions and limitations under the License.
15
 *
16
 * Change Log:
17
 *
18
 * 2020.04.24 - Initial implementation
19
 * 2020.04.29 - Added GitHub versioning
20
 *            - Added support for more sensors: WH40, WH41, WH43, WS68 and WS80
21
 * 2020.04.29 - Added sensor battery range conversion to 0-100%
22
 * 2020.05.03 - Optimized state dispatch and removed unnecessary attributes
23
 * 2020.05.04 - Added metric/imperial unit conversion
24
 * 2020.05.05 - Gave child sensors a friendlier default name
25
 * 2020.05.08 - Further state optimization and release to stable
26
 * 2020.05.11 - HTML templates
27
 *            - Normalization of floating values
28
 * 2020.05.12 - Added windDirectionCompass, ultravioletDanger, ultravioletColor, aqiIndex, aqiDanger, aqiColor attributes
29
 * 2020.05.13 - Improved PM2.5 -> AQI range conversion
30
 *            - HTML template syntax checking and optimization
31
 *            - UI error handling using red-colored state text messages
32
 * 2020.05.14 - Major refactoring and architectural change
33
 *            - PWS like the WS2902 are recognized and no longer split into multiple child sensors
34
 *            - Rain (WH40), Wind and Solar (WH80) and Outdoor Temp/Hum (WH32) if detected, are combined into a single
35
 *              virtual WS2902 PWS to improve HTML Templating
36
 *            - Fixed several imperial-metric conversion issues
37
 *            - Metric pressure is now converted to hPa
38
 *            - Laid the groundwork for identification and support of sensors WH41, WH55 and WH57
39
 *            - Added several calculated values such as windChill, dewPoint, heatIndex etc. with color and danger levels
40
 *            - Time of data received converted from UTC to hubitat default locale format
41
 *            - Added error handling using state variables
42
 *            - Code optimization
43
 * 2020.05.22 - Added orphaned sensor garbage collection using "Resync Sensors" commands
44
 * 2020.05.23 - Fixed a bug in the PM2.5 to AQI conversion
45
 * 2020.05.24 - Fixed a possible command() and parse() race condition
46
 * 2020.05.26 - Added icons support in the HTML template
47
 * 2020.05.30 - Added HTML template repository
48
 *            - Added support for multiple (up to 5) HTML template to each child sensor
49
 *            - Fixed wind icon as direction is reported as "from" where the wind originates
50
 * 2020.06.01 - Fixed a cosmetic bug where "pending" status would not be set on non-existing attributes
51
 * 2020.06.02 - Added visual confirmation of "resync sensors pending"
52
 * 2020.06.03 - Added last data received timestamp to the child drivers to easily spot if data is not being received from the sensor
53
 *            - Added battery icons (0%, 20%, 40%, 60%, 80%, 100%)
54
 *            - Reorganized error e/o status reporting, now displayed in a dedicated "status" attribute
55
 * 2020.06.04 - Added the ability to enter the MAC address directly as a DNI in the parent device creation page
56
 * 2020.06.05 - Added support for both MAC and IP addresses (since MACs don't work across VLANs)
57
 * 2020.06.06 - Add importURL for easier updating
58
 * 2020.06.08 - Added support for Lightning Detection Sensor (WH57)
59
 * 2020.06.08 - Added support for Multi-channel Water Leak Sensor (WH55)
60
 * 2020.06.21 - Added support for pressure correction to sea level based on altitude and temperature
61
 * 2020.06.22 - Added preference to let the end-user decide whether to compound or not outdoor sensors
62
 *              Added custom battery attributes in bundled PWS sensors
63
 * 2020.08.27 - Added user defined min/max voltage values to fine-tune battery status in sensors reporting it as voltage range
64
 *              Added Hubitat Package Manager repository tags
65
 * 2020.08.27 - Fixed null exception caused by preferences being set asynchronously
66
 *            - Removed sensor "time" attribute which could cause excessive sendEvent activity
67
 * 2020.08.31 - Added support for new Indoor Air Quality Sensor (WH45)
68
 *            - Optimized calculation of inferred values: dewPoint, heatIndex, windChill and AQI
69
 * 2020.09.08 - Added support for Water/Soil Temperature Sensor (WH34)
70
 * 2020.09.17 - Added (back) real-time AQI index, color and danger
Location: Walla Walla
Terms of Service
Documentation
Community
Support
Copyright 2021 Hubitat, Inc.
