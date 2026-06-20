package db.index;

import db.storage.Field;
import java.util.*;

public class BPlusTreeIndex implements Index {
    private static final int M = 4; // Max children in internal nodes

    private abstract static class Node {
        List<Field> keys = new ArrayList<>();
        abstract boolean isLeaf();
    }

    private static class InternalNode extends Node {
        List<Node> children = new ArrayList<>();
        @Override
        boolean isLeaf() { return false; }
    }

    private static class LeafNode extends Node {
        List<List<Integer>> values = new ArrayList<>();
        LeafNode next = null;
        @SuppressWarnings("unused")
        LeafNode prev = null;
        @Override
        boolean isLeaf() { return true; }
    }

    private Node root;

    public BPlusTreeIndex() {
        root = new LeafNode();
    }

    private LeafNode findLeaf(Node node, Field key) {
        if (node.isLeaf()) {
            return (LeafNode) node;
        }
        InternalNode internal = (InternalNode) node;
        int i = 0;
        while (i < internal.keys.size()) {
            if (key.compareTo(internal.keys.get(i)) < 0) {
                break;
            }
            i++;
        }
        return findLeaf(internal.children.get(i), key);
    }

    @Override
    public void insert(Field key, int recordId) {
        LeafNode leaf = findLeaf(root, key);
        int idx = Collections.binarySearch(leaf.keys, key);
        if (idx >= 0) {
            leaf.values.get(idx).add(recordId);
            return;
        }
        int insIdx = -idx - 1;
        leaf.keys.add(insIdx, key);
        List<Integer> list = new ArrayList<>();
        list.add(recordId);
        leaf.values.add(insIdx, list);

        if (leaf.keys.size() >= M) {
            splitLeaf(leaf);
        }
    }

    private void splitLeaf(LeafNode leaf) {
        LeafNode right = new LeafNode();
        int mid = leaf.keys.size() / 2;

        right.keys.addAll(leaf.keys.subList(mid, leaf.keys.size()));
        right.values.addAll(leaf.values.subList(mid, leaf.values.size()));

        leaf.keys.subList(mid, leaf.keys.size()).clear();
        leaf.values.subList(mid, leaf.values.size()).clear();

        right.next = leaf.next;
        if (right.next != null) {
            right.next.prev = right;
        }
        leaf.next = right;
        right.prev = leaf;

        Field splitKey = right.keys.get(0);
        InternalNode parent = findParent(root, leaf);
        if (parent == null) {
            InternalNode newRoot = new InternalNode();
            newRoot.keys.add(splitKey);
            newRoot.children.add(leaf);
            newRoot.children.add(right);
            root = newRoot;
        } else {
            insertIntoInternal(parent, splitKey, right);
        }
    }

    private void insertIntoInternal(InternalNode parent, Field key, Node rightChild) {
        int idx = 0;
        while (idx < parent.keys.size()) {
            if (key.compareTo(parent.keys.get(idx)) < 0) {
                break;
            }
            idx++;
        }
        parent.keys.add(idx, key);
        parent.children.add(idx + 1, rightChild);

        if (parent.keys.size() >= M) {
            splitInternal(parent);
        }
    }

    private void splitInternal(InternalNode node) {
        InternalNode right = new InternalNode();
        int mid = node.keys.size() / 2;

        Field splitKey = node.keys.get(mid);

        right.keys.addAll(node.keys.subList(mid + 1, node.keys.size()));
        right.children.addAll(node.children.subList(mid + 1, node.children.size()));

        node.keys.subList(mid, node.keys.size()).clear();
        node.children.subList(mid + 1, node.children.size()).clear();

        InternalNode parent = findParent(root, node);
        if (parent == null) {
            InternalNode newRoot = new InternalNode();
            newRoot.keys.add(splitKey);
            newRoot.children.add(node);
            newRoot.children.add(right);
            root = newRoot;
        } else {
            insertIntoInternal(parent, splitKey, right);
        }
    }

    private InternalNode findParent(Node current, Node target) {
        if (current.isLeaf()) {
            return null;
        }
        InternalNode internal = (InternalNode) current;
        for (Node child : internal.children) {
            if (child == target) {
                return internal;
            }
            InternalNode p = findParent(child, target);
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    @Override
    public List<Integer> search(Field key) {
        LeafNode leaf = findLeaf(root, key);
        int idx = Collections.binarySearch(leaf.keys, key);
        if (idx >= 0) {
            return new ArrayList<>(leaf.values.get(idx));
        }
        return new ArrayList<>();
    }

    @Override
    public List<Integer> rangeSearch(Field lower, Field upper) {
        List<Integer> result = new ArrayList<>();
        LeafNode curr = null;
        int idx = 0;

        if (lower == null) {
            Node node = root;
            while (!node.isLeaf()) {
                node = ((InternalNode) node).children.get(0);
            }
            curr = (LeafNode) node;
            idx = 0;
        } else {
            curr = findLeaf(root, lower);
            idx = Collections.binarySearch(curr.keys, lower);
            if (idx < 0) {
                idx = -idx - 1;
            }
        }

        while (curr != null) {
            while (idx < curr.keys.size()) {
                Field k = curr.keys.get(idx);
                if (upper != null && k.compareTo(upper) > 0) {
                    return result;
                }
                if (lower == null || k.compareTo(lower) >= 0) {
                    result.addAll(curr.values.get(idx));
                }
                idx++;
            }
            curr = curr.next;
            idx = 0;
        }
        return result;
    }

    @Override
    public void delete(Field key, int recordId) {
        LeafNode leaf = findLeaf(root, key);
        int idx = Collections.binarySearch(leaf.keys, key);
        if (idx >= 0) {
            List<Integer> list = leaf.values.get(idx);
            list.remove(Integer.valueOf(recordId));
            if (list.isEmpty()) {
                leaf.keys.remove(idx);
                leaf.values.remove(idx);

                if (leaf.keys.isEmpty()) {
                    handleLeafUnderflow(leaf);
                }
            }
        }
    }

    private void handleLeafUnderflow(LeafNode leaf) {
        if (leaf == root) {
            return;
        }

        InternalNode parent = findParent(root, leaf);
        if (parent == null) return;

        int childIdx = -1;
        for (int i = 0; i < parent.children.size(); i++) {
            if (parent.children.get(i) == leaf) {
                childIdx = i;
                break;
            }
        }

        LeafNode leftSibling = (childIdx > 0) ? (LeafNode) parent.children.get(childIdx - 1) : null;
        LeafNode rightSibling = (childIdx < parent.children.size() - 1) ? (LeafNode) parent.children.get(childIdx + 1) : null;

        if (leftSibling != null && leftSibling.keys.size() > 1) {
            int lastIdx = leftSibling.keys.size() - 1;
            leaf.keys.add(0, leftSibling.keys.remove(lastIdx));
            leaf.values.add(0, leftSibling.values.remove(lastIdx));
            parent.keys.set(childIdx - 1, leaf.keys.get(0));
            return;
        }

        if (rightSibling != null && rightSibling.keys.size() > 1) {
            leaf.keys.add(rightSibling.keys.remove(0));
            leaf.values.add(rightSibling.values.remove(0));
            parent.keys.set(childIdx, rightSibling.keys.get(0));
            return;
        }

        if (leftSibling != null) {
            leftSibling.next = leaf.next;
            if (leaf.next != null) {
                leaf.next.prev = leftSibling;
            }
            parent.keys.remove(childIdx - 1);
            parent.children.remove(childIdx);
            handleInternalUnderflow(parent);
            return;
        }

        if (rightSibling != null) {
            leaf.keys.addAll(rightSibling.keys);
            leaf.values.addAll(rightSibling.values);
            leaf.next = rightSibling.next;
            if (rightSibling.next != null) {
                rightSibling.next.prev = leaf;
            }
            parent.keys.remove(childIdx);
            parent.children.remove(childIdx + 1);
            handleInternalUnderflow(parent);
        }
    }

    private void handleInternalUnderflow(InternalNode node) {
        if (node == root) {
            if (node.keys.isEmpty() && !node.children.isEmpty()) {
                root = node.children.get(0);
            }
            return;
        }

        if (node.keys.size() >= 1) {
            return;
        }

        InternalNode parent = findParent(root, node);
        if (parent == null) return;

        int childIdx = -1;
        for (int i = 0; i < parent.children.size(); i++) {
            if (parent.children.get(i) == node) {
                childIdx = i;
                break;
            }
        }

        InternalNode leftSibling = (childIdx > 0) ? (InternalNode) parent.children.get(childIdx - 1) : null;
        InternalNode rightSibling = (childIdx < parent.children.size() - 1) ? (InternalNode) parent.children.get(childIdx + 1) : null;

        if (leftSibling != null && leftSibling.keys.size() > 1) {
            Field parentKey = parent.keys.get(childIdx - 1);
            int lastKeyIdx = leftSibling.keys.size() - 1;
            int lastChildIdx = leftSibling.children.size() - 1;

            node.keys.add(0, parentKey);
            node.children.add(0, leftSibling.children.remove(lastChildIdx));
            
            parent.keys.set(childIdx - 1, leftSibling.keys.remove(lastKeyIdx));
            return;
        }

        if (rightSibling != null && rightSibling.keys.size() > 1) {
            Field parentKey = parent.keys.get(childIdx);
            
            node.keys.add(parentKey);
            node.children.add(rightSibling.children.remove(0));
            
            parent.keys.set(childIdx, rightSibling.keys.remove(0));
            return;
        }

        if (leftSibling != null) {
            Field parentKey = parent.keys.remove(childIdx - 1);
            leftSibling.keys.add(parentKey);
            leftSibling.keys.addAll(node.keys);
            leftSibling.children.addAll(node.children);
            
            parent.children.remove(childIdx);
            handleInternalUnderflow(parent);
            return;
        }

        if (rightSibling != null) {
            Field parentKey = parent.keys.remove(childIdx);
            node.keys.add(parentKey);
            node.keys.addAll(rightSibling.keys);
            node.children.addAll(rightSibling.children);
            
            parent.children.remove(childIdx + 1);
            handleInternalUnderflow(parent);
        }
    }

    @Override
    public void clear() {
        root = new LeafNode();
    }
}


