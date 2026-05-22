package jfocus.ai.distraction;

import java.io.IOException;

public class WindowsMediaPauser {

    // char 179 = VK_MEDIA_PLAY_PAUSE，所有響應媒體鍵的應用程式都會暫停
    private static final String PAUSE_MEDIA_COMMAND =
            "(New-Object -ComObject WScript.Shell).SendKeys([char]179)";

    public static void pauseAllMedia() {
        Thread thread = new Thread(() -> {
            try {
                new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", PAUSE_MEDIA_COMMAND)
                        .start()
                        .waitFor();
            } catch (IOException e) {
                System.err.println("暫停媒體失敗: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "jfocus-media-pauser");
        thread.setDaemon(true);
        thread.start();
    }
}
