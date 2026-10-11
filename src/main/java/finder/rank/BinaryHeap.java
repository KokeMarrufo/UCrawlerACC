package finder.rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Binary max-heap. The comparator decides priority: a positive compare means the first value
 * is higher and belongs closer to the root.
 */
public final class BinaryHeap<T> {
    private final List<T> heap = new ArrayList<>();
    private final Comparator<T> comparator;

    public BinaryHeap(Comparator<T> comparator) {
        if (comparator == null) {
            throw new IllegalArgumentException("Heap comparator is required");
        }
        this.comparator = comparator;
    }

    public void push(T value) {
        heap.add(value);
        siftUp(heap.size() - 1);
    }

    public T pop() {
        if (heap.isEmpty()) {
            throw new NoSuchElementException("Heap is empty");
        }
        T top = heap.get(0);
        T last = heap.remove(heap.size() - 1);
        if (!heap.isEmpty()) {
            heap.set(0, last);
            siftDown(0);
        }
        return top;
    }

    public boolean isEmpty() {
        return heap.isEmpty();
    }

    public int size() {
        return heap.size();
    }

    private void siftUp(int index) {
        while (index > 0) {
            int parent = (index - 1) / 2;
            if (comparator.compare(heap.get(index), heap.get(parent)) <= 0) {
                break;
            }
            swap(index, parent);
            index = parent;
        }
    }

    private void siftDown(int index) {
        int size = heap.size();
        while (true) {
            int left = index * 2 + 1;
            int right = left + 1;
            int best = index;
            if (left < size && comparator.compare(heap.get(left), heap.get(best)) > 0) {
                best = left;
            }
            if (right < size && comparator.compare(heap.get(right), heap.get(best)) > 0) {
                best = right;
            }
            if (best == index) {
                return;
            }
            swap(index, best);
            index = best;
        }
    }

    private void swap(int left, int right) {
        T tmp = heap.get(left);
        heap.set(left, heap.get(right));
        heap.set(right, tmp);
    }
}
