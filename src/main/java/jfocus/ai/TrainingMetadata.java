package jfocus.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

import org.json.JSONObject;

/**
 * 統一管理訓練資料的 Metadata（例如：上次匯出的 ID、上次訓練的 ID），
 * 將這些資訊合併儲存在 .meta.json 中，方便後續管理。
 */
public class TrainingMetadata {

    private static final String META_SUFFIX = ".meta.json";

    public static int getLastExportedId(Path txtFilePath) {
        Path metaPath = getMetaPath(txtFilePath);
        return readMeta(metaPath).optInt("lastExportedId", 0);
    }

    public static void setLastExportedId(Path txtFilePath, int id) {
        Path metaPath = getMetaPath(txtFilePath);
        JSONObject meta = readMeta(metaPath);
        meta.put("lastExportedId", id);
        writeMeta(metaPath, meta);
    }

    public static int getLastTrainedId(Path txtFilePath) {
        Path metaPath = getMetaPath(txtFilePath);
        return readMeta(metaPath).optInt("lastTrainedId", 0);
    }

    public static void setLastTrainedId(Path txtFilePath, int id) {
        Path metaPath = getMetaPath(txtFilePath);
        JSONObject meta = readMeta(metaPath);
        meta.put("lastTrainedId", id);
        writeMeta(metaPath, meta);
    }

    public static Path getMetaPath(Path txtFilePath) {
        Path normalized = txtFilePath.toAbsolutePath().normalize();
        String fileName = normalized.getFileName().toString();
        if (fileName.endsWith(".txt")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }
        Path metaPath = normalized.resolveSibling(fileName + META_SUFFIX);
        migrateOldMetaFiles(normalized, metaPath);
        return metaPath;
    }

    private static void migrateOldMetaFiles(Path txtFilePath, Path newMetaPath) {
        Path oldLastIdPath = txtFilePath.resolveSibling(txtFilePath.getFileName() + ".lastid");
        Path oldTrainedIdPath = txtFilePath.resolveSibling(txtFilePath.getFileName() + ".trainedid");

        if (Files.exists(oldLastIdPath) || Files.exists(oldTrainedIdPath)) {
            JSONObject meta = readMeta(newMetaPath);
            boolean changed = false;

            if (Files.exists(oldLastIdPath)) {
                try {
                    String raw = Files.readString(oldLastIdPath, StandardCharsets.UTF_8).trim();
                    if (!raw.isEmpty()) {
                        meta.put("lastExportedId", Integer.parseInt(raw));
                        changed = true;
                    }
                    Files.deleteIfExists(oldLastIdPath);
                } catch (Exception e) {
                    System.err.println("轉移舊的 .lastid 失敗: " + e.getMessage());
                }
            }

            if (Files.exists(oldTrainedIdPath)) {
                try {
                    String raw = Files.readString(oldTrainedIdPath, StandardCharsets.UTF_8).trim();
                    if (!raw.isEmpty()) {
                        meta.put("lastTrainedId", Integer.parseInt(raw));
                        changed = true;
                    }
                    Files.deleteIfExists(oldTrainedIdPath);
                } catch (Exception e) {
                    System.err.println("轉移舊的 .trainedid 失敗: " + e.getMessage());
                }
            }

            if (changed) {
                writeMeta(newMetaPath, meta);
                System.out.println("已自動將舊的 .lastid / .trainedid 轉移合併至 .meta.json 中！");
            }
        }
    }

    private static JSONObject readMeta(Path metaPath) {
        if (!Files.exists(metaPath)) {
            return new JSONObject();
        }
        try {
            String raw = Files.readString(metaPath, StandardCharsets.UTF_8).trim();
            if (raw.isEmpty()) {
                return new JSONObject();
            }
            return new JSONObject(raw);
        } catch (Exception e) {
            System.err.println("讀取 meta 檔失敗或格式錯誤，將重新建立: " + e.getMessage());
            return new JSONObject();
        }
    }

    private static void writeMeta(Path metaPath, JSONObject meta) {
        try {
            if (metaPath.getParent() != null && !Files.exists(metaPath.getParent())) {
                Files.createDirectories(metaPath.getParent());
            }
            Path tempPath = metaPath.resolveSibling(metaPath.getFileName().toString() + ".tmp");
            
            Files.writeString(
                    tempPath,
                    meta.toString(4), // 4 spaces indentation for JSON
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
            System.err.println("寫入 meta 檔失敗: " + e.getMessage());
        }
    }
}
