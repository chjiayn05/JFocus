package jfocus.ai.distraction;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import java.util.Locale;

public class MacAccessibilityPermission {

    private interface ApplicationServices extends Library {
        ApplicationServices INSTANCE = Native.load("ApplicationServices", ApplicationServices.class);
        boolean AXIsProcessTrusted();
        boolean AXIsProcessTrustedWithOptions(Pointer options);
    }

    private interface CoreFoundation extends Library {
        CoreFoundation INSTANCE = Native.load("CoreFoundation", CoreFoundation.class);
        Pointer CFStringCreateWithCString(Pointer allocator, String cStr, int encoding);
        Pointer CFDictionaryCreate(Pointer allocator, Pointer[] keys, Pointer[] values,
                                   long numValues, Pointer keyCallbacks, Pointer valueCallbacks);
        void CFRelease(Pointer cf);
    }

    private static final int CF_STRING_ENCODING_UTF8 = 0x08000100;

    public static boolean isGranted() {
        try {
            return ApplicationServices.INSTANCE.AXIsProcessTrusted();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 若尚未取得輔助使用權限，向 OS 發起請求：
     * - macOS 13+：直接開啟「系統設定 → 隱私權與安全性 → 輔助使用」
     * - macOS 12 以下：跳出系統對話框引導使用者授權
     */
    public static void requestIfNeeded() {
        if (!isMacOS() || isGranted()) {
            return;
        }
        try {
            NativeLibrary cfLib = NativeLibrary.getInstance("CoreFoundation");

            // kCFBooleanTrue 是 CoreFoundation 的全域指標常數
            Pointer kCFBooleanTrue = cfLib.getGlobalVariableAddress("kCFBooleanTrue").getPointer(0);
            Pointer keyCallbacks = cfLib.getGlobalVariableAddress("kCFTypeDictionaryKeyCallBacks");
            Pointer valueCallbacks = cfLib.getGlobalVariableAddress("kCFTypeDictionaryValueCallBacks");

            Pointer keyStr = CoreFoundation.INSTANCE.CFStringCreateWithCString(
                    null, "AXTrustedCheckOptionPrompt", CF_STRING_ENCODING_UTF8);

            Pointer[] keys = {keyStr};
            Pointer[] values = {kCFBooleanTrue};
            Pointer dict = CoreFoundation.INSTANCE.CFDictionaryCreate(
                    null, keys, values, 1, keyCallbacks, valueCallbacks);

            ApplicationServices.INSTANCE.AXIsProcessTrustedWithOptions(dict);

            CoreFoundation.INSTANCE.CFRelease(dict);
            CoreFoundation.INSTANCE.CFRelease(keyStr);
        } catch (Exception e) {
            System.err.println("[Accessibility] 無法發起授權請求: " + e.getMessage());
        }
    }

    private static boolean isMacOS() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }
}
