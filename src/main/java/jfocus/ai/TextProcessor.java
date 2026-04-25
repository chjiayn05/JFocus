package jfocus.ai;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;

/* 提供文字清理與語料輸出功能。 */
public class TextProcessor {

    /* 共用的 Jieba 分詞器。 */
    private static final JiebaSegmenter segmenter = new JiebaSegmenter();
    private static final Pattern NOTIFY_PATTERN = Pattern.compile("\\(\\d+\\)");
    private static final Pattern NON_TEXT_PATTERN = Pattern.compile("[^\\u4e00-\\u9fa5a-z0-9]");

    /* 清理文字時會排除的停用詞。 */
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
        "的", "了", "在", "是", "就", "也", "和", "與", "這", "那",
        "一口氣", "看完", "卻發現", "裡面", "都是", "一個", "這個",
        "a", "an", "the", "is", "are", "in", "on", "to", "for"
    ));

    /**
     * 清理原始文字並輸出可用於特徵提取的內容。
     *
     * @param rawText 原始文字
     * @return 清理後的文字；若輸入為空則回傳空字串
     */
    public static String cleanText(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return "";
        }

        String text = rawText.toLowerCase();
        text = NOTIFY_PATTERN.matcher(text).replaceAll("");
        text = NON_TEXT_PATTERN.matcher(text).replaceAll("");

        // SEARCH 模式會切出較細的詞，較適合後續特徵提取。
        List<SegToken> tokens = segmenter.process(text, JiebaSegmenter.SegMode.SEARCH);

        StringBuilder processedText = new StringBuilder();

        for (SegToken token : tokens) {
            String word = token.word;

            if (!STOP_WORDS.contains(word) && word != null && !word.isBlank()) {
                processedText.append(word).append(" ");
            }
        }

        return processedText.toString().trim();
    }
}