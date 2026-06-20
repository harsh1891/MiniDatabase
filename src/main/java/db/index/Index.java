package db.index;

import db.storage.Field;
import java.util.List;

public interface Index {
    void insert(Field key, int recordId);
    List<Integer> search(Field key);
    List<Integer> rangeSearch(Field lower, Field upper);
    void delete(Field key, int recordId);
    void clear();
}
