/*
 * Chiseled Blocks: Create
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.create.client;

import com.nokhxyr.chiseledblocks.create.ChiseledBlocksCreate;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;

/** The moving parts of the pixel line machines, loaded with the block models. Client only. */
public final class LinePartials {
    public static final PartialModel SLICER_BLADE = PartialModel.of(ChiseledBlocksCreate.id("block/block_slicer/blade"));
    public static final PartialModel SLICER_POLE = PartialModel.of(ChiseledBlocksCreate.id("block/block_slicer/pole"));
    public static final PartialModel CUTTER_HEAD = PartialModel.of(ChiseledBlocksCreate.id("block/slice_cutter/head"));
    public static final PartialModel CUTTER_POLE = PartialModel.of(ChiseledBlocksCreate.id("block/slice_cutter/pole"));
    public static final PartialModel CRUSHER_HAMMER = PartialModel.of(ChiseledBlocksCreate.id("block/pixel_crusher/hammer"));
    public static final PartialModel CRUSHER_POLE = PartialModel.of(ChiseledBlocksCreate.id("block/pixel_crusher/pole"));

    private LinePartials() {
    }

    /** Loads the class, so the parts are known before models are baked. */
    public static void init() {
    }
}
