package db.index;

import db.storage.Field;
import java.util.*;

public class BitmapIndex implements Index {
    private final Map<Field, BitSet> bitmaps = new HashMap<>();

    public BitmapIndex() {}

    @Override
    public void insert(Field key, int recordId) {
        BitSet bs = bitmaps.computeIfAbsent(key, k -> new BitSet());
        bs.set(recordId);
    }

    @Override
    public List<Integer> search(Field key) {
        List<Integer> result = new ArrayList<>();
        BitSet bs = bitmaps.get(key);
        if (bs != null) {
            for (int i = bs.nextSetBit(0); i >= 0; i = bs.nextSetBit(i + 1)) {
                result.add(i);
            }
        }
        return result;
    }

    @Override
    public List<Integer> rangeSearch(Field lower, Field upper) {
        BitSet combined = new BitSet();
        for (Map.Entry<Field, BitSet> entry : bitmaps.entrySet()) {
            Field k = entry.getKey();
            if ((lower == null || k.compareTo(lower) >= 0) &&
                (upper == null || k.compareTo(upper) <= 0)) {
                combined.or(entry.getValue());
            }
        }
        List<Integer> result = new ArrayList<>();
        for (int i = combined.nextSetBit(0); i >= 0; i = combined.nextSetBit(i + 1)) {
            result.add(i);
        }
        return result;
    }

    @Override
    public void delete(Field key, int recordId) {
        BitSet bs = bitmaps.get(key);
        if (bs != null) {
            bs.clear(recordId);
            if (bs.isEmpty()) {
                bitmaps.remove(key);
            }
        }
    }

    @Override
    public void clear() {
        bitmaps.clear();
    }

    public BitSet getBitmap(Field key) {
        return bitmaps.get(key);
    }
}
