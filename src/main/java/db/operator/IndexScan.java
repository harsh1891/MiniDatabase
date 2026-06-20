package db.operator;

import db.Database;
import db.index.Index;
import db.storage.*;
import java.util.*;

public class IndexScan implements DbIterator {
    private static final long serialVersionUID = 1L;

    private final int tableid;
    private final String tableAlias;
    private final Index index;
    private final int fieldIndex;
    private final Field.Op op;
    private final Field operand;

    private transient List<Integer> recordIds;
    private transient Iterator<Integer> ridIterator;
    private transient CsvFile csvFile;
    private transient TupleDesc aliasTd;

    public IndexScan(int tableid, String tableAlias, Index index, int fieldIndex, Field.Op op, Field operand) {
        this.tableid = tableid;
        this.tableAlias = tableAlias;
        this.index = index;
        this.fieldIndex = fieldIndex;
        this.op = op;
        this.operand = operand;
    }

    public IndexScan(int tableid, Index index, int fieldIndex, Field.Op op, Field operand) {
        this(tableid, Database.getCatalog().getTableName(tableid), index, fieldIndex, op, operand);
    }

    @Override
    public void open() throws Exception {
        csvFile = Database.getCatalog().getDatabaseFile(tableid);
        
        List<Integer> rids;
        if (op == Field.Op.EQUALS) {
            rids = index.search(operand);
        } else {
            Field lower = null;
            Field upper = null;
            switch (op) {
                case GREATER_THAN:
                case GREATER_THAN_OR_EQ:
                    lower = operand;
                    break;
                case LESS_THAN:
                case LESS_THAN_OR_EQ:
                    upper = operand;
                    break;
                default:
                    break;
            }
            rids = index.rangeSearch(lower, upper);
            
            // Post-filtering for strict inequality (GREATER_THAN, LESS_THAN)
            if (op == Field.Op.GREATER_THAN || op == Field.Op.LESS_THAN) {
                List<Integer> filtered = new ArrayList<>();
                for (int rid : rids) {
                    Tuple t = csvFile.readTuple(rid);
                    if (t != null && t.getField(fieldIndex).compare(op, operand)) {
                        filtered.add(rid);
                    }
                }
                rids = filtered;
            }
        }
        
        this.recordIds = rids;
        this.ridIterator = recordIds.iterator();

        TupleDesc originalTd = csvFile.getTupleDesc();
        Type[] types = new Type[originalTd.numFields()];
        String[] names = new String[originalTd.numFields()];
        for (int i = 0; i < originalTd.numFields(); i++) {
            types[i] = originalTd.getFieldType(i);
            names[i] = tableAlias + "." + originalTd.getFieldName(i);
        }
        aliasTd = new TupleDesc(types, names);
    }

    @Override
    public boolean hasNext() throws Exception {
        return ridIterator != null && ridIterator.hasNext();
    }

    @Override
    public Tuple next() throws Exception {
        if (ridIterator == null || !ridIterator.hasNext()) {
            throw new NoSuchElementException("No more elements");
        }
        int rid = ridIterator.next();
        Tuple t = csvFile.readTuple(rid);
        if (t == null) {
            throw new NoSuchElementException("Tuple at recordId " + rid + " not found");
        }
        return new Tuple(aliasTd, t);
    }

    @Override
    public void rewind() throws Exception {
        if (recordIds == null) {
            open();
        } else {
            ridIterator = recordIds.iterator();
        }
    }

    @Override
    public void close() throws Exception {
        recordIds = null;
        ridIterator = null;
        csvFile = null;
    }

    @Override
    public TupleDesc getTupleDesc() {
        TupleDesc td = Database.getCatalog().getTupleDesc(tableid);
        Type[] types = new Type[td.numFields()];
        String[] names = new String[td.numFields()];
        for (int i = 0; i < td.numFields(); i++) {
            types[i] = td.getFieldType(i);
            names[i] = tableAlias + "." + td.getFieldName(i);
        }
        return new TupleDesc(types, names);
    }
}
