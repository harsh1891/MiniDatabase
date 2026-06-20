package db;

import java.io.*;
import java.util.*;

public class DataGenerator {
    private static final String[] FIRST_NAMES = {
        "Alice", "Bob", "Charlie", "David", "Eve", "Frank", "Grace", "Henry", "Ivy", "Jack",
        "Karen", "Leo", "Mia", "Nick", "Olivia", "Peter", "Quinn", "Rose", "Sam", "Tina",
        "Uma", "Victor", "Wendy", "Xavier", "Yasmine", "Zach", "Arthur", "Beatrice", "Connor", "Diana",
        "Edward", "Fiona", "George", "Hannah", "Ian", "Julia", "Kevin", "Laura", "Marcus", "Nora",
        "Oscar", "Penelope", "Ryan", "Sophia", "Thomas", "Valerie", "Walter", "Zoe", "Liam", "Emma"
    };

    private static final String[] LAST_NAMES = {
        "Smith", "Johnson", "Williams", "Brown", "Jones", "Miller", "Davis", "Garcia", "Rodriguez", "Wilson",
        "Martinez", "Anderson", "Taylor", "Thomas", "Hernandez", "Moore", "Martin", "Jackson", "Thompson", "White"
    };

    private static final String[] PRODUCTS = {
        "Laptop", "Phone", "Tablet", "Monitor", "Keyboard", "Mouse", "Printer", "Camera", "Headphones", "Speaker",
        "Smartwatch", "Router", "Charger", "Cable", "Backpack", "Desk", "Chair", "Lamp", "Notebook", "Pen"
    };

    public static void main(String[] args) {
        int numUsers = 10000;
        if (args.length > 0) {
            try {
                numUsers = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid user count, defaulting to 10000");
            }
        }

        System.out.println("Generating data for " + numUsers + " users...");

        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File usersFile = new File(dataDir, "users.csv");
        File ordersFile = new File(dataDir, "orders.csv");

        Random rand = new Random(42); // Seeded for reproducibility

        // 1. Generate users.csv
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(usersFile)))) {
            for (int i = 1; i <= numUsers; i++) {
                String name = FIRST_NAMES[rand.nextInt(FIRST_NAMES.length)] + " " + LAST_NAMES[rand.nextInt(LAST_NAMES.length)];
                int age = 18 + rand.nextInt(48); // 18 to 65
                pw.println(i + "," + name + "," + age);
            }
        } catch (IOException e) {
            System.err.println("Error writing users file: " + e.getMessage());
            System.exit(1);
        }

        // 2. Generate orders.csv (average ~3 orders per user)
        int orderId = 100001;
        int totalOrders = 0;
        try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(ordersFile)))) {
            for (int userId = 1; userId <= numUsers; userId++) {
                // Generate between 0 and 6 orders (average 3)
                int numOrders = rand.nextInt(7);
                for (int o = 0; o < numOrders; o++) {
                    String item = PRODUCTS[rand.nextInt(PRODUCTS.length)];
                    pw.println(orderId + "," + userId + "," + item);
                    orderId++;
                    totalOrders++;
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing orders file: " + e.getMessage());
            System.exit(1);
        }

        System.out.println(String.format("Successfully generated %d users and %d orders in data/", numUsers, totalOrders));
    }
}
