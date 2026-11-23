# Plume Launcher (Apple TV / tvOS Edition)

A custom, streamlined fork of [Plume Launcher](https://github.com/aradhyas8/Plume_Launcher) for Android TV and Google TV, reworked with a clean tvOS / Apple TV aesthetic and built-in 4K Aerial screensavers.

<p align="center">
  <img src="screenshots/home.png" alt="Home Screen" width="95%">
</p>

---

## What’s different in this fork

Stock Android TV launchers are full of ads, sponsored carousels, and visual clutter. The goal of this fork was to take Plume's lightweight base and reshape it into a true Apple TV experience: minimalist dock, floating frosted glass controls, and seamless 4K video screensavers.

### Floating Control Center
* Replaces the stock Android TV quick settings with a floating frosted glass pill panel in the top-right corner.
* **Remote Battery Level**: Queries the TV remote's battery percentage directly over Bluetooth LE GATT (`0000180F` Battery Service) so you actually see the battery percentage (e.g. `88%`).
* **Customizable HDMI / Quick Action Tile**: Configure your preferred HDMI port (with custom label and icon such as PC, AV Receiver, PlayStation, Xbox, Switch, or Apple TV), open the full HDMI Sources drawer, or trigger 4K Aerial screensavers.
* **Live Storage & Wallpaper Manager**: Shows available internal storage at a glance and opens the storage drawer to manage or delete downloaded 4K Aerial videos and custom wallpapers.
* **Network & Bluetooth**: Fast access to Wi-Fi and Bluetooth accessories, while the app dock smoothly pushes down so elements never overlap.

<p align="center">
  <img src="screenshots/control_center.png" alt="Apple TV Floating Control Center" width="95%">
</p>

### First-Run Setup & Custom HDMI Icons
* **Interactive Onboarding**: Fresh installs open translucent frosted-glass cards to set your profile initial next to the clock, pick which apps and HDMI inputs appear in the dock, and choose your Control Center shortcut (all skipped automatically on app updates).
* **Custom HDMI Icons & Names**: Long-press any HDMI dock tile or open the Sources / Apps drawer to rename ports and pick crisp monochrome icons (`PC`, `AV Receiver`, `PlayStation`, `Xbox`, `Nintendo Switch`, `Apple TV`, or 1:1 `HDMI` badges).

### Categorized App Library
* Fullscreen translucent app drawer with category filter pills (**Alle**, **Streaming**, **Musik**, **Spiele**, **Tools**).
* Live category filtering as you move across filter pills, with clean 6-column D-Pad grid navigation.
* Dock row stays uncluttered without a permanent `+` tile—long-press any dock icon to manage pinned apps.

<p align="center">
  <img src="screenshots/all_apps.png" alt="Categorized All-Apps Grid" width="95%">
</p>

### Apple TV 4K Aerials Library (114 Videos)
* Complete catalog of all official Apple TV Aerials sourced in full 4K SDR/HDR directly from Apple's CDN.
* Organized by category:
  * **Städte & Metropolen** (Dubai, London, New York, San Francisco, Hong Kong...)
  * **Landschaften & Natur** (Grand Canyon, Patagonia, Yosemite, Iceland, Greenland...)
  * **Weltall & ISS-Orbit** (Earth by Night, Auroras, Sahara, Caribbean...)
  * **Unterwasserwelt** (Coral Reefs, Sea Lions, Humpback Whales, Kelp Forests...)
* **Instant Snapshot Previews**: Every single aerial includes a bundled 16:9 preview thumbnail so you know what it looks like before hitting download.
* Background downloads with progress indication and local caching in internal storage.

<p align="center">
  <img src="screenshots/mediathek_cities.png" alt="Apple TV Mediathek - Cities" width="48%">
  <img src="screenshots/mediathek_space.png" alt="Apple TV Mediathek - Space" width="48%">
</p>

### tvOS Screensaver & Smooth Glass Menus
* When idle (or triggered via back button), the launcher smoothly transitions into a fullscreen video screensaver with clean location titles in the bottom-left corner.
* The floating settings drawer uses translucent smoky glass with rounded 26dp corners and subtle edge rims, keeping video playback visible in the background while gently fading the dock.

<p align="center">
  <img src="screenshots/settings_drawer.png" alt="Side Drawer" width="70%">
</p>

---

## Installation

### Via ADB
1. Enable **Developer Options** and **USB / Network Debugging** on your Android TV.
2. Build or download `app-release.apk`.
3. Install and grant permissions:
```bash
adb connect <TV-IP>:5555
adb install -r -d -g app-release.apk
adb shell pm grant dev.plume android.permission.WRITE_SECURE_SETTINGS
adb shell pm grant dev.plume android.permission.BLUETOOTH_CONNECT
```

### Build from source
Requires JDK 17+ and the Android SDK.
```bash
git clone https://github.com/jigrdfsgrdgrd/Plume_Launcher.git
cd Plume_Launcher
./gradlew assembleRelease
```
The compiled APK will be at `app/build/outputs/apk/release/app-release.apk`.

---

## Credits
Based on the original [Plume Launcher](https://github.com/aradhyas8/Plume_Launcher) by aradhyas8. Aerial video streams and snapshots belong to Apple Inc.
Licensed under GPL-3.0.
