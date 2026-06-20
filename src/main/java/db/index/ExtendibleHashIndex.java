package db.index;

import db.storage.Field;
import java.util.*;

public class ExtendibleHashIndex implements Index {
    private static final int BUCKET_CAPACITY = 2;

    private static class Entry {
        Field key;
        List<Integer> recordIds = new ArrayList<>();

        Entry(Field key, int recordId) {
            this.key = key;
            this.recordIds.add(recordId);
        }
    }

    private static class Bucket {
        int localDepth;
        List<Entry> entries = new ArrayList<>();

        Bucket(int localDepth) {
            this.localDepth = localDepth;
        }

        boolean isFull() {
            return entries.size() >= BUCKET_CAPACITY;
        }
    }

    private int globalDepth;
    private List<Bucket> directory;

    public ExtendibleHashIndex() {
        globalDepth = 0;
        directory = new ArrayList<>();
        directory.add(new Bucket(0));
    }

    private int getIndex(Field key) {
        int hash = key.hashCode();
        if (globalDepth == 0) return 0;
        return hash & ((1 << globalDepth) - 1);
    }

    @Override
    public void insert(Field key, int recordId) {
        while (true) {
            int dirIdx = getIndex(key);
            Bucket b = directory.get(dirIdx);

            for (Entry entry : b.entries) {
                if (entry.key.equals(key)) {
                    entry.recordIds.add(recordId);
                    return;
                }
            }

            if (!b.isFull()) {
                b.entries.add(new Entry(key, recordId));
                return;
            }

            if (b.localDepth == globalDepth) {
                doubleDirectory();
            }

            splitBucket(dirIdx);
        }
    }

    private void doubleDirectory() {
        int oldSize = directory.size();
        for (int i = 0; i < oldSize; i++) {
            directory.add(directory.get(i));
        }
        globalDepth++;
    }

    private void splitBucket(int dirIdx) {
        Bucket b = directory.get(dirIdx);
        int localDepth = b.localDepth;
        Bucket b1 = new Bucket(localDepth + 1);
        Bucket b2 = new Bucket(localDepth + 1);

        int splitBit = 1 << localDepth;
        List<Entry> oldEntries = b.entries;

        for (Entry entry : oldEntries) {
            int hash = entry.key.hashCode();
            if ((hash & splitBit) == 0) {
                b1.entries.add(entry);
            } else {
                b2.entries.add(entry);
            }
        }

        for (int i = 0; i < directory.size(); i++) {
            if (directory.get(i) == b) {
                if ((i & splitBit) == 0) {
                    directory.set(i, b1);
                } else {
                    directory.set(i, b2);
                }
            }
        }
    }

    @Override
    public List<Integer> search(Field key) {
        int dirIdx = getIndex(key);
        Bucket b = directory.get(dirIdx);
        for (Entry entry : b.entries) {
            if (entry.key.equals(key)) {
                return new ArrayList<>(entry.recordIds);
            }
        }
        return new ArrayList<>();
    }

    @Override
    public List<Integer> rangeSearch(Field lower, Field upper) {
        Set<Integer> results = new TreeSet<>();
        Set<Bucket> visited = new HashSet<>();
        for (Bucket b : directory) {
            if (visited.add(b)) {
                for (Entry entry : b.entries) {
                    Field k = entry.key;
                    if ((lower == null || k.compareTo(lower) >= 0) &&
                        (upper == null || k.compareTo(upper) <= 0)) {
                        results.addAll(entry.recordIds);
                    }
                }
            }
        }
        return new ArrayList<>(results);
    }

    @Override
    public void delete(Field key, int recordId) {
        int dirIdx = getIndex(key);
        Bucket b = directory.get(dirIdx);
        Iterator<Entry> it = b.entries.iterator();
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.key.equals(key)) {
                entry.recordIds.remove(Integer.valueOf(recordId));
                if (entry.recordIds.isEmpty()) {
                    it.remove();
                }
                return;
            }
        }
    }

    @Override
    public void clear() {
        globalDepth = 0;
        directory = new ArrayList<>();
        directory.add(new Bucket(0));
    }

    public int getGlobalDepth() {
        return globalDepth;
    }
}
