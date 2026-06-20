package db.operator;

import db.storage.*;
import java.util.NoSuchElementException;

public class Filter implements DbIterator {
    private static final long serialVersionUID = 1L;

    private final DbIterator child;
    private final int fieldIndex;
    private final Field.Op op;
    private final Field operand;
    private transient Tuple nextTuple;

    public Filter(DbIterator child, int fieldIndex, Field.Op op, Field operand) {
        this.child = child;
        this.fieldIndex = fieldIndex;
        this.op = op;
        this.operand = operand;
    }

    public Filter(DbIterator child, String fieldName, Field.Op op, Field operand) {
        this.child = child;
        this.fieldIndex = child.getTupleDesc().fieldNameToIndex(fieldName);
        this.op = op;
        this.operand = operand;
    }

    @Override
    public void open() throws Exception {
        child.open();
        nextTuple = null;
    }

    @Override
    public boolean hasNext() throws Exception {
        if (nextTuple != null) return true;
        while (child.hasNext()) {
            Tuple t = child.next();
            if (t.getField(fieldIndex).compare(op, operand)) {
                nextTuple = t;
                return true;
            }
        }
        return false;
    }

    @Override
    public Tuple next() throws Exception {
        if (!hasNext()) {
            throw new NoSuchElementException("No more matching tuples");
        }
        Tuple t = nextTuple;
        nextTuple = null;
        return t;
    }

    @Override
    public void rewind() throws Exception {
        child.rewind();
        nextTuple = null;
    }

    @Override
    public void close() throws Exception {
        child.close();
        nextTuple = null;
    }

    @Override
    public TupleDesc getTupleDesc() {
        return child.getTupleDesc();
    }
}
