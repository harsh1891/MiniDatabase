package db.storage;

import java.io.DataOutputStream;
import java.io.IOException;

public class IntField implements Field {
    private final int value;

    public IntField(int value) {
        this.value = value;
    }

    @Override
    public Object getValue() {
        return value;
    }

    public int getValueInt() {
        return value;
    }

    @Override
    public void serialize(DataOutputStream dos) throws IOException {
        dos.writeInt(value);
    }

    @Override
    public boolean compare(Op op, Field val) {
        if (val.getType() != Type.INT) {
            return false;
        }
        int otherVal = ((IntField) val).value;
        switch (op) {
            case EQUALS:
                return value == otherVal;
            case GREATER_THAN:
                return value > otherVal;
            case LESS_THAN:
                return value < otherVal;
            case LESS_THAN_OR_EQ:
                return value <= otherVal;
            case GREATER_THAN_OR_EQ:
                return value >= otherVal;
            case NOT_EQUALS:
                return value != otherVal;
            default:
                return false;
        }
    }

    @Override
    public Type getType() {
        return Type.INT;
    }

    @Override
    public int compareTo(Field o) {
        if (o.getType() != Type.INT) {
            throw new ClassCastException("Cannot compare IntField to " + o.getType());
        }
        return Integer.compare(this.value, ((IntField) o).value);
    }

    @Override
    public int hashCode() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof IntField) && (((IntField) o).value == value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}
