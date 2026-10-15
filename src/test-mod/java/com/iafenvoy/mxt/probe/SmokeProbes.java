package com.iafenvoy.mxt.probe;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/**
 * Framework checks: they prove the arena, the player and the check ledger behave the way the ported probes assume, so a
 * failure here narrows a broken run down to the harness instead of the feature under test.
 */
final class SmokeProbes {
    private SmokeProbes() {
    }

    static List<Probe> probes() {
        return List.of(
                Probe.of("smoke/arena", SmokeProbes::arena),
                Probe.of("smoke/player", SmokeProbes::player));
    }

    // The structure is all air, so the only blocks the harness itself places are the barrier shell: the floor one
    // layer under the arena and the walls just inside it.
    private static void arena(ProbeContext context) {
        context.check("arena floor is barrier", context.level().getBlockState(context.at(new BlockPos(8, -1, 8))).is(Blocks.BARRIER));
        context.check("arena interior is air", context.level().getBlockState(context.at(new BlockPos(8, 0, 8))).isAir());
        context.check("arena is open above", context.level().getBlockState(context.at(new BlockPos(8, 11, 8))).isAir());
        context.note("origin " + context.at(BlockPos.ZERO).toShortString());
    }

    // The heart of the port: the probe's player is a real one in the level, which is what the command driven probes
    // could only fake.
    private static void player(ProbeContext context) {
        context.check("player is in the level", context.level().players().contains(context.player()));
        context.check("player is in the player list", context.server().getPlayerList().getPlayers().contains(context.player()));
        context.check("player stands in the arena", context.level().getBlockState(context.playerSpot().below()).is(Blocks.BARRIER));
        context.note("player at " + context.playerSpot().toShortString());
    }
}
