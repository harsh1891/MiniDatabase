package db.parser;

import db.optimizer.LogicalPlan;
import db.storage.*;
import java.util.*;
import java.util.regex.*;

public class SQLParser {
    public static LogicalPlan parse(String sql) {
        LogicalPlan plan = new LogicalPlan();
        sql = sql.trim().replaceAll("\\s+", " ");
        String sqlLower = sql.toLowerCase();

        int selectIdx = sqlLower.indexOf("select ");
        int fromIdx = sqlLower.indexOf(" from ");
        if (selectIdx == -1 || fromIdx == -1) {
            throw new IllegalArgumentException("SQL must contain SELECT and FROM clauses");
        }

        String selectClause = sql.substring(selectIdx + 7, fromIdx).trim();
        String[] projFields = selectClause.split(",");
        for (String field : projFields) {
            plan.addProjectField(field.trim());
        }

        int whereIdx = sqlLower.indexOf(" where ");
        String fromJoinClause;
        String whereClause = null;
        if (whereIdx != -1) {
            fromJoinClause = sql.substring(fromIdx + 6, whereIdx).trim();
            whereClause = sql.substring(whereIdx + 7).trim();
        } else {
            fromJoinClause = sql.substring(fromIdx + 6).trim();
        }

        String[] joinParts = fromJoinClause.split("(?i)\\s+join\\s+");
        parseTableAndAlias(joinParts[0], plan);

        for (int i = 1; i < joinParts.length; i++) {
            String joinPart = joinParts[i].trim();
            int onIdx = joinPart.toLowerCase().indexOf(" on ");
            if (onIdx == -1) {
                throw new IllegalArgumentException("JOIN must have an ON condition");
            }
            String tableAliasPart = joinPart.substring(0, onIdx).trim();
            String onCondition = joinPart.substring(onIdx + 4).trim();

            String alias = parseTableAndAlias(tableAliasPart, plan);

            String[] joinKeys = onCondition.split("=");
            if (joinKeys.length != 2) {
                throw new IllegalArgumentException("Invalid ON join condition: " + onCondition);
            }
            String key1 = joinKeys[0].trim();
            String key2 = joinKeys[1].trim();

            int dot1 = key1.indexOf('.');
            int dot2 = key2.indexOf('.');
            if (dot1 == -1 || dot2 == -1) {
                throw new IllegalArgumentException("Join keys in ON must be qualified with table alias (e.g. t1.col1 = t2.col2)");
            }
            String t1Alias = key1.substring(0, dot1).trim();
            String f1 = key1.substring(dot1 + 1).trim();
            String t2Alias = key2.substring(0, dot2).trim();
            String f2 = key2.substring(dot2 + 1).trim();

            plan.addJoin(t1Alias, t2Alias, f1, f2);
        }

        if (whereClause != null && !whereClause.isEmpty()) {
            String[] conditions = whereClause.split("(?i)\\s+and\\s+");
            for (String cond : conditions) {
                cond = cond.trim();
                Field.Op op = null;
                String opStr = null;
                if (cond.contains(">=")) { op = Field.Op.GREATER_THAN_OR_EQ; opStr = ">="; }
                else if (cond.contains("<=")) { op = Field.Op.LESS_THAN_OR_EQ; opStr = "<="; }
                else if (cond.contains("<>")) { op = Field.Op.NOT_EQUALS; opStr = "<>"; }
                else if (cond.contains("!=")) { op = Field.Op.NOT_EQUALS; opStr = "!="; }
                else if (cond.contains("=")) { op = Field.Op.EQUALS; opStr = "="; }
                else if (cond.contains(">")) { op = Field.Op.GREATER_THAN; opStr = ">"; }
                else if (cond.contains("<")) { op = Field.Op.LESS_THAN; opStr = "<"; }
                else if (cond.toLowerCase().contains(" like ")) { op = Field.Op.LIKE; opStr = "(?i)\\s+like\\s+"; }

                if (op == null) {
                    throw new IllegalArgumentException("Unsupported operator in WHERE condition: " + cond);
                }

                String[] condParts = cond.split(opStr);
                String lhs = condParts[0].trim();
                String rhs = condParts[1].trim();

                int dotIdx = lhs.indexOf('.');
                if (dotIdx == -1) {
                    throw new IllegalArgumentException("Filter column must be qualified with table alias (e.g. u.age > 21)");
                }
                String tAlias = lhs.substring(0, dotIdx).trim();
                String colName = lhs.substring(dotIdx + 1).trim();

                Field val;
                if ((rhs.startsWith("'") && rhs.endsWith("'")) || (rhs.startsWith("\"") && rhs.endsWith("\""))) {
                    String strVal = rhs.substring(1, rhs.length() - 1);
                    val = new StringField(strVal, Type.STRING.getLen());
                } else {
                    val = new IntField(Integer.parseInt(rhs));
                }

                plan.addSelect(tAlias, colName, op, val);
            }
        }

        return plan;
    }

    private static String parseTableAndAlias(String part, LogicalPlan plan) {
        Pattern p = Pattern.compile("([a-zA-Z0-9_]+)(?:\\s+(?:as\\s+)?([a-zA-Z0-9_]+))?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(part.trim());
        if (m.matches()) {
            String tableName = m.group(1);
            String alias = m.group(2);
            if (alias == null) {
                alias = tableName;
            }
            plan.addTable(tableName, alias);
            return alias;
        } else {
            throw new IllegalArgumentException("Invalid table expression: " + part);
        }
    }
}
