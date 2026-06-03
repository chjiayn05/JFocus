package jfocus.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

import jfocus.activity.ActivityRecord;
import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.ai.BrowserTitleCleaner;
import jfocus.ai.TextProcessor;
import jfocus.ai.TrainingMetadata;
import jfocus.db.AppPaths;
import jfocus.db.DatabaseCore;
import jfocus.db.StorageException;

/* 匯出資料庫內容供模型訓練使用。 */
public class ExportData {
    private static final int DEFAULT_BATCH_SIZE = 1000;

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

        int lastExportedId = TrainingMetadata.getLastExportedId(outputPath);
        int currentMaxId = exportNewDataToTxt(lastExportedId, filePath);
        TrainingMetadata.setLastExportedId(outputPath, currentMaxId);
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
        int processed = 0;
        int written = 0;
        int skipped = 0;
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
                    processed++;
                    String line = toTrainingLine(activity);
                    currentMaxId = activity.id();
                    if (line.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    writer.write(line);
                    writer.newLine();
                    written++;
                }

                if (activities.size() < DEFAULT_BATCH_SIZE) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new StorageException("匯出失敗", e);
        }

        if (processed == 0) {
            System.out.println("ℹ無新活動資料可匯出。lastId=" + lastExportedId + ", 輸出檔=" + outputPath);
        } else {
            System.out.println("匯出摘要 -> 處理: " + processed + ", 寫入: " + written + ", 略過: " + skipped + ", 最新ID: " + currentMaxId);
        }

        return currentMaxId;
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

    private String toTrainingLine(ActivityRecord activity) {
        boolean browserActivity = BrowserTitleCleaner.isBrowserApp(activity.appName());
        String rawTitle = browserActivity
                ? BrowserTitleCleaner.extractImportantTitle(activity.appName(), activity.windowTitle())
                : activity.windowTitle();
        String cleanApp = browserActivity ? "" : textCleaner.apply(activity.appName());
        String cleanTitle = textCleaner.apply(rawTitle);

        if (cleanApp.isBlank() && cleanTitle.isBlank()) {
            return "";
        }

        // 過濾無意義的 YouTube 預設頁面標題
        String rawTitleLower = rawTitle.strip().toLowerCase();
        if (rawTitleLower.equals("youtube")
            || rawTitleLower.equals("訂閱內容 - youtube")
            || rawTitleLower.equals("觀看紀錄 - youtube")
            || rawTitleLower.equals("播放清單 - youtube")
            || rawTitleLower.equals("稍後觀看 - youtube")
            || rawTitleLower.equals("喜歡的影片 - youtube")
            || rawTitleLower.equals("電影 - youtube")
            || rawTitleLower.equals("直播 - youtube")) {
            return "";
        }

        StringBuilder line = new StringBuilder(64);
        line.append(activity.focus() ? "STUDY" : "PLAY");

        if (!cleanApp.isBlank()) {
            line.append(' ').append(cleanApp);
        }

        if (!cleanTitle.isBlank()) {
            line.append(' ').append(cleanTitle);
        }

        return line.toString();
    }
}
