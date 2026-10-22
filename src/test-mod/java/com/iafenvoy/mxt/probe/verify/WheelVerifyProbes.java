package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.mxt.runtime.wheel.WheelSourceTypes;
import com.iafenvoy.mxt.runtime.wheel.WheelSources;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A page and a kind a content mod registers: they are looked up by id and take part in the page numbering, and the
 * page the wheel lands on answers for its own entry only.
 */
public final class WheelVerifyProbes {
    private WheelVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/wheel_extension", WheelVerifyProbes::wheelExtension));
    }

    private static void wheelExtension(ProbeContext context) {
        String failure = verifyWheelExtension(context);
        if (failure != null) context.fail(failure);
    }

    private static String verifyWheelExtension(ProbeContext context) {
        Identifier sourceId = ProbeDefinitions.id("probe_source");
        Identifier kindId = ProbeDefinitions.id("probe_kind");
        Identifier entry = ProbeDefinitions.id("probe_entry");
        WheelSourceTypes.register(new ProbeWheelSource(sourceId, entry));
        WheelEntryKinds.register(new ProbeWheelKind(kindId, entry));
        WheelSource source = WheelSourceTypes.byId(sourceId).orElse(null);
        WheelEntryKind kind = WheelEntryKinds.byId(kindId).orElse(null);
        if (source == null || kind == null) return "a registered page or kind could not be read back by id";
        if (WheelSourceTypes.pages().indexOf(source) != WheelSourceTypes.BUILT_IN.size())
            return "a registered page did not land after the built-in ones";
        if (WheelSourceTypes.pageNumber(WheelSourceTypes.CONFIGURED) != 1)
            return "the configured page is not page one";
        if (!WheelSourceTypes.step(WheelSourceTypes.first(), -1).id().equals(sourceId))
            return "turning back from the first page did not wrap onto the registered one";
        if (!WheelSources.offers(context.player(), source, kind, entry)
                || WheelSources.offers(context.player(), source, kind, ProbeDefinitions.id("elsewhere")))
            return "the registered page did not answer for its own entry and only it";
        return null;
    }

    // A page that answers for exactly one entry, so what the shared check does with the page's own answer is what
    // the leg measures rather than anything a real page reads.
    private record ProbeWheelSource(Identifier id, Identifier entry) implements WheelSource {
        @Override
        public Component displayName() {
            return Component.literal("Probe Page");
        }

        @Override
        public boolean configured() {
            return false;
        }

        @Override
        public List<Identifier> grantSources(LivingEntity entity) {
            return List.of();
        }

        @Override
        public List<ItemStack> equipment(LivingEntity entity) {
            return List.of();
        }

        @Override
        public boolean offers(LivingEntity entity, WheelEntryKind kind, Identifier id) {
            return this.entry.equals(id);
        }
    }

    private record ProbeWheelKind(Identifier id, Identifier entry) implements WheelEntryKind {
        @Override
        public Component displayName() {
            return Component.literal("Probe Kind");
        }

        @Override
        public boolean holdsEntry() {
            return true;
        }

        @Override
        public boolean exists(RegistryAccess access, Identifier id) {
            return this.entry.equals(id);
        }

        @Override
        public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) {
            return this.entry.equals(id);
        }
    }
}
