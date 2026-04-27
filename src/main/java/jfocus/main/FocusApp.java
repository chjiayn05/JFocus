package jfocus.main;

import java.util.UUID;

import javafx.application.Application;
import jfocus.db.DatabaseCore;

/**
 * 應用程式主入口，負責啟動 UI 與提供本次 session id。
 */
public final class FocusApp {
    private static volatile String sessionId;

    private FocusApp() {
    }

    public static synchronized String startNewSession() {
        sessionId = UUID.randomUUID().toString();
        return sessionId;
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static void main(String[] args) {
        DatabaseCore.initializeDatabase();
        Application.launch(jfocus.ui.FocusUI.class, args);
    }
}