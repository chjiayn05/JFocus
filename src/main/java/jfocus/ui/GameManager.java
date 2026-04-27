package jfocus.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GameManager {
    // 貨幣與數據
    private int focusCoins = 0;
    private int masterStones = 0;
    private int totalXP = 0;
    private int dailyMinutes = 0;

    // 保底計數器
    private int pokeBallPity = 0;
    private int masterBallPity = 0;


    // 1. 新增這個：用來記錄玩家解鎖了哪些精靈階段 (格式: "004_1")
    private Set<String> unlockedStages = new HashSet<>();

    public GameManager() {
        // 可以在建構子裡預設解鎖御三家的第一階段
        unlockedStages.add("001_1"); // 妙蛙種子
        unlockedStages.add("004_1"); // 小火龍
        unlockedStages.add("007_1"); // 傑尼龜
    }
    // 2. 補上解鎖方法 (消除紅線)
    public void unlockPokemon(String id, int stage) {
        String key = id + "_" + stage;
        unlockedStages.add(key); // 加進已擁有的清單
        System.out.println("✅ 成功解鎖精靈: " + key);
    }
    
    // 已解鎖的清單 (存 ID)
    private List<String> unlockedPokemonIds = new ArrayList<>(Arrays.asList("001", "004", "007"));

    // --- 貨幣邏輯 ---
    public void addFocusTime(int minutes) {
        focusCoins += minutes; // 1 分鐘 = 1 幣
        dailyMinutes += minutes;

        // 滿 4 小時送一顆大師晶石
        if (dailyMinutes >= 240) {
            masterStones += 1;
            dailyMinutes = 0; // 重置或扣除，視你的需求
        }
    }

    // --- 抽獎系統 (Gacha) ---
    public String drawPokemon(String ballType) {
        double rand = Math.random();
        String rarity;

        if (ballType.equals("POKEBALL")) {
            if (focusCoins < 200)
                return "INSUFFICIENT_FUNDS";
            focusCoins -= 200;
            pokeBallPity++;

            if (pokeBallPity >= 20 || rand < 0.01) { // 1% 或 保底
                rarity = "Super Rare";
                pokeBallPity = 0;
            } else if (rand < 0.15) { // 14%
                rarity = "Rare";
            } else {
                rarity = "Standard";
            }
        } else { // MASTERBALL
            if (masterStones < 10)
                return "INSUFFICIENT_FUNDS";
            masterStones -= 10;
            masterBallPity++;

            if (masterBallPity >= 10 || rand < 0.20) { // 20% 或 保底
                rarity = "Super Rare";
                masterBallPity = 0;
            } else {
                rarity = "Rare";
            }
        }
        return rarity; // 回傳抽到的稀有度，再從 JSON 挑一隻該稀有度的 ID 給玩家
    }

public boolean isStageUnlocked(String id, int stage) {
    // 【第一關】先確認玩家有沒有這隻精靈？
    // 我們之前在抽獎時，是把 "id_1" (例如 "004_1") 存進 unlockedStages 裡
    if (!unlockedStages.contains(id + "_1")) {
        return false; // 連抽都沒抽到，直接回傳 false (保持黑影)
    }

    // 【第二關】確定有這隻精靈後，再來判斷 XP 等級夠不夠
    if (stage == 1) {
        return true;          // 既然抽到了，第一階段一定可用
    } else if (stage == 2) {
        return totalXP >= 50; // 總經驗值大於 50 解鎖二階
    } else if (stage == 3) {
        return totalXP >= 200; // 總經驗值大於 200 解鎖三階
    }

    return false; // 防呆，預防傳入奇怪的 stage 數字
}

    //抽獎

    // 假設這是在 GameManager.java 內
// 在 GameManager.java 裡
public String performPokeBallDraw(String drawType) {
    // 1. 準備所有可抽的清單
    List<String> pool = new ArrayList<>(Arrays.asList(
        "010", "013", "016", "063", "066", "092", "147", "179", "246", "280", "374", "443"
    ));

    // 2. 排除御三家 (確保清單沒有 001, 004, 007)
    pool.remove("001");
    pool.remove("004");
    pool.remove("007");

    // (可選) 3. 如果是高階抽獎，可以只留稀有怪
    if (drawType.equals("premium")) {
        pool = Arrays.asList("147", "246", "374", "443"); // 只抽龍系、準神等
    }

    // 4. 隨機抽取
    int randomIndex = (int) (Math.random() * pool.size());
    String resultId = pool.get(randomIndex);

    // 5. 將結果存入已解鎖清單
    unlockPokemon(resultId, 1); 

    return resultId;
}
    // Getters
    public int getFocusCoins() {
        return focusCoins;
    }

    public int getMasterStones() {
        return masterStones;
    }

    // --- 在 GameManager.java 中加入這兩個方法 ---

public void addFocusCoins(int amount) {
    this.focusCoins += amount; // 傳入負數就是扣錢
    if (this.focusCoins < 0) {
        this.focusCoins = 0; // 避免變成負債
    }
}

public void addMasterStones(int amount) {
    this.masterStones += amount;
    if (this.masterStones < 0) {
        this.masterStones = 0;
    }
}
}