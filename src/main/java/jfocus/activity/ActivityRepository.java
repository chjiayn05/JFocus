package jfocus.activity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/* 定義活動資料的讀寫介面。 */
public interface ActivityRepository {

    /**
     * 寫入一筆活動紀錄。
     *
     * @param activity 要儲存的活動紀錄
     */
    void saveActivity(ActivityRecord activity);

    /**
     * 依日期彙總使用時長。
     *
     * @param date 目標日期
     * @return 依應用程式聚合的使用秒數
     */
    Map<String, Integer> getAppUsageByDate(java.time.LocalDate date);

    /**
     * 依 session ID 彙總使用時長。
     *
     * @param sessionId 目標 session ID
     * @return 依應用程式聚合的使用秒數
     */
    Map<String, Integer> getAppUsageBySession(String sessionId);

    /**
     * 依日期區間彙總使用時長。
     *
     * @param startDate 起始日期
     * @param endDate 結束日期
     * @return 依應用程式聚合的使用秒數
     */
    Map<String, Integer> getAppUsageByRange(LocalDate startDate, LocalDate endDate);

    /**
     * 依 ID 遞增讀取活動紀錄。
     *
     * @param lastActivityId 上次讀取到的最後一筆 ID
     * @param limit 單次最大筆數
     * @return 依 ID 排序的活動紀錄列表
     */
    List<ActivityRecord> findActivitiesAfterId(int lastActivityId, int limit);
}
