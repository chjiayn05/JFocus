package jfocus.engine;

public interface FocusListener {
    // 引擎每秒會呼叫這個方法，告訴 UI 還剩幾秒
    void onTick(int secondsRemaining); 
    
    // 引擎倒數到 0 時會呼叫這個方法，告訴 UI 時間到了
    void onFinished();                 
}