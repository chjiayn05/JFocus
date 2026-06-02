package jfocus.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jfocus.io.UserData;

public class GameManager {
    private final int STAGE_1 = 0;
    private final int STAGE_2 = 1;
    private final int STAGE_3 = 2;

    private final int STAGE_1_XP_REQUIREMENT = 50;
    private final int STAGE_2_XP_REQUIREMENT = 200;
    private final int STAGE_3_XP_REQUIREMENT = 500;

    private final int[] STAGE_XP_REQUIREMENT = {
        STAGE_1_XP_REQUIREMENT,
        STAGE_1_XP_REQUIREMENT + STAGE_2_XP_REQUIREMENT,
        STAGE_1_XP_REQUIREMENT + STAGE_2_XP_REQUIREMENT + STAGE_3_XP_REQUIREMENT};

    // --- 貨幣與數據 ---
    private int focusCoins = 0;
    private int masterStones = 0;
    private int totalXP = 0;
    private int dailyMinutes = 0;

    // --- 抽獎與保底系統 ---
    private int pokeBallPity = 0;   // 普通球保底計數
    private int masterBallPity = 0; // 大師球保底計數
    private boolean lastDrawDuplicate = false; // 紀錄剛剛那抽是不是重複的

    // 動態分級獎池 (由 FocusUI 載入 JSON 後自動灌入)
    private final List<String> STANDARD_POOL = new ArrayList<>();
    private final List<String> RARE_POOL = new ArrayList<>();

    // 已解鎖關卡與 XP 紀錄
    private final Set<String> unlockedStageKeys = new LinkedHashSet<>();
    private final Map<String, Integer> pokemonXpById = new LinkedHashMap<>();

    // 當前夥伴
    private String currentPartnerId = jfocus.io.UserData.loadCurrentPartner(); 

    // ==========================================
    // 🌟 自動構建獎池 (解決手動輸入幾百隻的問題)
    // ==========================================
// ==========================================
    // 🌟 裝上防呆機制的動態獎池載入
    // ==========================================
// ==========================================
    // 🌟 自動構建獎池 (安全版：直接接收分類好的字串名單)
    // ==========================================
    public void setGachaPools(List<String> standard, List<String> rare) {
        STANDARD_POOL.clear();
        RARE_POOL.clear();

        // 直接把 FocusUI 分類好的名單倒進來
        if (standard != null) STANDARD_POOL.addAll(standard);
        if (rare != null) RARE_POOL.addAll(rare);

        // 🛡️ 終極防護網
        if (STANDARD_POOL.isEmpty()) {
            System.err.println("⚠️ 警告：普通獎池為空！強制放入小火龍。");
            STANDARD_POOL.add("004_charmander");
        }
        if (RARE_POOL.isEmpty()) {
            System.err.println("⚠️ 警告：大師獎池為空！強制放入快龍。");
            RARE_POOL.add("147_dratini");
        }

        System.out.println("🎲 獎池初始化完成: 普通池 " + STANDARD_POOL.size() + " 隻, 大師池 (神獸) " + RARE_POOL.size() + " 隻");
    }

    // ==========================================
    // 🌟 安全的抽獎核心邏輯
    // ==========================================
    public String performPokeBallDraw(String ballType) {
        this.lastDrawDuplicate = false; 
        double rand = Math.random();
        List<String> selectedPool;

        if ("MASTERBALL".equals(ballType)) {
            if (this.masterStones < 2) return "INSUFFICIENT_FUNDS";
            this.masterStones -= 2;
            this.masterBallPity++;

            if (this.masterBallPity >= 10 || rand < 0.20) {
                selectedPool = RARE_POOL;
                this.masterBallPity = 0; 
                System.out.println("✨ 大師球抽中稀有神獸！(保底計數重置)");
            } else {
                selectedPool = STANDARD_POOL;
            }
        } else {
            if (this.focusCoins < 200) return "INSUFFICIENT_FUNDS";
            this.focusCoins -= 200;
            this.pokeBallPity++;

            if (this.pokeBallPity >= 50 || rand < 0.01) {
                selectedPool = RARE_POOL;
                this.pokeBallPity = 0;
                System.out.println("🌟 普通球竟然抽中神獸！歐氣爆發！");
            } else {
                selectedPool = STANDARD_POOL;
            }
        }

        // 🛡️ 二度防呆：如果抽到的池子是空的，強制降級
        if (selectedPool == null || selectedPool.isEmpty()) {
            selectedPool = STANDARD_POOL;
        }

        // 🛡️ 終極防呆：如果連普通池都是空的 (代表沒有載入成功)，直接硬塞救命寶可夢！
        if (selectedPool.isEmpty()) {
            System.err.println("🚨 嚴重警告：所有獎池皆為空！觸發終極防呆機制。");
            selectedPool = java.util.Arrays.asList("004_charmander", "147_dratini");
        }

        // 隨機抽出一隻
        String prizeId = selectedPool.get(new java.util.Random().nextInt(selectedPool.size()));

        String pokemonId = normalizePokemonId(prizeId);

        if (unlockedStageKeys.contains(stageKey(pokemonId, 1))) {
            this.lastDrawDuplicate = true;
            if ("MASTERBALL".equals(ballType)) {
                this.masterStones += 1; 
                System.out.println("♻️ [重複] 大師球抽到重複神獸，已返還 1 顆大師晶石！");
            } else {
                this.focusCoins += 100; 
                System.out.println("♻️ [重複] 普通球抽到重複精靈，已返還 100 專注幣！");
            }
        } else {
            if (pokemonId != null) {
                unlockedStageKeys.add(stageKey(pokemonId, 1));
                pokemonXpById.putIfAbsent(pokemonId, 0);
            }
        }

        return prizeId;
    }

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
                    xpValue = Math.min(STAGE_XP_REQUIREMENT[STAGE_3], xpValue);
                    pokemonXpById.put(pokemonId, xpValue);
                }
            }
        }

        // 預設解鎖御三家 (使用完整資料夾名稱)
        if (unlockedStageKeys.isEmpty()) {
            unlockedStageKeys.add("001_bulbasaur_1");
            unlockedStageKeys.add("004_charmander_1");
            unlockedStageKeys.add("007_squirtle_1");
        }

        ensureXpEntriesForUnlockedStage1();
        reconcileEvolutionStagesWithXp();
    }

    public void initializePlayerState(int coins, int stones, int xp, Set<String> stageKeys) {
        initializePlayerState(coins, stones, xp, stageKeys, Collections.emptyMap());
    }

    public void addFocusTime(int minutes, String activePokemonId) {
        int safeMinutes = Math.max(0, minutes);
        focusCoins += safeMinutes; 
        totalXP += safeMinutes;    
        dailyMinutes += safeMinutes;

        while (dailyMinutes >= 240) {
            masterStones += 1;
            dailyMinutes -= 240;
            System.out.println("💎 累積專注滿 4 小時，獲得一顆大師晶石！");
        }

        String pokemonId = normalizePokemonId(activePokemonId);
        if (pokemonId != null && unlockedStageKeys.contains(stageKey(pokemonId, 1))) {
            int currentXp = pokemonXpById.getOrDefault(pokemonId, 0);
            int nextXp = Math.min(STAGE_XP_REQUIREMENT[STAGE_3], currentXp + safeMinutes);
            pokemonXpById.put(pokemonId, nextXp);
            syncEvolutionStagesForPokemon(pokemonId);
            UserData.savePlayerStats(focusCoins, masterStones, totalXP, pokemonId);
        }
    }

    public void addXP(int amount) {
        this.totalXP += amount;
    }

    // ==========================================
    // 🌟 核心抽獎邏輯 (含保底與返還機制)
    // ==========================================
 

    // 讓 UI 知道剛剛那一抽是不是重複的，以便跳出提示
    public boolean isLastDrawDuplicate() {
        return this.lastDrawDuplicate;
    }

    public boolean isStageUnlocked(String id, int stage) {
        if (stage < 1 || stage > 3) return false;
        String pokemonId = normalizePokemonId(id);
        if (pokemonId == null) return false;
        return unlockedStageKeys.contains(stageKey(pokemonId, stage));
    }

    public int getEvolutionStage(String id) {
        String pokemonId = normalizePokemonId(id);
        if (pokemonId == null) return 1;

        if (unlockedStageKeys.contains(stageKey(pokemonId, 3))) return 3;
        if (unlockedStageKeys.contains(stageKey(pokemonId, 2))) return 2;
        return 1;
    }

    public int getFocusCoins() { return focusCoins; }
    public int getMasterStones() { return masterStones; }
    public int getTotalXP() { return totalXP; }

    public int getPokemonXp(String pokemonId) {
        String normalized = normalizePokemonId(pokemonId);
        if (normalized == null) return 0;
        return Math.min(STAGE_XP_REQUIREMENT[STAGE_3], pokemonXpById.getOrDefault(normalized, 0));
    }

    public Map<String, Integer> getPokemonXpMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(pokemonXpById));
    }

    public Set<String> getUnlockedStageKeys() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(unlockedStageKeys));
    }

    private void syncEvolutionStages() {
        ensureXpEntriesForUnlockedStage1();
        List<String> basePokemonIds = new ArrayList<>();
        for (String key : unlockedStageKeys) {
            if (key.endsWith("_1")) {
                basePokemonIds.add(key.substring(0, key.length() - 2));
            }
        }
        for (String pokemonId : basePokemonIds) {
            syncEvolutionStagesForPokemon(pokemonId);
        }
    }

    private void reconcileEvolutionStagesWithXp() {
        Set<String> stage1Keys = new LinkedHashSet<>();
        for (String key : unlockedStageKeys) {
            if (key.endsWith("_1")) stage1Keys.add(key);
        }
        unlockedStageKeys.clear();
        unlockedStageKeys.addAll(stage1Keys);
        syncEvolutionStages();
    }

    private void syncEvolutionStagesForPokemon(String pokemonId) {
        if (!unlockedStageKeys.contains(stageKey(pokemonId, 1))) return;
        int pokemonXp = pokemonXpById.getOrDefault(pokemonId, 0);
        
        if (pokemonXp >= 0) unlockedStageKeys.add(stageKey(pokemonId, 1));
        if (pokemonXp >= STAGE_XP_REQUIREMENT[STAGE_1]) unlockedStageKeys.add(stageKey(pokemonId, 2));
        if (pokemonXp >= STAGE_XP_REQUIREMENT[STAGE_2]) unlockedStageKeys.add(stageKey(pokemonId, 3));
    }

    private void ensureXpEntriesForUnlockedStage1() {
        for (String key : unlockedStageKeys) {
            if (key.endsWith("_1")) {
                String pokemonId = key.substring(0, key.length() - 2);
                pokemonXpById.putIfAbsent(pokemonId, 0);
            }
        }
    }

    private void addStageKeyIfValid(String stageKey) {
        if (stageKey == null) return;
        String value = stageKey.trim();
        if (value.endsWith("_1") || value.endsWith("_2") || value.endsWith("_3")) {
            unlockedStageKeys.add(value);
        }
    }

    // 🌟 關鍵修復：不再擷取前三碼，直接使用完整的資料夾名稱作為獨立 ID
    // 這樣 265_beautifly 和 265_dustox 就會被視為兩隻完全不同的寶可夢
    private String normalizePokemonId(String idOrFolder) {
        if (idOrFolder == null) return null;
        String trimmed = idOrFolder.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String stageKey(String pokemonId, int stage) {
        return pokemonId + "_" + stage;
    }

    public String getCurrentPokemonId() {
        return this.currentPartnerId == null ? "004_charmander" : this.currentPartnerId; 
    }

    public void setCurrentPokemonId(String id) {
        String normalized = normalizePokemonId(id);
        if (normalized != null) {
            this.currentPartnerId = normalized;
            jfocus.io.UserData.saveCurrentPartner(normalized); 
        }
    }
}