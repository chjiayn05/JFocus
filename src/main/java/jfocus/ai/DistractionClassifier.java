package jfocus.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

import jfocus.db.AppPaths;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;

/**
 * 根據 app 與視窗標題判斷是否為分心活動。
 */
public class DistractionClassifier {
    private static final double DEFAULT_PLAY_THRESHOLD = 0.6;
    private static final Set<String> APP_BLACKLIST = Set.of(
            "netflix",
            "steam",
            "league of legends",
            "genshinimpact");
    private static final Set<String> BROWSER_APPS = Set.of(
            "google chrome",
            "safari",
            "microsoft edge",
            "chrome",
            "edge");

    private final DocumentCategorizerME categorizer;
    private final double playThreshold;

    /**
     * 使用預設模型路徑建立分心分類器。
     */
    public DistractionClassifier() {
        this(loadCategorizer(AppPaths.getModelPath()), DEFAULT_PLAY_THRESHOLD);
    }

    DistractionClassifier(DocumentCategorizerME categorizer, double playThreshold) {
        this.categorizer = categorizer;
        this.playThreshold = playThreshold;
    }

    /**
     * @param appName 應用程式名稱
     * @param windowTitle 視窗標題
     * @return true 表示分心，false 表示專注
     */
    public boolean isDistracting(String appName, String windowTitle) {
        String app = normalize(appName);
        String title = normalize(extractWindowTitle(windowTitle));

        if (isBlacklistedApp(app)) {
            return true;
        }

        if (isYoutubeHomePage(app, title)) {
            return false;
        }

        if (categorizer == null) {
            return false;
        }

        String cleanedTitle = TextProcessor.cleanText(title);
        if (cleanedTitle.isBlank()) {
            cleanedTitle = TextProcessor.cleanText(app);
        }
        if (cleanedTitle.isBlank()) {
            return false;
        }

        String[] tokens = cleanedTitle.split("\\s+");
        double[] outcomes = categorizer.categorize(tokens);
        String category = categorizer.getBestCategory(outcomes);

        int categoryIndex = categorizer.getIndex(category);
        double probability = categoryIndex >= 0 ? outcomes[categoryIndex] : 0.0;

        return "PLAY".equalsIgnoreCase(category) && probability >= playThreshold;
    }

    private static DocumentCategorizerME loadCategorizer(Path modelPath) {
        if (!Files.exists(modelPath)) {
            return null;
        }

        try (InputStream inputStream = Files.newInputStream(modelPath)) {
            return new DocumentCategorizerME(new DoccatModel(inputStream));
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isBlacklistedApp(String app) {
        if (app.isBlank()) {
            return false;
        }

        for (String keyword : APP_BLACKLIST) {
            if (app.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean isYoutubeHomePage(String app, String title) {
        if (!isBrowserApp(app)) {
            return false;
        }

        return "youtube".equals(title)
                || "youtube.com".equals(title)
                || "www.youtube.com".equals(title);
    }

    private boolean isBrowserApp(String app) {
        if (app.isBlank()) {
            return false;
        }

        for (String browser : BROWSER_APPS) {
            if (app.contains(browser)) {
                return true;
            }
        }
        return false;
    }

    private static String extractWindowTitle(String windowTitle) {
        if (windowTitle == null) {
            return "";
        }

        int separatorIndex = windowTitle.indexOf(": ");
        if (separatorIndex >= 0 && separatorIndex + 2 < windowTitle.length()) {
            return windowTitle.substring(separatorIndex + 2);
        }
        return windowTitle;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
