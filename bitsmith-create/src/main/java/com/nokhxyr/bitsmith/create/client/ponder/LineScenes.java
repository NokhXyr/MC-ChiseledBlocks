/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.client.ponder;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * The Ponder scenes of the bit line. The machines cut in the scene exactly as in a world: the
 * scene only turns the shafts and puts items down, the rest is the real machine at work.
 */
final class LineScenes {
    /** Belt speed in rpm; its sign makes items run from the motor towards the machines. */
    private static final float BELT_RPM = -48;
    /** Machine speed in rpm: one cut every 16 ticks. */
    private static final float MACHINE_RPM = 32;

    private LineScenes() {
    }

    /** The whole line on a belt: one block in, bits out. Schematic {@code ponder/bit_line.nbt}. */
    static void line(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("bit_line", "Cutting blocks into bits on a belt");
        scene.configureBasePlate(0, 0, 9);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);
        scene.showBasePlate();
        scene.idle(10);

        Selection belt = util.select().fromTo(1, 1, 4, 7, 1, 5);
        Selection machines = util.select().fromTo(1, 3, 4, 6, 3, 4);

        scene.world().showSection(belt, Direction.DOWN);
        scene.world().showSection(util.select().position(8, 1, 4), Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(belt, BELT_RPM);
        scene.idle(10);

        scene.world().showSection(machines, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutlineWithText(util.select().fromTo(2, 2, 4, 6, 2, 4), 80)
                .colored(PonderPalette.GREEN)
                .attachKeyFrame()
                .placeNearTarget()
                .text("The three machines go two blocks above a belt or a depot, with one block of air between them");
        scene.idle(90);

        scene.world().setKineticSpeed(machines, MACHINE_RPM);
        scene.effects().rotationSpeedIndicator(util.grid().at(1, 3, 4));
        scene.overlay().showText(70)
                .pointAt(util.vector().blockSurface(util.grid().at(3, 3, 4), Direction.NORTH))
                .attachKeyFrame()
                .placeNearTarget()
                .text("A shaft through their sides turns them: the faster it turns, the faster they cut");
        scene.idle(80);

        scene.world().createItemOnBelt(util.grid().at(1, 1, 4), Direction.DOWN, new ItemStack(Blocks.STONE));
        scene.idle(10);
        scene.overlay().showText(50)
                .pointAt(util.vector().topOf(util.grid().at(2, 1, 4)))
                .attachKeyFrame()
                .placeNearTarget()
                .text("The Bit Slicer cuts a block into 16 slices");
        scene.idle(45);
        scene.overlay().showText(50)
                .pointAt(util.vector().topOf(util.grid().at(4, 1, 4)))
                .placeNearTarget()
                .text("The Bit Cutter cuts each slice into 16 rods");
        scene.idle(45);
        scene.overlay().showText(50)
                .pointAt(util.vector().topOf(util.grid().at(6, 1, 4)))
                .placeNearTarget()
                .text("The Bit Crusher crushes each rod into 16 bits");
        scene.idle(60);

        scene.overlay().showText(90)
                .pointAt(util.vector().topOf(util.grid().at(8, 1, 4)))
                .attachKeyFrame()
                .placeNearTarget()
                .colored(PonderPalette.GREEN)
                .text("4096 bits come out of every block. Each machine only takes its own input, so one belt carries the whole line");
        scene.idle(100);
        scene.markAsFinished();
    }

    /** One machine over a depot, its speed, and what it does when stopped. Schematic {@code ponder/bit_depot.nbt}. */
    static void depot(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("bit_depot", "Using a bit line machine on a depot");
        scene.configureBasePlate(0, 0, 5);
        scene.world().setKineticSpeed(util.select().everywhere(), 0);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos depot = util.grid().at(2, 1, 2);
        BlockPos machine = util.grid().at(2, 3, 2);
        Selection kinetics = util.select().fromTo(1, 3, 2, 2, 3, 2);

        scene.world().showSection(util.select().position(depot), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(kinetics, Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(kinetics, MACHINE_RPM);
        scene.overlay().showText(60)
                .pointAt(util.vector().blockSurface(machine, Direction.WEST))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Each machine also works on a depot two blocks below it");
        scene.idle(70);

        scene.overlay().showControls(util.vector().topOf(depot), Pointing.DOWN, 30).withItem(new ItemStack(Blocks.OAK_PLANKS)).rightClick();
        scene.idle(10);
        scene.world().createItemOnBeltLike(depot, Direction.UP, new ItemStack(Blocks.OAK_PLANKS));
        scene.idle(30);
        scene.overlay().showText(60)
                .pointAt(util.vector().topOf(depot))
                .placeNearTarget()
                .text("The pieces stay on the depot, ready for the next machine or a funnel");
        scene.idle(70);

        scene.world().removeItemsFromBelt(depot);
        scene.world().setKineticSpeed(kinetics, MACHINE_RPM * 2);
        scene.effects().rotationSpeedIndicator(util.grid().at(1, 3, 2));
        scene.world().createItemOnBeltLike(depot, Direction.UP, new ItemStack(Blocks.OAK_PLANKS));
        scene.overlay().showText(60)
                .pointAt(util.vector().blockSurface(machine, Direction.NORTH))
                .attachKeyFrame()
                .placeNearTarget()
                .text("Twice the speed, twice as fast: 2 seconds per cut at 16 rpm");
        scene.idle(70);

        scene.world().removeItemsFromBelt(depot);
        scene.world().setKineticSpeed(kinetics, 0);
        scene.world().createItemOnBeltLike(depot, Direction.UP, new ItemStack(Blocks.OAK_PLANKS));
        scene.overlay().showText(70)
                .pointAt(util.vector().blockSurface(machine, Direction.NORTH))
                .attachKeyFrame()
                .colored(PonderPalette.RED)
                .placeNearTarget()
                .text("Stopped, a machine leaves items alone and lets them pass along a belt");
        scene.idle(80);
        scene.markAsFinished();
    }
}
