package jfocus.idle;

public interface IdleListener {
    /**
     * 閒置狀態改變時觸發
     * @param isIdle true 表示進入閒置狀態 (中斷計時), false 表示使用者回來了 (恢復計時)
     * @param idleTimeMillis 觸發閒置時的閒置毫秒數 (讓引擎可以回溯扣除這段時間)
     */
    void onIdleStateChanged(boolean isIdle, long idleTimeMillis);
}
