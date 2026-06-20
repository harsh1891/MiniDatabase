package db.storage;

import java.io.*;
import java.util.*;

public class CsvFile {
    private final File file;
    private final TupleDesc schema;

    private List<Tuple> cachedTuples = null;

    public CsvFile(File file, TupleDesc schema) {
        this.file = file;
        this.schema = schema;
    }

    public File getFile() {
        return file;
    }

    public TupleDesc getTupleDesc() {
        return schema;
    }

    private synchronized void ensureCache() {
        if (cachedTuples == null) {
            cachedTuples = readAllFromFile();
        }
    }

    public Tuple readTuple(int recordId) {
        ensureCache();
        if (recordId >= 0 && recordId < cachedTuples.size()) {
            return cachedTuples.get(recordId);
        }
        return null;
    }

    public Tuple parseLine(String line) {
        String[] parts = splitCsv(line);
        Tuple t = new Tuple(schema);
        for (int i = 0; i < schema.numFields(); i++) {
            String val = (i < parts.length) ? parts[i] : "";
            Field f = schema.getFieldType(i).parse(val);
            t.setField(i, f);
        }
        return t;
    }

    private String[] splitCsv(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString().trim());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        result.add(cur.toString().trim());
        return result.toArray(new String[0]);
    }

    public List<Tuple> readAll() {
        ensureCache();
        return new ArrayList<>(cachedTuples);
    }

    private List<Tuple> readAllFromFile() {
        List<Tuple> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            int currentId = 0;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Tuple t = parseLine(line);
                t.setRecordId(currentId);
                list.add(t);
                currentId++;
            }
        } catch (IOException e) {
            throw new RuntimeException("Error reading CSV file " + file.getName(), e);
        }
        return list;
    }
}
