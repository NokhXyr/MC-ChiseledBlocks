/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.playtest;

import com.nokhxyr.chiseledblocks.compat.jei.ChiseledBlocksJeiPlugin;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.registry.ChiseledItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** What JEI shows of Chiseled Blocks, read through its runtime. Only called when JEI is loaded. */
final class JeiCheck {
    private JeiCheck() {
    }

    /** Null when JEI lists every carved block item, else what is missing. */
    static String listsChiseledBlocks() {
        IJeiRuntime runtime = ChiseledBlocksJeiPlugin.runtime();
        if (runtime == null) {
            return "JEI runtime not ready";
        }
        long carved = runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK).stream()
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("chiseled/"))
                .count();
        if (carved < ChiseledItems.all().size()) {
            return "JEI lists " + carved + " carved items of " + ChiseledItems.all().size();
        }
        return null;
    }

    /** Null when JEI has a page of recipes for each of the Create addon's machines, else what is missing. */
    static String listsBitLine() {
        IJeiRuntime runtime = ChiseledBlocksJeiPlugin.runtime();
        if (runtime == null) {
            return "JEI runtime not ready";
        }
        for (String machine : new String[]{"block_slicer", "slice_cutter", "pixel_crusher"}) {
            var type = runtime.getRecipeManager().getRecipeType(ResourceLocation.fromNamespaceAndPath("chiseledblocks_create", machine));
            if (type.isEmpty()) {
                return "no JEI category for " + machine;
            }
            long recipes = runtime.getRecipeManager().createRecipeLookup(type.get()).get().count();
            if (recipes < ChiseledItems.all().size() / 2) {
                return machine + " has only " + recipes + " recipes";
            }
        }
        return null;
    }

    /** Opens JEI on how {@code stack} is made, as a look up on it would. */
    static void showRecipesFor(ItemStack stack) {
        IJeiRuntime runtime = ChiseledBlocksJeiPlugin.runtime();
        if (runtime != null) {
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,
                    VanillaTypes.ITEM_STACK, stack));
        }
    }

    /** Opens JEI on stone bits, as a look up on the bits would: their information page. */
    static void showStoneBits() {
        IJeiRuntime runtime = ChiseledBlocksJeiPlugin.runtime();
        if (runtime != null) {
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,
                    VanillaTypes.ITEM_STACK, PixelItem.of(Blocks.STONE.defaultBlockState(), 1)));
        }
    }
}
