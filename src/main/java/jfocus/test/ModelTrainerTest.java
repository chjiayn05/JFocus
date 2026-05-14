package jfocus.test;

import java.time.LocalDateTime;

import jfocus.activity.ActivityRecord;
import jfocus.activity.ActivityRepository;
import jfocus.activity.JdbcActivityRepository;
import jfocus.ai.ModelTrainer;
import jfocus.db.DatabaseCore;

public class ModelTrainerTest {

    public static void main(String[] args) {
        System.out.println("=== 開始測試 ModelTrainer 批次訓練邏輯 ===");

        // 1. 初始化資料庫與 Repository
        DatabaseCore dbCore = new DatabaseCore();
        dbCore.initialize();
        ActivityRepository repository = new JdbcActivityRepository(dbCore);

        // 2. 先觸發一次訓練，看有沒有初始狀態
        System.out.println("\n[步驟 1] 初次觸發 trainModel()，設定基準...");
        ModelTrainer.trainModel();

        // 3. 寫入 20 筆資料
        System.out.println("\n[步驟 2] 寫入 20 筆假資料...");
        for (int i = 0; i < 20; i++) {
            repository.saveActivity(new ActivityRecord(
                0, // ID 由 DB 自動產生
                "TestApp_Focus", 
                "Test Window " + i,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now(),
                true,
                "test-session"
            ));
        }

        // 4. 觸發訓練，預期不到 50 筆，略過訓練
        System.out.println("\n[步驟 3] 再次觸發 trainModel()，預期會略過訓練...");
        ModelTrainer.trainModel();

        // 5. 寫入 40 筆資料 (累積 60 筆)
        System.out.println("\n[步驟 4] 繼續寫入 40 筆假資料...");
        for (int i = 0; i < 40; i++) {
            repository.saveActivity(new ActivityRecord(
                0,
                "TestApp_Play", 
                "Test Play Window " + i,
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now(),
                false,
                "test-session"
            ));
        }

        // 6. 觸發訓練，預期超過 50 筆，會啟動訓練
        System.out.println("\n[步驟 5] 第三次觸發 trainModel()，預期會開始訓練...");
        ModelTrainer.trainModel();

        System.out.println("\n=== 測試完成 ===");
    }
}
