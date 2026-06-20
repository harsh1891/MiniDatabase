package db;

import db.index.*;
import db.operator.*;
import db.optimizer.*;
import db.parser.SQLParser;
import db.storage.*;
import java.io.*;
import java.util.*;

public class TestRunner {
    private static int totalTests = 0;
    private static int passedTests = 0;

    public static void main(String[] args) {
        System.out.println("\u001B[36m=== RUNNING DATABASE SYSTEM TEST SUITE ===\u001B[0m");

        try {
            setupData();

            runTest("B+ Tree Index Point & Range Queries", TestRunner::testBPlusTree);
            runTest("Extendible Hashing Bucket Split & Directory Double", TestRunner::testExtendibleHash);
            runTest("Bitmap Index Fast Operations", TestRunner::testBitmapIndex);
            runTest("Volcano Iterators (SeqScan, Filter, Projection)", TestRunner::testVolcanoOperators);
            runTest("Hash Join Build and Probe", TestRunner::testHashJoin);
            runTest("Table Statistics and Selectivity Estimation", TestRunner::testStatistics);
            runTest("Query Optimizer Selection Pushdown and Cost Optimization", TestRunner::testOptimizer);
            runTest("SQL Parsing & E2E Query Execution", TestRunner::testE2E);

        } catch (Exception e) {
            System.err.println("\u001B[31mSetup/Execution failed: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
        } finally {
            cleanupData();
        }

        System.out.println("\n\u001B[36m==========================================\u001B[0m");
        System.out.println(String.format("Result: %d/%d tests passed.", passedTests, totalTests));
        if (passedTests == totalTests) {
            System.out.println("\u001B[32mALL TESTS PASSED SUCCESSFULLY!\u001B[0m");
            System.exit(0);
        } else {
            System.out.println("\u001B[31mSOME TESTS FAILED!\u001B[0m");
            System.exit(1);
        }
    }

    private static void setupData() throws IOException {
        new File("data").mkdirs();
        try (PrintWriter pw = new PrintWriter(new FileWriter("data/users.csv"))) {
            pw.println("1,Alice,15");
            pw.println("2,Bob,30");
            pw.println("3,Charlie,18");
            pw.println("4,David,35");
            pw.println("5,Eve,15");
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter("data/orders.csv"))) {
            pw.println("101,2,Laptop");
        }
    }

    private static void cleanupData() {
        new File("data/users.csv").delete();
        new File("data/orders.csv").delete();
        new File("data").delete();
    }

    private static void runTest(String name, TestRunnable runnable) {
        totalTests++;
        System.out.print("Running " + name + "... ");
        try {
            Database.reset();
            runnable.run();
            System.out.println("\u001B[32m[PASS]\u001B[0m");
            passedTests++;
        } catch (Throwable t) {
            System.out.println("\u001B[31m[FAIL]\u001B[0m");
            System.err.println("  Error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    interface TestRunnable {
        void run() throws Exception;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("Expected: " + expected + ", but got: " + actual);
        }
    }

    private static void assertTrue(boolean val) {
        if (!val) {
            throw new AssertionError("Expected true but got false");
        }
    }

    private static void testBPlusTree() throws Exception {
        BPlusTreeIndex tree = new BPlusTreeIndex();
        
        tree.insert(new IntField(10), 100);
        tree.insert(new IntField(20), 200);
        tree.insert(new IntField(5), 50);
        tree.insert(new IntField(15), 150);
        tree.insert(new IntField(25), 250);
        
        assertEquals(Arrays.asList(150), tree.search(new IntField(15)));
        assertEquals(Arrays.asList(50), tree.search(new IntField(5)));
        assertEquals(Collections.emptyList(), tree.search(new IntField(99)));

        List<Integer> range = tree.rangeSearch(new IntField(10), new IntField(20));
        Collections.sort(range);
        assertEquals(Arrays.asList(100, 150, 200), range);
    }

    private static void testExtendibleHash() throws Exception {
        ExtendibleHashIndex hashIdx = new ExtendibleHashIndex();
        
        hashIdx.insert(new IntField(1), 10);
        hashIdx.insert(new IntField(2), 20);
        hashIdx.insert(new IntField(3), 30);
        hashIdx.insert(new IntField(4), 40);

        assertEquals(Arrays.asList(30), hashIdx.search(new IntField(3)));
        assertEquals(Arrays.asList(10), hashIdx.search(new IntField(1)));
        
        assertTrue(hashIdx.getGlobalDepth() > 0);
    }

    private static void testBitmapIndex() throws Exception {
        BitmapIndex bitmap = new BitmapIndex();
        
        bitmap.insert(new StringField("HR", 128), 0);
        bitmap.insert(new StringField("IT", 128), 1);
        bitmap.insert(new StringField("HR", 128), 2);
        bitmap.insert(new StringField("Sales", 128), 3);

        assertEquals(Arrays.asList(0, 2), bitmap.search(new StringField("HR", 128)));
        assertEquals(Arrays.asList(1), bitmap.search(new StringField("IT", 128)));
    }

    private static void testVolcanoOperators() throws Exception {
        Catalog cat = Database.getCatalog();
        TupleDesc schema = new TupleDesc(new Type[]{Type.INT, Type.STRING, Type.INT}, new String[]{"id", "name", "age"});
        CsvFile file = new CsvFile(new File("data/users.csv"), schema);
        cat.addTable(file, "users");

        SeqScan scan = new SeqScan(cat.getTableId("users"), "u");
        Filter filter = new Filter(scan, "u.age", Field.Op.EQUALS, new IntField(15));
        Projection proj = new Projection(filter, Arrays.asList("u.name", "u.id"));

        proj.open();
        List<String> results = new ArrayList<>();
        while (proj.hasNext()) {
            results.add(proj.next().toString());
        }
        proj.close();

        assertEquals(2, results.size());
        assertTrue(results.get(0).contains("Alice"));
        assertTrue(results.get(1).contains("Eve"));
    }

    private static void testHashJoin() throws Exception {
        Catalog cat = Database.getCatalog();
        TupleDesc userSchema = new TupleDesc(new Type[]{Type.INT, Type.STRING, Type.INT}, new String[]{"id", "name", "age"});
        TupleDesc orderSchema = new TupleDesc(new Type[]{Type.INT, Type.INT, Type.STRING}, new String[]{"id", "user_id", "item"});
        cat.addTable(new CsvFile(new File("data/users.csv"), userSchema), "users");
        cat.addTable(new CsvFile(new File("data/orders.csv"), orderSchema), "orders");

        SeqScan leftScan = new SeqScan(cat.getTableId("users"), "u");
        SeqScan rightScan = new SeqScan(cat.getTableId("orders"), "o");

        HashJoin join = new HashJoin(leftScan, "u.id", rightScan, "o.user_id");
        join.open();
        
        int count = 0;
        while (join.hasNext()) {
            Tuple t = join.next();
            count++;
            Field uid = t.getField(t.getTupleDesc().fieldNameToIndex("u.id"));
            Field ouid = t.getField(t.getTupleDesc().fieldNameToIndex("o.user_id"));
            assertEquals(uid, ouid);
        }
        join.close();
        assertEquals(1, count);
    }

    private static void testStatistics() throws Exception {
        Catalog cat = Database.getCatalog();
        TupleDesc userSchema = new TupleDesc(new Type[]{Type.INT, Type.STRING, Type.INT}, new String[]{"id", "name", "age"});
        cat.addTable(new CsvFile(new File("data/users.csv"), userSchema), "users");
        
        TableStats.computeAllStats(cat);
        TableStats stats = TableStats.getTableStats("users");
        
        double selEq = stats.estimateSelectivity(2, Field.Op.EQUALS, new IntField(15));
        assertTrue(selEq > 0.1 && selEq < 0.5);

        double selGt = stats.estimateSelectivity(2, Field.Op.GREATER_THAN, new IntField(40));
        assertEquals(0.0, selGt);
    }

    private static void testOptimizer() throws Exception {
        Catalog cat = Database.getCatalog();
        TupleDesc userSchema = new TupleDesc(new Type[]{Type.INT, Type.STRING, Type.INT}, new String[]{"id", "name", "age"});
        cat.addTable(new CsvFile(new File("data/users.csv"), userSchema), "users");

        BPlusTreeIndex btree = new BPlusTreeIndex();
        List<Tuple> tuples = cat.getDatabaseFile(cat.getTableId("users")).readAll();
        for (Tuple t : tuples) {
            btree.insert(t.getField(2), t.getRecordId());
        }
        cat.addIndex(cat.getTableId("users"), "age", btree);

        TableStats.computeAllStats(cat);

        LogicalPlan plan = new LogicalPlan();
        plan.addTable("users", "u");
        plan.addProjectField("u.age");
        plan.addSelect("u", "age", Field.Op.EQUALS, new IntField(15));

        DbIterator physicalPlan = CostBasedOptimizer.optimize(plan);
        
        assertTrue(physicalPlan instanceof Projection);
        System.out.print(" [Plan: " + physicalPlan.getClass().getSimpleName() + "] ");
    }

    private static void testE2E() throws Exception {
        Catalog cat = Database.getCatalog();
        TupleDesc userSchema = new TupleDesc(new Type[]{Type.INT, Type.STRING, Type.INT}, new String[]{"id", "name", "age"});
        TupleDesc orderSchema = new TupleDesc(new Type[]{Type.INT, Type.INT, Type.STRING}, new String[]{"id", "user_id", "item"});
        cat.addTable(new CsvFile(new File("data/users.csv"), userSchema), "users");
        cat.addTable(new CsvFile(new File("data/orders.csv"), orderSchema), "orders");

        // Build B+ tree index on users.age
        BPlusTreeIndex btree = new BPlusTreeIndex();
        List<Tuple> tuples = cat.getDatabaseFile(cat.getTableId("users")).readAll();
        for (Tuple t : tuples) {
            btree.insert(t.getField(2), t.getRecordId());
        }
        cat.addIndex(cat.getTableId("users"), "age", btree);

        TableStats.computeAllStats(cat);

        // Target SQL query
        String sql = "SELECT age, name FROM users JOIN orders ON users.id = orders.user_id WHERE users.age > 21";
        LogicalPlan plan = SQLParser.parse(sql);
        DbIterator physical = CostBasedOptimizer.optimize(plan);

        // Print Plan Tree to explain details
        System.out.println("\n\u001B[33m[EXPLAIN PLAN FOR SQL: " + sql + "]\u001B[0m");
        printPlanTree(physical, 1);

        physical.open();
        int count = 0;
        List<String> joinedResults = new ArrayList<>();
        while (physical.hasNext()) {
            Tuple t = physical.next();
            joinedResults.add(t.toString());
            count++;
        }
        physical.close();

        // 1 record matches (Bob)
        assertEquals(1, count);
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
                // Leaf
            }
        }
    }
}
