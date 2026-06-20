package db.optimizer;

import db.storage.*;
import java.util.*;

public class TableStats {
    private static final int NUM_HIST_BUCKETS = 10;
    private static final Map<String, TableStats> statsMap = new HashMap<>();

    public static void setTableStats(String tableName, TableStats stats) {
        statsMap.put(tableName.toLowerCase(), stats);
    }

    public static TableStats getTableStats(String tableName) {
        return statsMap.get(tableName.toLowerCase());
    }

    public static void computeAllStats(Catalog catalog) {
        Iterator<Integer> it = catalog.tableIdIterator();
        while (it.hasNext()) {
            int tableid = it.next();
            String name = catalog.getTableName(tableid);
            CsvFile file = catalog.getDatabaseFile(tableid);
            TupleDesc td = file.getTupleDesc();
            List<Tuple> tuples = file.readAll();
            
            TableStats stats = new TableStats(tuples, td);
            setTableStats(name, stats);
        }
    }

    private final int numTuples;
    private final TupleDesc td;
    private final Map<Integer, Object> histograms = new HashMap<>();

    public TableStats(List<Tuple> tuples, TupleDesc td) {
        this.numTuples = tuples.size();
        this.td = td;
        
        for (int i = 0; i < td.numFields(); i++) {
            if (td.getFieldType(i) == Type.INT) {
                int min = Integer.MAX_VALUE;
                int max = Integer.MIN_VALUE;
                for (Tuple t : tuples) {
                    IntField f = (IntField) t.getField(i);
                    int val = f.getValueInt();
                    if (val < min) min = val;
                    if (val > max) max = val;
                }
                if (min == Integer.MAX_VALUE) {
                    min = 0;
                    max = 0;
                }
                IntHistogram hist = new IntHistogram(NUM_HIST_BUCKETS, min, max);
                for (Tuple t : tuples) {
                    IntField f = (IntField) t.getField(i);
                    hist.addValue(f.getValueInt());
                }
                histograms.put(i, hist);
            } else {
                StringHistogram hist = new StringHistogram();
                for (Tuple t : tuples) {
                    StringField f = (StringField) t.getField(i);
                    hist.addValue((String) f.getValue());
                }
                histograms.put(i, hist);
            }
        }
    }

    public double estimateSelectivity(int fieldIdx, Field.Op op, Field val) {
        if (td.getFieldType(fieldIdx) == Type.INT) {
            IntHistogram hist = (IntHistogram) histograms.get(fieldIdx);
            return hist.estimateSelectivity(op, ((IntField) val).getValueInt());
        } else {
            StringHistogram hist = (StringHistogram) histograms.get(fieldIdx);
            return hist.estimateSelectivity(op, (String) val.getValue());
        }
    }

    public int totalTuples() {
        return numTuples;
    }

    // --- Inner Class: IntHistogram ---
    public static class IntHistogram {
        private final int[] buckets;
        private final int min;
        private final int max;
        private final double bucketWidth;
        private int totalVals = 0;

        public IntHistogram(int numBuckets, int min, int max) {
            this.buckets = new int[numBuckets];
            this.min = min;
            this.max = max;
            double range = (double) max - min + 1;
            this.bucketWidth = Math.max(1.0, range / numBuckets);
        }

        public void addValue(int v) {
            if (v < min || v > max) return;
            int idx = (int) ((v - min) / bucketWidth);
            if (idx >= buckets.length) {
                idx = buckets.length - 1;
            }
            buckets[idx]++;
            totalVals++;
        }

        public double estimateSelectivity(Field.Op op, int v) {
            if (totalVals == 0) return 0.0;

            if (op == Field.Op.EQUALS) {
                if (v < min || v > max) return 0.0;
                int idx = (int) ((v - min) / bucketWidth);
                if (idx >= buckets.length) idx = buckets.length - 1;
                return (double) buckets[idx] / bucketWidth / totalVals;
            }

            if (op == Field.Op.NOT_EQUALS) {
                return 1.0 - estimateSelectivity(Field.Op.EQUALS, v);
            }

            if (op == Field.Op.GREATER_THAN || op == Field.Op.GREATER_THAN_OR_EQ) {
                if (v < min) return 1.0;
                if (v >= max) return op == Field.Op.GREATER_THAN_OR_EQ && v == max ? estimateSelectivity(Field.Op.EQUALS, v) : 0.0;
                int idx = (int) ((v - min) / bucketWidth);
                if (idx >= buckets.length) idx = buckets.length - 1;

                double sum = 0.0;
                // Fraction of the current bucket that is greater than v
                double bRight = min + (idx + 1) * bucketWidth;
                double fraction = (bRight - v) / bucketWidth;
                sum += fraction * buckets[idx];

                // Sum all subsequent buckets
                for (int i = idx + 1; i < buckets.length; i++) {
                    sum += buckets[i];
                }
                return sum / totalVals;
            }

            if (op == Field.Op.LESS_THAN || op == Field.Op.LESS_THAN_OR_EQ) {
                if (v <= min) return op == Field.Op.LESS_THAN_OR_EQ && v == min ? estimateSelectivity(Field.Op.EQUALS, v) : 0.0;
                if (v > max) return 1.0;
                int idx = (int) ((v - min) / bucketWidth);
                if (idx >= buckets.length) idx = buckets.length - 1;

                double sum = 0.0;
                // Fraction of current bucket less than v
                double bLeft = min + idx * bucketWidth;
                double fraction = (v - bLeft) / bucketWidth;
                sum += fraction * buckets[idx];

                // Sum all preceding buckets
                for (int i = 0; i < idx; i++) {
                    sum += buckets[i];
                }
                return sum / totalVals;
            }

            return 0.5; // Default fallback
        }
    }

    // --- Inner Class: StringHistogram ---
    public static class StringHistogram {
        private final Map<String, Integer> stringCounts = new HashMap<>();
        private int totalVals = 0;

        public void addValue(String s) {
            stringCounts.put(s, stringCounts.getOrDefault(s, 0) + 1);
            totalVals++;
        }

        public double estimateSelectivity(Field.Op op, String v) {
            if (totalVals == 0) return 0.0;

            if (op == Field.Op.EQUALS) {
                return (double) stringCounts.getOrDefault(v, 0) / totalVals;
            }
            if (op == Field.Op.NOT_EQUALS) {
                return 1.0 - estimateSelectivity(Field.Op.EQUALS, v);
            }
            if (op == Field.Op.LIKE) {
                double matchCount = 0;
                for (Map.Entry<String, Integer> entry : stringCounts.entrySet()) {
                    if (entry.getKey().toLowerCase().contains(v.toLowerCase())) {
                        matchCount += entry.getValue();
                    }
                }
                return matchCount / totalVals;
            }

            // For inequalities, do simple lexicographical estimation
            if (op == Field.Op.GREATER_THAN || op == Field.Op.GREATER_THAN_OR_EQ) {
                double greaterCount = 0;
                for (Map.Entry<String, Integer> entry : stringCounts.entrySet()) {
                    int cmp = entry.getKey().compareTo(v);
                    if (cmp > 0 || (op == Field.Op.GREATER_THAN_OR_EQ && cmp == 0)) {
                        greaterCount += entry.getValue();
                    }
                }
                return greaterCount / totalVals;
            }

            if (op == Field.Op.LESS_THAN || op == Field.Op.LESS_THAN_OR_EQ) {
                double lessCount = 0;
                for (Map.Entry<String, Integer> entry : stringCounts.entrySet()) {
                    int cmp = entry.getKey().compareTo(v);
                    if (cmp < 0 || (op == Field.Op.LESS_THAN_OR_EQ && cmp == 0)) {
                        lessCount += entry.getValue();
                    }
                }
                return lessCount / totalVals;
            }

            return 0.33; // Default
        }
    }
}
