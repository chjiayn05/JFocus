package jfocus.ai;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import jfocus.db.AppPaths;
import opennlp.tools.doccat.DoccatFactory;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;
import opennlp.tools.doccat.DocumentSample;
import opennlp.tools.doccat.DocumentSampleStream;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

/* 負責訓練並輸出 OpenNLP 文件分類模型。 */
public class ModelTrainer {

    /* 使用預設路徑訓練模型。 */
    public static void trainModel() {
        trainModel(AppPaths.getTrainingDataPath(), AppPaths.getModelPath());
    }

    /**
     * 使用文字語料訓練分類模型並輸出為二進位檔。
     *
     * @param txtFilePath 訓練資料檔路徑
     * @param binFilePath 模型輸出檔路徑
     */
    public static void trainModel(String txtFilePath, String binFilePath) {
        trainModel(Path.of(txtFilePath), Path.of(binFilePath));
    }

    private static void trainModel(Path txtPath, Path modelPath) {
        System.out.println("🔥 啟動 AI 訓練熔爐...");
        long startTime = System.currentTimeMillis();

        try {
            InputStreamFactory isf = new MarkableFileInputStreamFactory(txtPath.toFile());
            try (ObjectStream<String> lineStream = new PlainTextByLineStream(isf, StandardCharsets.UTF_8);
                 ObjectStream<DocumentSample> sampleStream = new DocumentSampleStream(lineStream)) {

                TrainingParameters params = new TrainingParameters();
                params.put(TrainingParameters.ITERATIONS_PARAM, 100);

                // 小資料集先保留較低門檻，避免早期特徵被過度過濾。
                params.put(TrainingParameters.CUTOFF_PARAM, 1);

                DoccatModel model = DocumentCategorizerME.train("zho", sampleStream, params, new DoccatFactory());

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
            System.out.println("✅ AI 模型訓練完成！產出檔案: " + modelPath);
            System.out.println("⏱️ 耗時: " + (endTime - startTime) + " 毫秒");

        } catch (IOException e) {
            System.err.println("❌ 訓練過程中發生錯誤，請檢查 txt 檔是否存在: " + txtPath + "，" + e.getMessage());
        }
    }
}