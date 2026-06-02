package jfocus.ai;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import jfocus.db.AppPaths;
import jfocus.io.ExportData;
import opennlp.tools.doccat.BagOfWordsFeatureGenerator;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.doccat.FeatureGenerator;
import opennlp.tools.doccat.NGramFeatureGenerator;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/* 負責訓練並輸出 OpenNLP 文件分類模型。 */
public class ModelTrainer {

    private static final int TRAIN_BATCH_SIZE = 50;

    /** 直接從指定檔案訓練，不匯出新資料、不更新 metadata。 */
    public static void forceTrainFromFile(Path txtPath, Path modelPath) throws IOException {
        boolean success = trainModel(txtPath, modelPath);
        if (!success) throw new IOException("模型訓練失敗，語料可能為空。");
    }

    /* 使用預設路徑訓練模型。 */
    public static void trainModel() {
        Path trainingPath = AppPaths.getTrainingDataPath();
        initializeDefaultTrainingData(trainingPath);
        
        int latestId = new ExportData().exportNewDataToTxt(trainingPath.toString());
        System.out.println("已匯出訓練資料至: " + trainingPath + "，最新ID: " + latestId);

        checkAndTrainModel(trainingPath, AppPaths.getModelPath(), latestId);
    }

    private static void initializeDefaultTrainingData(Path targetPath) {
        if (!Files.exists(targetPath)) {
            try {
                Path sourcePath = Path.of("res", "ml-data", "training.txt");
                if (Files.exists(sourcePath)) {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath);
                    System.out.println("已將預設訓練資料複製至: " + targetPath);
                }
            } catch (IOException e) {
                System.err.println("複製預設訓練資料失敗: " + e.getMessage());
            }
        }
    }

    /**
     * 使用文字語料訓練分類模型並輸出為二進位檔。
     *
     * @param txtFilePath 訓練資料檔路徑
     * @param binFilePath 模型輸出檔路徑
     */
    public static void trainModel(String txtFilePath, String binFilePath) {
        Path txtPath = Path.of(txtFilePath);
        initializeDefaultTrainingData(txtPath);
        
        int latestId = new ExportData().exportNewDataToTxt(txtFilePath);
        System.out.println("已匯出訓練資料至: " + txtFilePath + "，最新ID: " + latestId);

        checkAndTrainModel(txtPath, Path.of(binFilePath), latestId);
    }

    private static void checkAndTrainModel(Path txtPath, Path modelPath, int latestId) {
        int lastTrainedId = TrainingMetadata.getLastTrainedId(txtPath);
        boolean isModelMissing = !Files.exists(modelPath);
        boolean isTrainingDataNewer = isTrainingDataNewerThanModel(txtPath, modelPath);

        if (latestId - lastTrainedId >= TRAIN_BATCH_SIZE || isModelMissing || isTrainingDataNewer) {
            System.out.println("累積新資料筆數 (" + (latestId - lastTrainedId) + ") 已達批次大小 " + TRAIN_BATCH_SIZE
                    + (isModelMissing ? " 或找不到模型" : "")
                    + (isTrainingDataNewer ? " 或訓練資料已更新" : "")
                    + "，開始訓練模型...");
            boolean success = trainModel(txtPath, modelPath);
            if (success) {
                TrainingMetadata.setLastTrainedId(txtPath, latestId);
            }
        } else {
            System.out.println("累積新資料筆數 (" + (latestId - lastTrainedId) + ") 未達批次大小 " + TRAIN_BATCH_SIZE + "，略過模型訓練。");
        }
    }

    private static boolean isTrainingDataNewerThanModel(Path txtPath, Path modelPath) {
        try {
            return Files.exists(txtPath)
                    && Files.exists(modelPath)
                    && Files.getLastModifiedTime(txtPath).compareTo(Files.getLastModifiedTime(modelPath)) > 0;
        } catch (IOException e) {
            System.err.println("無法比較訓練資料與模型時間，將略過時間檢查: " + e.getMessage());
            return false;
        }
    }

    private static boolean trainModel(Path txtPath, Path modelPath) {
        System.out.println("啟動 AI 訓練熔爐...");
        long startTime = System.currentTimeMillis();

        try {
            // 若語料為空則略過訓練，避免 OpenNLP 報錯
            if (!Files.exists(txtPath) || Files.size(txtPath) == 0) {
                System.err.println("無可用語料可供訓練，已略過。檔案: " + txtPath);
                return false;
            }

            InputStreamFactory isf = new MarkableFileInputStreamFactory(txtPath.toFile());
            try (ObjectStream<String> lineStream = new PlainTextByLineStream(isf, StandardCharsets.UTF_8);
                 ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {

                TrainingParameters params = new TrainingParameters();
                params.put(TrainingParameters.ITERATIONS_PARAM, 100);

                // 小資料集先保留較低門檻，避免早期特徵被過度過濾。
                params.put(TrainingParameters.CUTOFF_PARAM, 1);

                FeatureGenerator[] featureGenerators = {
                    new BagOfWordsFeatureGenerator(),
                    new NGramFeatureGenerator(2, 3)
                };
                DoccatModel model = DocumentCategorizerME.train("zho", sampleStream, params, new DoccatFactory(featureGenerators));

                File outputFile = modelPath.toFile();
                File parent = outputFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                try (OutputStream modelOut = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                    model.serialize(modelOut);
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("模型訓練完成！產出檔案: " + modelPath);
            System.out.println("耗時: " + (endTime - startTime) + " 毫秒");
            return true;

        } catch (IOException e) {
            System.err.println("訓練過程中發生錯誤，請檢查 txt 檔是否存在: " + txtPath + "，" + e.getMessage());
            return false;
        }
    }
}
