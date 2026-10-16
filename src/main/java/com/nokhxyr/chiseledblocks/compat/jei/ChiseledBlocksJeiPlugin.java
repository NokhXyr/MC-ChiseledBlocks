/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.compat.jei;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * JEI: an information page for bits. Every carved block item is listed by JEI on its own. Only
 * loaded when JEI is installed.
 */
@JeiPlugin
public final class ChiseledBlocksJeiPlugin implements IModPlugin {
    private static @Nullable IJeiRuntime runtime;

    /** The JEI runtime once JEI is ready, for the playtest. */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ChiseledBlocks.id("jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(ChiseledBlocksRegistries.PIXEL.get(), Component.translatable("chiseledblocks.jei.pixel"));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }
}
