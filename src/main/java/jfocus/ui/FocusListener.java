package jfocus.ui;
// 這是老師上課講的interface
public interface FocusListener {
    void onTimeUpdate(int secondsRemaining);    // 時間跳動時
    void onDistractionDetected(String appName); // 抓到分心時
    void onFocusFinished();                     // 時間結束時
}