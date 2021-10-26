## VeSync: Levoit Air Purifier Drivers

These drivers add support for Levoit Air Core 200S and 400S air purifiers. Installing the integration driver, and configuring with the VeSync account information will automatically discover existing equipment, as long as they have been setup with a VeSycn

Equipment found will be added as child devices under the VeSync Integration device, and will have the same name and initial label as what is configures in the VeSync account. Pressing 'Resync Equipment' will discover all newly added devices.

When discovered, a Core 200S purifier  will result in two devices being installed in Hubitat, one to control the operation of the purifier fan and display, one to control the night light. A Core 400S purifier will result in one device being installed.

The purifiers show up as fans and switches, and there's also an 'info' attribute that is useful for displaying in a dashboard tile as HTML. The child devices are actuators, so their public methods may be invoked from rules.

There are four files to install, all as Hubitat drivers. Copy and paste into the Hubitat UI under driver code.

1. VeSyncIntegration.groovy -- this is the parent device. It represents the VeSync account. Configure with account email and password, plus a refresh (polling) interval.
2. LevoitCore200S.groovy -- the driver for the 200S purifier.
3. LevoitCore200S Light.groovy -- the driver for the 200S night light.
4. LevoitCore400S.groovy -- the driver for the 400S purifier.

Installation:

After installing the drivers, add a virtual device with the VeSync Integration driver, then configure it with your credentials and the desired data refresh internal, and hit 'Save Preferences.' Once that is done, press the 'Resync Equipment' button on the device page and if your credentials are correct, you should see child devices come online. 


The Groovy code is loosely based on the Etekcity Python library at: https://github.com/webdjoe/pyvesync
