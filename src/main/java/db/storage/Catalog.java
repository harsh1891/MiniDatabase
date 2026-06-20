package db.storage;

import db.index.Index;
import java.util.*;

public class Catalog {
    private static class Table {
        final CsvFile file;
        final String name;
        final String pkeyField;

        Table(CsvFile file, String name, String pkeyField) {
            this.file = file;
            this.name = name;
            this.pkeyField = pkeyField;
        }
    }

    private final Map<Integer, Table> tablesById = new HashMap<>();
    private final Map<String, Integer> idByName = new HashMap<>();
    private final Map<Integer, Map<String, Index>> indexes = new HashMap<>();

    public Catalog() {}

    public void addIndex(int tableid, String columnName, Index index) {
        indexes.computeIfAbsent(tableid, k -> new HashMap<>()).put(columnName.toLowerCase(), index);
    }

    public Index getIndex(int tableid, String columnName) {
        Map<String, Index> cols = indexes.get(tableid);
        if (cols == null) return null;
        return cols.get(columnName.toLowerCase());
    }

    public void addTable(CsvFile file, String name, String pkeyField) {
        int id = name.toLowerCase().hashCode();
        tablesById.put(id, new Table(file, name, pkeyField));
        idByName.put(name.toLowerCase(), id);
    }

    public void addTable(CsvFile file, String name) {
        addTable(file, name, "");
    }

    public int getTableId(String name) {
        if (name == null) {
            throw new NoSuchElementException("Table name cannot be null");
        }
        Integer id = idByName.get(name.toLowerCase());
        if (id == null) {
            throw new NoSuchElementException("Table " + name + " not found");
        }
        return id;
    }

    public TupleDesc getTupleDesc(int tableid) {
        Table t = tablesById.get(tableid);
        if (t == null) {
            throw new NoSuchElementException("Table with id " + tableid + " not found");
        }
        return t.file.getTupleDesc();
    }

    public CsvFile getDatabaseFile(int tableid) {
        Table t = tablesById.get(tableid);
        if (t == null) {
            throw new NoSuchElementException("Table with id " + tableid + " not found");
        }
        return t.file;
    }

    public String getPrimaryKey(int tableid) {
        Table t = tablesById.get(tableid);
        if (t == null) {
            throw new NoSuchElementException("Table with id " + tableid + " not found");
        }
        return t.pkeyField;
    }

    public String getTableName(int tableid) {
        Table t = tablesById.get(tableid);
        if (t == null) {
            throw new NoSuchElementException("Table with id " + tableid + " not found");
        }
        return t.name;
    }

    public Iterator<Integer> tableIdIterator() {
        return tablesById.keySet().iterator();
    }

    public void clear() {
        tablesById.clear();
        idByName.clear();
    }
}
