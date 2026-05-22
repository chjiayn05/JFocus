package jfocus.ai.distraction;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class MacMediaPauser {

    // MediaRemote.framework (私有框架) — 這是 macOS 12+ 上唯一可靠的
    // 跨 app 媒體暫停方式，被 NepTunes、Nuage 等 menu bar 工具廣泛使用。
    // CGEventPost(kCGHIDEventTap, mediaKeyEvent) 在 macOS 12+ 已被靜默阻擋。
    private interface MediaRemote extends Library {
        MediaRemote INSTANCE = Native.load(
                "/System/Library/PrivateFrameworks/MediaRemote.framework/MediaRemote",
                MediaRemote.class);
        // kMRTogglePlayPause = 2
        void MRMediaRemoteSendCommand(int command, Pointer options);
    }

    private static final int kMRTogglePlayPause = 2;

    public static void pauseAllMedia() {
        Thread thread = new Thread(() -> {
            try {
                MediaRemote.INSTANCE.MRMediaRemoteSendCommand(kMRTogglePlayPause, null);
            } catch (Exception e) {
                System.err.println("[MediaPauser] 執行失敗: " + e.getMessage());
            }
        }, "jfocus-media-pauser");
        thread.setDaemon(true);
        thread.start();
    }
}
