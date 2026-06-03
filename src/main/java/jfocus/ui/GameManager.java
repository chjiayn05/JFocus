package jfocus.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    private int pokeBallPity = 0;
    private int masterBallPity = 0;
    private boolean lastDrawDuplicate = false;

    // 動態分級獎池 (由 FocusUI 載入 JSON 後自動灌入)
    private final List<String> STANDARD_POOL = new ArrayList<>();
    private final List<String> RARE_POOL = new ArrayList<>();

    // 已解鎖關卡：pokemonId → 已解鎖的 stage 集合（1/2/3）
    private final Map<String, Set<Integer>> unlockedStages = new LinkedHashMap<>();
    private final Map<String, Integer> pokemonXpById = new LinkedHashMap<>();

    // 當前夥伴
    private String currentPartnerId = jfocus.io.UserData.loadCurrentPartner();

    public void setGachaPools(List<String> standard, List<String> rare) {
        STANDARD_POOL.clear();
        RARE_POOL.clear();

        if (standard != null) STANDARD_POOL.addAll(standard);
        if (rare != null) RARE_POOL.addAll(rare);

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

        if (selectedPool == null || selectedPool.isEmpty()) {
            selectedPool = STANDARD_POOL;
        }

        if (selectedPool.isEmpty()) {
            System.err.println("🚨 嚴重警告：所有獎池皆為空！觸發終極防呆機制。");
            selectedPool = java.util.Arrays.asList("004_charmander", "147_dratini");
        }

        String prizeId = selectedPool.get(new java.util.Random().nextInt(selectedPool.size()));
        String pokemonId = normalizePokemonId(prizeId);

        if (unlockedStages.containsKey(pokemonId)) {
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
                addUnlockedStage(pokemonId, 1);
                pokemonXpById.putIfAbsent(pokemonId, 0);
            }
        }

        return prizeId;
    }

    public void initializePlayerState(int coins, int stones, int xp,
            Map<String, Set<Integer>> stageMap, Map<String, Integer> pokemonXpMap) {
        focusCoins = Math.max(0, coins);
        masterStones = Math.max(0, stones);
        totalXP = Math.max(0, xp);

        unlockedStages.clear();
        pokemonXpById.clear();

        if (stageMap != null) {
            for (Map.Entry<String, Set<Integer>> entry : stageMap.entrySet()) {
                String pokemonId = normalizePokemonId(entry.getKey());
                if (pokemonId != null && entry.getValue() != null) {
                    for (int stage : entry.getValue()) {
                        addUnlockedStage(pokemonId, stage);
                    }
                }
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

        if (unlockedStages.isEmpty()) {
            addUnlockedStage("001_bulbasaur", 1);
            addUnlockedStage("004_charmander", 1);
            addUnlockedStage("007_squirtle", 1);
        }

        ensureXpEntriesForUnlockedPokemon();
        reconcileEvolutionStagesWithXp();
    }

    public void initializePlayerState(int coins, int stones, int xp, Map<String, Set<Integer>> stageMap) {
        initializePlayerState(coins, stones, xp, stageMap, Collections.emptyMap());
    }

    public void addFocusTime(int minutes, String activePokemonId) {
        int safeMinutes = Math.max(0, minutes);
        focusCoins += safeMinutes;
        totalXP += safeMinutes;
        dailyMinutes += safeMinutes;

        while (dailyMinutes >= 120) {
            masterStones += 1;
            dailyMinutes -= 120;
            System.out.println("💎 累積專注滿 2 小時，獲得一顆大師晶石！");
        }

        String pokemonId = normalizePokemonId(activePokemonId);
        if (pokemonId != null && unlockedStages.containsKey(pokemonId)) {
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

    public boolean isLastDrawDuplicate() {
        return this.lastDrawDuplicate;
    }

    public boolean isStageUnlocked(String id, int stage) {
        if (stage < 1 || stage > 3) return false;
        String pokemonId = normalizePokemonId(id);
        if (pokemonId == null) return false;
        return unlockedStages.getOrDefault(pokemonId, Set.of()).contains(stage);
    }

    public int getEvolutionStage(String id) {
        String pokemonId = normalizePokemonId(id);
        if (pokemonId == null) return 1;

        Set<Integer> stages = unlockedStages.getOrDefault(pokemonId, Set.of());
        if (stages.contains(3)) return 3;
        if (stages.contains(2)) return 2;
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

    public Map<String, Set<Integer>> getUnlockedStages() {
        Map<String, Set<Integer>> copy = new LinkedHashMap<>();
        unlockedStages.forEach((id, stages) ->
                copy.put(id, Collections.unmodifiableSet(new HashSet<>(stages))));
        return Collections.unmodifiableMap(copy);
    }

    private void syncEvolutionStages() {
        ensureXpEntriesForUnlockedPokemon();
        for (String pokemonId : new ArrayList<>(unlockedStages.keySet())) {
            syncEvolutionStagesForPokemon(pokemonId);
        }
    }

    private void reconcileEvolutionStagesWithXp() {
        // 只保留 stage 1，再重算其他 stage
        unlockedStages.replaceAll((id, stages) -> {
            Set<Integer> s1Only = new HashSet<>();
            if (stages.contains(1)) s1Only.add(1);
            return s1Only;
        });
        unlockedStages.values().removeIf(Set::isEmpty);
        syncEvolutionStages();
    }

    private void syncEvolutionStagesForPokemon(String pokemonId) {
        if (!unlockedStages.containsKey(pokemonId)) return;
        int pokemonXp = pokemonXpById.getOrDefault(pokemonId, 0);
        Set<Integer> stages = unlockedStages.computeIfAbsent(pokemonId, k -> new HashSet<>());
        stages.add(1);
        if (pokemonXp >= STAGE_XP_REQUIREMENT[STAGE_1]) stages.add(2);
        if (pokemonXp >= STAGE_XP_REQUIREMENT[STAGE_2]) stages.add(3);
    }

    private void ensureXpEntriesForUnlockedPokemon() {
        for (String pokemonId : unlockedStages.keySet()) {
            pokemonXpById.putIfAbsent(pokemonId, 0);
        }
    }

    private void addUnlockedStage(String pokemonId, int stage) {
        if (pokemonId == null || pokemonId.isBlank() || stage < 1 || stage > 3) return;
        unlockedStages.computeIfAbsent(pokemonId, k -> new HashSet<>()).add(stage);
    }

    private String normalizePokemonId(String idOrFolder) {
        if (idOrFolder == null) return null;
        String trimmed = idOrFolder.trim();
        return trimmed.isEmpty() ? null : trimmed;
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
