/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import com.nokhxyr.bitsmith.compat.jei.BitsmithJeiPlugin;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.registry.CarvedItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Blocks;

/** What JEI shows of Bitsmith, read through its runtime. Only called when JEI is loaded. */
final class JeiCheck {
    private JeiCheck() {
    }

    /** Null when JEI lists the carved items and the mill page, else what is missing. */
    static String listsBitsmith() {
        IJeiRuntime runtime = BitsmithJeiPlugin.runtime();
        if (runtime == null) {
            return "JEI runtime not ready";
        }
        long carved = runtime.getIngredientManager().getAllIngredients(VanillaTypes.ITEM_STACK).stream()
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().startsWith("carved/"))
                .count();
        long mill = runtime.getRecipeManager().createRecipeLookup(BitsmithJeiPlugin.MILL).get().count();
        if (carved < CarvedItems.all().size()) {
            return "JEI lists " + carved + " carved items of " + CarvedItems.all().size();
        }
        return mill >= CarvedItems.all().size() - 20 ? null : "only " + mill + " mill recipes";
    }

    /** Opens JEI's recipe screen on what makes stone bits, as a look up on the bits would. */
    static void showStoneBits() {
        IJeiRuntime runtime = BitsmithJeiPlugin.runtime();
        if (runtime != null) {
            runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory().createFocus(RecipeIngredientRole.OUTPUT,
                    VanillaTypes.ITEM_STACK, BitItem.of(Blocks.STONE.defaultBlockState(), 1)));
        }
    }
}
