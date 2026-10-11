package finder.text;

import java.util.ArrayList;
import java.util.List;

/**
 * AVL tree of vocabulary words. Prefix completion walks this tree.
 * A plain sorted list would also answer prefixes; the AVL keeps inserts balanced,
 * which is the Chapter 3 structure this feature is built on.
 */
public final class AvlTree {
    private Node root;

    public void insert(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }
        root = insert(root, key);
    }

    public boolean contains(String key) {
        if (key == null) {
            return false;
        }
        Node node = root;
        while (node != null) {
            int cmp = key.compareTo(node.key);
            if (cmp == 0) {
                return true;
            }
            node = cmp < 0 ? node.left : node.right;
        }
        return false;
    }

    public int height() {
        return height(root);
    }

    public int size() {
        return size(root);
    }

    public List<String> wordsWithPrefix(String prefix) {
        List<String> out = new ArrayList<>();
        if (prefix == null || prefix.isEmpty()) {
            return out;
        }
        collectPrefix(root, prefix, out);
        return out;
    }

    public List<String> allWords() {
        List<String> out = new ArrayList<>();
        inOrder(root, out);
        return out;
    }

    private Node insert(Node node, String key) {
        if (node == null) {
            return new Node(key);
        }
        int cmp = key.compareTo(node.key);
        if (cmp < 0) {
            node.left = insert(node.left, key);
        } else if (cmp > 0) {
            node.right = insert(node.right, key);
        } else {
            return node;
        }
        updateHeight(node);
        return balance(node);
    }

    private Node balance(Node node) {
        int factor = balanceFactor(node);
        if (factor > 1) {
            if (balanceFactor(node.left) < 0) {
                node.left = rotateLeft(node.left);
            }
            return rotateRight(node);
        }
        if (factor < -1) {
            if (balanceFactor(node.right) > 0) {
                node.right = rotateRight(node.right);
            }
            return rotateLeft(node);
        }
        return node;
    }

    private int balanceFactor(Node node) {
        return node == null ? 0 : height(node.left) - height(node.right);
    }

    private Node rotateRight(Node y) {
        Node x = y.left;
        Node moved = x.right;
        x.right = y;
        y.left = moved;
        updateHeight(y);
        updateHeight(x);
        return x;
    }

    private Node rotateLeft(Node x) {
        Node y = x.right;
        Node moved = y.left;
        y.left = x;
        x.right = moved;
        updateHeight(x);
        updateHeight(y);
        return y;
    }

    private void updateHeight(Node node) {
        node.height = 1 + Math.max(height(node.left), height(node.right));
    }

    private int height(Node node) {
        return node == null ? 0 : node.height;
    }

    private int size(Node node) {
        if (node == null) {
            return 0;
        }
        return 1 + size(node.left) + size(node.right);
    }

    private void collectPrefix(Node node, String prefix, List<String> out) {
        if (node == null) {
            return;
        }
        if (node.key.startsWith(prefix)) {
            collectPrefix(node.left, prefix, out);
            out.add(node.key);
            collectPrefix(node.right, prefix, out);
        } else if (node.key.compareTo(prefix) < 0) {
            collectPrefix(node.right, prefix, out);
        } else {
            collectPrefix(node.left, prefix, out);
        }
    }

    private void inOrder(Node node, List<String> out) {
        if (node == null) {
            return;
        }
        inOrder(node.left, out);
        out.add(node.key);
        inOrder(node.right, out);
    }

    private static final class Node {
        private final String key;
        private Node left;
        private Node right;
        private int height = 1;

        private Node(String key) {
            this.key = key;
        }
    }
}
