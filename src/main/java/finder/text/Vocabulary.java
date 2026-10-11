package finder.text;

import java.util.Collection;

/** The set of words seen in saved page text, stored in an AVL tree. */
public final class Vocabulary {
    private final AvlTree tree = new AvlTree();

    public void add(String word) {
        if (word == null || word.isBlank()) {
            return;
        }
        tree.insert(word);
    }

    public void addAll(Collection<String> words) {
        if (words == null) {
            return;
        }
        for (String word : words) {
            add(word);
        }
    }

    public void addText(String text) {
        addAll(Tokens.words(text));
    }

    public boolean contains(String word) {
        return word != null && tree.contains(word);
    }

    public AvlTree tree() {
        return tree;
    }

    public int size() {
        return tree.size();
    }
}
