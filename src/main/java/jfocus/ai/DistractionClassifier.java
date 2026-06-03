package jfocus.ai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import jfocus.ai.rules.DistractionRuleRepository;
import jfocus.ai.rules.JdbcDistractionRuleRepository;
import jfocus.ai.rules.KeywordRule;
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
    private static final int MAX_KEYWORD_CANDIDATES = 6;
    private static final int MAX_KEYWORD_LENGTH = 30;
    private static final Pattern BRACKETED_TEXT_PATTERN = Pattern.compile("[\\[【(（]([^\\]】)）]{2,30})[\\]】)）]");
    private static final Pattern TITLE_SEPARATOR_PATTERN = Pattern.compile("\\s*(?:[-|｜·•—–_:：]+)\\s*");
    private static final Set<String> LOW_VALUE_KEYWORDS = Set.of(
            "google chrome",
            "chrome",
            "youtube",
            "safari",
            "microsoft edge",
            "edge",
            "音訊",
            "音訊播放中",
            "播放中",
            "audio",
            "playing audio",
            "首頁",
            "home",
            "登入",
            "login",
            "搜尋",
            "search",
            "新分頁",
            "new tab",
            "google 搜尋",
            "起始頁面");
    private static final Set<String> MUSIC_WORD = Set.of(
        "音樂",
        "music",
        "金曲",
        "歌曲"
    );

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

    public DistractionClassifier(DistractionRuleRepository ruleRepository) {
        this(null, DEFAULT_PLAY_THRESHOLD, ruleRepository);
    }

    DistractionClassifier(DocumentCategorizerME categorizer, double playThreshold,
            DistractionRuleRepository ruleRepository) {
        this.categorizer = categorizer;
        this.playThreshold = playThreshold;
        this.ruleRepository = Objects.requireNonNull(ruleRepository, "ruleRepository cannot be null");
    }

    /**
     * 儲存白名單關鍵字到資料庫。
     */
    public void addWhitelistRule(String keyword) {
        ruleRepository.saveRule(RuleListType.WHITELIST, new KeywordRule(keyword));
    }

    /**
     * 儲存黑名單關鍵字到資料庫。
     */
    public void addBlacklistRule(String keyword) {
        ruleRepository.saveRule(RuleListType.BLACKLIST, new KeywordRule(keyword));
    }

    /**
     * 產生可供 UI 顯示的黑白名單關鍵字候選。
     */
    public List<String> suggestRuleKeywords(String appName, String windowTitle) {
        String app = normalize(appName);
        String extractedTitle = extractWindowTitle(windowTitle);
        String browserTitle = isBrowserApp(app)
                ? BrowserTitleCleaner.stripBrowserSuffix(extractedTitle)
                : stripBrowserSuffix(extractedTitle);
        String importantTitle = isBrowserApp(app)
                ? BrowserTitleCleaner.extractImportantTitle(app, extractedTitle)
                : stripBrowserSuffix(extractedTitle);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        addBracketedCandidates(candidates, browserTitle);
        addTitlePartCandidates(candidates, removeBracketedText(browserTitle));
        addTitlePartCandidates(candidates, removeBracketedText(importantTitle));
        addTokenCandidates(candidates, importantTitle);

        if (!isBrowserApp(app)) {
            addKeywordCandidate(candidates, app);
        }

        return new ArrayList<>(candidates).stream()
                .limit(MAX_KEYWORD_CANDIDATES)
                .toList();
    }

    /**
     * 移除白名單關鍵字。
     */
    public void removeWhitelistRule(String keyword) {
        ruleRepository.deleteRule(RuleListType.WHITELIST, new KeywordRule(keyword));
    }

    /**
     * 移除黑名單關鍵字。
     */
    public void removeBlacklistRule(String keyword) {
        ruleRepository.deleteRule(RuleListType.BLACKLIST, new KeywordRule(keyword));
    }

    /**
     * 取得所有白名單規則。
     */
    public List<KeywordRule> getWhitelistRules() {
        return ruleRepository.getRules(RuleListType.WHITELIST);
    }

    /**
     * 取得所有黑名單規則。
     */
    public List<KeywordRule> getBlacklistRules() {
        return ruleRepository.getRules(RuleListType.BLACKLIST);
    }

    /**
     * 使用單一關鍵字規則判斷是否命中白名單。
     */
    public boolean isWhitelisted(String appName, String windowTitle) {
        return matchesWhitelistRule(toSearchText(appName, extractWindowTitle(windowTitle)));
    }

    /**
     * 使用單一關鍵字規則判斷是否命中黑名單。
     */
    public boolean isBlacklisted(String appName, String windowTitle) {
        return matchesBlacklistRule(toSearchText(appName, extractWindowTitle(windowTitle)));
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
        String searchableText = toSearchText(app, title);

        if (matchesWhitelistRule(searchableText)) {
            return false;
        }

        if (matchesBlacklistRule(searchableText)) {
            return true;
        }

        if (isYoutubeUnnecessaryPage(app, title) || isMusic(app, title)) {
            return false;
        }

        if (categorizer == null) {
            return false;
        }

        String cleanedTitle = TextProcessor.cleanText(BrowserTitleCleaner.extractImportantTitle(app, title));
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
        boolean isYoutubeVideo = isBrowserApp(app) && title.contains("youtube");
        // YouTube 需要更高 PLAY 信心（0.70）才算分心，避免誤擋學習影片。
        double effectiveThreshold = isYoutubeVideo ? 0.70 : playThreshold;
        boolean distracted = "PLAY".equalsIgnoreCase(category) && probability >= effectiveThreshold;
        // model 完全不確定時（任一類信心 < 0.55），YouTube 預設分心。
        // 學習頻道請加白名單排除。
        if (!distracted && isYoutubeVideo && probability < 0.55) {
            distracted = true;
        }

        System.out.println(distracted ? "[PLAY]" : "[STUDY]" + " | Text = \"" + cleanedTitle + "\"");
        System.out.println("Probability=" + probability + " | Distracted = " + distracted);

        return distracted;
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

    private boolean matchesWhitelistRule(String text) {
        return ruleRepository.matches(RuleListType.WHITELIST, text);
    }

    private boolean matchesBlacklistRule(String text) {
        return ruleRepository.matches(RuleListType.BLACKLIST, text);
    }

    private boolean isYoutubeUnnecessaryPage(String app, String title) {
        if (!isBrowserApp(app)) {
            return false;
        }

        String normalizedTitle = stripBrowserSuffix(title);
        // 去掉 Chrome 在播放時附加的「- 音訊播放中」後綴再比對
        String stripped = normalizedTitle.replaceAll("\\s*-\\s*音訊播放中\\s*$", "").trim();
        return "youtube".equals(stripped)
                || "youtube".equals(normalizedTitle)
                || "訂閱內容 - youtube".equals(stripped)
                || "觀看紀錄 - youtube".equals(stripped)
                || "播放清單 - youtube".equals(stripped)
                || "稍後觀看 - youtube".equals(stripped)
                || "喜歡的影片 - youtube".equals(stripped)
                || "電影 - youtube".equals(stripped)
                || "直播 - youtube".equals(stripped);
    }

    private boolean isMusic(String app, String title) {
        String normalizedTitle = stripBrowserSuffix(title);
        String stripped = normalizedTitle.replaceAll("\\s*-\\s*音訊播放中\\s*$", "").trim();
        
        return app.contains("music") || MUSIC_WORD.stream().anyMatch(stripped::contains);
    }

    private String stripBrowserSuffix(String title) {
        return BrowserTitleCleaner.stripBrowserSuffix(title);
    }

    private boolean isBrowserApp(String app) {
        return BrowserTitleCleaner.isBrowserApp(app);
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

    private static String toSearchText(String appName, String windowTitle) {
        String app = normalize(appName);
        String title = normalize(windowTitle);
        if (app.isBlank()) {
            return title;
        }
        if (title.isBlank()) {
            return app;
        }
        return app + " " + title;
    }

    private static void addBracketedCandidates(Set<String> candidates, String title) {
        var matcher = BRACKETED_TEXT_PATTERN.matcher(normalize(title));
        while (matcher.find()) {
            addKeywordCandidate(candidates, matcher.group(1));
        }
    }

    private static String removeBracketedText(String title) {
        return BRACKETED_TEXT_PATTERN.matcher(normalize(title)).replaceAll("");
    }

    private static void addTitlePartCandidates(Set<String> candidates, String title) {
        String[] parts = TITLE_SEPARATOR_PATTERN.split(normalize(title));
        for (String part : parts) {
            addKeywordCandidate(candidates, part);
        }
    }

    private static void addTokenCandidates(Set<String> candidates, String title) {
        String cleanedTitle = TextProcessor.cleanText(title);
        if (cleanedTitle.isBlank()) {
            return;
        }

        for (String token : cleanedTitle.split("\\s+")) {
            addKeywordCandidate(candidates, token);
        }
    }

    private static void addKeywordCandidate(Set<String> candidates, String value) {
        String keyword = normalize(value);
        if (isUsefulKeyword(keyword)) {
            candidates.add(keyword);
        }
    }

    private static boolean isUsefulKeyword(String keyword) {
        if (keyword.isBlank()
                || keyword.length() < 2
                || keyword.length() > MAX_KEYWORD_LENGTH
                || LOW_VALUE_KEYWORDS.contains(keyword)) {
            return false;
        }
        return keyword.chars().anyMatch(Character::isLetterOrDigit);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
