package jfocus.main;

import java.util.UUID;

import javafx.application.Application;
import jfocus.db.DatabaseCore;
import jfocus.notification.NoOpNotificationService;
import jfocus.notification.NotificationService;
import jfocus.notification.SystemNotificationService;
import jfocus.ai.ModelTrainer;

/**
 * 應用程式主入口，負責啟動 UI
 */
public final class FocusApp {
    private static volatile String sessionId;
    private static volatile NotificationService notificationService = new NoOpNotificationService();

    private FocusApp() {
    }

    public static synchronized String startNewSession() {
        sessionId = UUID.randomUUID().toString();
        return sessionId;
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static NotificationService getNotificationService() {
        return notificationService;
    }

    public static synchronized void initializeNotificationService() {
        System.out.println("[FocusApp] Initializing notification service...");
        notificationService.shutdown();
        notificationService = new SystemNotificationService();
        System.out.println("[FocusApp] Notification service initialized. Available: " + notificationService.isAvailable());
    }

    public static synchronized void shutdownNotificationService() {
        notificationService.shutdown();
        notificationService = new NoOpNotificationService();
    }

    public static synchronized void startModelTraining(){
        ModelTrainer.trainModel();
    }

    public static void main(String[] args) {
        DatabaseCore.initializeDatabase();
        startModelTraining();
        initializeNotificationService();
        try {
            Application.launch(jfocus.ui.FocusUI.class, args);
        } finally {
            shutdownNotificationService();
        }
    }
}
