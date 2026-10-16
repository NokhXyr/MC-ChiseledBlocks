/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.compat;

import com.nokhxyr.bitsmith.create.BitPieceItem;
import com.nokhxyr.bitsmith.create.BitsmithCreate;
import com.nokhxyr.bitsmith.create.LineStage;
import com.nokhxyr.bitsmith.item.CarvedBlockItem;
import com.nokhxyr.bitsmith.registry.CarvedItems;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.ingredients.subtypes.ISubtypeInterpreter;
import mezz.jei.api.ingredients.subtypes.UidContext;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * JEI: a page per machine of the bit line and per material (block to slices, slice to rods, rod to
 * bits), the machines as catalysts, and how to build the line. Only loaded when JEI is installed.
 */
@JeiPlugin
public final class CreateJeiPlugin implements IModPlugin {
    /** The recipe type of each machine, its id being the machine's. */
    public static final Map<LineStage, RecipeType<Cut>> TYPES = new EnumMap<>(LineStage.class);

    static {
        for (LineStage stage : LineStage.values()) {
            TYPES.put(stage, RecipeType.create(BitsmithCreate.MOD_ID, stage.id(), Cut.class));
        }
    }

    /** One item cut into 16 of the next piece. */
    public record Cut(ItemStack input, ItemStack output) {
    }

    @Override
    public ResourceLocation getPluginUid() {
        return BitsmithCreate.id("jei");
    }

    /** Slices and rods of different materials are different ingredients, like the blocks they come from. */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        ISubtypeInterpreter<ItemStack> byMaterial = new ISubtypeInterpreter<>() {
            @Override
            public Object getSubtypeData(ItemStack stack, UidContext context) {
                return BitPieceItem.material(stack);
            }

            @Override
            public String getLegacyStringSubtypeInfo(ItemStack stack, UidContext context) {
                return BitPieceItem.material(stack).toString();
            }
        };
        registration.registerSubtypeInterpreter(BitsmithCreate.SLICE.get(), byMaterial);
        registration.registerSubtypeInterpreter(BitsmithCreate.ROD.get(), byMaterial);
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        for (LineStage stage : LineStage.values()) {
            registration.addRecipeCategories(new CutCategory(stage, registration.getJeiHelpers().getGuiHelper()
                    .createDrawableItemLike(BitsmithCreate.MACHINE_ITEMS.get(stage).get())));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Map<LineStage, List<Cut>> recipes = new EnumMap<>(LineStage.class);
        for (LineStage stage : LineStage.values()) {
            recipes.put(stage, new ArrayList<>());
        }
        for (CarvedBlockItem item : CarvedItems.all()) {
            Block block = item.material();
            if (block == null || block.asItem() == Items.AIR) {
                continue;
            }
            BlockState material = block.defaultBlockState();
            recipes.get(LineStage.SLICER).add(new Cut(new ItemStack(block), LineStage.SLICER.outputs(material, 1).get(0)));
            recipes.get(LineStage.CUTTER).add(new Cut(BitPieceItem.of(BitsmithCreate.SLICE.get(), material, 1),
                    LineStage.CUTTER.outputs(material, 1).get(0)));
            recipes.get(LineStage.CRUSHER).add(new Cut(BitPieceItem.of(BitsmithCreate.ROD.get(), material, 1),
                    LineStage.CRUSHER.outputs(material, 1).get(0)));
        }
        for (LineStage stage : LineStage.values()) {
            registration.addRecipes(TYPES.get(stage), recipes.get(stage));
            registration.addIngredientInfo(BitsmithCreate.MACHINE_ITEMS.get(stage).get(), Component.translatable("bitsmith_create.jei.line"));
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (LineStage stage : LineStage.values()) {
            registration.addRecipeCatalyst(BitsmithCreate.MACHINE_ITEMS.get(stage).get(), TYPES.get(stage));
        }
    }

    private static final class CutCategory implements IRecipeCategory<Cut> {
        private final LineStage stage;
        private final IDrawable icon;

        CutCategory(LineStage stage, IDrawable icon) {
            this.stage = stage;
            this.icon = icon;
        }

        @Override
        public RecipeType<Cut> getRecipeType() {
            return TYPES.get(stage);
        }

        @Override
        public Component getTitle() {
            return Component.translatable("block." + BitsmithCreate.MOD_ID + "." + stage.id());
        }

        @Override
        public int getWidth() {
            return 150;
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
        public void setRecipe(IRecipeLayoutBuilder builder, Cut recipe, IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 4, 4).setStandardSlotBackground()
                    .addIngredient(VanillaTypes.ITEM_STACK, recipe.input());
            builder.addSlot(RecipeIngredientRole.CATALYST, 34, 4)
                    .addIngredient(VanillaTypes.ITEM_STACK, new ItemStack(BitsmithCreate.MACHINE_ITEMS.get(stage).get()));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 94, 4).setStandardSlotBackground()
                    .addIngredient(VanillaTypes.ITEM_STACK, recipe.output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Cut recipe, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(40).setPosition(56, 5);
        }

        @Override
        public void draw(Cut recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
            graphics.drawString(Minecraft.getInstance().font, Component.translatable("bitsmith_create.jei.cut", LineStage.SPLIT),
                    4, 25, 0xFF606060, false);
        }
    }
}
