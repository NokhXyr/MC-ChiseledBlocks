/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.render;

import com.nokhxyr.chiseledblocks.block.Materials;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Tinting of carved quads. A quad's tint index carries both the material and the material's own
 * tint index, so grass, leaves or water coloured bits keep their biome colour.
 */
public final class ChiseledColors {
    private static final int TINT_BITS = 4;

    private ChiseledColors() {
    }

    /** Tint index for a quad of {@code material}, or -1 when the material is not tinted. */
    public static int encode(int material, int materialTint) {
        if (materialTint < 0) {
            return -1;
        }
        return (material << TINT_BITS) | (materialTint & ((1 << TINT_BITS) - 1));
    }

    public static int color(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex < 0) {
            return -1;
        }
        BlockState material = Materials.state(tintIndex >>> TINT_BITS);
        return Minecraft.getInstance().getBlockColors().getColor(material, level, pos, tintIndex & ((1 << TINT_BITS) - 1));
    }
}
