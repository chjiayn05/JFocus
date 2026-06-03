package jfocus.ui;

import java.time.LocalDate;
import java.util.Random;

public class FocusCommentGenerator {

    private static String formatTime(long seconds) {
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        if (h > 0 && m > 0) {
            return h + "小時" + m + "分鐘";
        } else if (h > 0) {
            return h + "小時";
        } else {
            return m + "分鐘";
        }
    }

    public static String getComment(double focusScore, long focusSeconds, long distractionSeconds, LocalDate date) {
        if (focusSeconds <= 0) {
            return "今天沒有專注";
        }

        // 使用日期的雜湊值做隨機數種子，使得同一天的評語在相同數據下固定，但不同日期或數據會有生動的語氣變化
        int seed = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
        Random random = new Random(seed);

        StringBuilder sb = new StringBuilder();

        // 1. 專注時長評論 (Part 1)
        if (focusSeconds >= 21600) { // 6小時以上
            String[] options = {
                "今天你累積專注了超過 6 小時（共 " + formatTime(focusSeconds) + "），簡直是鐵人等級的修行，太不可思議了！",
                "你今天進行了長達 " + formatTime(focusSeconds) + " 的魔鬼專注特訓，這等耐力連快龍都自嘆不如！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (focusSeconds >= 14400) { // 4~6小時
            String[] options = {
                "今天完成了長達 " + formatTime(focusSeconds) + " 的專注特訓，這對完成任務有著關鍵性的助益！",
                "今天累計專注了 " + formatTime(focusSeconds) + "，高效持久的專注表現讓你的夥伴大受鼓舞！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (focusSeconds >= 7200) { // 2~4小時
            String[] options = {
                "今天穩健地專注了 " + formatTime(focusSeconds) + "，工作與修行進度正一步步穩紮穩打地前進。",
                "累計專注時間達到 " + formatTime(focusSeconds) + "，是一個極具成效的黃金專注期！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (focusSeconds >= 120) { // 2分鐘~2小時
            String[] options = {
                "今天專注了 " + formatTime(focusSeconds) + "，雖然時間稍短，但只要每天持續累積，夥伴也一定能順利進化！",
                "今天累計完成了 " + formatTime(focusSeconds) + " 的修行，繼續保持這個節奏，積少成多！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else { // 2分鐘以下
            String[] options = {
                "今天只專注了 " + formatTime(focusSeconds) + "，稍微有點短呢，夥伴正眼巴巴地盼著與你一起努力喔！",
                "今日累計專注僅有 " + formatTime(focusSeconds) + "。冒險才剛開始，明天讓我們試著拉長專注的節奏吧！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // 2. 分心程度評論 (Part 2)
        long totalSecs = focusSeconds + distractionSeconds;
        double distractRatio = totalSecs > 0 ? (distractionSeconds / (double) totalSecs) : 0;

        if (distractionSeconds == 0) {
            String[] options = {
                "最驚人的是，今天完全沒有任何分心記錄！簡直就像完美迴避了所有干擾，效率百分百！",
                "令人讚嘆的是，今日抗分心率為完美的 100%，所有野生分心怪獸都被你的專注護盾直接蒸發！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (distractionSeconds <= 600) { // 10分鐘以內
            String[] options = {
                "而且今天分心時間極短（僅 " + formatTime(distractionSeconds) + "），干擾因素幾乎不存在，專注純度極高！",
                "分心時間控制在極佳的 " + formatTime(distractionSeconds) + " 內，抗干擾表現非常優異！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (distractRatio < 0.15) { // 分心比例小於 15%
            String[] options = {
                "此外，分心時間（" + formatTime(distractionSeconds) + "）僅佔總時間的極小比例，抗分心能力非常強大！",
                "雖然稍有打岔，但分心佔比極低，大部分時間你都牢牢掌握著專注的主導權。"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (distractionSeconds >= 5400 && distractRatio >= 0.40) { // 分心大於 1.5 小時且佔比大於 40%
            String[] options = {
                "不過，今天分心累積達 " + formatTime(distractionSeconds) + "（佔比高達 " + String.format("%.0f%%", distractRatio * 100) + "），被野生的分心怪獸（社群/軟體）拉扯了太多注意力，明天試著把手機放遠一點！",
                "可惜今天注意力有點被瓜分，分心時間長達 " + formatTime(distractionSeconds) + "。下次努力阻擋分心怪獸，找回屬於你的效率吧！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "雖然中間偶爾被別的事物打斷（共計分心 " + formatTime(distractionSeconds) + "），但所幸你仍順利重回專注軌道。",
                "儘管分心佔去了 " + formatTime(distractionSeconds) + "，但你及時踩了煞車，完成了當天的修行。"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // 3. 綜合評估 / 寶可夢對應語氣 (Part 3)
        if (focusScore >= 0.90) {
            String[] options = {
                "夥伴今天與你完美心靈契合，大腦開發度 100%！這絕對是神奇寶貝大師等級的巔峰表現！",
                "太無敵了！今天效率簡直像噴火龍使出了閃焰衝鋒，沒有什麼能阻擋你的前進！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (focusScore >= 0.70) {
            String[] options = {
                "夥伴向你投來信任且讚許的眼神。今天的努力很棒，明天也跟夥伴一起加油！",
                "今天的修練非常成功，收服了許多任務，距離大師之路又更近了一大步！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (focusScore >= 0.50) {
            String[] options = {
                "夥伴稍微打了一記哈欠，建議你待會好好放鬆，休息夠了，我們下一輪計時再接再厲！",
                "今天的專注表現中規中矩，喝杯水，拍拍灰塵，調整好狀態，隨時準備重新出發！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "今天的狀態似乎有點低迷呢，沒關係，先與夥伴好好休息補滿體力，明天重新出發收服任務！",
                "被分心怪獸包圍了嗎？不要氣餒！今晚放空腦袋，明天我們重新調整節奏再戰！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        return sb.toString();
    }

    public static String getComment(double focusScore, LocalDate date) {
        // 舊簽名的向下相容：預設以 4 小時專注與 30 分鐘分心為基準
        return getComment(focusScore, 14400, 1800, date);
    }

    public static String getWeeklyComment(double distRatio, double totalFocusHours, String topSubject, LocalDate date) {
        int seed = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder();

        // Part 1: Focus Volume
        if (totalFocusHours >= 20.0) {
            String[] options = {
                "本週您的專注特訓累計長達 " + String.format("%.1f", totalFocusHours) + " 小時，真是不折不扣的「專注鐵人」！",
                "這週你完成了長達 " + String.format("%.1f", totalFocusHours) + " 小時的修行，夥伴看見你的耐力都深感佩服！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (totalFocusHours >= 10.0) {
            String[] options = {
                "本週達成了累計 " + String.format("%.1f", totalFocusHours) + " 小時的專注，夥伴陪你走過了一段極為充實的成長旅程。",
                "這週完成了累計 " + String.format("%.1f", totalFocusHours) + " 小時的特訓，這是一段非常紮實的效率展現！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (totalFocusHours >= 5.0) {
            String[] options = {
                "本週累計專注 " + String.format("%.1f", totalFocusHours) + " 小時，這是一段穩健而持續進步的冒險歷程。",
                "這週專注時長為 " + String.format("%.1f", totalFocusHours) + " 小時，在穩定的步伐中持續收服各項任務。"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "本週專注時間較為零星（共 " + String.format("%.1f", totalFocusHours) + " 小時），修行強度稍顯不足，要多加油囉！",
                "本週累計專注僅有 " + String.format("%.1f", totalFocusHours) + " 小時。夥伴在訓練場等著你，下週我們一起努力拉長時間吧！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // Part 2: Top Subject
        if (topSubject != null && !topSubject.equals("無") && !topSubject.equals("未分類")) {
            String[] options = {
                "特別是在『" + topSubject + "』上投入了最專注的熱情，這絕對是您本週的重點突破學科！",
                "大部分時間你都在跟『" + topSubject + "』並肩作戰，對該領域的攻克非常專注！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "各學科的時間分配十分平衡，展現了全方位的修煉節奏！",
                "本週均衡發展，各領域都有所推進，是一個全面開花的修行週！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // Part 3: Distraction Ratio
        if (distRatio < 10.0) {
            String[] options = {
                "更棒的是，本週分心佔比極低（僅 " + String.format("%.1f%%", distRatio) + "），成功避開了野生分心怪獸的偷襲，效率拉滿！",
                "抗分心表現極其神勇（分心比 " + String.format("%.1f%%", distRatio) + "），大腦的防禦屏障固若金湯！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (distRatio < 25.0) {
            String[] options = {
                "分心軟體的使用比例（" + String.format("%.1f%%", distRatio) + "）控制在合理範疇內，工作與放鬆的分配相當得宜。",
                "雖然有些微雜音，但分心比（" + String.format("%.1f%%", distRatio) + "）並不影響大局，專注節奏非常流暢。"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "不過，本週分心比例偏高（達 " + String.format("%.1f%%", distRatio) + "），似乎被野生的干擾怪獸拉扯了太多精力。下週挑戰看看關閉社群通知吧！",
                "遺憾的是分心累積有些多，分心比達到了 " + String.format("%.1f%%", distRatio) + "。冒險途中難免有誘惑，下週和夥伴一起把手機放遠一點！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // Part 4: Pokémon Encouragement
        String[] pokemonOptions = {
            "你的夥伴今天充滿了精神，正期待著下週與你一同迎來大進化！",
            "就像妙蛙種子使出了生長，期待你下週的專注能量更上一層樓！",
            "夥伴對你投來了崇拜與期待的眼神，拍拍灰塵，我們下週繼續勇闖大師之路！",
            "這週累積的經驗值非常可觀，下週繼續與精靈夥伴一起突破界限吧！"
        };
        sb.append(pokemonOptions[random.nextInt(pokemonOptions.length)]);

        return sb.toString();
    }

    public static String getMonthlyComment(double distRatio, double totalFocusHours, String topSubject, LocalDate date) {
        int seed = date.getYear() * 10000 + date.getMonthValue() * 100 + date.getDayOfMonth();
        Random random = new Random(seed);
        StringBuilder sb = new StringBuilder();

        // Part 1: Focus Volume
        if (totalFocusHours >= 80.0) {
            String[] options = {
                "這一個月來，你展現了如同傳奇訓練家般的堅韌毅力！總專注時間累積達到了驚人的 " + String.format("%.1f", totalFocusHours) + " 小時！",
                "本月專注總時數高達 " + String.format("%.1f", totalFocusHours) + " 小時！如此驚人的表現，夥伴都為你感到無比驕傲！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (totalFocusHours >= 40.0) {
            String[] options = {
                "本月累計專注達 " + String.format("%.1f", totalFocusHours) + " 小時，穩健的專注習慣已悄然建立，工作與冒險的步調極佳！",
                "本月完成了累計 " + String.format("%.1f", totalFocusHours) + " 小時的高效專注，這是一段令人讚許的長期堅持成果！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (totalFocusHours >= 15.0) {
            String[] options = {
                "本月總專注時間為 " + String.format("%.1f", totalFocusHours) + " 小時，是一個很好的起點，繼續與夥伴保持這個前進姿態吧！",
                "本月修行時間累計 " + String.format("%.1f", totalFocusHours) + " 小時，專注的幼苗正在萌芽，持續耕耘便能開花結果！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "本月的修行時間累計僅有 " + String.format("%.1f", totalFocusHours) + " 小時，是不是被其他冒險引開了注意力呢？下個月重新打起精神吧！",
                "本月專注時數偏低（共 " + String.format("%.1f", totalFocusHours) + " 小時）。精靈球正需要補滿專注能量，下個月讓我們和夥伴一起重新出發！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // Part 2: Top Subject
        if (topSubject != null && !topSubject.equals("無") && !topSubject.equals("未分類")) {
            String[] options = {
                "尤其是對『" + topSubject + "』進行了長期的深度攻克，展現出無懈可擊的耐心，您已在該領域踏出穩健的步伐！",
                "這個月中你與『" + topSubject + "』纏鬥最久，付出的汗水也最多，該學科的進步將是顯而易見的！"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "在不同領域的修行中都留下了你的腳印，展現了極佳的平衡感與多方向的探索能力！",
                "各項特訓進度都非常平穩，全方位的自我提升讓你的冒險之旅更加充實！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // Part 3: Distraction Ratio
        if (distRatio < 12.0) {
            String[] options = {
                "更難得的是，月平均分心率控制在近乎完美的 " + String.format("%.1f%%", distRatio) + "，你的抗干擾能力堪稱範本，不給分心軟體任何可乘之機！",
                "月度分心佔比僅為極佳的 " + String.format("%.1f%%", distRatio) + "，自律性極強，成功阻絕了大多數不必要的干擾。"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else if (distRatio < 25.0) {
            String[] options = {
                "雖然長路漫漫偶有分心，但月平均分心比（" + String.format("%.1f%%", distRatio) + "）仍維持在安全水位內，表現合格！",
                "月度分心率為 " + String.format("%.1f%%", distRatio) + "，在漫長的一個月修行中，這是一個十分合理且健康的比值。"
            };
            sb.append(options[random.nextInt(options.length)]);
        } else {
            String[] options = {
                "不過，月度分心比高達 " + String.format("%.1f%%", distRatio) + "。長期來看，分心軟體瓜分了你不少的精力，下個月試著啟用專注干擾阻擋，守護寶可夢經驗值！",
                "本月有些頻繁被社群與影音分心（分心比 " + String.format("%.1f%%", distRatio) + "）。別氣餒，下個月試著每天減少一點分心時間，必定能看到突破！"
            };
            sb.append(options[random.nextInt(options.length)]);
        }

        sb.append(" ");

        // Part 4: Pokémon Encouragement
        String[] pokemonOptions = {
            "這股熊熊燃燒的專注之火，就像噴火龍的終極火焰一般耀眼。下個月繼續前進吧，訓練家！",
            "你的寶可夢夥伴本月成長巨大，實力大幅提升，已經準備好迎接下一個賽季的挑戰！",
            "本月的努力將化為夥伴進化的堅實養分，離成為寶可夢大師的終極榮耀只差一步！",
            "持之以恆的修行是最強的秘笈。下個月讓我們跟夥伴繼續攜手，書寫你的冒險篇章！"
        };
        sb.append(pokemonOptions[random.nextInt(pokemonOptions.length)]);

        return sb.toString();
    }
}
