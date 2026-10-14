/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.core.edit.ChiselShape;
import com.nokhxyr.bitsmith.core.edit.Symmetry;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.item.ChiselSettings;

import java.util.BitSet;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/**
 * One chisel stroke, computed the same way on both sides: the client runs it to predict the
 * result at once, the server runs it to apply it for real.
 */
public final class ChiselOperation {

    /** Receives each bit to remove, with its current material. */
    @FunctionalInterface
    public interface Removal {
        void remove(int index, int material);
    }

    private ChiselOperation() {
    }

    /**
     * Reports every non air voxel the stroke covers, and their mirror images, whose material the
     * tool may cut. Each voxel is reported once.
     *
     * @param materialAt current material of a voxel index
     * @param cuttable   whether the chisel can cut a material
     * @return number of bits reported
     */
    public static int apply(ChiselSettings settings, Symmetry symmetry, int voxel, Face face,
                            IntUnaryOperator materialAt, IntPredicate cuttable, Removal removal) {
        BitSet seen = new BitSet(BitGrid.VOLUME);
        int[] count = {0};
        ChiselShape.forEach(settings.mode(), settings.size(), voxel & 15, voxel >> 8, (voxel >> 4) & 15, face, index ->
                symmetry.forEachImage(index, image -> {
                    if (seen.get(image)) {
                        return;
                    }
                    seen.set(image);
                    int material = materialAt.applyAsInt(image);
                    if (material != BitGrid.AIR && cuttable.test(material)) {
                        removal.remove(image, material);
                        count[0]++;
                    }
                }));
        return count[0];
    }
}
