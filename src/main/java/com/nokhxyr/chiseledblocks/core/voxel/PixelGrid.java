/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import java.util.Arrays;

/**
 * Immutable 16x16x16 grid of materials, stored as a sorted palette plus bit-packed indices.
 *
 * <p>The encoding is canonical: two grids with the same voxels always have the same palette and
 * the same packed data, whatever the order of the edits that produced them. That makes
 * {@link #equals(Object)} and {@link #hashCode()} content based, so identical carvings can share
 * one instance, one mesh and one collision shape.
 *
 * <p>Material ids are opaque ints owned by the caller. {@link #AIR} (0) is empty space.
 */
public final class PixelGrid {
    public static final int SIZE = 16;
    public static final int AREA = SIZE * SIZE;
    public static final int VOLUME = SIZE * SIZE * SIZE;
    public static final int AIR = 0;

    private static final long[] NO_DATA = new long[0];

    private final int[] palette;
    private final int bitsPerEntry;
    private final int valuesPerLong;
    private final long[] data;
    private final int filledCount;
    private final int hash;

    private PixelGrid(int[] palette, int bitsPerEntry, long[] data, int filledCount) {
        this.palette = palette;
        this.bitsPerEntry = bitsPerEntry;
        this.valuesPerLong = bitsPerEntry == 0 ? 0 : 64 / bitsPerEntry;
        this.data = data;
        this.filledCount = filledCount;
        this.hash = 31 * Arrays.hashCode(palette) + Arrays.hashCode(data);
    }

    /** Index layout matches Minecraft chunk sections: y major, then z, then x. */
    public static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    public static PixelGrid filled(int material) {
        return new PixelGrid(new int[]{material}, 0, NO_DATA, material == AIR ? 0 : VOLUME);
    }

    /** Builds a grid from {@link #VOLUME} materials laid out by {@link #index(int, int, int)}. */
    public static PixelGrid of(int[] voxels) {
        if (voxels.length != VOLUME) {
            throw new IllegalArgumentException("Expected " + VOLUME + " voxels, got " + voxels.length);
        }

        // Palettes are tiny in practice, so a linear scan with a last-hit shortcut beats hashing.
        int[] found = new int[8];
        int count = 0;
        int filled = 0;
        int last = voxels[0] + 1;
        for (int material : voxels) {
            if (material != AIR) {
                filled++;
            }
            if (material == last) {
                continue;
            }
            last = material;
            boolean known = false;
            for (int k = 0; k < count; k++) {
                if (found[k] == material) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                if (count == found.length) {
                    found = Arrays.copyOf(found, count * 2);
                }
                found[count++] = material;
            }
        }
        int[] palette = Arrays.copyOf(found, count);
        Arrays.sort(palette);

        if (palette.length == 1) {
            return new PixelGrid(palette, 0, NO_DATA, filled);
        }

        int bits = 32 - Integer.numberOfLeadingZeros(palette.length - 1);
        int perLong = 64 / bits;
        long[] data = new long[(VOLUME + perLong - 1) / perLong];
        int lastMaterial = palette[0];
        long lastIndex = 0;
        for (int i = 0; i < VOLUME; i++) {
            int material = voxels[i];
            if (material != lastMaterial) {
                lastMaterial = material;
                lastIndex = Arrays.binarySearch(palette, material);
            }
            data[i / perLong] |= lastIndex << ((i % perLong) * bits);
        }
        return new PixelGrid(palette, bits, data, filled);
    }

    /**
     * Unpacks raw serialized data into voxels, mapping each stored palette index through
     * {@code palette}. Indices that point outside the palette become {@link #AIR}, so corrupt or
     * hostile data can never throw.
     */
    public static int[] decode(int[] palette, int bitsPerEntry, long[] data) {
        int[] voxels = new int[VOLUME];
        if (palette.length == 0) {
            return voxels;
        }
        if (bitsPerEntry <= 0 || bitsPerEntry > 32) {
            Arrays.fill(voxels, palette[0]);
            return voxels;
        }
        int perLong = 64 / bitsPerEntry;
        long mask = (1L << bitsPerEntry) - 1;
        for (int i = 0; i < VOLUME; i++) {
            int word = i / perLong;
            if (word >= data.length) {
                break;
            }
            int paletteIndex = (int) ((data[word] >>> ((i % perLong) * bitsPerEntry)) & mask);
            voxels[i] = paletteIndex < palette.length ? palette[paletteIndex] : AIR;
        }
        return voxels;
    }

    /** Copy of the sorted palette. */
    public int[] palette() {
        return palette.clone();
    }

    /** Copy of the packed palette indices; empty when {@link #bitsPerEntry()} is 0. */
    public long[] packedData() {
        return data.clone();
    }

    public int get(int x, int y, int z) {
        return get(index(x, y, z));
    }

    public int get(int index) {
        if (bitsPerEntry == 0) {
            return palette[0];
        }
        long word = data[index / valuesPerLong];
        int shift = (index % valuesPerLong) * bitsPerEntry;
        return palette[(int) ((word >>> shift) & ((1L << bitsPerEntry) - 1))];
    }

    /** Copies every voxel into {@code out}, which must hold {@link #VOLUME} entries. */
    public int[] toArray(int[] out) {
        if (bitsPerEntry == 0) {
            Arrays.fill(out, 0, VOLUME, palette[0]);
            return out;
        }
        long mask = (1L << bitsPerEntry) - 1;
        for (int i = 0; i < VOLUME; i++) {
            long word = data[i / valuesPerLong];
            out[i] = palette[(int) ((word >>> ((i % valuesPerLong) * bitsPerEntry)) & mask)];
        }
        return out;
    }

    public PixelGridBuilder toBuilder() {
        return new PixelGridBuilder(toArray(new int[VOLUME]));
    }

    /** True when every voxel of the boundary layer on {@code face} matches {@code covering}. */
    public boolean isFaceCovered(Face face, java.util.function.IntPredicate covering) {
        if (bitsPerEntry == 0) {
            return covering.test(palette[0]);
        }
        int depth = face.positive ? SIZE - 1 : 0;
        int[] pos = new int[3];
        int u = (face.axis + 1) % 3;
        int v = (face.axis + 2) % 3;
        pos[face.axis] = depth;
        for (int a = 0; a < SIZE; a++) {
            for (int b = 0; b < SIZE; b++) {
                pos[u] = a;
                pos[v] = b;
                if (!covering.test(get(pos[0], pos[1], pos[2]))) {
                    return false;
                }
            }
        }
        return true;
    }

    public int paletteSize() {
        return palette.length;
    }

    public int paletteEntry(int i) {
        return palette[i];
    }

    public boolean contains(int material) {
        return Arrays.binarySearch(palette, material) >= 0;
    }

    public int filledCount() {
        return filledCount;
    }

    public boolean isEmpty() {
        return filledCount == 0;
    }

    /** True when every voxel holds the same non-air material, so a plain block can replace it. */
    public boolean isUniformSolid() {
        return palette.length == 1 && palette[0] != AIR;
    }

    public int bitsPerEntry() {
        return bitsPerEntry;
    }

    /** Approximate heap footprint of the voxel payload, in bytes. */
    public int payloadBytes() {
        return data.length * Long.BYTES + palette.length * Integer.BYTES;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PixelGrid other) || other.hash != hash) {
            return false;
        }
        return Arrays.equals(palette, other.palette) && Arrays.equals(data, other.data);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "PixelGrid{palette=" + Arrays.toString(palette) + ", filled=" + filledCount + '}';
    }
}
