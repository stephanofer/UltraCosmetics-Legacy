package be.isach.ultracosmetics.cosmetics.killeffects.runtime;

import java.util.HashSet;
import java.util.Set;

/** Main-thread allocator. Released IDs are not reused until the high range wraps. */
public final class EntityIdAllocator {
    private final Set<Integer> active = new HashSet<>();
    private int next = 2_000_000_000;

    public int allocate() {
        for (int attempts = 0; attempts < 100_000_000; attempts++) {
            int id = next--;
            if (next < 1_900_000_001) next = 2_000_000_000;
            if (active.add(id)) return id;
        }
        throw new IllegalStateException("Kill Effect entity ID range exhausted");
    }

    public void release(int id) {
        active.remove(id);
    }

    public int activeCount() {
        return active.size();
    }
}
