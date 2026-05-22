package jfocus.ai.distraction;

import java.util.Locale;

public class SystemAwareMediaPauser {
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String WINDOWS_TOKEN = "win";
    private static final String MAC_TOKEN = "mac";

    public static void pauseAllMedia() {
        String osName = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        if (osName.contains(WINDOWS_TOKEN)) {
            WindowsMediaPauser.pauseAllMedia();
        } else if (osName.contains(MAC_TOKEN)) {
            MacMediaPauser.pauseAllMedia();
        }
    }
}
