package db;

import db.index.BPlusTreeIndex;
import db.operator.*;
import db.optimizer.CostBasedOptimizer;
import db.optimizer.TableStats;
import db.parser.SQLParser;
import db.storage.*;
import java.io.File;
import java.util.*;

public class Benchmark {
    public static void main(String[] args) {
        System.out.println("\u001B[36m====================================================\u001B[0m");
        System.out.println("\u001B[36m*             DATABASE ENGINE BENCHMARK            *\u001B[0m");
        System.out.println("\u001B[36m====================================================\u001B[0m");

        File usersFile = new File("data/users.csv");
        File ordersFile = new File("data/orders.csv");

        if (!usersFile.exists() || !ordersFile.exists()) {
            System.err.println("Mock data files not found. Please run ./build.ps1 -generate 100000 first.");
            System.exit(1);
        }

        try {
            Catalog catalog = Database.getCatalog();
            TupleDesc uTd = new TupleDesc(new Type[]{Type.INT, Type.STRING, Type.INT}, new String[]{"id", "name", "age"});
            TupleDesc oTd = new TupleDesc(new Type[]{Type.INT, Type.INT, Type.STRING}, new String[]{"id", "user_id", "item"});

            catalog.addTable(new CsvFile(usersFile, uTd), "users");
            catalog.addTable(new CsvFile(ordersFile, oTd), "orders");
            
            int uTableId = catalog.getTableId("users");
            int oTableId = catalog.getTableId("orders");

            List<Tuple> uTuples = catalog.getDatabaseFile(uTableId).readAll();

            // Build index for users.id
            System.out.println("Building B+ Tree Index on users.id...");
            BPlusTreeIndex idBtree = new BPlusTreeIndex();
            for (Tuple t : uTuples) {
                idBtree.insert(t.getField(0), t.getRecordId());
            }
            catalog.addIndex(uTableId, "id", idBtree);

            // Build index for users.age
            System.out.println("Building B+ Tree Index on users.age...");
            BPlusTreeIndex ageBtree = new BPlusTreeIndex();
            for (Tuple t : uTuples) {
                ageBtree.insert(t.getField(2), t.getRecordId());
            }
            catalog.addIndex(uTableId, "age", ageBtree);

            System.out.println("Computing optimizer statistics...");
            TableStats.computeAllStats(catalog);

            // --- BENCHMARK 1: POINT QUERY SPEEDUP ---
            System.out.println("\n--- 1. POINT QUERY SPEEDUP ---");
            System.out.println("Query: SELECT name, age FROM users WHERE id = 50000");

            // Warm up point queries
            for (int i = 0; i < 50; i++) {
                DbIterator plan = new Filter(new SeqScan(uTableId, "u"), 0, Field.Op.EQUALS, new IntField(50000));
                plan.open();
                while (plan.hasNext()) plan.next();
                plan.close();

                DbIterator planIdx = new IndexScan(uTableId, "u", idBtree, 0, Field.Op.EQUALS, new IntField(50000));
                planIdx.open();
                while (planIdx.hasNext()) planIdx.next();
                planIdx.close();
            }

            // Benchmark SeqScan point query
            long start = System.nanoTime();
            int seqCount = 0;
            DbIterator seqScanPlan = new Filter(new SeqScan(uTableId, "u"), 0, Field.Op.EQUALS, new IntField(50000));
            seqScanPlan.open();
            while (seqScanPlan.hasNext()) {
                seqScanPlan.next();
                seqCount++;
            }
            seqScanPlan.close();
            double seqScanTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("Without index (SeqScan + Filter):   %9.3f ms  (Rows returned: %d)", seqScanTime, seqCount));

            // Benchmark IndexScan point query
            start = System.nanoTime();
            int idxCount = 0;
            DbIterator indexScanPlan = new IndexScan(uTableId, "u", idBtree, 0, Field.Op.EQUALS, new IntField(50000));
            indexScanPlan.open();
            while (indexScanPlan.hasNext()) {
                indexScanPlan.next();
                idxCount++;
            }
            indexScanPlan.close();
            double indexScanTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("With B+ Tree index on id:           %9.3f ms  (%.1fx faster!)", indexScanTime, seqScanTime / indexScanTime));


            // --- BENCHMARK 2: INDEX SELECTION RANGE/MULTI-POINT SPEEDUP ---
            System.out.println("\n--- 2. INDEX RANGE QUERY SPEEDUP ---");
            System.out.println("Query: SELECT name, age FROM users WHERE age = 30");

            // Warm up
            for (int i = 0; i < 30; i++) {
                DbIterator plan = new Filter(new SeqScan(uTableId, "u"), 2, Field.Op.EQUALS, new IntField(30));
                plan.open();
                while (plan.hasNext()) plan.next();
                plan.close();

                DbIterator planIdx = new IndexScan(uTableId, "u", ageBtree, 2, Field.Op.EQUALS, new IntField(30));
                planIdx.open();
                while (planIdx.hasNext()) planIdx.next();
                planIdx.close();
            }

            // Benchmark SeqScan range
            start = System.nanoTime();
            int seqRangeCount = 0;
            DbIterator seqScanRange = new Filter(new SeqScan(uTableId, "u"), 2, Field.Op.EQUALS, new IntField(30));
            seqScanRange.open();
            while (seqScanRange.hasNext()) {
                seqScanRange.next();
                seqRangeCount++;
            }
            seqScanRange.close();
            double seqRangeTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("Without index (SeqScan + Filter):   %9.3f ms  (Rows returned: %d)", seqRangeTime, seqRangeCount));

            // Benchmark IndexScan range
            start = System.nanoTime();
            int idxRangeCount = 0;
            DbIterator indexScanRange = new IndexScan(uTableId, "u", ageBtree, 2, Field.Op.EQUALS, new IntField(30));
            indexScanRange.open();
            while (indexScanRange.hasNext()) {
                indexScanRange.next();
                idxRangeCount++;
            }
            indexScanRange.close();
            double indexRangeTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("With B+ Tree index on age:          %9.3f ms  (%.1fx faster!)", indexRangeTime, seqRangeTime / indexRangeTime));


            // --- BENCHMARK 3: JOIN & OPTIMIZATION SPEEDUP ---
            System.out.println("\n--- 3. E2E JOIN QUERY OPTIMIZATION COMPARISON ---");
            System.out.println("Query: SELECT u.name, o.item FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age = 30");

            // Option A: Unoptimized (No Index, Naive Join Order, No Filter Pushdown)
            // Filter(HashJoin(orders JOIN users) on user_id=id, users.age=30)
            // Warm up
            for (int i = 0; i < 5; i++) {
                DbIterator plan = new Filter(
                    new HashJoin(new SeqScan(oTableId, "o"), 1, new SeqScan(uTableId, "u"), 0),
                    5, Field.Op.EQUALS, new IntField(30)
                );
                plan.open();
                while (plan.hasNext()) plan.next();
                plan.close();
            }

            start = System.nanoTime();
            DbIterator naivePlan = new Filter(
                new HashJoin(new SeqScan(oTableId, "o"), 1, new SeqScan(uTableId, "u"), 0),
                5, Field.Op.EQUALS, new IntField(30)
            );
            naivePlan.open();
            int naiveCount = 0;
            while (naivePlan.hasNext()) {
                naivePlan.next();
                naiveCount++;
            }
            naivePlan.close();
            double naiveTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("1. Baseline (No Index, Build orders, No Pushdown):   %9.3f ms  (Rows: %d)", naiveTime, naiveCount));

            // Option B: Rule-Based Pushed-down (No Index, Build users (Filtered) [size ~2,000], Probe orders [size ~300,000])
            // HashJoin(Filter(users, age=30) JOIN orders) on id=user_id
            // Warm up
            for (int i = 0; i < 5; i++) {
                DbIterator plan = new HashJoin(
                    new Filter(new SeqScan(uTableId, "u"), 2, Field.Op.EQUALS, new IntField(30)), 0,
                    new SeqScan(oTableId, "o"), 1
                );
                plan.open();
                while (plan.hasNext()) plan.next();
                plan.close();
            }

            start = System.nanoTime();
            DbIterator rulePlan = new HashJoin(
                new Filter(new SeqScan(uTableId, "u"), 2, Field.Op.EQUALS, new IntField(30)), 0,
                new SeqScan(oTableId, "o"), 1
            );
            rulePlan.open();
            int ruleCount = 0;
            while (rulePlan.hasNext()) {
                rulePlan.next();
                ruleCount++;
            }
            rulePlan.close();
            double ruleTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("2. Rule-Based (No Index, Pushdown + Build Users):    %9.3f ms  (%.1fx faster!)", ruleTime, naiveTime / ruleTime));

            // Option C: Cost-Based Optimizer Enabled (B+ Tree Index Scan on users.age, Build users (Filtered) [size ~2,000], Probe orders)
            // Warm up
            for (int i = 0; i < 5; i++) {
                DbIterator plan = CostBasedOptimizer.optimize(
                    SQLParser.parse("SELECT u.name, o.item FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age = 30")
                );
                plan.open();
                while (plan.hasNext()) plan.next();
                plan.close();
            }

            start = System.nanoTime();
            DbIterator optPlan = CostBasedOptimizer.optimize(
                SQLParser.parse("SELECT u.name, o.item FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age = 30")
            );
            optPlan.open();
            int optCount = 0;
            while (optPlan.hasNext()) {
                optPlan.next();
                optCount++;
            }
            optPlan.close();
            double optTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("3. Cost-Based (B+ Tree Index + Build Users):          %9.3f ms  (%.1fx faster than Baseline, %.1fx faster than Rule-Based)", 
                optTime, naiveTime / optTime, ruleTime / optTime));

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
