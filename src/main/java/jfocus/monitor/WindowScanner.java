package jfocus.monitor;

import java.util.Map;

public interface WindowScanner {
    /**
     * 掃描目前所有開啟且可見的視窗
     * @return Map，Key 為視窗的唯一識別碼，Value 為 WindowSession 物件
     */
    Map<String, WindowSession> scanWindows();
}
