package jfocus.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import jfocus.activity.ActivityRecord;
import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.ai.TextProcessor;
import jfocus.db.AppPaths;
import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/* 匯出資料庫內容供模型訓練使用。 */
public class ExportData {
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final String META_SUFFIX = ".lastid";

    private final ActivityRepository repository;
    private final UnaryOperator<String> textCleaner;

    /* 使用預設 repository 與文字清理器建立匯出服務。 */
    public ExportData() {
        this(new JdbcActivityRepository(new DatabaseCore()), TextProcessor::cleanText);
    }

    /**
     * 使用指定 repository 建立匯出服務。
     *
     * @param repository 活動資料 repository
     */
    public ExportData(ActivityRepository repository) {
        this(repository, TextProcessor::cleanText);
    }

    /**
     * 使用指定 repository 與文字清理器建立匯出服務。
     *
     * @param repository 活動資料 repository
     * @param textCleaner 文字清理邏輯
     */
    public ExportData(ActivityRepository repository, UnaryOperator<String> textCleaner) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.textCleaner = Objects.requireNonNull(textCleaner, "textCleaner cannot be null");
    }

    /**
     * 使用預設訓練語料路徑自動增量匯出。
     *
     * @return 本次匯出後的最新 ID
     */
    public int exportNewDataToTxt() {
        return exportNewDataToTxt(AppPaths.getTrainingDataPath().toString());
    }

    /**
     * 自動讀取/更新 meta 檔，以增量方式將新資料附加到訓練文字檔。
     *
     * @param filePath 輸出檔案路徑
     * @return 本次匯出後的最新 ID
     */
    public int exportNewDataToTxt(String filePath) {
        Path outputPath = validateAndGetOutputPath(filePath);
        Path metaPath = resolveMetaPath(outputPath);

        int lastExportedId = readLastExportedId(metaPath);
        int currentMaxId = exportNewDataToTxt(lastExportedId, filePath);
        writeLastExportedId(metaPath, currentMaxId);
        return currentMaxId;
    }

    /**
     * 以增量方式將新資料附加到訓練文字檔。
     *
     * @param lastExportedId 上次匯出的最後一筆 ID；首次匯出可傳入 0
     * @param filePath 輸出檔案路徑
     * @return 本次匯出後的最新 ID
     */
    public int exportNewDataToTxt(int lastExportedId, String filePath) {
        if (lastExportedId < 0) {
            throw new IllegalArgumentException("lastExportedId cannot be negative");
        }

        int currentMaxId = lastExportedId;
        Path outputPath = validateAndGetOutputPath(filePath);
        createParentDirectories(outputPath);

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputPath,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {

            while (true) {
                List<ActivityRecord> activities = repository.findActivitiesAfterId(currentMaxId, DEFAULT_BATCH_SIZE);
                if (activities.isEmpty()) {
                    break;
                }

                for (ActivityRecord activity : activities) {
                    writer.write(toTrainingLine(activity));
                    writer.newLine();
                    currentMaxId = activity.id();
                }

                if (activities.size() < DEFAULT_BATCH_SIZE) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new StorageException("匯出失敗", e);
        }

        return currentMaxId;
    }
    
    /**
     * 讀取全部歷史紀錄並轉成訓練語料。
     *
     * @return 清理後的訓練語料列表
     */
    public List<String> extractTrainingData() {
        List<String> trainingData = new ArrayList<>();

        int currentMaxId = 0;
        while (true) {
            List<ActivityRecord> activities = repository.findActivitiesAfterId(currentMaxId, DEFAULT_BATCH_SIZE);
            if (activities.isEmpty()) {
                break;
            }

            for (ActivityRecord activity : activities) {
                trainingData.add(toTrainingLine(activity));
                currentMaxId = activity.id();
            }

            if (activities.size() < DEFAULT_BATCH_SIZE) {
                break;
            }
        }

        System.out.println("🧠 成功萃取出 " + trainingData.size() + " 筆 AI 訓練語料！");
        return trainingData;
    }

    private void createParentDirectories(Path outputPath) {
        Path parent = outputPath.toAbsolutePath().normalize().getParent();
        if (parent == null) {
            return;
        }

        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new StorageException("建立匯出目錄失敗", e);
        }
    }

    private Path validateAndGetOutputPath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("filePath cannot be null or blank");
        }
        return Path.of(filePath);
    }

    private Path resolveMetaPath(Path outputPath) {
        Path normalized = outputPath.toAbsolutePath().normalize();
        Path fileName = normalized.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("filePath must point to a file");
        }

        return normalized.resolveSibling(fileName + META_SUFFIX);
    }

    private int readLastExportedId(Path metaPath) {
        if (!Files.exists(metaPath)) {
            return 0;
        }

        try {
            String raw = Files.readString(metaPath, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return 0;
            }

            int lastExportedId = Integer.parseInt(raw);
            if (lastExportedId < 0) {
                throw new StorageException("lastExportedId meta 檔不可為負數: " + metaPath, null);
            }
            return lastExportedId;
        } catch (NumberFormatException e) {
            throw new StorageException("lastExportedId meta 檔格式錯誤: " + metaPath, e);
        } catch (IOException e) {
            throw new StorageException("讀取 lastExportedId meta 檔失敗", e);
        }
    }

    private void writeLastExportedId(Path metaPath, int id) {
        if (id < 0) {
            throw new IllegalArgumentException("id cannot be negative");
        }

        createParentDirectories(metaPath);
        Path tempPath = metaPath.resolveSibling(metaPath.getFileName().toString() + ".tmp");

        try {
            Files.writeString(
                    tempPath,
                    Integer.toString(id),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            try {
                Files.move(tempPath, metaPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, metaPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new StorageException("寫入 lastExportedId meta 檔失敗", e);
        }
    }

    private String toTrainingLine(ActivityRecord activity) {
        StringBuilder line = new StringBuilder(64);
        line.append(activity.focus() ? "STUDY" : "PLAY");

        String cleanApp = textCleaner.apply(activity.appName());
        if (!cleanApp.isBlank()) {
            line.append(' ').append(cleanApp);
        }

        String cleanTitle = textCleaner.apply(activity.windowTitle());
        if (!cleanTitle.isBlank()) {
            line.append(' ').append(cleanTitle);
        }

        return line.toString();
    }
}
