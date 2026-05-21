package jfocus.monitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class MacWindowScanner implements WindowScanner {

    @Override
    public Map<String, WindowSession> scanWindows() {
        Map<String, WindowSession> currentScan = new HashMap<>();

        // 透過 AppleScript 取得 macOS 目前開啟的視窗 (過濾掉最小化/縮小的視窗)
        String script = "tell application \"System Events\"\n" +
                "    set windowList to \"\"\n" +
                "    repeat with p in (every process whose visible is true)\n" +
                "        set pName to name of p\n" +
                "        try\n" +
                "            repeat with w in (every window of p)\n" +
                "                set isMini to false\n" +
                "                try\n" +
                "                    set isMini to miniaturized of w\n" +
                "                on error\n" +
                "                    set isMini to false\n" +
                "                end try\n" +
                "                if isMini is false then\n" +
                "                    set wName to name of w\n" +
                "                    if wName is not \"\" then\n" +
                "                        set windowList to windowList & pName & \"::\" & wName & \"\\n\"\n" +
                "                    end if\n" +
                "                end if\n" +
                "            end repeat\n" +
                "        end try\n" +
                "    end repeat\n" +
                "    return windowList\n" +
                "end tell";

        try {
            ProcessBuilder pb = new ProcessBuilder("osascript", "-e", script);
            Process p = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty())
                    continue;
                String[] parts = line.split("::", 2);
                if (parts.length == 2) {
                    String processName = parts[0].trim();
                    String title = parts[1].trim();
                    // 把 "ProcessName::WindowTitle" 當成唯一識別碼
                    String windowKey = processName + "::" + title;
                    currentScan.put(windowKey, new WindowSession(windowKey, processName, title));
                }
            }
            p.waitFor();
        } catch (Exception e) {
            System.err.println("macOS 掃描視窗時發生錯誤: " + e.getMessage());
        }

        return currentScan;
    }
}
