package db.storage;

import java.io.DataOutputStream;
import java.io.IOException;

public class StringField implements Field {
    private final String value;
    private final int maxSize;

    public StringField(String value, int maxSize) {
        this.maxSize = maxSize;
        if (value.length() > maxSize) {
            this.value = value.substring(0, maxSize);
        } else {
            this.value = value;
        }
    }

    @Override
    public Object getValue() {
        return value;
    }

    @Override
    public void serialize(DataOutputStream dos) throws IOException {
        String padded = value;
        if (padded.length() < maxSize) {
            padded = String.format("%-" + maxSize + "s", padded);
        }
        dos.writeBytes(padded);
    }

    @Override
    public boolean compare(Op op, Field val) {
        if (val.getType() != Type.STRING) {
            return false;
        }
        String otherVal = ((StringField) val).value;
        int cmp = value.compareTo(otherVal);
        switch (op) {
            case EQUALS:
                return cmp == 0;
            case GREATER_THAN:
                return cmp > 0;
            case LESS_THAN:
                return cmp < 0;
            case LESS_THAN_OR_EQ:
                return cmp <= 0;
            case GREATER_THAN_OR_EQ:
                return cmp >= 0;
            case NOT_EQUALS:
                return cmp != 0;
            case LIKE:
                return value.contains(otherVal);
            default:
                return false;
        }
    }

    @Override
    public Type getType() {
        return Type.STRING;
    }

    @Override
    public int compareTo(Field o) {
        if (o.getType() != Type.STRING) {
            throw new ClassCastException("Cannot compare StringField to " + o.getType());
        }
        return this.value.compareTo(((StringField) o).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof StringField) && (((StringField) o).value.equals(value));
    }

    @Override
    public String toString() {
        return value;
    }
}
