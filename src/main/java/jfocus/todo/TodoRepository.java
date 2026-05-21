package jfocus.todo;

import java.util.List;
import java.util.Optional;

/**
 * 待辦事項的資料存取介面。
 */
public interface TodoRepository {

    /**
     * 新增一筆待辦事項。
     *
     * @param todo 待新增的待辦事項（id 欄位將被忽略，由資料庫自動產生）
     */
    void saveTodo(TodoRecord todo);

    /**
     * 依 id 更新一筆待辦事項的所有欄位。
     *
     * @param todo 含有新內容的待辦事項（id 須存在於資料庫中）
     */
    void updateTodo(TodoRecord todo);

    /**
     * 依 id 刪除一筆待辦事項。
     *
     * @param id 目標待辦事項的 id
     */
    void deleteTodo(int id);

    /**
     * 取得所有待辦事項，依時限升冪排列（無時限的排在最後）。
     *
     * @return 所有待辦事項的清單
     */
    List<TodoRecord> getAllTodos();

    /**
     * 依 id 查詢單筆待辦事項。
     *
     * @param id 目標待辦事項的 id
     * @return 若存在則回傳 {@link Optional} 包裹的紀錄，否則回傳 {@link Optional#empty()}
     */
    Optional<TodoRecord> getTodoById(int id);
}
