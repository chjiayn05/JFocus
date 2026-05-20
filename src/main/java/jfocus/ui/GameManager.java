package jfocus.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jfocus.ui.FocusUI.PokemonData;

public class GameManager {
    private static final int STAGE_2_XP_REQUIREMENT = 50;
    private static final int STAGE_3_XP_REQUIREMENT = 200;

    // 貨幣與數據
    private int focusCoins = 0;
    private int masterStones = 0;
    private int totalXP = 0;
    private int dailyMinutes = 0;

    // 保底計數器
    private int pokeBallPity = 0;
    private int masterBallPity = 0;
    // 1. 定義分級獎池 (這裡放的是資料夾名稱或 ID)
    private static final List<String> STANDARD_POOL = Arrays.asList("010_caterpie", "013_weedle", "016_pidgey", "019_rattata");
    private static final List<String> RARE_POOL = Arrays.asList("001_bulbasaur", "004_charmander", "007_squirtle", "025_pikachu");
    private static final List<String> SUPER_RARE_POOL = Arrays.asList("147_dratini", "092_gastly", "063_abra");

    // 已解鎖關卡，格式: ddd_s (例如 004_2)
    private final Set<String> unlockedStageKeys = new LinkedHashSet<>(
            Arrays.asList("001_1", "004_1", "007_1"));
    // 每隻寶可夢自己的 XP，key: ddd
    private final Map<String, Integer> pokemonXpById = new LinkedHashMap<>();

    public void initializePlayerState(int coins, int stones, int xp, Set<String> stageKeys,
            Map<String, Integer> pokemonXpMap) {
        focusCoins = Math.max(0, coins);
        masterStones = Math.max(0, stones);
        totalXP = Math.max(0, xp);

        unlockedStageKeys.clear();
        pokemonXpById.clear();

        if (stageKeys != null) {
            for (String stageKey : stageKeys) {
                addStageKeyIfValid(stageKey);
            }
        }

        if (pokemonXpMap != null) {
            for (Map.Entry<String, Integer> entry : pokemonXpMap.entrySet()) {
                String pokemonId = normalizePokemonId(entry.getKey());
                if (pokemonId != null) {
                    int xpValue = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
                    xpValue = Math.min(STAGE_3_XP_REQUIREMENT, xpValue);
                    pokemonXpById.put(pokemonId, xpValue);
                }
            }
        }

        if (unlockedStageKeys.isEmpty()) {
            unlockedStageKeys.add("001_1");
            unlockedStageKeys.add("004_1");
            unlockedStageKeys.add("007_1");
        }

        ensureXpEntriesForUnlockedStage1();
        reconcileEvolutionStagesWithXp();
    }

    public void initializePlayerState(int coins, int stones, int xp, Set<String> stageKeys) {
        initializePlayerState(coins, stones, xp, stageKeys, Collections.emptyMap());
    }

    // --- 貨幣邏輯 ---
    public void addFocusTime(int minutes, String activePokemonId) {
        int safeMinutes = Math.max(0, minutes);
        focusCoins += safeMinutes; // 1 分鐘 = 1 幣
        totalXP += safeMinutes;    // 將專注時間轉為經驗值
        dailyMinutes += safeMinutes;

        System.out.println("🪙 【結算】增加專注幣: " + safeMinutes + " | 總 XP 變為: " + totalXP);

        // 滿 4 小時送一顆大師晶石
        while (dailyMinutes >= 240) {
            masterStones += 1;
            dailyMinutes -= 240;
            System.out.println("💎 獲得一顆大師晶石！");
        }

        String pokemonId = normalizePokemonId(activePokemonId);
        System.out.println("🔍 【準備加經驗】目前出戰寶可夢 ID: " + pokemonId);

        if (pokemonId != null) {
            // 檢查這隻寶可夢是否在「已解鎖名單」中
            if (unlockedStageKeys.contains(stageKey(pokemonId, 1))) {
                int currentXp = pokemonXpById.getOrDefault(pokemonId, 0);
                int nextXp = Math.min(STAGE_3_XP_REQUIREMENT, currentXp + safeMinutes);
                pokemonXpById.put(pokemonId, nextXp);
                syncEvolutionStagesForPokemon(pokemonId);
                
                System.out.println("✨ 【成功】寶可夢 [" + pokemonId + "] 經驗值增加！目前 XP: " + nextXp);
            } else {
                System.err.println("⚠️ 【失敗】寶可夢 [" + pokemonId + "] 不在已解鎖清單中，無法獲得經驗值！");
            }
        } else {
            System.err.println("⚠️ 【失敗】沒有傳入有效的寶可夢 ID！");
        }
    }

    public void addXP(int amount) {
    // 1. 增加記憶體中的數值
    this.totalXP += amount;
    System.out.println("📈 經驗值增加！目前總經驗: " + this.totalXP);

  
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
        if (stage < 1 || stage > 3) {
            return false;
        }

        String pokemonId = normalizePokemonId(id);
        if (pokemonId == null) {
            return false;
        }

        return unlockedStageKeys.contains(stageKey(pokemonId, stage));
    }

    // 抽獎
// 在 GameManager 裡準備三個空箱子
    private final List<String> standardPool = new ArrayList<>();
    private final List<String> rarePool = new ArrayList<>();
    private final List<String> superRarePool = new ArrayList<>();


    // 寫一個方法接收名單
    public void buildGachaPools(List<PokemonData> allPokemon) {
        for (PokemonData data : allPokemon) {
            String fullId = data.getId() + "_" + data.getFolderName().split("_")[1]; // 假設組出 "147_dratini"
            
            if ("SUPER_RARE".equals(data.getRarity())) {
                superRarePool.add(fullId);
            } else if ("RARE".equals(data.getRarity())) {
                rarePool.add(fullId);
            } else {
                standardPool.add(fullId); // 預設都是普通
            }
        }
        System.out.println("🎰 抽獎池建立完成！超稀有: " + superRarePool.size() + " 隻");
    }
public String performPokeBallDraw(String ballType) {
if ("MASTERBALL".equals(ballType)) {
            if (this.masterStones < 1) { // 假設大師球一次消耗 1 顆大師晶石 (請依你企劃修改)
                return "INSUFFICIENT_FUNDS";
            }
            this.masterStones -= 1; // 扣除大師晶石
            System.out.println("💎 消耗 1 顆大師晶石！剩餘：" + this.masterStones);
        } else {
            if (this.focusCoins < 200) {
                return "INSUFFICIENT_FUNDS";
            }
            this.focusCoins -= 200; // 扣除專注幣
            System.out.println("🪙 消耗 200 枚專注幣！剩餘：" + this.focusCoins);
        }

        // 2. 決定抽到哪個等級 (隨機 0.0 ~ 1.0)
        double rand = Math.random();
        List<String> selectedPool;
        
        if (ballType.equals("MASTERBALL")) {
            // 大師球：20% 超稀有, 80% 稀有
            selectedPool = (rand < 0.20) ? SUPER_RARE_POOL : RARE_POOL;
        } else {
            // 普通球：1% 超稀有, 14% 稀有, 85% 普通
            if (rand < 0.01) {
                selectedPool = SUPER_RARE_POOL;
            } else if (rand < 0.15) {
                selectedPool = RARE_POOL;
            } else {
                selectedPool = STANDARD_POOL;
            }
        }

        // 3. 從選定的池子裡隨機抽一隻
        String prizeId = selectedPool.get(new java.util.Random().nextInt(selectedPool.size()));

        // 4. 解鎖邏輯 (原本的邏輯)
        String pokemonId = normalizePokemonId(prizeId);
        if (pokemonId != null) {
            unlockedStageKeys.add(stageKey(pokemonId, 1));
            pokemonXpById.putIfAbsent(pokemonId, 0);
        }

        return prizeId;
    }
// ==========================================
    // 取得指定寶可夢目前的「最高進化階段」(1, 2, 或 3)
    // ==========================================
    public int getEvolutionStage(String id) {
        String pokemonId = normalizePokemonId(id);
        if (pokemonId == null) {
            return 1; // 防呆，預設回傳 1
        }

        // 從最高階段 (3) 往下檢查，如果有解鎖就回傳該階段
        if (unlockedStageKeys.contains(stageKey(pokemonId, 3))) {
            return 3; // 已經進化到水箭龜
        } else if (unlockedStageKeys.contains(stageKey(pokemonId, 2))) {
            return 2; // 已經進化到卡咪龜
        }
        
        return 1; // 預設是第一階段 (傑尼龜)
    }
    // Getters
    public int getFocusCoins() {
        return focusCoins;
    }

    public int getMasterStones() {
        return masterStones;
    }

    public int getTotalXP() {
        return totalXP;
    }

    public int getPokemonXp(String pokemonId) {
        String normalized = normalizePokemonId(pokemonId);
        if (normalized == null) {
            return 0;
        }
        return Math.min(STAGE_3_XP_REQUIREMENT, pokemonXpById.getOrDefault(normalized, 0));
    }

    
    public Map<String, Integer> getPokemonXpMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pokemonXpById));
    }

    public Set<String> getUnlockedStageKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(unlockedStageKeys));
    }

    public List<String> getUnlockedPokemonIds() {
        List<String> ids = new ArrayList<>();
        for (String key : unlockedStageKeys) {
            if (key.endsWith("_1")) {
                ids.add(key.substring(0, 3));
            }
        }
        return ids;
    }

    private void syncEvolutionStages() {
        ensureXpEntriesForUnlockedStage1();

        List<String> basePokemonIds = new ArrayList<>();
        for (String key : unlockedStageKeys) {
            if (key.endsWith("_1") && key.length() >= 3) {
                basePokemonIds.add(key.substring(0, 3));
            }
        }

        for (String pokemonId : basePokemonIds) {
            syncEvolutionStagesForPokemon(pokemonId);
        }
    }

    private void reconcileEvolutionStagesWithXp() {
        Set<String> stage1Keys = new LinkedHashSet<>();
        for (String key : unlockedStageKeys) {
            if (key.endsWith("_1")) {
                stage1Keys.add(key);
            }
        }

        unlockedStageKeys.clear();
        unlockedStageKeys.addAll(stage1Keys);
        syncEvolutionStages();
    }

    private void syncEvolutionStagesForPokemon(String pokemonId) {
        if (!unlockedStageKeys.contains(stageKey(pokemonId, 1))) {
            return;
        }

        int pokemonXp = pokemonXpById.getOrDefault(pokemonId, 0);
        if (pokemonXp >= STAGE_2_XP_REQUIREMENT) {
            unlockedStageKeys.add(stageKey(pokemonId, 2));
        }
        if (pokemonXp >= STAGE_3_XP_REQUIREMENT) {
            unlockedStageKeys.add(stageKey(pokemonId, 3));
        }
    }

    private void ensureXpEntriesForUnlockedStage1() {
        for (String key : unlockedStageKeys) {
            if (key.endsWith("_1") && key.length() >= 3) {
                String pokemonId = key.substring(0, 3);
                pokemonXpById.putIfAbsent(pokemonId, 0);
            }
        }
    }

    private void addStageKeyIfValid(String stageKey) {
        if (stageKey == null) {
            return;
        }

        String value = stageKey.trim();
        if (value.matches("\\d{3}_[1-3]")) {
            unlockedStageKeys.add(value);
        }
    }

    private String normalizePokemonId(String idOrFolder) {
        if (idOrFolder == null) {
            return null;
        }

        String trimmed = idOrFolder.trim();
        if (trimmed.length() < 3) {
            return null;
        }

        String maybeId = trimmed.substring(0, 3);
        if (!maybeId.matches("\\d{3}")) {
            return null;
        }

        return maybeId;
    }

    private String stageKey(String pokemonId, int stage) {
        return pokemonId + "_" + stage;
    }
    // ==========================================
    // 貼在 GameManager.java 最下面 (大括號 } 結束前)
    // ==========================================

    // 1. 【新增】記錄玩家現在正在帶哪一隻寶可夢出門 (預設給小火龍 004)
    private String currentPartnerId = jfocus.io.UserData.loadCurrentPartner(); 

    // 2. 取得當前夥伴的 ID
    public String getCurrentPokemonId() {
        return this.currentPartnerId; 
    }

// 3. 【修改】當玩家在 UI 切換夥伴時，不僅要改記憶體，還要寫入資料庫！
    public void setCurrentPokemonId(String id) {
        String normalized = normalizePokemonId(id);
        if (normalized != null) {
            this.currentPartnerId = normalized;
            jfocus.io.UserData.saveCurrentPartner(normalized); // 同步存檔！
        }
    }
}