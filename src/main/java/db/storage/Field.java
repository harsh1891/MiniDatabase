package db.storage;

import java.io.DataOutputStream;
import java.io.IOException;

public interface Field extends Comparable<Field> {
    void serialize(DataOutputStream dos) throws IOException;
    boolean compare(Op op, Field val);
    Type getType();
    Object getValue();
    int hashCode();
    boolean equals(Object o);
    String toString();

    enum Op {
        EQUALS, GREATER_THAN, LESS_THAN, LESS_THAN_OR_EQ, GREATER_THAN_OR_EQ, LIKE, NOT_EQUALS
    }
}
