package club.thor.pads;

/** Instructions only. Club does not read or write Eden's configuration. */
final class EdenGuide {
    static final String PACKAGE="dev.eden.eden_emulator";
    static final String INSTRUCTIONS=
        "1. Start routing in Club. Your saved lineup decides which physical controller supplies P1, P2, P3 and P4.\n\n"
        +"2. Open Eden → Settings → Controls. If you have a custom layout, save it as an Eden profile before changing its device.\n\n"
        +"3. For each player you want to use with Club, select Controller Club P1, P2, P3 or P4 as its input device. Eden may append another device-order number to the name. Keep the other players and profiles as you prefer.\n\n"
        +"4. Review the buttons and sticks, save your settings, and launch your game. Choose the named Club devices for buttons and sticks; UDP sources are for optional motion input.\n\n"
        +"After reboot\nClub restores its lineup and routing choice. If Eden stops responding, reselect the same named Club device: Eden can change its internal device order after reboot or reconnection. Club never rewrites your Eden settings.\n\n"
        +"Optional gyro\nUse Club's local motion server at 127.0.0.1:37821 and choose the motion source matching your player. Leave your existing motion setup alone if you do not need it.\n\n"
        +"Routing off\nThe Thor passes through to Club P1. External controllers return to normal Android handling.";
    private EdenGuide(){}
}
