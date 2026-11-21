package club.thor.pads;

/** Manual, opt-in setup. Club never reads or rewrites Eden's configuration. */
final class EdenGuide {
    static final String PACKAGE="dev.eden.eden_emulator";
    static final String INSTRUCTIONS=
        "1. Start Club routing. Your saved lineup supplies P1, P2, P3 and P4.\n\n"
        +"2. In Eden, save any custom player layout as its own profile before choosing another. Keep players you do not want to change as they are.\n\n"
        +"3. In Eden's UDP/Cemuhook settings, enable UDP controller input and include 127.0.0.1:37821. Export stable profiles below using that server list in exactly the same order. The verified default is 127.0.0.1:26760,127.0.0.1:37821.\n\n"
        +"4. Extract the exported ZIP. Copy its four INI files into Eden's config/input folder, alongside existing profiles. Do not replace config.ini or a custom profile. Rename a new file if its name already exists.\n\n"
        +"5. Open Eden → Settings → Controls → the player you want → Profile. Load Controller Club P1 Stable, P2 Stable, P3 Stable or P4 Stable. Use the Profile dialog, not the device Auto-map picker. These profiles include buttons, sticks and gyro with the verified Xbox-position layout.\n\n"
        +"Why profiles?\nEden can cache duplicate Android Club entries, change numeric device ports, or select an input overlay from the device picker. Those entries belong to Eden, not extra players in Club. Stable profiles use Club's loopback input instead of those Android IDs.\n\n"
        +"After reboot\nClub remembers its lineup and routing choice. Keep Eden's UDP server order unchanged. If you change that order, export and load matching profiles again.\n\n"
        +"Routing off\nThe Thor passes through to Club P1. External controllers return to normal Android handling.\n\n"
        +"Exporting never selects a profile or changes your current mappings. You choose which players use Club.";
    private EdenGuide(){}
}
