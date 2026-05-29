package jfocus.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jfocus.db.AppPaths;

public final class TrainingDataResetter {
    private static final Path DEFAULT_TRAINING_DATA_PATH = Path.of("res", "ml-data", "training.txt");

    private TrainingDataResetter() {}

    /** 重置模型為預設值，lastExportedId / lastTrainedId 保持不變，之後只訓練新資料。 */
    public static void resetModelToDefault() throws IOException {
        Path trainingPath = AppPaths.getTrainingDataPath();
        Path modelPath    = AppPaths.getModelPath();
        Files.createDirectories(trainingPath.getParent());
        Files.deleteIfExists(trainingPath);
        Files.deleteIfExists(modelPath);
        Files.copy(DEFAULT_TRAINING_DATA_PATH, trainingPath, StandardCopyOption.REPLACE_EXISTING);
        ModelTrainer.forceTrainFromFile(trainingPath, modelPath);
    }

    /** 重置 metadata（id 歸零）並重新拉取所有資料重新訓練。 */
    public static void retrainWithAllData() throws IOException {
        Path trainingPath = AppPaths.getTrainingDataPath();
        Path modelPath    = AppPaths.getModelPath();
        Files.createDirectories(trainingPath.getParent());
        Files.deleteIfExists(trainingPath);
        Files.deleteIfExists(modelPath);
        Files.copy(DEFAULT_TRAINING_DATA_PATH, trainingPath, StandardCopyOption.REPLACE_EXISTING);
        TrainingMetadata.setLastExportedId(trainingPath, 0);
        TrainingMetadata.setLastTrainedId(trainingPath, 0);
        ModelTrainer.trainModel();
    }

    /** @deprecated 改用 {@link #resetModelToDefault()} 或 {@link #retrainWithAllData()} */
    @Deprecated
    public static void resetDefaultTrainingDataAndTrain() throws IOException {
        retrainWithAllData();
    }
}
