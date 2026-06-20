package db.optimizer;

import java.util.*;
import db.storage.TupleDesc;

public class RuleBasedOptimizer {
    public static List<LogicalPlan.SelectNode> getFiltersForTable(LogicalPlan plan, String alias) {
        List<LogicalPlan.SelectNode> results = new ArrayList<>();
        for (LogicalPlan.SelectNode select : plan.getSelects()) {
            if (select.tAlias.equalsIgnoreCase(alias)) {
                results.add(select);
            }
        }
        return results;
    }

    public static List<String> getRequiredFieldsForTable(LogicalPlan plan, String alias, TupleDesc originalTd) {
        Set<String> required = new HashSet<>();
        
        for (String field : plan.getProjectedFields()) {
            if (isFieldOfTable(field, alias, originalTd)) {
                required.add(stripAlias(field));
            }
        }

        for (LogicalPlan.SelectNode select : plan.getSelects()) {
            if (select.tAlias.equalsIgnoreCase(alias)) {
                required.add(select.field);
            }
        }

        for (LogicalPlan.JoinNode join : plan.getJoins()) {
            if (join.t1Alias.equalsIgnoreCase(alias)) {
                required.add(join.f1);
            }
            if (join.t2Alias.equalsIgnoreCase(alias)) {
                required.add(join.f2);
            }
        }

        return new ArrayList<>(required);
    }

    private static boolean isFieldOfTable(String field, String alias, TupleDesc td) {
        int dotIdx = field.indexOf('.');
        if (dotIdx != -1) {
            String tablePart = field.substring(0, dotIdx);
            return tablePart.equalsIgnoreCase(alias);
        }
        try {
            td.fieldNameToIndex(field);
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private static String stripAlias(String field) {
        int dotIdx = field.indexOf('.');
        if (dotIdx != -1) {
            return field.substring(dotIdx + 1);
        }
        return field;
    }
}
