package db.optimizer;

import db.storage.Field;
import java.util.*;

public class LogicalPlan {
    public static class JoinNode {
        public final String t1Alias;
        public final String t2Alias;
        public final String f1;
        public final String f2;

        public JoinNode(String t1Alias, String t2Alias, String f1, String f2) {
            this.t1Alias = t1Alias;
            this.t2Alias = t2Alias;
            this.f1 = f1;
            this.f2 = f2;
        }

        @Override
        public String toString() {
            return t1Alias + "." + f1 + " = " + t2Alias + "." + f2;
        }
    }

    public static class SelectNode {
        public final String tAlias;
        public final String field;
        public final Field.Op op;
        public final Field operand;

        public SelectNode(String tAlias, String field, Field.Op op, Field operand) {
            this.tAlias = tAlias;
            this.field = field;
            this.op = op;
            this.operand = operand;
        }

        @Override
        public String toString() {
            return tAlias + "." + field + " " + op + " " + operand;
        }
    }

    public static class TableNode {
        public final String tableName;
        public final String alias;

        public TableNode(String tableName, String alias) {
            this.tableName = tableName;
            this.alias = alias;
        }
    }

    private final List<TableNode> tables = new ArrayList<>();
    private final List<JoinNode> joins = new ArrayList<>();
    private final List<SelectNode> selects = new ArrayList<>();
    private final List<String> projectedFields = new ArrayList<>();

    public LogicalPlan() {}

    public void addTable(String tableName, String alias) {
        tables.add(new TableNode(tableName, alias));
    }

    public void addJoin(String t1Alias, String t2Alias, String f1, String f2) {
        joins.add(new JoinNode(t1Alias, t2Alias, f1, f2));
    }

    public void addSelect(String tAlias, String field, Field.Op op, Field operand) {
        selects.add(new SelectNode(tAlias, field, op, operand));
    }

    public void addProjectField(String field) {
        projectedFields.add(field);
    }

    public List<TableNode> getTables() { return tables; }
    public List<JoinNode> getJoins() { return joins; }
    public List<SelectNode> getSelects() { return selects; }
    public List<String> getProjectedFields() { return projectedFields; }
}
