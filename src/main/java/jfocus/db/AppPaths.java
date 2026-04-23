package jfocus.db;

import java.nio.file.Path;

/**
 * 集中管理應用程式預設路徑。
 */
public final class AppPaths {
    private static final String BASE_DIR_PROPERTY = "jfocus.base.dir";
    private static final String BASE_DIR_ENV = "JFOCUS_BASE_DIR";
    private static final String DEFAULT_BASE_DIR_NAME = "jfocus-data";

    private static final String DB_DIR_NAME = "db";
    private static final String DB_FILE_NAME = "jfocus.db";

    private static final String ML_DIR_NAME = "ml";
    private static final String TRAINING_FILE_NAME = "training.txt";
    private static final String MODEL_FILE_NAME = "model.bin";

    private AppPaths() {
    }

    /**
     * 取得應用資料根目錄。
     *
     * 預設為當前工作目錄下的 jfocus-data，可透過 jfocus.base.dir 或 JFOCUS_BASE_DIR 覆蓋。
     */
    public static Path getBaseDir() {
        String configuredBaseDir = firstNonBlank(System.getProperty(BASE_DIR_PROPERTY), System.getenv(BASE_DIR_ENV));
        if (configuredBaseDir == null) {
            return Path.of(System.getProperty("user.dir"), DEFAULT_BASE_DIR_NAME).toAbsolutePath().normalize();
        }

        return toPathWithHomeExpansion(configuredBaseDir);
    }

    /**
     * 取得 SQLite 資料庫檔案路徑。
     */
    public static Path getDatabasePath() {
        return getBaseDir().resolve(DB_DIR_NAME).resolve(DB_FILE_NAME);
    }

    /**
     * 取得訓練語料檔路徑。
     */
    public static Path getTrainingDataPath() {
        return getBaseDir().resolve(ML_DIR_NAME).resolve(TRAINING_FILE_NAME);
    }

    /**
     * 取得模型輸出檔路徑。
     */
    public static Path getModelPath() {
        return getBaseDir().resolve(ML_DIR_NAME).resolve(MODEL_FILE_NAME);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static Path toPathWithHomeExpansion(String value) {
        String trimmed = value.trim();
        String userHome = System.getProperty("user.home");

        if (trimmed.equals("~")) {
            return Path.of(userHome).toAbsolutePath().normalize();
        }
        if (trimmed.startsWith("~/")) {
            return Path.of(userHome, trimmed.substring(2)).toAbsolutePath().normalize();
        }

        return Path.of(trimmed).toAbsolutePath().normalize();
    }
}
