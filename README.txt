Working on AltosDroid.

This project is designed to be managed with Android Studio.

On koto, android studio is installed in ~/src/android/android-studio and can be
run with:

	$ ~/src/android/android-studio/bin/studio.sh



ToDo list

 ✓ Data fields on map view. Distance, bearing to target, target and 'me' lat/lon.

 ✓ Select Device menu - devices need to be larger for tapping.

 * Select Frequency. Add 'edit frequencies' menu item.

 ✓ Select Tracker. Color of meatball should be purple

 ✓ Select Tracker needs title "Select Tracker"

 ✓ Delete Tracker needs title "Delete Tracker"

 * Setup

	Telemetry Rate
	Units
	Text Size
	Map Type (?) Probably not
	Map Offline/Online (nope)
	Load Maps (nope)


 * Put Load Maps in top-level menu instead of setup

 * Idle Mode

	Call Sign
	Frequency (Make this a pop-down menu thingy)
	Monitor
	Reboot
	Fire Igniters

 * Edit frequency list
 * Configuration
 * Idle mode
 ✓ Offline maps

BUGS

 * Map type selection breaks after switching away/back

 * Select tracker should center map on new target
