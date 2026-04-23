package jfocus.db;

/**
 * 表示資料存取流程中的執行錯誤。
 */
public class StorageException extends RuntimeException {

    /**
     * 建立新的資料存取例外。
     *
     * @param message 錯誤訊息
     * @param cause 原始例外
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
