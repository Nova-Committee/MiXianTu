package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.attachment.AuraChunkAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.ActualConcentrationContext;
import com.iafenvoy.mxt.network.payload.AuraStateS2CPayload;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.AuraClientState;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.BlockAuraService;
import com.iafenvoy.mxt.screen.resourcebar.AuraZoneBarContributor;
import com.iafenvoy.saturn.data.resource.ResourceBar;
import com.iafenvoy.saturn.data.resource.ResourceBar.Anchor;
import com.iafenvoy.saturn.data.resource.ResourceBar.ValueDisplay;
import com.iafenvoy.saturn.data.resourcebar.ResourceBarContext.Layout;
import com.iafenvoy.saturn.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.screen.resourcebar.ResourceBarRenderState;
import com.mojang.serialization.Codec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The two bars a zone may declare, read through the contributor the library's columns ask: which anchor each bar
 * lands on, what it is called, and which numbers it carries. The client state is fed here, so nothing has to run a
 * client to check the answer.
 */
public final class AuraHudVerifyProbes {
    private AuraHudVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/aura_hud", AuraHudVerifyProbes::auraHud));
    }

    private static void auraHud(ProbeContext context) {
        Holder<AuraZone> zone = context.require(MxtResourceKeys.AURA_ZONE, "overworld");
        Holder<Aura> aura = context.require(MxtResourceKeys.AURA, "spirit_power");
        positionSample(context, aura);
        clientSample(context, zone, aura);
        // The stored bar reports the ceiling the pool itself carries; the zone number is only its fallback.
        AuraClientState.update(Optional.of(zone),
                Map.of(aura, new AuraPool(120.0D, 200.0D, 0.0D, 0.0D)),
                Map.of(aura, new AuraPool(60.0D, 120.0D, 0.0D, 0.0D)));
        // The snapshot is interpolated towards what was synced, so the numbers are read after a couple of ticks.
        context.after(2, () -> {
            try {
                AuraZone.ClientHud hud = zone.value().clientHud();
                List<ResourceBarRenderState> left = collect(context, Anchor.LEFT);
                List<ResourceBarRenderState> right = collect(context, Anchor.RIGHT);
                ResourceBarRenderState sensed = find(left, "mxt:sensed_concentration");
                ResourceBarRenderState stored = find(left, "mxt:stored_aura");
                context.check("the left column carries both aura bars", sensed != null && stored != null);
                context.expect("the right column stays empty", right.size(), 0);
                if (sensed != null) {
                    AuraZone.Bar definition = hud.sensedConcentration().orElseThrow();
                    context.expect("sensed bar order", sensed.order(), definition.order());
                    context.expect("sensed bar index", sensed.index(), 1);
                    context.expect("sensed bar uses the zone maximum", sensed.maximum(), definition.maximum());
                    context.expect("sensed bar label", sensed.name().orElse(null),
                            Component.translatable("hud.mxt.resource_bar.sensed_concentration"));
                    context.check("sensed bar reads the environment pool",
                            sensed.current() > 0.0D && sensed.current() <= definition.maximum());
                    context.expect("sensed bar texture row",
                            sensed.renderData() instanceof OriginsRenderData data ? data.barIndex() : -1,
                            definition.barIndex());
                }
                if (stored != null) {
                    AuraZone.Bar definition = hud.storedAura().orElseThrow();
                    context.expect("stored bar order", stored.order(), definition.order());
                    context.expect("stored bar index", stored.index(), 0);
                    // Both numbers interpolate from the empty pool with the same factor, so their ratio names the
                    // ceiling the bar picked: the pool's 200, not the zone's fallback.
                    context.check("stored bar prefers the pool ceiling over the zone number",
                            stored.current() > 0.0D
                                    && Math.abs(stored.maximum() / stored.current() - 200.0D / 120.0D) < 1e-9);
                    context.expect("stored bar label", stored.name().orElse(null),
                            Component.translatable("hud.mxt.resource_bar.stored_aura"));
                    context.check("stored bar reads the actual pool",
                            stored.current() > 0.0D && stored.current() <= 200.0D);
                    context.check("stored bar follows the zone inverted flag",
                            stored.renderData() instanceof OriginsRenderData data && data.inverted() == definition.inverted());
                }
                AuraClientState.update(Optional.empty(), Map.of(), Map.of());
                context.expect("no zone draws no bars", collect(context, Anchor.LEFT).size(), 0);
                context.expect("no zone draws no right column either", collect(context, Anchor.RIGHT).size(), 0);
            } finally {
                AuraClientState.clear();
                context.done();
            }
        });
    }

    private static void positionSample(ProbeContext context, Holder<Aura> aura) {
        ServerLevel level = context.level();
        BlockPos position = context.at(4, 2, 4);
        LevelChunk chunk = level.getChunkAt(position);
        AuraService.ensureInitialized(chunk, position);
        level.setBlockAndUpdate(position, MxtBlocks.SPIRIT_STONE_ORE.get().defaultBlockState());
        BlockAuraService.rebuild(chunk, SectionPos.blockToSectionCoord(position.getY()));
        savedChunkRebuild(context, chunk, SectionPos.blockToSectionCoord(position.getY()), aura);

        AuraResult actual = AuraService.getPositionAura(level, position);
        AuraResult environment = AuraService.getSensedAura(level, position);
        AuraPool localPool = actual.pool(aura);
        context.check("the adjacent stock supplies almost the whole ore contribution", localPool.supplied() > 4.9D);
        context.check("the ore contributes to actual rather than environmental concentration",
                localPool.amount() > environment.pool(aura).amount());

        AuraStateS2CPayload payload = new AuraStateS2CPayload(Optional.ofNullable(actual.source()), actual.aura(), environment.aura());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries(), ConnectionType.NEOFORGE);
        try {
            AuraStateS2CPayload.STREAM_CODEC.encode(buffer, payload);
            AuraStateS2CPayload decoded = AuraStateS2CPayload.STREAM_CODEC.decode(buffer);
            context.expect("aura sample reads the entire packet", buffer.readableBytes(), 0);
            AuraClientState.update(decoded.source(), decoded.actual(), decoded.environment());
            AuraClientState.Snapshot sample = AuraClientState.latest().orElseThrow();
            context.expect("right HUD uses the transmitted current value", sample.actualPoolForResource(aura.value().resource()).amount(), localPool.amount());
            context.expect("right HUD uses the transmitted maximum", sample.actualPoolForResource(aura.value().resource()).maximum(), localPool.maximum());
            context.expect("environment survives the packet separately", sample.environmentPoolForResource(aura.value().resource()).amount(), environment.pool(aura).amount());
        } finally {
            buffer.release();
            AuraClientState.clear();
        }

        ResourceBar bar = aura.value().resource().value().bars().stream()
                .filter(definition -> definition.anchor() == Anchor.RIGHT && definition.context() == ActualConcentrationContext.INSTANCE)
                .findFirst().orElseThrow();
        context.check("the right HUD does not override the pool maximum", bar.maximum().isEmpty());
        context.expect("the right HUD prints the value and its maximum", bar.valueDisplay(), ValueDisplay.CURRENT_AND_MAXIMUM);
    }

    private static void savedChunkRebuild(ProbeContext context, LevelChunk chunk, int sectionY, Holder<Aura> spirit) {
        Holder<Aura> water = context.require(MxtResourceKeys.AURA, "water_power");
        AuraChunkAttachment original = chunk.getData(MxtAttachments.AURA_CHUNK);
        AuraChunkAttachment saved = new AuraChunkAttachment();
        saved.initializeAuras(Map.of(), sectionY, 2);
        saved.pools(sectionY).put(spirit, AuraPool.natural(12.5D, 20.0D, 0.0D));

        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, context.registries());
        Codec<AuraChunkAttachment> codec = AuraChunkAttachment.CODEC.codec();
        AuraChunkAttachment loaded = codec.parse(ops, codec.encodeStart(ops, saved).getOrThrow()).getOrThrow();
        context.check("an empty seeded section survives saving",
                loaded.ready(sectionY + 1) && loaded.pools(sectionY + 1).isEmpty());
        context.expect("the saved spirit stock survives loading", loaded.pools(sectionY).get(spirit).amount(), 12.5D);
        context.check("the saved section has no water pool yet", !loaded.pools(sectionY).containsKey(water));

        chunk.setData(MxtAttachments.AURA_CHUNK, loaded);
        try {
            BlockAuraService.rebuild(chunk);
            context.expect("rebuilding a loaded chunk adds the ore to the existing stock",
                    loaded.pools(sectionY).get(spirit).amount(), 17.5D);
            context.expect("rebuilding a loaded chunk can insert a new pool",
                    loaded.pools(sectionY).getOrDefault(water, AuraPool.empty()).amount(), 5.0D);
            BlockAuraService.rebuild(chunk);
            context.expect("a second rebuild keeps the same spirit stock", loaded.pools(sectionY).get(spirit).amount(), 17.5D);
            context.expect("a second rebuild keeps the same water stock",
                    loaded.pools(sectionY).getOrDefault(water, AuraPool.empty()).amount(), 5.0D);
        } finally {
            chunk.setData(MxtAttachments.AURA_CHUNK, original);
        }
    }

    private static void clientSample(ProbeContext context, Holder<AuraZone> zone, Holder<Aura> aura) {
        AuraClientState.clear();
        try {
            context.check("no sample before the first update", AuraClientState.latest().isEmpty());
            AuraPool actual = new AuraPool(13.75D, 24.0D, 0.5D, 2.0D);
            AuraClientState.update(Optional.of(zone), Map.of(aura, actual),
                    Map.of(aura, AuraPool.natural(4.25D, 10.0D, 0.0D)));
            actual.change(3.0D);
            AuraClientState.current();
            AuraClientState.Snapshot sample = AuraClientState.latest().orElseThrow();
            context.expect("latest sample names the server zone", sample.source(), Optional.of(zone));
            context.expect("latest actual value is exact and detached", sample.actualPool(aura).amount(), 13.75D);
            context.expect("latest environmental value is exact", sample.environmentPool(aura).amount(), 4.25D);
            context.expect("actual HUD resolves the pool by its resource", sample.actualPoolForResource(aura.value().resource()).amount(), 13.75D);
            context.expect("environment HUD resolves the pool by its resource", sample.environmentPoolForResource(aura.value().resource()).amount(), 4.25D);
            context.expect("a resource absent from the sample has no HUD capacity",
                    sample.actualPoolForResource(context.require(SaturnResourceKeys.RESOURCE, "water_power")).maximum(), 0.0D);
            context.expect("latest sample retains block supply", sample.actualPool(aura).supplied(), 2.0D);
            try {
                sample.actualPool(aura).change(1.0D);
                context.fail("latest sample pool allowed mutation");
            } catch (UnsupportedOperationException expected) {
                context.expect("latest sample pool preserves its value", sample.actualPool(aura).amount(), 13.75D);
            }
            AuraClientState.update(Optional.empty(), Map.of(), Map.of());
            AuraClientState.Snapshot empty = AuraClientState.latest().orElseThrow();
            context.check("an empty server sample is still available", empty.source().isEmpty() && empty.actual().isEmpty());
        } finally {
            AuraClientState.clear();
        }
        context.check("clearing removes the latest sample", AuraClientState.latest().isEmpty());
        context.expect("clearing removes interpolated values", AuraClientState.current().actualConcentration(), 0.0D);
    }

    private static List<ResourceBarRenderState> collect(ProbeContext context, Anchor anchor) {
        List<ResourceBarRenderState> bars = new ArrayList<>();
        AuraZoneBarContributor.INSTANCE.collect(context.player(), Layout.SELF_HUD, anchor, bars);
        return bars;
    }

    private static @Nullable ResourceBarRenderState find(List<ResourceBarRenderState> bars, String id) {
        for (ResourceBarRenderState state : bars) if (state.id().toString().equals(id)) return state;
        return null;
    }
}
