package db;

import db.index.BPlusTreeIndex;
import db.operator.DbIterator;
import db.optimizer.CostBasedOptimizer;
import db.optimizer.TableStats;
import db.parser.SQLParser;
import db.storage.*;
import java.io.*;
import java.util.*;

public class Console {
    public static void main(String[] args) {
        System.out.println("\u001B[35m====================================================\u001B[0m");
        System.out.println("\u001B[35m*           MINI DATABASE SYSTEM SHELL             *\u001B[0m");
        System.out.println("\u001B[35m====================================================\u001B[0m");
        System.out.println("Initializing catalog and pre-loading sample database...");

        try {
            setupSampleDatabase();
            System.out.println("\u001B[32mSuccessfully loaded tables: 'users' and 'orders'!\u001B[0m");
            System.out.println("Built B+ Tree index on 'users.age' and 'orders.user_id'.");
            System.out.println("Type your SQL query or 'explain <query>' to see plans.");
            System.out.println("Type 'help' for instructions, 'exit' to quit.\n");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                System.out.print("\u001B[34mminidb> \u001B[0m");
                String line = reader.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) {
                    break;
                }

                if ("help".equalsIgnoreCase(line)) {
                    printHelp();
                    continue;
                }

                boolean explain = false;
                if (line.toLowerCase().startsWith("explain ")) {
                    explain = true;
                    line = line.substring(8).trim();
                }

                try {
                    long start = System.nanoTime();
                    var plan = SQLParser.parse(line);
                    DbIterator physicalPlan = CostBasedOptimizer.optimize(plan);
                    long compileTime = System.nanoTime() - start;

                    if (explain) {
                        System.out.println("\u001B[33m--- OPTIMIZED EXECUTION PLAN ---\u001B[0m");
                        printPlanTree(physicalPlan, 0);
                        System.out.println(String.format("Compilation/Optimization time: %.3f ms", compileTime / 1e6));
                    } else {
                        physicalPlan.open();
                        
                        // Collect results
                        List<Tuple> results = new ArrayList<>();
                        while (physicalPlan.hasNext()) {
                            results.add(physicalPlan.next());
                        }
                        physicalPlan.close();
                        long execTime = System.nanoTime() - start;

                        // Print results in a formatted grid
                        printGrid(physicalPlan.getTupleDesc(), results);
                        System.out.println(String.format("%d rows returned (Execution time: %.3f ms)", results.size(), execTime / 1e6));
                    }
                } catch (Exception e) {
                    System.out.println("\u001B[31mError: " + e.getMessage() + "\u001B[0m");
                }
                System.out.println();
            }
        } catch (Exception e) {
            System.err.println("\u001B[31mFailed to start console: " + e.getMessage() + "\u001B[0m");
            e.printStackTrace();
        }
    }

    private static void setupSampleDatabase() throws Exception {
        new File("data").mkdirs();
        
        // Write CSV files if not exists
        File uFile = new File("data/users.csv");
        if (!uFile.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(uFile))) {
                pw.println("1,Alice,23");
                pw.println("2,Bob,30");
                pw.println("3,Charlie,25");
                pw.println("4,David,35");
                pw.println("5,Eve,23");
                pw.println("6,Frank,40");
                pw.println("7,Grace,30");
            }
        }

        File oFile = new File("data/orders.csv");
        if (!oFile.exists()) {
            try (PrintWriter pw = new PrintWriter(new FileWriter(oFile))) {
                pw.println("101,1,Laptop");
                pw.println("102,1,Phone");
                pw.println("103,2,Book");
                pw.println("104,3,Tablet");
                pw.println("105,6,Monitor");
                pw.println("106,7,Keyboard");
            }
        }

        Catalog catalog = Database.getCatalog();
        TupleDesc uTd = new TupleDesc(new Type[]{Type.INT, Type.STRING, Type.INT}, new String[]{"id", "name", "age"});
        TupleDesc oTd = new TupleDesc(new Type[]{Type.INT, Type.INT, Type.STRING}, new String[]{"id", "user_id", "item"});

        catalog.addTable(new CsvFile(uFile, uTd), "users");
        catalog.addTable(new CsvFile(oFile, oTd), "orders");

        // Build B+ tree index on users.age
        BPlusTreeIndex ageIdx = new BPlusTreeIndex();
        List<Tuple> uTuples = catalog.getDatabaseFile(catalog.getTableId("users")).readAll();
        for (Tuple t : uTuples) {
            ageIdx.insert(t.getField(2), t.getRecordId());
        }
        catalog.addIndex(catalog.getTableId("users"), "age", ageIdx);

        // Build B+ tree index on orders.user_id
        BPlusTreeIndex userIdIdx = new BPlusTreeIndex();
        List<Tuple> oTuples = catalog.getDatabaseFile(catalog.getTableId("orders")).readAll();
        for (Tuple t : oTuples) {
            userIdIdx.insert(t.getField(1), t.getRecordId());
        }
        catalog.addIndex(catalog.getTableId("orders"), "user_id", userIdIdx);

        TableStats.computeAllStats(catalog);
    }

    private static void printHelp() {
        System.out.println("Supported SQL Grammar:");
        System.out.println("  SELECT [fields] FROM [table] [alias] JOIN [table2] [alias2] ON [alias.key] = [alias2.key] WHERE [alias.col] [op] [val]");
        System.out.println("\nExamples:");
        System.out.println("  SELECT u.name, o.item FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age > 24");
        System.out.println("  SELECT u.name, u.age FROM users u WHERE u.age = 30");
        System.out.println("  explain SELECT u.name FROM users u WHERE u.age = 23");
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

    private static void printGrid(TupleDesc td, List<Tuple> rows) {
        int numCols = td.numFields();
        int[] colWidths = new int[numCols];

        // Header widths
        for (int i = 0; i < numCols; i++) {
            colWidths[i] = td.getFieldName(i).length();
        }

        // Row cell widths
        for (Tuple row : rows) {
            for (int i = 0; i < numCols; i++) {
                String val = row.getField(i) == null ? "null" : row.getField(i).toString();
                if (val.length() > colWidths[i]) {
                    colWidths[i] = val.length();
                }
            }
        }

        // Print top boundary
        printSeparator(colWidths);

        // Print header
        System.out.print("| ");
        for (int i = 0; i < numCols; i++) {
            System.out.print(String.format("%-" + colWidths[i] + "s | ", td.getFieldName(i)));
        }
        System.out.println();

        // Print middle boundary
        printSeparator(colWidths);

        // Print rows
        for (Tuple row : rows) {
            System.out.print("| ");
            for (int i = 0; i < numCols; i++) {
                String val = row.getField(i) == null ? "null" : row.getField(i).toString();
                System.out.print(String.format("%-" + colWidths[i] + "s | ", val));
            }
            System.out.println();
        }

        // Print bottom boundary
        printSeparator(colWidths);
    }

    private static void printSeparator(int[] widths) {
        System.out.print("+");
        for (int w : widths) {
            System.out.print("-".repeat(w + 2) + "+");
        }
        System.out.println();
    }
}
