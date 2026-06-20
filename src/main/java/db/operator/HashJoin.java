package db.operator;

import db.storage.*;
import java.util.*;

public class HashJoin implements DbIterator {
    private static final long serialVersionUID = 1L;

    private final DbIterator leftChild;
    private final int leftJoinFieldIndex;
    private final DbIterator rightChild;
    private final int rightJoinFieldIndex;
    private final TupleDesc td;

    private transient Map<Field, List<Tuple>> leftHashMap;
    private transient Tuple currentRightTuple;
    private transient Iterator<Tuple> leftMatchesIterator;

    public HashJoin(DbIterator leftChild, int leftJoinFieldIndex, DbIterator rightChild, int rightJoinFieldIndex) {
        this.leftChild = leftChild;
        this.leftJoinFieldIndex = leftJoinFieldIndex;
        this.rightChild = rightChild;
        this.rightJoinFieldIndex = rightJoinFieldIndex;
        this.td = TupleDesc.merge(leftChild.getTupleDesc(), rightChild.getTupleDesc());
    }

    public HashJoin(DbIterator leftChild, String leftJoinFieldName, DbIterator rightChild, String rightJoinFieldName) {
        this.leftChild = leftChild;
        this.leftJoinFieldIndex = leftChild.getTupleDesc().fieldNameToIndex(leftJoinFieldName);
        this.rightChild = rightChild;
        this.rightJoinFieldIndex = rightChild.getTupleDesc().fieldNameToIndex(rightJoinFieldName);
        this.td = TupleDesc.merge(leftChild.getTupleDesc(), rightChild.getTupleDesc());
    }

    @Override
    public void open() throws Exception {
        leftChild.open();
        rightChild.open();

        leftHashMap = new HashMap<>();
        while (leftChild.hasNext()) {
            Tuple leftTuple = leftChild.next();
            Field key = leftTuple.getField(leftJoinFieldIndex);
            leftHashMap.computeIfAbsent(key, k -> new ArrayList<>()).add(leftTuple);
        }

        currentRightTuple = null;
        leftMatchesIterator = null;
    }

    @Override
    public boolean hasNext() throws Exception {
        if (leftMatchesIterator != null && leftMatchesIterator.hasNext()) {
            return true;
        }

        while (rightChild.hasNext()) {
            currentRightTuple = rightChild.next();
            Field rightKey = currentRightTuple.getField(rightJoinFieldIndex);
            List<Tuple> matches = leftHashMap.get(rightKey);
            if (matches != null && !matches.isEmpty()) {
                leftMatchesIterator = matches.iterator();
                return true;
            }
        }

        return false;
    }

    @Override
    public Tuple next() throws Exception {
        if (!hasNext()) {
            throw new NoSuchElementException("No more matching joined tuples");
        }

        Tuple leftTuple = leftMatchesIterator.next();
        
        Tuple merged = new Tuple(td);
        int idx = 0;
        for (int i = 0; i < leftTuple.getTupleDesc().numFields(); i++) {
            merged.setField(idx++, leftTuple.getField(i));
        }
        for (int i = 0; i < currentRightTuple.getTupleDesc().numFields(); i++) {
            merged.setField(idx++, currentRightTuple.getField(i));
        }
        return merged;
    }

    @Override
    public void rewind() throws Exception {
        rightChild.rewind();
        currentRightTuple = null;
        leftMatchesIterator = null;
    }

    @Override
    public void close() throws Exception {
        leftChild.close();
        rightChild.close();
        leftHashMap = null;
        currentRightTuple = null;
        leftMatchesIterator = null;
    }

    @Override
    public TupleDesc getTupleDesc() {
        return td;
    }
}
