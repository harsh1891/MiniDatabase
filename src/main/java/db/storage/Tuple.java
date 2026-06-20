package db.storage;

import java.io.Serializable;

public class Tuple implements Serializable {
    private static final long serialVersionUID = 1L;

    private TupleDesc schema;
    private final Field[] fields;
    private int recordId; // Optional record ID (e.g. index/row number in CSV file)

    public Tuple(TupleDesc td) {
        this.schema = td;
        this.fields = new Field[td.numFields()];
        this.recordId = -1;
    }

    /**
     * Shallow copy constructor that shares the fields array.
     * Used for zero-allocation aliasing and schema mapping in execution operators.
     */
    public Tuple(TupleDesc td, Tuple other) {
        this.schema = td;
        this.fields = other.fields;
        this.recordId = other.recordId;
    }


    public TupleDesc getTupleDesc() {
        return schema;
    }

    public void setTupleDesc(TupleDesc td) {
        this.schema = td;
    }

    public void setField(int i, Field f) {
        if (i < 0 || i >= fields.length) {
            throw new IndexOutOfBoundsException("Invalid field index " + i);
        }
        fields[i] = f;
    }

    public Field getField(int i) {
        if (i < 0 || i >= fields.length) {
            throw new IndexOutOfBoundsException("Invalid field index " + i);
        }
        return fields[i];
    }

    public int getRecordId() {
        return recordId;
    }

    public void setRecordId(int id) {
        this.recordId = id;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            sb.append(fields[i] == null ? "null" : fields[i].toString());
            if (i < fields.length - 1) {
                sb.append("\t");
            }
        }
        return sb.toString();
    }
}
