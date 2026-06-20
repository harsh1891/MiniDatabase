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

            DbIterator pointSeqScan = new Filter(new SeqScan(uTableId, "u"), 0, Field.Op.EQUALS, new IntField(50000));
            DbIterator pointIndexScan = new IndexScan(uTableId, "u", idBtree, 0, Field.Op.EQUALS, new IntField(50000));

            // Warm up point queries to trigger JVM JIT compilation
            for (int i = 0; i < 100; i++) {
                pointSeqScan.open();
                while (pointSeqScan.hasNext()) pointSeqScan.next();
                pointSeqScan.close();

                pointIndexScan.open();
                while (pointIndexScan.hasNext()) pointIndexScan.next();
                pointIndexScan.close();
            }

            // Clean up heap before measuring SeqScan point query
            System.gc();
            Thread.sleep(50);

            // Benchmark SeqScan point query
            long start = System.nanoTime();
            int seqCount = 0;
            pointSeqScan.open();
            while (pointSeqScan.hasNext()) {
                pointSeqScan.next();
                seqCount++;
            }
            pointSeqScan.close();
            double seqScanTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("Without index (SeqScan + Filter):   %9.3f ms  (Rows returned: %d)", seqScanTime, seqCount));

            // Clean up heap before measuring IndexScan point query
            System.gc();
            Thread.sleep(50);

            // Benchmark IndexScan point query
            start = System.nanoTime();
            int idxCount = 0;
            pointIndexScan.open();
            while (pointIndexScan.hasNext()) {
                pointIndexScan.next();
                idxCount++;
            }
            pointIndexScan.close();
            double indexScanTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("With B+ Tree index on id:           %9.3f ms  (%.1fx faster!)", indexScanTime, seqScanTime / indexScanTime));


            // --- BENCHMARK 2: INDEX SELECTION RANGE/MULTI-POINT SPEEDUP ---
            System.out.println("\n--- 2. INDEX RANGE QUERY SPEEDUP ---");
            System.out.println("Query: SELECT name, age FROM users WHERE age = 30");

            DbIterator rangeSeqScan = new Filter(new SeqScan(uTableId, "u"), 2, Field.Op.EQUALS, new IntField(30));
            DbIterator rangeIndexScan = new IndexScan(uTableId, "u", ageBtree, 2, Field.Op.EQUALS, new IntField(30));

            // Warm up range scans
            for (int i = 0; i < 50; i++) {
                rangeSeqScan.open();
                while (rangeSeqScan.hasNext()) rangeSeqScan.next();
                rangeSeqScan.close();

                rangeIndexScan.open();
                while (rangeIndexScan.hasNext()) rangeIndexScan.next();
                rangeIndexScan.close();
            }

            // Clean up heap before measuring SeqScan range query
            System.gc();
            Thread.sleep(50);

            // Benchmark SeqScan range
            start = System.nanoTime();
            int seqRangeCount = 0;
            rangeSeqScan.open();
            while (rangeSeqScan.hasNext()) {
                rangeSeqScan.next();
                seqRangeCount++;
            }
            rangeSeqScan.close();
            double seqRangeTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("Without index (SeqScan + Filter):   %9.3f ms  (Rows returned: %d)", seqRangeTime, seqRangeCount));

            // Clean up heap before measuring IndexScan range query
            System.gc();
            Thread.sleep(50);

            // Benchmark IndexScan range
            start = System.nanoTime();
            int idxRangeCount = 0;
            rangeIndexScan.open();
            while (rangeIndexScan.hasNext()) {
                rangeIndexScan.next();
                idxRangeCount++;
            }
            rangeIndexScan.close();
            double indexRangeTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("With B+ Tree index on age:          %9.3f ms  (%.1fx faster!)", indexRangeTime, seqRangeTime / indexRangeTime));


            // --- BENCHMARK 3: JOIN & OPTIMIZATION SPEEDUP ---
            System.out.println("\n--- 3. E2E JOIN QUERY OPTIMIZATION COMPARISON ---");
            System.out.println("Query: SELECT u.name, o.item FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age = 30");

            // Plan A: Baseline (No Index, Build orders, No Pushdown)
            DbIterator naivePlan = new Filter(
                new HashJoin(new SeqScan(oTableId, "o"), 1, new SeqScan(uTableId, "u"), 0),
                5, Field.Op.EQUALS, new IntField(30)
            );

            // Plan B: Rule-Based Pushed-down (No Index, Build users (Filtered) [size ~2,000], Probe orders [size ~300,000])
            DbIterator rulePlan = new HashJoin(
                new Filter(new SeqScan(uTableId, "u"), 2, Field.Op.EQUALS, new IntField(30)), 0,
                new SeqScan(oTableId, "o"), 1
            );

            // Plan C: Cost-Based Optimizer Plan (B+ Tree Index Scan on users.age, Build users (Filtered) [size ~2,000], Probe orders)
            // We optimize this once outside the timed block to exclude regex parsing and compiler planning time.
            DbIterator optPlan = CostBasedOptimizer.optimize(
                SQLParser.parse("SELECT u.name, o.item FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age = 30")
            );

            System.out.println("\n[Visualizing Execution Plans]");
            System.out.println("--- 1. Baseline Plan Tree ---");
            printPlanTree(naivePlan, 0);
            System.out.println("--- 2. Rule-Based Plan Tree ---");
            printPlanTree(rulePlan, 0);
            System.out.println("--- 3. Cost-Based Plan Tree ---");
            printPlanTree(optPlan, 0);
            System.out.println();

            // Warm up join plans extensively to ensure stable JIT execution
            for (int i = 0; i < 20; i++) {
                naivePlan.open();
                while (naivePlan.hasNext()) naivePlan.next();
                naivePlan.close();

                rulePlan.open();
                while (rulePlan.hasNext()) rulePlan.next();
                rulePlan.close();

                optPlan.open();
                while (optPlan.hasNext()) optPlan.next();
                optPlan.close();
            }

            // Clean up heap before measuring Baseline
            System.gc();
            Thread.sleep(50);

            // Benchmark Plan A
            start = System.nanoTime();
            naivePlan.open();
            int naiveCount = 0;
            while (naivePlan.hasNext()) {
                naivePlan.next();
                naiveCount++;
            }
            naivePlan.close();
            double naiveTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("1. Baseline (No Index, Build orders, No Pushdown):   %9.3f ms  (Rows: %d)", naiveTime, naiveCount));

            // Clean up heap before measuring Rule-Based
            System.gc();
            Thread.sleep(50);

            // Benchmark Plan B
            start = System.nanoTime();
            rulePlan.open();
            int ruleCount = 0;
            while (rulePlan.hasNext()) {
                rulePlan.next();
                ruleCount++;
            }
            rulePlan.close();
            double ruleTime = (System.nanoTime() - start) / 1e6;
            System.out.println(String.format("2. Rule-Based (No Index, Pushdown + Build Users):    %9.3f ms  (%.1fx faster!)", ruleTime, naiveTime / ruleTime));

            // Clean up heap before measuring Cost-Based
            System.gc();
            Thread.sleep(50);

            // Benchmark Plan C
            start = System.nanoTime();
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

    private static void printPlanTree(DbIterator plan, int indent) {
        String ind = "  ".repeat(indent);
        String name = plan.getClass().getSimpleName();
        String details = "";

        try {
            if (plan.getClass().getSimpleName().equals("Filter")) {
                var fIdxField = plan.getClass().getDeclaredField("fieldIndex");
                fIdxField.setAccessible(true);
                int fIdx = (int) fIdxField.get(plan);
                
                var opField = plan.getClass().getDeclaredField("op");
                opField.setAccessible(true);
                Object op = opField.get(plan);
                
                var operandField = plan.getClass().getDeclaredField("operand");
                operandField.setAccessible(true);
                Object operand = operandField.get(plan);

                String colName = plan.getTupleDesc().getFieldName(fIdx);
                details = String.format(" [Predicate: %s %s %s]", colName, op, operand);
            } 
            else if (plan.getClass().getSimpleName().equals("IndexScan")) {
                var indexField = plan.getClass().getDeclaredField("index");
                indexField.setAccessible(true);
                Object index = indexField.get(plan);
                
                var fIdxField = plan.getClass().getDeclaredField("fieldIndex");
                fIdxField.setAccessible(true);
                int fIdx = (int) fIdxField.get(plan);
                
                var opField = plan.getClass().getDeclaredField("op");
                opField.setAccessible(true);
                Object op = opField.get(plan);
                
                var operandField = plan.getClass().getDeclaredField("operand");
                operandField.setAccessible(true);
                Object operand = operandField.get(plan);

                String colName = plan.getTupleDesc().getFieldName(fIdx);
                details = String.format(" [Index: %s on %s, Predicate: %s %s %s]", 
                    index.getClass().getSimpleName(), colName, colName, op, operand);
            }
            else if (plan.getClass().getSimpleName().equals("SeqScan")) {
                var aliasField = plan.getClass().getDeclaredField("tableAlias");
                aliasField.setAccessible(true);
                String alias = (String) aliasField.get(plan);
                details = String.format(" [Table/Alias: %s]", alias);
            }
            else if (plan.getClass().getSimpleName().equals("HashJoin")) {
                var leftIdxField = plan.getClass().getDeclaredField("leftJoinFieldIndex");
                leftIdxField.setAccessible(true);
                int leftIdx = (int) leftIdxField.get(plan);
                
                var rightIdxField = plan.getClass().getDeclaredField("rightJoinFieldIndex");
                rightIdxField.setAccessible(true);
                int rightIdx = (int) rightIdxField.get(plan);

                var leftChildField = plan.getClass().getDeclaredField("leftChild");
                leftChildField.setAccessible(true);
                DbIterator leftChild = (DbIterator) leftChildField.get(plan);
                
                var rightChildField = plan.getClass().getDeclaredField("rightChild");
                rightChildField.setAccessible(true);
                DbIterator rightChild = (DbIterator) rightChildField.get(plan);

                String leftCol = leftChild.getTupleDesc().getFieldName(leftIdx);
                String rightCol = rightChild.getTupleDesc().getFieldName(rightIdx);

                details = String.format(" [ON %s = %s]", leftCol, rightCol);
            }
            else if (plan.getClass().getSimpleName().equals("Projection")) {
                details = String.format(" [Output: %s]", plan.getTupleDesc());
            }
        } catch (Exception e) {
            // Fallback
        }

        System.out.println(ind + "+- " + name + details);
        
        try {
            java.lang.reflect.Field childField = plan.getClass().getDeclaredField("child");
            childField.setAccessible(true);
            DbIterator child = (DbIterator) childField.get(plan);
            if (child != null) {
                printPlanTree(child, indent + 1);
            }
        } catch (Exception e) {
            try {
                java.lang.reflect.Field leftField = plan.getClass().getDeclaredField("leftChild");
                java.lang.reflect.Field rightField = plan.getClass().getDeclaredField("rightChild");
                leftField.setAccessible(true);
                rightField.setAccessible(true);
                DbIterator left = (DbIterator) leftField.get(plan);
                DbIterator right = (DbIterator) rightField.get(plan);
                if (left != null) printPlanTree(left, indent + 1);
                if (right != null) printPlanTree(right, indent + 1);
            } catch (Exception ex) {
                // Leaf node
            }
        }
    }
}
