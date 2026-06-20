package db.optimizer;

import db.Database;
import db.index.Index;
import db.operator.*;
import db.storage.*;
import java.util.*;

public class CostBasedOptimizer {
    public static DbIterator optimize(LogicalPlan plan) throws Exception {
        Catalog catalog = Database.getCatalog();
        
        Map<String, DbIterator> basePlans = new HashMap<>();
        Map<String, Integer> estimatedSizes = new HashMap<>();

        for (LogicalPlan.TableNode tableNode : plan.getTables()) {
            String alias = tableNode.alias;
            String tableName = tableNode.tableName;
            int tableid = catalog.getTableId(tableName);
            TableStats stats = TableStats.getTableStats(tableName);
            int originalSize = (stats != null) ? stats.totalTuples() : 100;

            List<LogicalPlan.SelectNode> filters = RuleBasedOptimizer.getFiltersForTable(plan, alias);
            
            DbIterator baseScan = null;
            LogicalPlan.SelectNode chosenIndexFilter = null;
            double bestCost = originalSize;

            if (stats != null) {
                for (LogicalPlan.SelectNode filter : filters) {
                    Index idx = catalog.getIndex(tableid, filter.field);
                    if (idx != null) {
                        double selectivity = stats.estimateSelectivity(
                            catalog.getTupleDesc(tableid).fieldNameToIndex(filter.field),
                            filter.op,
                            filter.operand
                        );
                        double indexScanCost = selectivity * originalSize + 1.0;
                        if (indexScanCost < bestCost) {
                            bestCost = indexScanCost;
                            chosenIndexFilter = filter;
                        }
                    }
                }
            }

            if (chosenIndexFilter != null) {
                Index idx = catalog.getIndex(tableid, chosenIndexFilter.field);
                int fieldIdx = catalog.getTupleDesc(tableid).fieldNameToIndex(chosenIndexFilter.field);
                baseScan = new IndexScan(tableid, alias, idx, fieldIdx, chosenIndexFilter.op, chosenIndexFilter.operand);
            } else {
                baseScan = new SeqScan(tableid, alias);
            }

            DbIterator currentPlan = baseScan;
            double finalSelectivity = 1.0;
            if (stats != null) {
                for (LogicalPlan.SelectNode filter : filters) {
                    if (filter == chosenIndexFilter) continue;
                    int fieldIdx = catalog.getTupleDesc(tableid).fieldNameToIndex(filter.field);
                    finalSelectivity *= stats.estimateSelectivity(fieldIdx, filter.op, filter.operand);
                    currentPlan = new Filter(currentPlan, fieldIdx, filter.op, filter.operand);
                }
            }

            basePlans.put(alias.toLowerCase(), currentPlan);
            
            int estimatedSize = (int) (originalSize * finalSelectivity);
            if (chosenIndexFilter != null && stats != null) {
                double sel = stats.estimateSelectivity(
                    catalog.getTupleDesc(tableid).fieldNameToIndex(chosenIndexFilter.field),
                    chosenIndexFilter.op,
                    chosenIndexFilter.operand
                );
                estimatedSize = (int) (estimatedSize * sel);
            }
            estimatedSizes.put(alias.toLowerCase(), Math.max(1, estimatedSize));
        }

        List<String> remainingAliases = new ArrayList<>();
        for (LogicalPlan.TableNode tableNode : plan.getTables()) {
            remainingAliases.add(tableNode.alias.toLowerCase());
        }

        if (remainingAliases.isEmpty()) {
            throw new IllegalArgumentException("No tables in query");
        }

        String bestStartAlias = null;
        int minSize = Integer.MAX_VALUE;
        for (String alias : remainingAliases) {
            int size = estimatedSizes.get(alias);
            if (size < minSize) {
                minSize = size;
                bestStartAlias = alias;
            }
        }

        remainingAliases.remove(bestStartAlias);
        DbIterator joinedPlan = basePlans.get(bestStartAlias);
        int joinedSize = estimatedSizes.get(bestStartAlias);

        while (!remainingAliases.isEmpty()) {
            LogicalPlan.JoinNode bestJoin = null;
            String bestJoinAlias = null;
            int bestJoinCost = Integer.MAX_VALUE;

            for (LogicalPlan.JoinNode join : plan.getJoins()) {
                String t1 = join.t1Alias.toLowerCase();
                String t2 = join.t2Alias.toLowerCase();
                
                String targetAlias = null;
                if (remainingAliases.contains(t1) && !remainingAliases.contains(t2)) {
                    targetAlias = t1;
                } else if (remainingAliases.contains(t2) && !remainingAliases.contains(t1)) {
                    targetAlias = t2;
                }

                if (targetAlias != null) {
                    int rightSize = estimatedSizes.get(targetAlias);
                    int cost = joinedSize + rightSize;
                    if (cost < bestJoinCost) {
                        bestJoinCost = cost;
                        bestJoin = join;
                        bestJoinAlias = targetAlias;
                    }
                }
            }

            if (bestJoin == null) {
                String smallestAlias = null;
                int smallestSize = Integer.MAX_VALUE;
                for (String alias : remainingAliases) {
                    int size = estimatedSizes.get(alias);
                    if (size < smallestSize) {
                        smallestSize = size;
                        smallestAlias = alias;
                    }
                }
                bestJoinAlias = smallestAlias;
                DbIterator rightPlan = basePlans.get(bestJoinAlias);
                joinedPlan = new HashJoin(joinedPlan, 0, rightPlan, 0);
                joinedSize = joinedSize * estimatedSizes.get(bestJoinAlias);
            } else {
                DbIterator rightPlan = basePlans.get(bestJoinAlias);
                
                String leftKey = bestJoin.t1Alias.equalsIgnoreCase(bestJoinAlias) ? bestJoin.f2 : bestJoin.f1;
                String rightKey = bestJoin.t1Alias.equalsIgnoreCase(bestJoinAlias) ? bestJoin.f1 : bestJoin.f2;
                
                String leftQualifiedKey = (bestJoin.t1Alias.equalsIgnoreCase(bestJoinAlias) ? bestJoin.t2Alias : bestJoin.t1Alias) + "." + leftKey;
                String rightQualifiedKey = bestJoinAlias + "." + rightKey;

                joinedPlan = new HashJoin(joinedPlan, leftQualifiedKey, rightPlan, rightQualifiedKey);
                
                int rightSize = estimatedSizes.get(bestJoinAlias);
                joinedSize = (int) ((double) (joinedSize * rightSize) / Math.max(10, Math.max(joinedSize, rightSize)));
            }
            remainingAliases.remove(bestJoinAlias);
        }

        if (!plan.getProjectedFields().isEmpty()) {
            List<String> projected = plan.getProjectedFields();
            if (projected.size() == 1 && "*".equals(projected.get(0))) {
                return joinedPlan;
            }
            
            TupleDesc finalTd = joinedPlan.getTupleDesc();
            int[] indices = new int[projected.size()];
            for (int i = 0; i < projected.size(); i++) {
                String field = projected.get(i);
                try {
                    indices[i] = finalTd.fieldNameToIndex(field);
                } catch (NoSuchElementException e) {
                    int dotIdx = field.indexOf('.');
                    if (dotIdx != -1) {
                        indices[i] = finalTd.fieldNameToIndex(field.substring(dotIdx + 1));
                    } else {
                        throw e;
                    }
                }
            }
            joinedPlan = new Projection(joinedPlan, indices);
        }

        return joinedPlan;
    }
}
