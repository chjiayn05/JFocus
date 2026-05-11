package jfocus.monitor;

import java.time.LocalDateTime;

public class WindowSession {
    public String hwnd; // 視窗的唯一識別碼
    public String processName; // 程式名稱 (例如 chrome.exe)
    public String title; // 視窗標題
    public LocalDateTime startTime;
    public LocalDateTime endTime;
    public boolean isDistracted = false;

    public WindowSession(String hwnd, String processName, String title) {
        this.hwnd = hwnd;
        this.processName = processName;
        this.title = title;
        this.startTime = LocalDateTime.now(); // 建立時即為開始時間
    }
}