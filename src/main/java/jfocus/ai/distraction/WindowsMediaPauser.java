package jfocus.ai.distraction;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public class WindowsMediaPauser {

    // VK_MEDIA_PLAY_PAUSE = 0xB3，直接注入 OS input stream，不需要前景視窗
    private static final byte VK_MEDIA_PLAY_PAUSE = (byte) 0xB3;
    private static final int KEYEVENTF_KEYUP = 0x0002;

    private interface User32Ext extends Library {
        User32Ext INSTANCE = Native.load("user32", User32Ext.class);
        void keybd_event(byte bVk, byte bScan, int dwFlags, Pointer dwExtraInfo);
    }

    public static void pauseAllMedia() {
        User32Ext.INSTANCE.keybd_event(VK_MEDIA_PLAY_PAUSE, (byte) 0, 0, Pointer.NULL);
        User32Ext.INSTANCE.keybd_event(VK_MEDIA_PLAY_PAUSE, (byte) 0, KEYEVENTF_KEYUP, Pointer.NULL);
    }
}
