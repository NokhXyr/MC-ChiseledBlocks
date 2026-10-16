/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.client;

import com.nokhxyr.bitsmith.create.BitsmithCreate;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/** The moving parts of the bit line machines, loaded with the block models. Client only. */
public final class LinePartials {
    public static final PartialModel SLICER_BLADE = PartialModel.of(BitsmithCreate.id("block/bit_slicer/blade"));
    public static final PartialModel SLICER_POLE = PartialModel.of(BitsmithCreate.id("block/bit_slicer/pole"));
    public static final PartialModel CUTTER_HEAD = PartialModel.of(BitsmithCreate.id("block/bit_cutter/head"));
    public static final PartialModel CUTTER_POLE = PartialModel.of(BitsmithCreate.id("block/bit_cutter/pole"));
    public static final PartialModel CRUSHER_HAMMER = PartialModel.of(BitsmithCreate.id("block/bit_crusher/hammer"));
    public static final PartialModel CRUSHER_POLE = PartialModel.of(BitsmithCreate.id("block/bit_crusher/pole"));

    private LinePartials() {
    }

    /** Loads the class, so the parts are known before models are baked. */
    public static void init() {
    }
}
