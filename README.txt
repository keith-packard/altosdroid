Working on AltosDroid.

This project is designed to be managed with Android Studio.

On koto, android studio is installed in ~/src/android/android-studio and can be
run with:

	$ ~/src/android/android-studio/bin/studio.sh



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

 * Pop-down menu for frequency in idle mode.

 * Round most spoken numbers to two digits.

 ✓ Center on current tracker, allowing the user to pan around but
   reset after 'a while'? with a button?

 ✓ When to pin to a specific tracker and when to auto-select most
   recent?

 ✓ How to indicate which tracker selection mode you're in?

    - Move flight state to Flight view
    - Add (auto) or (serial) to title bar

 ✓ the map moves back to current phone position

 * Add a voice on/off button

 * Add auto callsign mode to wait for a tracker with the specified
   callsign

 
