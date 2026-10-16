/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.client.ponder;

import com.nokhxyr.bitsmith.create.BitsmithCreate;
import com.nokhxyr.bitsmith.create.LineStage;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

/** Ponder scenes for the bit line machines, shown when a player holds W on one of them. */
public final class BitsmithPonderPlugin implements PonderPlugin {
    /** The Ponder index tag gathering the three machines. */
    public static final ResourceLocation TAG = BitsmithCreate.id("bit_line");

    @Override
    public String getModId() {
        return BitsmithCreate.MOD_ID;
    }

    private static ItemLike[] machines() {
        return new ItemLike[]{BitsmithCreate.MACHINE_ITEMS.get(LineStage.SLICER).get(),
                BitsmithCreate.MACHINE_ITEMS.get(LineStage.CUTTER).get(), BitsmithCreate.MACHINE_ITEMS.get(LineStage.CRUSHER).get()};
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        PonderSceneRegistrationHelper<ItemLike> items = helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));
        items.forComponents(machines())
                .addStoryBoard("bit_line", LineScenes::line, TAG)
                .addStoryBoard("bit_depot", LineScenes::depot, TAG);
    }

    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(TAG)
                .addToIndex()
                .item(BitsmithCreate.MACHINE_ITEMS.get(LineStage.SLICER).get(), true, false)
                .title("Bit Line")
                .description("Machines that cut whole blocks into bits")
                .register();
        PonderTagRegistrationHelper<ItemLike> items = helper.withKeyFunction(item -> BuiltInRegistries.ITEM.getKey(item.asItem()));
        var tag = items.addToTag(TAG);
        for (ItemLike machine : machines()) {
            tag.add(machine);
        }
    }
}
