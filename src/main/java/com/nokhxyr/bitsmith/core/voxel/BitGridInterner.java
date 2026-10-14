/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.voxel;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/**
 * Returns one shared instance per distinct grid content. A thousand identical carved blocks then
 * hold one grid, which lets every cache keyed on it (mesh, shape) hit for all of them.
 * Entries disappear once no block references the grid any more.
 */
public final class BitGridInterner {
    private final WeakHashMap<BitGrid, WeakReference<BitGrid>> pool = new WeakHashMap<>();

    public synchronized BitGrid intern(BitGrid grid) {
        WeakReference<BitGrid> ref = pool.get(grid);
        BitGrid existing = ref == null ? null : ref.get();
        if (existing != null) {
            return existing;
        }
        pool.put(grid, new WeakReference<>(grid));
        return grid;
    }

    public synchronized int size() {
        return pool.size();
    }
}
