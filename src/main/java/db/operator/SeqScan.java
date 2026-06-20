package db.operator;

import db.Database;
import db.storage.*;
import java.util.*;

public class SeqScan implements DbIterator {
    private static final long serialVersionUID = 1L;

    private final int tableid;
    private final String tableAlias;
    private transient Iterator<Tuple> iterator;
    private transient List<Tuple> tuples;
    private transient TupleDesc aliasTd;

    public SeqScan(int tableid, String tableAlias) {
        this.tableid = tableid;
        this.tableAlias = tableAlias;
    }

    public SeqScan(int tableid) {
        this(tableid, Database.getCatalog().getTableName(tableid));
    }

    public String getAlias() {
        return tableAlias;
    }

    @Override
    public void open() throws Exception {
        CsvFile file = Database.getCatalog().getDatabaseFile(tableid);
        tuples = file.readAll();
        iterator = tuples.iterator();

        TupleDesc originalTd = file.getTupleDesc();
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
        if (iterator == null) return false;
        return iterator.hasNext();
    }

    @Override
    public Tuple next() throws Exception {
        if (iterator == null) throw new NoSuchElementException("Iterator not open");
        Tuple t = iterator.next();
        return new Tuple(aliasTd, t);
    }

    @Override
    public void rewind() throws Exception {
        if (tuples == null) {
            open();
        } else {
            iterator = tuples.iterator();
        }
    }

    @Override
    public void close() throws Exception {
        iterator = null;
        tuples = null;
        aliasTd = null;
    }

    @Override
    public TupleDesc getTupleDesc() {
        if (aliasTd == null) {
            CsvFile file = Database.getCatalog().getDatabaseFile(tableid);
            TupleDesc originalTd = file.getTupleDesc();
            Type[] types = new Type[originalTd.numFields()];
            String[] names = new String[originalTd.numFields()];
            for (int i = 0; i < originalTd.numFields(); i++) {
                types[i] = originalTd.getFieldType(i);
                names[i] = tableAlias + "." + originalTd.getFieldName(i);
            }
            aliasTd = new TupleDesc(types, names);
        }
        return aliasTd;
    }
}
