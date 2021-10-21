## Levoit Air Purifier Drivers

These drivers add support for Levoit Air Core 200S and 400S air purifiers. Installing the integration driver, and configuring with the VeSync account information will automatically discover existing equipment.

Equipment found will be added as child devices under the VeSync Integration device, and will have the same name and initial label as what is configures in the VeSync account. Pressing 'Resync Equipment' will discover all newly added devices.

The purifiers show up as fans and switches, and there's also an 'info' attribute that is useful for displaying in a dashboard tile. The child devices are also actuators, so their public methods may be invoked from rules.
