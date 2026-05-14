package jfocus.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import jfocus.ai.rules.AppWindowRule;
import jfocus.ai.rules.DistractionRuleRepository;
import jfocus.ai.rules.JdbcDistractionRuleRepository;
import jfocus.ai.rules.RuleListType;
import jfocus.db.AppPaths;
import jfocus.db.DatabaseCore;
import opennlp.tools.doccat.DoccatModel;
import opennlp.tools.doccat.DocumentCategorizerME;

/**
 * 根據 app 與視窗標題判斷是否為分心活動。
 */
public class DistractionClassifier {
    private static final double DEFAULT_PLAY_THRESHOLD = 0.6;
    private static final String WINDOW_TITLE_SEPARATOR = ": ";
    private static final Set<String> BROWSER_APPS = Set.of(
            "google chrome",
            "safari",
            "microsoft edge",
            "chrome",
            "edge");

    private final DocumentCategorizerME categorizer;
    private final double playThreshold;
    private final DistractionRuleRepository ruleRepository;

    /**
     * 使用預設模型路徑建立分心分類器。
     */
    public DistractionClassifier() {
        this(
                loadCategorizer(AppPaths.getModelPath()),
                DEFAULT_PLAY_THRESHOLD,
                new JdbcDistractionRuleRepository(new DatabaseCore()));
    }

    DistractionClassifier(DocumentCategorizerME categorizer, double playThreshold) {
        this(categorizer, playThreshold, new JdbcDistractionRuleRepository(new DatabaseCore()));
    }

    DistractionClassifier(DocumentCategorizerME categorizer, double playThreshold,
            DistractionRuleRepository ruleRepository) {
        this.categorizer = categorizer;
        this.playThreshold = playThreshold;
        this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository cannot be null");
    }

    /**
     * 儲存白名單規則到資料庫。
     */
    public void addWhitelistRule(String appName, String windowTitle) {
        ruleRepository.saveRule(RuleListType.WHITELIST, new AppWindowRule(appName, extractWindowTitle(windowTitle)));
    }

    /**
     * 儲存黑名單規則到資料庫。
     */
    public void addBlacklistRule(String appName, String windowTitle) {
        ruleRepository.saveRule(RuleListType.BLACKLIST, new AppWindowRule(appName, extractWindowTitle(windowTitle)));
    }

    /**
     * 移除白名單規則。
     */
    public void removeWhitelistRule(String appName, String windowTitle) {
        ruleRepository.deleteRule(RuleListType.WHITELIST, new AppWindowRule(appName, extractWindowTitle(windowTitle)));
    }

    /**
     * 移除黑名單規則。
     */
    public void removeBlacklistRule(String appName, String windowTitle) {
        ruleRepository.deleteRule(RuleListType.BLACKLIST, new AppWindowRule(appName, extractWindowTitle(windowTitle)));
    }

    /**
     * 取得所有白名單規則。
     */
    public List<AppWindowRule> getWhitelistRules() {
        return ruleRepository.getRules(RuleListType.WHITELIST);
    }

    /**
     * 取得所有黑名單規則。
     */
    public List<AppWindowRule> getBlacklistRules() {
        return ruleRepository.getRules(RuleListType.BLACKLIST);
    }

    /**
     * 使用 appName + windowTitle 判斷是否命中白名單。
     */
    public boolean isWhitelisted(String appName, String windowTitle) {
        String app = normalize(appName);
        String title = normalize(extractWindowTitle(windowTitle));
        return matchesWhitelistRule(app, title);
    }

    /**
     * 使用 appName + windowTitle 判斷是否命中黑名單。
     */
    public boolean isBlacklisted(String appName, String windowTitle) {
        String app = normalize(appName);
        String title = normalize(extractWindowTitle(windowTitle));
        return matchesBlacklistRule(app, title);
    }

    /**
     * 是否屬於瀏覽器中的網站活動。
     */
    public boolean isWebsiteActivity(String appName, String windowTitle) {
        String app = normalize(appName);
        String title = normalize(extractWindowTitle(windowTitle));
        return isBrowserApp(app) && !title.isBlank();
    }

    /**
     * @param appName 應用程式名稱
     * @param windowTitle 視窗標題
     * @return true 表示分心，false 表示專注
     */
    public boolean isDistracting(String appName, String windowTitle) {
        String app = normalize(appName);
        String title = normalize(extractWindowTitle(windowTitle));

        if (matchesWhitelistRule(app, title)) {
            return false;
        }

        if (matchesBlacklistRule(app, title)) {
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

    private boolean matchesWhitelistRule(String app, String title) {
        return ruleRepository.matches(RuleListType.WHITELIST, app, title);
    }

    private boolean matchesBlacklistRule(String app, String title) {
        return ruleRepository.matches(RuleListType.BLACKLIST, app, title);
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

        int separatorIndex = windowTitle.indexOf(WINDOW_TITLE_SEPARATOR);
        int titleStartIndex = separatorIndex + WINDOW_TITLE_SEPARATOR.length();
        if (separatorIndex >= 0 && titleStartIndex < windowTitle.length()) {
            return windowTitle.substring(titleStartIndex);
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
