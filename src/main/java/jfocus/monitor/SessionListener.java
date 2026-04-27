package jfocus.monitor;

public interface SessionListener {
    // 🚀 新增：當新視窗一出現就立刻呼叫
    void onSessionStarted(WindowSession session);

    // 當視窗關閉時呼叫（維持原樣，用於統計總時長）
    void onSessionEnded(WindowSession session);
}