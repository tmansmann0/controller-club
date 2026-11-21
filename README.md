# Controller Club for AYN Thor

A bottom-screen controller lineup for Android. Tested on an AYN Thor with Android 13 and firmware 1.0.0.377.

Current input support includes buttons, sticks, triggers, D-pad input, and gyro/accelerometer forwarding over the local Cemuhook (DSU) connection. Motion has been verified from the original Steam Controller over Bluetooth LE and the Thor’s built-in sensors. Motion is converted into the DSU/DS4 coordinate frame using physical units (g and degrees per second), without smoothing or sensitivity multipliers. Other controllers use their own Android-exposed motion sensors when available. The tested Xbox Nano exposes no motion sensor to Android.

Open Controller Club and tap **Nearby** to discover and pair Bluetooth gamepads, then tap a player slot to move or swap a controller. The initial default puts the Thor first and other controllers in discovery order. Your chosen lineup and removed controllers are remembered across reconnects and reboots using controller identities. All four Club slots remain assignable, including P1, while built-in input access is recovering; Android’s system controller order does not reserve a Club slot. Each Joy-Con can be assigned as a sideways pad; **Combine Joy-Cons** sends a left and right half to the same port. Club keeps **Controller Club P1** through **P4** registered while its service runs, including empty seats and while routing is off. Choose **Start routing** to send the assigned physical controllers to those players. A game that offers explicit input assignments can use those names to match its player slots. Tap the top display again if a game loses controller focus after you touch the bottom display. Use the **Emulators** menu to switch output modes or open Dolphin or Cemu with their named Club profiles.

**Stop routing** releases the physical pads immediately and keeps the four named virtual devices registered. P1 passes through the Thor’s built-in controls; P2–P4 remain idle. Controller names, vendor/product IDs and axis layout are identical across output modes. The service continues after closing its UI and restores the saved routing on/off choice after reboot and first unlock. Force-stopping the app stops its router; Android requires reopening a force-stopped app before its boot receiver can run again. If you manually chose a Club device in an emulator, select your original profile when you return to regular play. For the Mario Kart Wii setup action, use **Restore previous** to undo the game-specific mapping. With built-in controller access, Club routes the assigned Thor controls exclusively and forwards the Thor’s system buttons separately. Android still lists the original hardware, and some games require you to select the desired Xbox pads in their controller settings. The seat names and device descriptors are stable. Android’s controller numbers can differ. The named virtual pads carry buttons and sticks. Motion is sent separately over the local DSU connection at `127.0.0.1:37821`; an emulator must support and select that motion source. Wii pointer and native Wii Remote passthrough are outside this route. See below for rumble support and its DSU limitation.

The emulator menu detects Dolphin, Cemu Plus, ARMSX3, installed Switch port apps and several other emulators, and provides input setup guidance. Per-game support varies. A native Switch port is not a general-purpose Switch emulator.

## Original Steam Controller rear paddles

In a player panel, tap **Steam rear paddles** while the original Steam Controller is connected. Choose the left and right paddle independently: A/B/X/Y (Xbox positions), bumpers, triggers, stick clicks, D-pad directions, Select/Back, Start, Guide, or Unassigned. The picker uses Club's own controls. Both paddles start unassigned; saved bindings survive reconnection and reboot and follow the Steam Controller when its player slot changes.

A paddle holds its assigned control for as long as it is pressed. Ordinary buttons continue working, including holding the same button with both a paddle and a face button. Changes take effect immediately while routing is on. The outputs use the existing Club input route, including Eden's stable DSU profiles; no emulator configuration is rewritten. This is gamepad remapping, not keyboard macros or Android app shortcuts. The decoder and live rebinding have automated coverage; physical paddle verification is pending.

## Battery and rumble

Version 0.6.6 adds reported battery percentages and charging/full status to the player cards and assignment list. Paired Joy-Cons show each half separately. Thor uses the console battery; external controllers use Android's per-controller battery API (Android 12+). Unknown levels remain **Battery unavailable**, and Club does not infer charging from a cable, a full percentage, or a controller name. Readings refresh about every ten seconds and follow the physical controller when players are swapped. Identical controllers that cannot be distinguished safely are left unavailable.

The named Android **Controller Club P1–P4** devices now accept Linux `FF_RUMBLE` effects and forward them to the assigned physical controller's Android vibrator. Combined Joy-Cons receive the same effect on both halves. An emulator must support Android gamepad vibration and target a Club device. Effects stop when routing stops, an assignment changes, or the helper disconnects; each physical vibration expires after 120 ms unless refreshed. Android's general vibration API combines the strong/weak magnitudes into one amplitude. A **Test rumble** button appears in the player panel when Android reports vibration support.

**Eden's stable DSU profiles do not return rumble.** This update does not change or replace those profiles. The original Steam Controller's custom BLE path currently forwards input and motion only; its proprietary battery reports and haptic output are not implemented here. Other controllers likewise depend on what their Android driver exposes. These new feedback paths have local automated coverage; hardware feedback still needs verification on the Thor.

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

### Eden — stable profiles

Eden can cache Android GUID/port registrations, retain duplicate native device entries, and renumber them after reconnects or reboot. For Eden, use the optional **Controller Club P1 Stable** through **P4 Stable** profiles rather than its device Auto-map picker. These profiles carry **buttons, sticks and gyro** over Club's existing loopback Cemuhook connection. They preserve the Xbox-position layout verified with Xbox Nano on P1 and the original Steam Controller on P2 in Mario Kart 8 Deluxe.

The Android **Controller Club P1–P4** devices remain available for other emulators. Duplicate Club entries, generic UDP labels and input-overlay entries in Eden's picker belong to Eden; they are not extra players created by Club.

## Manual Eden setup

1. Start Club routing and arrange the physical controllers in your preferred slots.
2. Save any custom Eden player layout as its own profile before changing it. Leave players you do not want to change alone.
3. In Eden's UDP/Cemuhook input settings, enable UDP controller input and include `127.0.0.1:37821`. Keep existing servers and their order.
4. In Club, open **Emulators → Eden → Set up controls → Export stable profiles**. Enter Eden's UDP server list in exactly the same order, then choose where to save the ZIP. The default is the verified list `127.0.0.1:26760,127.0.0.1:37821`.
5. Extract the ZIP. Copy its four `.ini` files into Eden's `config/input` folder, alongside your existing profiles. Do not replace `config.ini` or a custom profile; rename a new file first if its filename already exists.
6. For each desired player in **Eden → Settings → Controls**, open the **Profile** dialog and load **Controller Club P1 Stable**, **P2 Stable**, **P3 Stable**, or **P4 Stable**. Enable that player if needed. Do not use the device Auto-map picker to replace the loaded profile.
7. Save and launch the game. Other players, profiles and custom mappings remain yours to configure.

Club remembers its lineup and routing on/off choice. The profiles follow Club's player slots rather than Eden's changing Android-device order. **Keep Eden's UDP server order unchanged**; if you change it, export and load matching profiles again. With Club second after the default server, P1–P4 use pad IDs 4–7. With Club first, they use 0–3. The exporter calculates this offset and validates the server list.

Export is explicit and only writes the ZIP you choose. Club does not inspect or rewrite Eden's installed configuration, select a profile, change player assignments, or run background INI repairs. No diagnostic permission is required. The ZIP also contains these installation instructions and its exact server order.

## Local verification

After building, run `ZIG=/path/to/zig scripts/test.sh`. The suite includes byte-for-byte comparisons with the verified P1/P2 profiles, P1–P4 pad offsets for several server orders, Xbox-position button values, archive contents, and invalid-list rejection. Tests do not connect to an Android device.
