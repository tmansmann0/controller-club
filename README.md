# Controller Club for AYN Thor

A bottom-screen controller lineup for Android. Tested on an AYN Thor with Android 13 and firmware 1.0.0.377.

Current input support includes buttons, sticks, triggers, D-pad input, and gyro/accelerometer forwarding over the local Cemuhook (DSU) connection. Motion has been verified from the original Steam Controller over Bluetooth LE and the Thor’s built-in sensors. Motion is converted into the DSU/DS4 coordinate frame using physical units (g and degrees per second), without smoothing or sensitivity multipliers. Other controllers use their own Android-exposed motion sensors when available. The tested Xbox Nano exposes no motion sensor to Android.

Open Controller Club and tap **Nearby** to discover and pair Bluetooth gamepads, then tap a player slot to move or swap a controller. The initial default puts the Thor first and other controllers in discovery order. Your chosen lineup and removed controllers are remembered across reconnects and reboots using controller identities. All four Club slots remain assignable, including P1, while built-in input access is recovering; Android’s system controller order does not reserve a Club slot. Each Joy-Con can be assigned as a sideways pad; **Combine Joy-Cons** sends a left and right half to the same port. Club keeps **Controller Club P1** through **P4** registered while its service runs, including empty seats and while routing is off. Choose **Start routing** to send the assigned physical controllers to those players. A game that offers explicit input assignments can use those names to match its player slots. Tap the top display again if a game loses controller focus after you touch the bottom display. Use the **Emulators** menu to switch output modes or open Dolphin or Cemu with their named Club profiles.

**Stop routing** releases the physical pads immediately and keeps the four named virtual devices registered. P1 passes through the Thor’s built-in controls; P2–P4 remain idle. Controller names, vendor/product IDs and axis layout are identical across output modes. The service continues after closing its UI and restores the saved routing on/off choice after reboot and first unlock. Force-stopping the app stops its router; Android requires reopening a force-stopped app before its boot receiver can run again. If you manually chose a Club device in an emulator, select your original profile when you return to regular play. For the Mario Kart Wii setup action, use **Restore previous** to undo the game-specific mapping. With built-in controller access, Club routes the assigned Thor controls exclusively and forwards the Thor’s system buttons separately. Android still lists the original hardware, and some games require you to select the desired Xbox pads in their controller settings. The seat names and device descriptors are stable. Android’s controller numbers can differ. The named virtual pads carry buttons and sticks. Motion is sent separately over the local DSU connection at `127.0.0.1:37821`; an emulator must support and select that motion source. Rumble, Wii pointer and native Wii Remote passthrough are outside this route.

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

### Eden

Club exposes **Controller Club P1–P4** in Eden's Android device picker and separately provides optional DSU motion. Eden integration is manual: Club never changes controller bindings, profiles or emulator files. See the manual setup below. Stopping routing releases external controllers and passes the Thor through to the named P1 device.

## Manual Eden setup

Club provides **Controller Club P1–P4** as Android gamepads. It does not read or write Eden's configuration. Open **Emulators → Eden → Set up controls** in Club for the manual steps, or:

1. Start Club routing and arrange your physical controllers in the desired slots.
2. Open Eden's **Settings → Controls**. Save your custom layout as a profile before changing its device.
3. Select **Controller Club P1**, **P2**, **P3** or **P4** for each player you want to use through Club. Keep other players and existing profiles as you prefer.
4. Check the buttons and sticks, save, and launch your game.

Club remembers its lineup and routing on/off choice. Eden stores its own mappings. If a mapping stops responding after reboot or reconnecting hardware, reselect the same named Club device in Eden. Eden's internal Android device-order number can change; fixed Club names alone cannot prevent that. No diagnostic permission is required by Club.

For optional motion, configure the local DSU server at `127.0.0.1:37821` and choose the matching player source in Eden's motion settings. Use the named Android devices for buttons and sticks. Existing motion settings can be left unchanged.
