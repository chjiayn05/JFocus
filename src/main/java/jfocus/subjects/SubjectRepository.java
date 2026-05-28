package jfocus.subjects;

import java.util.List;

public interface SubjectRepository {
    List<String> findAll();
    void add(String name);
    void rename(String oldName, String newName);
    void delete(String name);
}
