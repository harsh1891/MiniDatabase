package db;

import db.storage.Catalog;

public class Database {
    private static final Database instance = new Database();
    private final Catalog catalog;

    private Database() {
        catalog = new Catalog();
    }

    public static Database getInstance() {
        return instance;
    }

    public static Catalog getCatalog() {
        return instance.catalog;
    }

    public static void reset() {
        instance.catalog.clear();
    }
}
