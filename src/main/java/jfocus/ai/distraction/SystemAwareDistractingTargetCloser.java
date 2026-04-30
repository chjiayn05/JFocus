package jfocus.ai.distraction;

import java.util.Locale;

import jfocus.monitor.WindowSession;

public class SystemAwareDistractingTargetCloser implements DistractingTargetCloser {
    private static final String OS_NAME_PROPERTY = "os.name";
    private static final String WINDOWS_TOKEN = "win";
    private static final String MAC_TOKEN = "mac";

    private final DistractingTargetCloser windowsCloser;
    private final DistractingTargetCloser macCloser;

    public SystemAwareDistractingTargetCloser() {
        this(new WindowsDistractingTargetCloser(), new MacDistractingTargetCloser());
    }

    SystemAwareDistractingTargetCloser(DistractingTargetCloser windowsCloser, DistractingTargetCloser macCloser) {
        this.windowsCloser = windowsCloser;
        this.macCloser = macCloser;
    }

    @Override
    public boolean closeDistractingTarget(WindowSession session, boolean closeTabOnly) {
        if (session == null) {
            return false;
        }

        String osName = System.getProperty(OS_NAME_PROPERTY, "").toLowerCase(Locale.ROOT);
        if (osName.contains(WINDOWS_TOKEN)) {
            return windowsCloser.closeDistractingTarget(session, closeTabOnly);
        }

        if (osName.contains(MAC_TOKEN)) {
            return macCloser.closeDistractingTarget(session, closeTabOnly);
        }

        return false;
    }
}
