package com.iafenvoy.mxt.probe;

import com.iafenvoy.mxt.testmod.MxtTestMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.function.Consumer;

/**
 * One game test.
 *
 * <p>{@code maxTicks} is only a budget for bodies that spread their work over ticks (see {@link ProbeContext#after}); a
 * body that asserts everything before returning finishes on the tick it starts.
 *
 * <p>{@code structure} picks the arena. Every probe gets its own batch (see {@link ProbeTests}), so a probe may move
 * the level, the server config or the registry access it was handed without any other probe seeing it.
 */
public record Probe(Identifier id, Identifier structure, int maxTicks, boolean kit, Consumer<ProbeContext> body) {
    /**
     * The default arena: 32 x 12 x 32, room for one apparatus and the beings that work it.
     */
    public static final Identifier ARENA = structure("arena");
    /**
     * A 72 x 12 x 72 arena, for probes that lay beings out on the twelve block grid.
     */
    public static final Identifier GRID = structure("grid");
    public static final int DEFAULT_TICKS = 200;

    public static Probe of(String path, Consumer<ProbeContext> body) {
        return kitted(path, false, DEFAULT_TICKS, ARENA, body);
    }

    public static Probe of(String path, int maxTicks, Consumer<ProbeContext> body) {
        return kitted(path, false, maxTicks, ARENA, body);
    }

    public static Probe on(Identifier structure, String path, Consumer<ProbeContext> body) {
        return kitted(path, false, DEFAULT_TICKS, structure, body);
    }

    public static Probe on(Identifier structure, String path, int maxTicks, Consumer<ProbeContext> body) {
        return kitted(path, false, maxTicks, structure, body);
    }

    /**
     * A probe whose body assumes the Qingxiao scenario was handed out first, because the old leg was written to be run
     * after {@code /mxt_test kit}: the payer's cultivation decides what a price formula reads, so a bare probe player
     * cannot pay for anything.
     */
    public static Probe kitted(String path, Consumer<ProbeContext> body) {
        return kitted(path, true, DEFAULT_TICKS, ARENA, body);
    }

    public static Probe kitted(String path, int maxTicks, Consumer<ProbeContext> body) {
        return kitted(path, true, maxTicks, ARENA, body);
    }

    public static Probe onKitted(Identifier structure, String path, Consumer<ProbeContext> body) {
        return kitted(path, true, DEFAULT_TICKS, structure, body);
    }

    private static Probe kitted(String path, boolean kit, int maxTicks, Identifier structure, Consumer<ProbeContext> body) {
        return new Probe(Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path), structure, maxTicks, kit, body);
    }

    /**
     * The test function this probe is registered under; a namespace of its own keeps it out of the test id space.
     */
    ResourceKey<Consumer<GameTestHelper>> functionKey() {
        return ResourceKey.create(Registries.TEST_FUNCTION,
                Identifier.fromNamespaceAndPath(this.id.getNamespace(), "probe/" + this.id.getPath()));
    }

    private static Identifier structure(String path) {
        return Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, path);
    }
}
