package db.operator;

import db.storage.*;
import java.util.*;

public class Projection implements DbIterator {
    private static final long serialVersionUID = 1L;

    private final DbIterator child;
    private final int[] fieldIndices;
    private final TupleDesc td;

    public Projection(DbIterator child, List<String> projectFields) {
        this.child = child;
        TupleDesc childTd = child.getTupleDesc();
        this.fieldIndices = new int[projectFields.size()];
        Type[] types = new Type[projectFields.size()];
        String[] names = new String[projectFields.size()];

        for (int i = 0; i < projectFields.size(); i++) {
            String name = projectFields.get(i);
            int idx = childTd.fieldNameToIndex(name);
            fieldIndices[i] = idx;
            types[i] = childTd.getFieldType(idx);
            names[i] = childTd.getFieldName(idx);
        }
        this.td = new TupleDesc(types, names);
    }

    public Projection(DbIterator child, int[] fieldIndices) {
        this.child = child;
        this.fieldIndices = fieldIndices;
        TupleDesc childTd = child.getTupleDesc();
        Type[] types = new Type[fieldIndices.length];
        String[] names = new String[fieldIndices.length];
        for (int i = 0; i < fieldIndices.length; i++) {
            types[i] = childTd.getFieldType(fieldIndices[i]);
            names[i] = childTd.getFieldName(fieldIndices[i]);
        }
        this.td = new TupleDesc(types, names);
    }

    @Override
    public void open() throws Exception {
        child.open();
    }

    @Override
    public boolean hasNext() throws Exception {
        return child.hasNext();
    }

    @Override
    public Tuple next() throws Exception {
        Tuple t = child.next();
        Tuple projected = new Tuple(td);
        projected.setRecordId(t.getRecordId());
        for (int i = 0; i < fieldIndices.length; i++) {
            projected.setField(i, t.getField(fieldIndices[i]));
        }
        return projected;
    }

    @Override
    public void rewind() throws Exception {
        child.rewind();
    }

    @Override
    public void close() throws Exception {
        child.close();
    }

    @Override
    public TupleDesc getTupleDesc() {
        return td;
    }
}
