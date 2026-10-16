/*
 * Chiseled Blocks: Create
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.create.client.ponder;

import com.nokhxyr.chiseledblocks.create.ChiseledBlocksCreate;
import com.nokhxyr.chiseledblocks.create.LineStage;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/** Ponder scenes for the pixel line machines, shown when a player holds W on one of them. */
public final class ChiseledBlocksPonderPlugin implements PonderPlugin {
    /** The Ponder index tag gathering the three machines. */
    public static final ResourceLocation TAG = ChiseledBlocksCreate.id("pixel_line");

    @Override
    public String getModId() {
        return ChiseledBlocksCreate.MOD_ID;
    }

    private static ItemLike[] machines() {
        return new ItemLike[]{ChiseledBlocksCreate.MACHINE_ITEMS.get(LineStage.SLICER).get(),
                ChiseledBlocksCreate.MACHINE_ITEMS.get(LineStage.CUTTER).get(), ChiseledBlocksCreate.MACHINE_ITEMS.get(LineStage.CRUSHER).get()};
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> items = helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));
        items.forComponents(machines())
                .addStoryBoard("pixel_line", LineScenes::line, TAG)
                .addStoryBoard("pixel_depot", LineScenes::depot, TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TAG)
                .addToIndex()
                .item(ChiseledBlocksCreate.MACHINE_ITEMS.get(LineStage.SLICER).get(), true, false)
                .title("Pixel Line")
                .description("Machines that cut whole blocks into pixels")
                .register();
        PonderTagRegistrationHelper<ItemLike> items = helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));
        var tag = items.addToTag(TAG);
        for (ItemLike machine : machines()) {
            tag.add(machine);
        }
    }
}
