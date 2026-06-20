package db.operator;

import db.storage.Tuple;
import db.storage.TupleDesc;
import java.io.Serializable;

public interface DbIterator extends Serializable {
    void open() throws Exception;
    boolean hasNext() throws Exception;
    Tuple next() throws Exception;
    void rewind() throws Exception;
    void close() throws Exception;
    TupleDesc getTupleDesc();
}
