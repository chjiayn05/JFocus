package jfocus.todo;

import java.time.LocalDateTime;

/**
 * 表示一筆待辦事項紀錄。
 *
 * @param id       資料庫自動產生的主鍵；新增時傳入 0
 * @param task     待辦事情（不可為 null 或空白）
 * @param deadline 時限（可為 null）
 * @param isDone   是否完成
 * @param notes    備註（可為 null）
 */
public record TodoRecord(
        int id,
        String task,
        LocalDateTime deadline,
        boolean isDone,
        String notes) {

    public TodoRecord {
        if (task == null || task.isBlank()) {
            throw new IllegalArgumentException("task cannot be null or blank");
        }
    }
}
