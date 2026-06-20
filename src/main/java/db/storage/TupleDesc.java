package db.storage;

import java.io.Serializable;
import java.util.Arrays;
import java.util.NoSuchElementException;

public class TupleDesc implements Serializable {
    private static final long serialVersionUID = 1L;

    public static class TDItem implements Serializable {
        private static final long serialVersionUID = 1L;
        public final Type fieldType;
        public final String fieldName;

        public TDItem(Type t, String n) {
            this.fieldType = t;
            this.fieldName = n;
        }

        public String toString() {
            return fieldName + "(" + fieldType + ")";
        }
    }

    private final TDItem[] items;

    public TupleDesc(Type[] typeAr, String[] fieldAr) {
        if (typeAr == null || typeAr.length == 0) {
            throw new IllegalArgumentException("Type array cannot be null or empty");
        }
        items = new TDItem[typeAr.length];
        for (int i = 0; i < typeAr.length; i++) {
            String name = (fieldAr != null && fieldAr.length > i && fieldAr[i] != null) ? fieldAr[i] : "f" + i;
            items[i] = new TDItem(typeAr[i], name);
        }
    }

    public TupleDesc(Type[] typeAr) {
        this(typeAr, new String[0]);
    }

    public int numFields() {
        return items.length;
    }

    public String getFieldName(int i) {
        if (i < 0 || i >= items.length) {
            throw new IndexOutOfBoundsException("Invalid field index " + i);
        }
        return items[i].fieldName;
    }

    public Type getFieldType(int i) {
        if (i < 0 || i >= items.length) {
            throw new IndexOutOfBoundsException("Invalid field index " + i);
        }
        return items[i].fieldType;
    }

    public int fieldNameToIndex(String name) {
        if (name == null) {
            throw new NoSuchElementException("Field name cannot be null");
        }
        // Match either simple name or table-qualified name (e.g. table.col)
        for (int i = 0; i < items.length; i++) {
            if (items[i].fieldName.equalsIgnoreCase(name)) {
                return i;
            }
            // Strip table name if dot present
            int dotIdx = items[i].fieldName.indexOf('.');
            if (dotIdx != -1) {
                String baseName = items[i].fieldName.substring(dotIdx + 1);
                if (baseName.equalsIgnoreCase(name)) {
                    return i;
                }
            }
        }
        // Search if input has a dot but items don't
        int inputDotIdx = name.indexOf('.');
        if (inputDotIdx != -1) {
            String baseInputName = name.substring(inputDotIdx + 1);
            for (int i = 0; i < items.length; i++) {
                if (items[i].fieldName.equalsIgnoreCase(baseInputName)) {
                    return i;
                }
            }
        }
        throw new NoSuchElementException("Field name " + name + " not found");
    }

    public int getSize() {
        int size = 0;
        for (TDItem item : items) {
            size += item.fieldType.getLen();
        }
        return size;
    }

    public static TupleDesc merge(TupleDesc td1, TupleDesc td2) {
        Type[] mergedTypes = new Type[td1.numFields() + td2.numFields()];
        String[] mergedNames = new String[td1.numFields() + td2.numFields()];
        int idx = 0;
        for (int i = 0; i < td1.numFields(); i++) {
            mergedTypes[idx] = td1.getFieldType(i);
            mergedNames[idx] = td1.getFieldName(i);
            idx++;
        }
        for (int i = 0; i < td2.numFields(); i++) {
            mergedTypes[idx] = td2.getFieldType(i);
            mergedNames[idx] = td2.getFieldName(i);
            idx++;
        }
        return new TupleDesc(mergedTypes, mergedNames);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TupleDesc)) return false;
        TupleDesc other = (TupleDesc) o;
        if (this.numFields() != other.numFields()) return false;
        for (int i = 0; i < numFields(); i++) {
            if (this.getFieldType(i) != other.getFieldType(i)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(items);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            sb.append(items[i].toString());
            if (i < items.length - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }
}
