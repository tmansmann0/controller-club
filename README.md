# Controller Club for AYN Thor

A bottom-screen controller lineup for Android. Tested on an AYN Thor with Android 13 and firmware 1.0.0.377.

Current input support includes buttons, sticks, triggers, and D-pad input. Gyroscope and accelerometer forwarding are not implemented, including for the original Steam Controller and the Eden connection.

Open Controller Club and tap **Nearby** to discover and pair Bluetooth gamepads, then tap a player slot to move or swap a controller. The default puts the Thor first and other controllers in discovery order. Each Joy-Con can be assigned as a sideways pad; **Combine Joy-Cons** sends a left and right half to the same port. Choose **Start routing** to expose occupied seats as **Xbox Controller P1** through **P4**. A game that offers explicit input assignments can use those names to match its player slots. Tap the top display again if a game loses controller focus after you touch the bottom display. Use the **Emulators** menu to switch output modes or open Dolphin or Cemu with their named Club profiles.

**Stop routing** drops the virtual controllers and releases the physical pads immediately. Closing the app task or stopping the helper also stops routing. If you manually chose a Club device in an emulator, select your original profile when you return to regular play. For the Mario Kart Wii setup action, use **Restore previous** to undo the game-specific mapping. With built-in controller access and Xbox output, Club routes the assigned Thor controls exclusively and forwards the Thor’s system buttons separately. Android still lists the original hardware, and some games require you to select the desired Xbox pads in their controller settings. The seat names and Xbox device descriptors are stable. Android’s controller numbers can differ. Virtual pads carry buttons and sticks; rumble, gyro, Wii pointer and native Wii Remote passthrough are outside this route.

The emulator menu detects Dolphin, Cemu Plus, ARMSX3, installed Switch port apps and several other emulators, and provides input setup guidance. Per-game support varies. A native Switch port is not a general-purpose Switch emulator.

## Build and start

Requires the Android SDK (API 34 and build-tools 34.0.0), Java 17, Zig 0.15.x, Android debug keystore, and USB debugging on the Thor for initial installation. Set `ANDROID_SDK_ROOT`, `JAVA_HOME`, and `ZIG` if the defaults in the scripts do not apply.

```sh
scripts/build.sh
scripts/start.sh
```

`start.sh` installs the APK and starts a temporary shell helper using the device’s existing access to Linux gamepad interfaces. It needs no root and makes no firmware changes. USB can be unplugged after installation. On the tested Thor firmware, Club starts its own router after reboot and the first unlock. It restores read access to the built-in controller through the firmware permission service used by AYN Settings. The helper only accepts authenticated requests on device loopback and drops routing if it stops hearing from the app. Run `scripts/stop.sh` to stop both the app and helper. Uninstall Controller Club as usual if you no longer want it.

## Bottom-screen switching

The Thor can show a gesture bar on the lower panel without supporting a separate lower-panel Recents gesture. The stock shortcut is the physical **AYN button → Task**. Android **Settings → System → Gestures → System navigation → 3-button navigation** may be more practical for Home/Back on the lower display; it does not add separate lower Recents. [Thor Back Button Hero](https://github.com/denissteinhorst/thor-back-button-hero) offers an independent lower-screen switcher on a double-tap of Back.

## Optional named defaults for Dolphin and Cemu Plus

With **Dolphin / Cemu profiles** selected in the Emulators menu, routing on, and USB attached, run `scripts/profiles.py --install`. This adds Wii Classic Controller, GameCube and Wii U profiles for P1–P4 as separate files. It does not overwrite existing profiles or select a new one. Dolphin's preset name includes the current Android controller number: if it changes, rerun the command and use the new numbered profile. In Cemu Plus, keep P1 as GamePad and use Pro Controller for later seats where the game allows it. Named presets are optional; ARMSX3 and other apps use their own input mapping.

The Nearby view scans Bluetooth Classic and LE while open. Devices must be advertising or in pairing mode; pads connected by USB show up directly in the player lineup. A real Wii Remote can be listed while discoverable, but Dolphin Android requires a USB [DolphinBar](https://dolphin-emu.org/blog/2026/09/24/dolphin-progress-report-release-2609/#2606-186-android-route-dolphinbar-balance-board-to-the-balance-board-slot) for real remote input. Pair the remote to the bar in Mode 4, then select **Real Wii Remote** in Dolphin.

## Quick Settings

Swipe down twice and tap **Controller Club** to open the player lineup on the lower display. If the tile is missing, add it from **Edit tiles**. After reboot and the first unlock, the app starts its router automatically on the tested Thor firmware, including access to the built-in controls. Start routing can be enabled with the Thor alone and routes external controllers as they connect. On firmware without the AYN permission service, the USB helper remains available for built-in controller access.

### Mario Kart Wii multiplayer

For the US release, use **Emulators → Dolphin → Mario Kart Wii → Use Club seats**. On first use, select the **Dolphin Emulator** folder in Android’s file picker. With routing ready, start routing and launch Mario Kart Wii. Dolphin loads P1 and P2 from the Club lineup, including when you swap the Xbox Nano and the Thor controls. **Restore previous** restores the prior game-specific mappings.

### Eden on Android

With Eden closed, open **Emulators → Eden → Set up controls → Use Club seats**, grant access to its folder when asked, then reopen Eden. In **Eden controllers** mode, all four players follow the Club lineup, including an external controller as P1 or the Thor in any slot. On the tested Thor firmware, Club restores built-in controller access and starts its local router automatically after reboot and first unlock. On other firmware, use `scripts/start.sh` to provide access through the USB helper.

Club sends the four fixed slots over Eden’s local Cemuhook input connection. Stop routing to release external controllers and return the Thor to P1 immediately. The helper continues forwarding the Thor to P1 while routing is off, including after the Club UI closes. Stopping the helper ends this forwarding. **Restore previous** restores the controller settings changed by this setup.

The tested Eden build is the project’s signed [controller/display reconnection fix build](https://git.eden-emu.dev/eden-ci/pr/releases/tag/4571-16eedd0344), installed as version `737ab4d`. The earlier tested build `1f6734c` crashed or froze when Android recreated its activity after a Bluetooth keyboard connected or disconnected. The original Steam Controller exposes a keyboard that can trigger this behavior.

### Original Steam Controller

Use **Steam + B** at power-on to reconnect to the most recent Bluetooth host, or **Steam + Start** for the other saved host. To pair with the Thor for the first time, open **Nearby** and power on with **Steam + Y**. Controller Club starts pairing from the controller’s Bluetooth advertisement. Android may ask for confirmation. After a successful connection, Controller Club watches for the saved controller and can start pairing again if it advertises under a new address. See [Valve’s guide for the 2015 Steam Controller](https://help.steampowered.com/en/faqs/view/1796-5FC3-88B3-C85F).

Assign **Steam Controller (2015)** a seat and start routing. Club remembers the chosen seat and reuses it when available. The left trackpad clicks act as a D-pad, and the right trackpad acts as a right stick. Stop routing to return the controller to its default keyboard and mouse mode.
