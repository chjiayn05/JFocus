package jfocus.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GameManager {
    // 貨幣與數據
    private int focusCoins = 0;
    private int masterStones = 0;
    private int totalXP = 0;
    private int dailyMinutes = 0;

    // 保底計數器
    private int pokeBallPity = 0;
    private int masterBallPity = 0;

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

    // 在 GameManager.java 裡
    public boolean isStageUnlocked(String id, int stage) {
        if (!unlockedPokemonIds.contains(id))
            return false; // 連第一段都沒抽到

        if (stage == 1)
            return true;
        if (stage == 2)
            return totalXP >= 50;
        if (stage == 3)
            return totalXP >= 200;

        return false;
    }

    //抽獎

    // 假設這是在 GameManager.java 內
public String performPokeBallDraw() {
    this.focusCoins -= 200; // 扣錢
    
    // 這裡我們簡單示範：從所有 ID 裡隨機挑一個
    String[] pool = {"001_bulbasaur", "004_charmander", "007_squirtle", "092_gastly", "147_dratini"};
    int randomIndex = new java.util.Random().nextInt(pool.length);
    String prizeId = pool[randomIndex];

    // 檢查是否已解鎖，如果是新的就加進 List
    if (!unlockedPokemonIds.contains(prizeId)) {
        unlockedPokemonIds.add(prizeId);
    }
    
    return prizeId;
}
    // Getters
    public int getFocusCoins() {
        return focusCoins;
    }

    public int getMasterStones() {
        return masterStones;
    }
}