package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.iafenvoy.saturn.api.wheel.WheelEntryKind;
import com.iafenvoy.saturn.api.wheel.WheelSource;
import com.iafenvoy.saturn.api.AbilityHooks;
import com.iafenvoy.saturn.attachment.AbilityAttachment;
import com.iafenvoy.saturn.attachment.WheelLayoutAttachment;
import com.iafenvoy.saturn.data.ability.Ability;
import com.iafenvoy.saturn.registry.SaturnAttachments;
import com.iafenvoy.saturn.registry.SaturnResourceKeys;
import com.iafenvoy.saturn.runtime.Sources;
import com.iafenvoy.saturn.runtime.wheel.WheelService;
import com.iafenvoy.saturn.runtime.wheel.WheelLayout;
import com.iafenvoy.saturn.runtime.wheel.WheelSlot;
import com.iafenvoy.saturn.network.payload.WheelActionC2SPayload;
import com.iafenvoy.saturn.screen.wheel.WheelConfigurationLayout;
import com.iafenvoy.saturn.screen.wheel.WheelPage;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.connection.ConnectionType;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.saturn.runtime.wheel.WheelEntryKinds;
import com.iafenvoy.saturn.runtime.wheel.WheelSourceTypes;
import com.iafenvoy.saturn.runtime.wheel.WheelSources;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Collections;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A page and a kind a content mod registers: they are looked up by id and take part in the page numbering, and the
 * page the wheel lands on answers for its own entry only.
 */
public final class WheelVerifyProbes {
    private WheelVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/wheel_extension", WheelVerifyProbes::wheelExtension),
                Probe.of("verify/wheel/dispatch", WheelVerifyProbes::dispatch),
                Probe.of("verify/wheel/equipment", WheelVerifyProbes::equipment),
                Probe.of("verify/wheel/protocol", WheelVerifyProbes::protocol),
                Probe.of("verify/wheel/configuration_layout", WheelVerifyProbes::configurationLayout));
    }

    private static void wheelExtension(ProbeContext context) {
        String failure = verifyWheelExtension(context);
        if (failure != null) context.fail(failure);
    }

    private static String verifyWheelExtension(ProbeContext context) {
        Identifier sourceId = ProbeDefinitions.id("probe_source");
        Identifier kindId = ProbeDefinitions.id("probe_kind");
        Identifier entry = ProbeDefinitions.id("probe_entry");
        int expectedIndex = WheelSourceTypes.byId(sourceId).map(WheelSourceTypes.pages()::indexOf)
                .orElse(WheelSourceTypes.pages().size());
        WheelSourceTypes.register(new ProbeWheelSource(sourceId, entry));
        WheelEntryKinds.register(new ProbeWheelKind(kindId, entry));
        WheelSource source = WheelSourceTypes.byId(sourceId).orElse(null);
        WheelEntryKind kind = WheelEntryKinds.byId(kindId).orElse(null);
        if (source == null || kind == null) return "a registered page or kind could not be read back by id";
        if (WheelSourceTypes.pages().indexOf(source) != expectedIndex)
            return "a registered page did not land after the built-in ones";
        if (WheelSourceTypes.pageNumber(WheelSourceTypes.CONFIGURED) != 1)
            return "the configured page is not page one";
        if (!WheelSourceTypes.step(WheelSourceTypes.first(), -1).is(WheelSourceTypes.pages().getLast()))
            return "turning back from the first page did not wrap onto the registered one";
        if (!WheelSources.offers(context.player(), source, kind, entry)
                || WheelSources.offers(context.player(), source, kind, ProbeDefinitions.id("elsewhere")))
            return "the registered page did not answer for its own entry and only it";
        return null;
    }

    private static void dispatch(ProbeContext context) {
        Identifier entry = ProbeDefinitions.id("dispatch_entry");
        AtomicInteger presses = new AtomicInteger(), directions = new AtomicInteger();
        WheelEntryKind kind = new DispatchKind(ProbeDefinitions.id("dispatch_kind"), entry, presses, directions);
        WheelSource allowed = new ProbeWheelSource(ProbeDefinitions.id("dispatch_source"), entry);
        WheelSource refused = new ProbeWheelSource(ProbeDefinitions.id("refused_source"), ProbeDefinitions.id("other"));
        context.check("press checks source", !WheelService.trigger(context.player(), refused, kind, entry));
        context.check("directed request checks source", !WheelService.trigger(context.player(), refused, kind, entry, Optional.of(false)));
        context.check("missing entry refused", !WheelService.trigger(context.player(), allowed, kind, ProbeDefinitions.id("absent")));
        context.expect("refused press has no effects", presses.get(), 0);
        context.expect("refused directed request has no effects", directions.get(), 0);
        context.check("valid press", WheelService.trigger(context.player(), allowed, kind, entry));
        context.check("valid directed request", WheelService.trigger(context.player(), allowed, kind, entry, Optional.of(false)));
        context.expect("press invoked once", presses.get(), 1);
        context.expect("directed invoked once", directions.get(), 1);
        Identifier ability = ProbeDefinitions.PROBE_WHEEL_ABILITY;
        context.player().getData(SaturnAttachments.WHEEL_LAYOUT).setLayout(WheelLayout.EMPTY.with(0,
                WheelSlot.of(WheelEntryKinds.ABILITY, ability)));
        context.check("layout does not grant ability", !WheelService.trigger(context.player(), WheelSourceTypes.CONFIGURED,
                WheelEntryKinds.ABILITY, ability, Optional.of(false)));
    }

    private static void equipment(ProbeContext context) {
        ServerPlayer player = context.player();
        Holder<Ability> ability = context.require(SaturnResourceKeys.ABILITY, ProbeDefinitions.PROBE_WHEEL_ABILITY);
        AbilityAttachment grants = player.getData(SaturnAttachments.ABILITY_HOLDER);
        List<Identifier> previous = List.copyOf(grants.sources().of(ability));
        ItemStack main = player.getMainHandItem(), head = player.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack handCarrier = new ItemStack(Items.STICK), headCarrier = new ItemStack(Items.LEATHER_HELMET);
        ItemStack extensionCarrier = new ItemStack(Items.PAPER);
        Identifier owner = ProbeDefinitions.id("wheel_equipment_hook");
        AbilityHooks.register(owner, new AbilityHooks() {
            @Override public List<Holder<Ability>> itemAbilities(LivingEntity entity, ItemStack stack) {
                return entity == player && (stack == handCarrier || stack == headCarrier || stack == extensionCarrier) ? List.of(ability) : List.of();
            }
            @Override public List<ItemStack> equippedItems(LivingEntity entity) {
                return entity == player ? List.of(extensionCarrier) : List.of();
            }
        });
        try {
            player.setItemSlot(EquipmentSlot.MAINHAND, handCarrier);
            player.setItemSlot(EquipmentSlot.HEAD, headCarrier);
            grants.setSources(ability, List.of(Sources.equipment(EquipmentSlot.MAINHAND, handCarrier)));
            context.check("hand-only ability", WheelSources.handOnly(player, ability));
            context.check("hand carrier via generic hook", WheelSources.carrier(player, WheelSourceTypes.MAIN_HAND, ability).orElse(null) == handCarrier);
            context.check("extension carrier via generic hook", WheelSources.carrier(player, WheelSourceTypes.EQUIPPED, ability).orElse(null) == extensionCarrier);
            grants.setSources(ability, List.of(Sources.equipment(EquipmentSlot.HEAD, headCarrier)));
            context.check("armor ability configurable", !WheelSources.handOnly(player, ability));
            context.check("armor included in configured carriers", WheelSourceTypes.CONFIGURED.equipment(player).contains(headCarrier));
            context.check("stale hand source refused", !WheelSources.offers(player, WheelSourceTypes.MAIN_HAND, WheelEntryKinds.ABILITY, ProbeDefinitions.PROBE_WHEEL_ABILITY));
            grants.setSources(ability, List.of(Sources.EQUIPPED));
            context.check("extension source follows grants", WheelSources.offers(player, WheelSourceTypes.EQUIPPED, WheelEntryKinds.ABILITY, ProbeDefinitions.PROBE_WHEEL_ABILITY));
        } finally {
            AbilityHooks.register(owner, null);
            player.setItemSlot(EquipmentSlot.MAINHAND, main);
            player.setItemSlot(EquipmentSlot.HEAD, head);
            grants.setSources(ability, previous);
        }
    }

    private static void protocol(ProbeContext context) {
        Identifier id = ProbeDefinitions.PROBE_WHEEL_ABILITY;
        WheelSlot slot = WheelSlot.of(WheelEntryKinds.ABILITY, id);
        List<WheelSlot> oversized = new ArrayList<>();
        for (int i = 0; i < 20; i++) oversized.add(slot);
        context.expect("oversized layout trimmed", new WheelLayout(oversized).slots().size(), 12);
        context.expect("short layout padded", new WheelLayout(List.of(slot)).slot(11), WheelSlot.EMPTY);
        context.expect("out-of-range slot empty", WheelLayout.EMPTY.slot(-1), WheelSlot.EMPTY);
        context.expect("malformed kind empty", WheelEntryKinds.parse("bad kind"), WheelEntryKinds.EMPTY);
        context.expect("unknown kind empty", WheelEntryKinds.parse("missing:kind"), WheelEntryKinds.EMPTY);
        context.check("invalid armed values cleared", WheelService.sanitizeArmed(-1).isEmpty() && WheelService.sanitizeArmed(WheelService.MAX_ARMED + 1).isEmpty());
        context.expect("upper armed boundary", WheelService.sanitizeArmed(WheelService.MAX_ARMED).orElseThrow(), WheelService.MAX_ARMED);
        WheelLayout layout = WheelLayout.EMPTY.with(3, slot);
        JsonElement json = WheelLayout.CODEC.encodeStart(JsonOps.INSTANCE, layout).getOrThrow();
        context.expect("layout JSON round-trip", WheelLayout.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(), layout);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries(), ConnectionType.NEOFORGE);
        try {
            WheelLayout.STREAM_CODEC.encode(buffer, layout);
            WheelLayout received = WheelLayout.STREAM_CODEC.decode(buffer);
            context.expect("layout network round-trip", received, layout);
            WheelLayoutAttachment attachment = context.player().getData(SaturnAttachments.WHEEL_LAYOUT);
            Optional<WheelLayout> previous = attachment.layout();
            WheelPage mainPage = new WheelPage(WheelSourceTypes.CONFIGURED, Collections.nCopies(WheelLayout.SLOTS, null), WheelLayout.SLOTS);
            WheelPage automaticPage = new WheelPage(WheelSourceTypes.MAIN_HAND, Collections.nCopies(WheelLayout.SLOTS, null), 3);
            try {
                boolean configured = !new WheelLayoutAttachment().layout().orElse(WheelLayout.EMPTY).isEmpty();
                context.expect("new layout hides main HUD page", mainPage.hudShown(configured), 0);
                context.expect("new layout keeps automatic HUD page", automaticPage.hudShown(configured), 3);
                attachment.setLayout(received);
                configured = !attachment.layout().orElseThrow().isEmpty();
                context.expect("synchronized layout restores twelve main HUD cells", mainPage.hudShown(configured), 12);
                context.expect("configured main page keeps automatic HUD page", automaticPage.hudShown(configured), 3);
                WheelLayout cleared = layout.with(3, WheelSlot.EMPTY);
                WheelLayout.STREAM_CODEC.encode(buffer, cleared);
                attachment.setLayout(WheelLayout.STREAM_CODEC.decode(buffer));
                configured = !attachment.layout().orElseThrow().isEmpty();
                context.expect("clearing final slot hides only main HUD page", mainPage.hudShown(configured), 0);
                context.expect("clearing main page keeps automatic HUD page", automaticPage.hudShown(configured), 3);
                attachment.setLayout(layout);
                context.expect("configuring again restores main HUD page", mainPage.hudShown(!attachment.layout().orElseThrow().isEmpty()), 12);
            } finally {
                attachment.setLayout(previous.orElse(null));
            }
            for (Optional<Boolean> enabled : List.of(Optional.<Boolean>empty(), Optional.of(true), Optional.of(false))) {
                WheelActionC2SPayload payload = new WheelActionC2SPayload(WheelSourceTypes.CONFIGURED.id(), WheelEntryKinds.ABILITY.id(), id, enabled);
                WheelActionC2SPayload.STREAM_CODEC.encode(buffer, payload);
                context.expect("action network round-trip " + enabled, WheelActionC2SPayload.STREAM_CODEC.decode(buffer), payload);
            }
        } finally {
            buffer.release();
        }
        WheelLayout submitted = layout.with(4, WheelSlot.of(WheelEntryKinds.ABILITY, ProbeDefinitions.id("missing")));
        context.expect("server removes stale definition", WheelService.sanitize(context.player(), submitted), layout);
    }

    private static void configurationLayout(ProbeContext context) {
        for (int[] size : new int[][]{{320, 240}, {426, 240}, {640, 360}, {200, 150}}) {
            WheelConfigurationLayout layout = WheelConfigurationLayout.of(size[0], size[1]);
            String name = size[0] + "x" + size[1];
            context.check(name + " visible pool row", layout.bottom() - layout.top() >= WheelConfigurationLayout.CELL);
            context.check(name + " pool separated from slots", layout.bottom() < layout.slotY());
            context.check(name + " slots above done button", layout.slotY(11) + WheelConfigurationLayout.CELL <= size[1] - 34);
            for (int i = 0; i < 12; i++) context.expect(name + " slot hit " + i, layout.slotAt(layout.x(i) + 1, layout.slotY(i) + 1), i);
            int scroll = layout.maximumScroll(200);
            context.check(name + " long pool scrolls", scroll > 0);
            context.expect(name + " scissor top", layout.optionAt(layout.left() + 1, layout.top() - 1, scroll, 200), -1);
            context.expect(name + " cell gap", layout.optionAt(layout.left() + 23, layout.top() + 1, 0, 200), -1);
            @SuppressWarnings("IntegerDivisionInFloatingPointContext")
            double lastRowY = layout.top() + 199 / layout.columns() * WheelConfigurationLayout.STEP - scroll + 1;
            context.expect(name + " last row reachable", layout.optionAt(layout.x(199) + 1, lastRowY, scroll, 200), 199);
        }
    }

    private record DispatchKind(Identifier id, Identifier entry, AtomicInteger presses, AtomicInteger directions) implements WheelEntryKind {
        @Override public Component displayName() { return Component.literal("Dispatch Kind"); }
        @Override public boolean holdsEntry() { return true; }
        @Override public boolean exists(RegistryAccess access, Identifier id) { return this.entry.equals(id); }
        @Override public boolean trigger(ServerPlayer player, WheelSource source, Identifier id) { this.presses.incrementAndGet(); return true; }
        @Override public boolean directed(ServerPlayer player, WheelSource source, Identifier id, boolean wanted) { this.directions.incrementAndGet(); return true; }
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
