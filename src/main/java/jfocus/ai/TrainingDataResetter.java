package jfocus.ai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import jfocus.db.AppPaths;

public final class TrainingDataResetter {
    private static final Path DEFAULT_TRAINING_DATA_PATH = Path.of("res", "ml-data", "training.txt");

    private TrainingDataResetter() {
    }

    // public static void resetTrainingMetadataAndTrain() {
    //     ModelTrainer.forceTrainModel();
    // }

    public static void resetDefaultTrainingDataAndTrain() throws IOException {
        Path trainingPath = AppPaths.getTrainingDataPath();
        Path modelPath = AppPaths.getModelPath();

        Files.createDirectories(trainingPath.getParent());
        Files.deleteIfExists(trainingPath);
        Files.deleteIfExists(modelPath);
        Files.copy(DEFAULT_TRAINING_DATA_PATH, trainingPath, StandardCopyOption.REPLACE_EXISTING);

        ModelTrainer.trainModel();
    }
}
