/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.compat.jei;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.CarvedBlockItem;
import com.nokhxyr.bitsmith.machine.BitMillBlockEntity;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import com.nokhxyr.bitsmith.registry.CarvedItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * JEI: a Bit Mill page for every material (one block in, 4096 bits out, the mill as the machine),
 * and a note on the bits and carved block items. Only loaded when JEI is installed.
 */
@JeiPlugin
public final class BitsmithJeiPlugin implements IModPlugin {
    public static final RecipeType<MillRecipe> MILL = RecipeType.create(Bitsmith.MOD_ID, "bit_mill", MillRecipe.class);
    private static @Nullable IJeiRuntime runtime;

    /** One block cut into bits. */
    public record MillRecipe(ItemStack block, ItemStack bits) {
    }

    /** The JEI runtime once JEI is ready, for the playtest. */
    public static @Nullable IJeiRuntime runtime() {
        return runtime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Bitsmith.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new MillCategory(registration.getJeiHelpers().getGuiHelper()
                .createDrawableItemLike(BitsmithRegistries.BIT_MILL_ITEM.get())));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        List<MillRecipe> recipes = new ArrayList<>();
        for (CarvedBlockItem item : CarvedItems.all()) {
            Block material = item.material();
            if (material != null && material.asItem() != Items.AIR) {
                recipes.add(new MillRecipe(new ItemStack(material), BitItem.of(material.defaultBlockState(), 64)));
            }
        }
        registration.addRecipes(MILL, recipes);
        registration.addIngredientInfo(BitsmithRegistries.BIT.get(), Component.translatable("bitsmith.jei.bit"));
        registration.addIngredientInfo(BitsmithRegistries.BIT_MILL_ITEM.get(), Component.translatable("bitsmith.jei.mill"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(BitsmithRegistries.BIT_MILL_ITEM.get(), MILL);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static final class MillCategory implements IRecipeCategory<MillRecipe> {
        private final IDrawable icon;

        MillCategory(IDrawable icon) {
            this.icon = icon;
        }

        @Override
        public RecipeType<MillRecipe> getRecipeType() {
            return MILL;
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block.bitsmith.bit_mill");
        }

        @Override
        public int getWidth() {
            return 120;
        }

        @Override
        public int getHeight() {
            return 34;
        }

        @Override
        public IDrawable getIcon() {
            return icon;
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, MillRecipe recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 4, 4).setStandardSlotBackground()
                    .addIngredient(VanillaTypes.ITEM_STACK, recipe.block());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 64, 4).setStandardSlotBackground()
                    .addIngredient(VanillaTypes.ITEM_STACK, recipe.bits());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, MillRecipe recipe, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(BitMillBlockEntity.CUT_TICKS).setPosition(30, 5);
        }

        @Override
        public void draw(MillRecipe recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("bitsmith.jei.mill.bits", BitMillBlockEntity.BITS_PER_BLOCK), 4, 25, 0xFF606060, false);
        }
    }
}
