Working on AltosDroid.

This project is designed to be managed with Android Studio.

On koto, android studio is installed in ~/src/android/android-studio and can be
run with:

	$ ~/src/android/android-studio/bin/studio.sh

Testing Process

 * On virtual devices, ensure all views and dialogs render without
   crashes. Check both online and offline maps where possible.

 * On physical devices:

	* use ao-send-telem on a telemetry stream of a flight or two
	* use idle mode -> monitor
	* open 'download maps' and ensure site list is populated

Release Process

 * Bump version number. Generally four digits with the associated
   AltOS version followed by an android revision. Also bump the
   Android release number.

 * Commit change and tag using version number. Build and test a signed
   version using the emulators and real devices.

 * Publish as a Beta Test version on the Google Play Store:

    Altus Metrum org page:

	https://play.google.com/console/u/0/developers/4749971557595682041/app-list

    Open testing for AltosDroid page:

	https://play.google.com/console/u/0/developers/4749971557595682041/app/4975358408572149422/tracks/open-testing

 * Once sufficient test coverage has been achieved, promote to release
   and then also ship on Amazon App Store:

	https://developer.amazon.com/apps-and-games/console/apps/list.html#/

ToDo list

 ✓ Data fields on map view. Distance, bearing to target, target and 'me' lat/lon.

 ✓ Select Device menu - devices need to be larger for tapping.

 ✓ Select Tracker. Color of meatball should be purple

 ✓ Select Tracker needs title "Select Tracker"

 ✓ Delete Tracker needs title "Delete Tracker"

 ✓ Offline maps

 ✓ Setup

	Telemetry Rate
	Units
	Text Size

	Map Type (?) Probably not
	Map Offline/Online (nope)
	Load Maps (nope)

 ✓ Don't need to make text size work -- android has it internally.

 ✓ Select Frequency. Add 'edit frequencies' menu item.

 ✓ Put Load Maps in top-level menu instead of setup

 ✓ Idle Mode

	Call Sign
	Frequency
	Monitor
	Reboot
	Fire Igniters

 ✓ Map type selection breaks after switching away/back

 ✓ Select tracker should center map on new target

 ✓ Rocket trackers in online mode end up duplicated

 ✓ Need scroll view wrappers around three data views for giant font mode

 ✓ Make sure telemetry keeps working when the phone sleeps and on app switch

 ✓ Center on current tracker, allowing the user to pan around but
   reset after 'a while'? with a button?

 ✓ When to pin to a specific tracker and when to auto-select most
   recent?

 ✓ How to indicate which tracker selection mode you're in?

    - Move flight state to Flight view
    - Add (auto) or (serial) to title bar

 ✓ the map moves back to current phone position

 ✓ Add a voice on/off button

 ✓ Support USB connected receivers

 ✓ Fire Igniters background color is wrong

 ✓ Fire Igniters causes ANR issues

 ✓ Add pop-down menu for frequency in idle mode.

 ✓ Switching to offline maps and current location is at upper left
   instead of centered.

 ✓ Idle Mode frequency display doesn't seem to have the correct value

 ✓ Round most spoken numbers to two digits.

 ✓ Reporting direction (delta from current track) in Recover view

 ✓ Add receiver and target alt to recover screen (at least for debug)

 ✓ Frequency selection menu should indicate the current frequency somehow

 * Elevation numbers seem weird.

 * On the iOS application -- 'My lat/my lon' never update, along with
   related data. Restarting the app didn't fix this, but rebooting the
   phone did.

---------------------------------

 * Prefer the 'Fused' location provider instead of GPS

 * Rotate online map to match track/compass

 * Add target configuration to Idle mode

.	• Main deploy altitude
.	• Apogee delay
.	• Apogee lockout
.	• Igniter firing mode
.	• Pad orientation

.	• Beeper Frequency
.	• Beep units

.	• Maximum flight log size
.	• Accel calibration

.	• Callsign
.	• Frequency
.	• Radio enable
.	• Telemetry baud rate
.	• APRS interval
.	• APRS SSID
.	• APRS format
.	• APRS offset

	• Tracker motion
	• Tracker interval
	• radio 10mw
	• GPS receiver

	• Pyro configuration

 * Add auto callsign mode to wait for a tracker with the specified
   callsign

 * Support multiple receivers

 * Improve tablet support somehow. Maybe by showing maps adjacent to
   data? Showing graphs of key values?

 * Download data and view telem/eeprom data in graphs. Share data more
   easily.
