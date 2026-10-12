package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;
import java.util.UUID;

/**
 * The dynamic half of a weapon binding, asserted on what the service writes rather than on a live attribute: the
 * numbers are read back off the stack's own component, which is the thing vanilla later applies per slot. Driving
 * the real tick listener is what makes the per-hand group meaningful - the failure this leg exists for is an offhand
 * stack whose entries are labelled mainhand, which vanilla then never looks up.
 */
public final class WeaponBindingProbes {
    private static final Identifier BLADE_DAMAGE = Identifier.fromNamespaceAndPath("mxt_test", "weapon/dynamic_blade/damage");
    private static final Identifier BLADE_SPEED = Identifier.fromNamespaceAndPath("mxt_test", "weapon/dynamic_blade/speed");
    private static final Identifier FAN_ARMOR = Identifier.fromNamespaceAndPath("mxt_test", "weapon/offhand_fan/armor");
    private static final Identifier FORMULA_DAMAGE = Identifier.fromNamespaceAndPath("mxt_test", "weapon/formula_blade/damage");
    private static final Identifier FORMULA_BONUS = Identifier.fromNamespaceAndPath("mxt_test", "weapon/formula_blade/bonus");

    private WeaponBindingProbes() {
    }

    public static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        // A console has no player and this leg needs a caster for every formula, so it stands in a throwaway one.
        ServerPlayer player = source.getPlayer();
        if (player == null) player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "weapon_probe"));
        // The leg occupies both hands and moves the caster's level, so both are put back rather than left changed.
        ItemStack wornMain = player.getItemBySlot(EquipmentSlot.MAINHAND);
        ItemStack wornOff = player.getItemBySlot(EquipmentSlot.OFFHAND);
        int wornLevel = player.experienceLevel;
        boolean ok = true;
        try {
            ok = legs(source, player);
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, wornMain);
            player.setItemInHand(InteractionHand.OFF_HAND, wornOff);
            player.experienceLevel = wornLevel;
        }
        return ok ? 1 : 0;
    }

    private static boolean legs(CommandSourceStack source, ServerPlayer player) {
        boolean ok = true;
        ItemStack blade = new ItemStack(MxtTestWeaponItems.DYNAMIC_BLADE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        setLevel(player, 7);
        tick(player);

        // The provider replaces the written amount, and it is re-read every tick rather than baked at load.
        ok &= leg(source, "dynamic_value",
                close(amount(blade, EquipmentSlot.MAINHAND, BLADE_DAMAGE), 9.0D)
                        && close(amount(blade, EquipmentSlot.MAINHAND, BLADE_SPEED), 0.35D),
                "damage=" + amount(blade, EquipmentSlot.MAINHAND, BLADE_DAMAGE)
                        + " speed=" + amount(blade, EquipmentSlot.MAINHAND, BLADE_SPEED),
                "damage=9.0 speed=0.35");

        setLevel(player, 12);
        tick(player);
        ok &= leg(source, "reevaluated",
                close(amount(blade, EquipmentSlot.MAINHAND, BLADE_DAMAGE), 14.0D),
                "damage=" + amount(blade, EquipmentSlot.MAINHAND, BLADE_DAMAGE), "damage=14.0");

        // A value that comes out zero contributes nothing, so the entry is left out instead of written as 0.
        setLevel(player, 0);
        tick(player);
        ok &= leg(source, "zero_skipped",
                close(amount(blade, EquipmentSlot.MAINHAND, BLADE_DAMAGE), 2.0D)
                        && Double.isNaN(amount(blade, EquipmentSlot.MAINHAND, BLADE_SPEED)),
                "damage=" + amount(blade, EquipmentSlot.MAINHAND, BLADE_DAMAGE)
                        + " speed=" + amount(blade, EquipmentSlot.MAINHAND, BLADE_SPEED),
                "damage=2.0 speed=none");

        // The regression this whole leg exists for: the offhand stack has to carry offhand entries, and the
        // vanilla per-slot predicate is what has to find them.
        ItemStack fan = new ItemStack(MxtTestWeaponItems.OFFHAND_FAN.get());
        setLevel(player, 7);
        player.setItemInHand(InteractionHand.OFF_HAND, fan);
        tick(player);
        ok &= leg(source, "offhand_group",
                group(fan, FAN_ARMOR) == EquipmentSlotGroup.OFFHAND
                        && close(amount(fan, EquipmentSlot.OFFHAND, FAN_ARMOR), 10.0D)
                        && !EquipmentSlotGroup.MAINHAND.test(EquipmentSlot.OFFHAND),
                "group=" + group(fan, FAN_ARMOR) + " offhand=" + amount(fan, EquipmentSlot.OFFHAND, FAN_ARMOR)
                        + " mainhand=" + amount(fan, EquipmentSlot.MAINHAND, FAN_ARMOR),
                "group=offhand offhand=10.0 mainhand=none");

        // Both hands at once: each stack answers only in its own hand, which holds because the two ids differ.
        tick(player);
        ok &= leg(source, "both_hands",
                group(blade, BLADE_DAMAGE) == EquipmentSlotGroup.MAINHAND
                        && Double.isNaN(amount(blade, EquipmentSlot.OFFHAND, BLADE_DAMAGE))
                        && Double.isNaN(amount(fan, EquipmentSlot.MAINHAND, FAN_ARMOR))
                        && distinctIds(blade, fan),
                "blade=" + group(blade, BLADE_DAMAGE) + " fan=" + group(fan, FAN_ARMOR)
                        + " blade_offhand=" + amount(blade, EquipmentSlot.OFFHAND, BLADE_DAMAGE),
                "blade=mainhand fan=offhand blade_offhand=none");

        // Repeating a tick must not append a second copy of an id it already wrote.
        ok &= leg(source, "idempotent", count(blade, BLADE_DAMAGE) == 1,
                "entries=" + count(blade, BLADE_DAMAGE), "entries=1");

        // Functions come from the mod's own registry, so a fixture that calls one covers that wiring too.
        ItemStack formula = new ItemStack(MxtTestWeaponItems.FORMULA_BLADE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, formula);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        setLevel(player, 7);
        tick(player);
        ok &= leg(source, "formula_function",
                close(amount(formula, EquipmentSlot.MAINHAND, FORMULA_DAMAGE), 14.0D)
                        && close(amount(formula, EquipmentSlot.MAINHAND, FORMULA_BONUS), 0.2D),
                "clamp=" + amount(formula, EquipmentSlot.MAINHAND, FORMULA_DAMAGE)
                        + " max=" + amount(formula, EquipmentSlot.MAINHAND, FORMULA_BONUS),
                "clamp=14.0 max=0.2");

        // Above the clamp the additive half stops growing while the multiplied half keeps its own slope.
        setLevel(player, 20);
        tick(player);
        ok &= leg(source, "formula_clamped",
                close(amount(formula, EquipmentSlot.MAINHAND, FORMULA_DAMAGE), 20.0D)
                        && close(amount(formula, EquipmentSlot.MAINHAND, FORMULA_BONUS), 1.5D),
                "clamp=" + amount(formula, EquipmentSlot.MAINHAND, FORMULA_DAMAGE)
                        + " max=" + amount(formula, EquipmentSlot.MAINHAND, FORMULA_BONUS),
                "clamp=20.0 max=1.5");
        return ok;
    }

    private static void tick(ServerPlayer player) {
        ItemBindingService.onEntityTick(new EntityTickEvent.Post(player));
    }

    // The field is written directly because giveExperienceLevels also runs the level-up sound and the attribute
    // refresh, and this leg is about the formula reading the number, not about how the number got there.
    private static void setLevel(ServerPlayer player, int level) {
        player.experienceLevel = level;
    }

    // Asked through the per-slot overload on purpose: that is the call vanilla's equipment pass makes, and it is
    // the one that goes through the entry's slot predicate.
    private static double amount(ItemStack stack, EquipmentSlot slot, Identifier id) {
        double[] found = {Double.NaN};
        stack.forEachModifier(slot, (attribute, modifier) -> {
            if (modifier.is(id)) found[0] = modifier.amount();
        });
        return found[0];
    }

    private static EquipmentSlotGroup group(ItemStack stack, Identifier id) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return null;
        for (ItemAttributeModifiers.Entry entry : modifiers.modifiers())
            if (entry.modifier().is(id)) return entry.slot();
        return null;
    }

    private static int count(ItemStack stack, Identifier id) {
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) return 0;
        return (int) modifiers.modifiers().stream().filter(entry -> entry.modifier().is(id)).count();
    }

    // Two stacks in one inventory may not share an id: the per-entity modifier map is keyed by id alone, so the
    // second write would evict the first instead of standing beside it.
    private static boolean distinctIds(ItemStack blade, ItemStack fan) {
        ItemAttributeModifiers left = blade.get(DataComponents.ATTRIBUTE_MODIFIERS);
        ItemAttributeModifiers right = fan.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (left == null || right == null) return false;
        List<Identifier> ids = right.modifiers().stream().map(entry -> entry.modifier().id()).toList();
        return left.modifiers().stream().noneMatch(entry -> ids.contains(entry.modifier().id()));
    }

    private static boolean close(double actual, double expected) {
        return !Double.isNaN(actual) && Math.abs(actual - expected) < 1.0E-3D;
    }

    private static boolean leg(CommandSourceStack source, String name, boolean ok, String actual, String expected) {
        String line = "weapon binding probe: " + name + " actual=" + actual + " expected=" + expected
                + (ok ? " OK" : " MISMATCH");
        if (ok) source.sendSuccess(() -> Component.literal(line), false);
        else source.sendFailure(Component.literal(line));
        return ok;
    }
}
