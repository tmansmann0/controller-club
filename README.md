# Controller Club for AYN Thor

A bottom-screen controller lineup for Android. Tested on an AYN Thor with Android 13 and firmware 1.0.0.377.

Current input support includes buttons, sticks, triggers, D-pad input, and gyro/accelerometer forwarding over the local Cemuhook (DSU) connection. Motion has been verified from the original Steam Controller over Bluetooth LE and the Thor’s built-in sensors. Motion is converted into the DSU/DS4 coordinate frame using physical units (g and degrees per second), without smoothing or sensitivity multipliers. Other controllers use their own Android-exposed motion sensors when available. The tested Xbox Nano exposes no motion sensor to Android.

Open Controller Club and tap **Nearby** to discover and pair Bluetooth gamepads, then tap a player slot to move or swap a controller. The initial default puts the Thor first and other controllers in discovery order. Your chosen lineup and removed controllers are remembered across reconnects and reboots using controller identities. All four Club slots remain assignable, including P1, while built-in input access is recovering; Android’s system controller order does not reserve a Club slot. Each Joy-Con can be assigned as a sideways pad; **Combine Joy-Cons** sends a left and right half to the same port. Club keeps **Controller Club P1** through **P4** registered while its service runs, including empty seats and while routing is off. Choose **Start routing** to send the assigned physical controllers to those players. A game that offers explicit input assignments can use those names to match its player slots. Tap the top display again if a game loses controller focus after you touch the bottom display. Use the **Emulators** menu to switch output modes or open Dolphin or Cemu with their named Club profiles.

**Stop routing** releases the physical pads immediately and leaves the four named virtual devices idle. Controller names, vendor/product IDs and axis layout are identical across output modes. The service continues after closing its UI and restores the saved routing on/off choice after reboot and first unlock. Force-stopping the app stops its router; Android requires reopening a force-stopped app before its boot receiver can run again. If you manually chose a Club device in an emulator, select your original profile when you return to regular play. For the Mario Kart Wii setup action, use **Restore previous** to undo the game-specific mapping. With built-in controller access, Club routes the assigned Thor controls exclusively and forwards the Thor’s system buttons separately. Android still lists the original hardware, and some games require you to select the desired Xbox pads in their controller settings. The seat names and device descriptors are stable. Android’s controller numbers can differ. The named virtual pads carry buttons and sticks. Motion is sent separately over the local DSU connection at `127.0.0.1:37821`; an emulator must support and select that motion source. Rumble, Wii pointer and native Wii Remote passthrough are outside this route.

The emulator menu detects Dolphin, Cemu Plus, ARMSX3, installed Switch port apps and several other emulators, and provides input setup guidance. Per-game support varies. A native Switch port is not a general-purpose Switch emulator.

## Build and start

Requires the Android SDK (API 34 and build-tools 34.0.0), Java 17, Zig 0.15.x, Android debug keystore, and USB debugging on the Thor for initial installation. Set `ANDROID_SDK_ROOT`, `JAVA_HOME`, and `ZIG` if the defaults in the scripts do not apply.

```sh
scripts/build.sh
scripts/start.sh
```

`start.sh` installs the APK, stops any legacy USB helper, adds the Quick Settings tile, and opens Club. Club uses the router bundled with its APK rather than selecting an older USB helper. It needs no root. USB can be unplugged after installation. On the tested Thor firmware, Club starts its own router after reboot and the first unlock, restores the saved lineup and routing on/off choice, and creates all four named player devices even while routing is off. It restores read access to the built-in controller through the firmware permission service used by AYN Settings, retrying automatically while that input is unavailable. The helper only accepts authenticated requests on device loopback and releases physical input if it stops hearing from the app. Run `scripts/stop.sh` to stop the app and any legacy helper. Uninstall Controller Club as usual if you no longer want it.

## Bottom-screen switching

The Thor can show a gesture bar on the lower panel without supporting a separate lower-panel Recents gesture. The stock shortcut is the physical **AYN button → Task**. Android **Settings → System → Gestures → System navigation → 3-button navigation** may be more practical for Home/Back on the lower display; it does not add separate lower Recents. [Thor Back Button Hero](https://github.com/denissteinhorst/thor-back-button-hero) offers an independent lower-screen switcher on a double-tap of Back.

## Optional named defaults for Dolphin and Cemu Plus

With **Dolphin / Cemu profiles** selected in the Emulators menu, routing on, and USB attached, run `scripts/profiles.py --install`. This adds Wii Classic Controller, GameCube and Wii U profiles for P1–P4 as separate files. It does not overwrite existing profiles or select a new one. Dolphin's preset name includes the current Android controller number: if it changes, rerun the command and use the new numbered profile. In Cemu Plus, keep P1 as GamePad and use Pro Controller for later seats where the game allows it. Named presets are optional; ARMSX3 and other apps use their own input mapping.

The Nearby view scans Bluetooth Classic and LE while open. Devices must be advertising or in pairing mode; pads connected by USB show up directly in the player lineup. A real Wii Remote can be listed while discoverable, but Dolphin Android requires a USB [DolphinBar](https://dolphin-emu.org/blog/2026/09/24/dolphin-progress-report-release-2609/#2606-186-android-route-dolphinbar-balance-board-to-the-balance-board-slot) for real remote input. Pair the remote to the bar in Mode 4, then select **Real Wii Remote** in Dolphin.

## Quick Settings

Swipe down twice and tap **Controller Club** to open the player lineup on the lower display. If the tile is missing, add it from **Edit tiles**. After reboot and the first unlock, the app starts its router automatically on the tested Thor firmware, including access to the built-in controls. Start routing can be enabled with the Thor alone and routes external controllers as they connect. Built-in routing requires Thor firmware exposing the AYN permission service.

### Mario Kart Wii multiplayer

For the US release, use **Emulators → Dolphin → Mario Kart Wii → Use Club seats**. On first use, select the **Dolphin Emulator** folder in Android’s file picker. With routing ready, start routing and launch Mario Kart Wii. Dolphin loads P1 and P2 from the Club lineup, including when you swap the Xbox Nano and the Thor controls. **Restore previous** restores the prior game-specific mappings.

### Eden on Android

With Eden closed, open **Emulators → Eden → Set up controls → Use Club seats**, grant access to its folder when asked, then reopen Eden. In **Eden controllers** mode, all four players follow the Club lineup, including an external controller as P1 or the Thor in any slot. On the tested Thor firmware, Club restores built-in controller access and starts its local router automatically after reboot and first unlock. Built-in routing requires Thor firmware exposing the AYN permission service.

Club exposes **Controller Club P1–P4** as Android input devices in Eden mode too. It also sends the four fixed slots, including available gyro and accelerometer samples, over Eden’s local Cemuhook input connection for existing Club presets and motion bindings. Eden still lists this compatibility connection under its own UDP labels; the named Android devices are separate. Run **Use Club seats** again after upgrading from an earlier version to add the motion bindings. Stop routing to release external controllers and return the Thor to P1 when built-in input access is available. The helper continues forwarding the Thor to P1 while routing is off, including after the Club UI closes. Stopping the helper ends this forwarding. **Restore previous** restores the controller settings changed by this setup.

The tested Eden build is the project’s signed [controller/display reconnection fix build](https://git.eden-emu.dev/eden-ci/pr/releases/tag/4571-16eedd0344), installed as version `737ab4d`. The earlier tested build `1f6734c` crashed or froze when Android recreated its activity after a Bluetooth keyboard connected or disconnected. The original Steam Controller exposes a keyboard that can trigger this behavior.

### Original Steam Controller

Use **Steam + B** at power-on to reconnect to the most recent Bluetooth host, or **Steam + Start** for the other saved host. To pair with the Thor for the first time, open **Nearby** and power on with **Steam + Y**. Controller Club starts pairing from the controller’s Bluetooth advertisement. Android may ask for confirmation. After a successful connection, Controller Club watches for the saved controller and can start pairing again if it advertises under a new address. See [Valve’s guide for the 2015 Steam Controller](https://help.steampowered.com/en/faqs/view/1796-5FC3-88B3-C85F).

Assign **Steam Controller (2015)** a seat and start routing. Club remembers the chosen seat and reuses it when available. The left trackpad clicks act as a D-pad, and the right trackpad acts as a right stick. Raw gyro and accelerometer samples follow the controller’s assigned DSU player slot; Eden’s setup includes the motion bindings for all four players. Stop routing to return the controller to its default keyboard and mouse mode.

## Mapping compatibility

The virtual devices use the original Club profile identities (`1209:5440` through `1209:5443`) and axis layout in every output mode. Old bindings to Xbox-named devices or Eden UDP devices do not automatically become bindings to these named devices. Eden's Android backend also stores an enumeration-based port alongside the device GUID; its behavior when other hardware connects before boot requires an on-device check. Do not rewrite a running emulator's settings. The current update does not modify Eden's installed configuration automatically.
