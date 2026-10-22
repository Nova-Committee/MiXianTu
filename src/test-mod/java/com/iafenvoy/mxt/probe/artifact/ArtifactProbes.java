package com.iafenvoy.mxt.probe.artifact;

import com.iafenvoy.mxt.data.ability.type.StorageAbilityType;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService.RefineResult;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosSlotTypes;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.ISlotType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The artifact fixture roster: one test per definition shaped question, every one asked through the service the game
 * itself uses - matching by id, by list and by tag, ceilings, storage slots, the charm and ownership gates, refining.
 */
public final class ArtifactProbes {
    private ArtifactProbes() {
    }

    public static List<Probe> probes() {
        List<Probe> probes = new ArrayList<>(List.of(
                Probe.of("artifact/roster", ArtifactProbes::roster),
                Probe.of("artifact/refine", ArtifactProbes::refine),
                Probe.of("artifact/charm_gate", ArtifactProbes::charmGate),
                Probe.of("artifact/ownership", ArtifactProbes::ownership)));
        probes.addAll(ArtifactMountProbes.probes());
        probes.addAll(ArtifactFlightProbes.probes());
        probes.addAll(ArtifactStorageProbes.probes());
        probes.addAll(ArtifactHoldProbes.probes());
        return List.copyOf(probes);
    }

    // One row per item: what the definition claiming it must answer about ownership, capacity and the vehicle entry.
    private static void roster(ProbeContext context) {
        Provider access = context.registries();
        FormulaContext formula = FormulaContext.of(context.player());
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        Holder<Aura> waterPower = context.require(MxtResourceKeys.AURA, ProbeDefinitions.WATER_POWER);
        Holder<Aura> soulPower = context.require(MxtResourceKeys.AURA, ProbeDefinitions.SOUL_POWER);

        for (ArtifactRow row : List.of(
                new ArtifactRow(Items.IRON_SWORD, "azure_flight_sword", 60, 0, 0, 0, false, true, true),
                new ArtifactRow(Items.AMETHYST_SHARD, "spirit_gathering_jade", 200, 100, 25, 27, true, false, false),
                new ArtifactRow(Items.PRISMARINE_SHARD, "ward_jade_talisman", 40, 0, 0, 0, true, false, false),
                // The second item of the same definition, reached through the item tag its `items` list names.
                new ArtifactRow(Items.QUARTZ, "ward_jade_talisman", 40, 0, 0, 0, true, false, false),
                new ArtifactRow(Items.BELL, "cloud_beast_bell", 60, 0, 0, 18, true, false, false),
                // The three-seat vehicle, so the roster also covers a mount whose seats are more than the default.
                new ArtifactRow(Items.HEART_OF_THE_SEA, "cloud_skiff", 100, 0, 0, 0, false, true, false))) {
            ItemStack stack = new ItemStack(row.item());
            Reference<Artifact> holder = ArtifactService.definition(access, stack).orElse(null);
            Artifact definition = holder == null ? null : holder.value();
            // The registry id of the definition that answered is the row's first claim.
            boolean matches = holder != null && HolderHelper.id(holder).equals(ProbeDefinitions.id(row.definition()))
                    && row.curiosEquipable() == ArtifactService.curiosEquipable(access, stack)
                    && row.requireOwner() == definition.requireOwner()
                    && row.mount() == ArtifactService.mount(access, stack).isPresent()
                    && containerSlots(access, stack, formula) == row.slots()
                    && ArtifactService.capacity(access, stack, qi, 0.0D, formula) == row.qi()
                    && ArtifactService.capacity(access, stack, waterPower, 0.0D, formula) == row.waterPower()
                    && ArtifactService.capacity(access, stack, soulPower, 0.0D, formula) == row.soulPower();
            context.check("artifact roster " + BuiltInRegistries.ITEM.getKey(row.item()) + " = "
                    + (holder == null ? "unclaimed" : HolderHelper.id(holder)), matches);
        }
    }

    // The refine action charges 40 of a 200 ceiling, so feeding raises the ceiling to 200 * 1.1.
    private static void refine(ProbeContext context) {
        Provider access = context.registries();
        FormulaContext formula = FormulaContext.of(context.player());
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        boolean refined = ArtifactService.refine(jadeStack, context.player()) == RefineResult.REFINED;
        double jadeStored = ArtifactService.stored(jadeStack, qi);
        double jadeCeiling = ArtifactService.capacity(access, jadeStack, qi, 0.0D, formula);
        context.check("artifact roster claim_action stored=" + jadeStored + " ceiling=" + jadeCeiling,
                refined && ArtifactService.isOwner(jadeStack, context.player().getUUID()) && close(jadeStored, 40.0D) && close(jadeCeiling, 220.0D));
    }

    // What Curios is told about these items: the two definitions that declare the slot fit it, the sword does not.
    private static void charmGate(ProbeContext context) {
        ItemStack wardStack = new ItemStack(Items.PRISMARINE_SHARD);
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(jadeStack, context.player());
        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        ISlotType charm = CuriosSlotTypes.getSlotType("charm", false);
        SlotContext charmSlot = new SlotContext("charm", context.player(), 0, false, true);
        boolean charmGate = charm != null && CuriosApi.isStackValid(charmSlot, wardStack)
                && CuriosApi.isStackValid(charmSlot, jadeStack)
                && !CuriosApi.isStackValid(charmSlot, flightStack);
        context.check("artifact roster charm gate (declared yes, undeclared no)", charmGate);
    }

    // Ownership is the definition's choice: a definition asking for an owner refuses until it has one, while one
    // that does not is open to anybody until it is refined and then answers to its owner alone.
    private static void ownership(ProbeContext context) {
        Provider access = context.registries();
        ItemStack flightStack = new ItemStack(Items.IRON_SWORD);
        ItemStack jadeStack = new ItemStack(Items.AMETHYST_SHARD);
        ArtifactService.refine(jadeStack, context.player());
        Reference<Artifact> flightHolder = ArtifactService.definition(access, flightStack).orElse(null);
        Reference<Artifact> jadeHolder = ArtifactService.definition(access, jadeStack).orElse(null);
        ItemStack plainJade = new ItemStack(Items.AMETHYST_SHARD);
        ItemStack ownedSword = flightStack.copy();
        ArtifactService.refine(ownedSword, context.player());
        UUID stranger = UUID.randomUUID();
        boolean ownershipGate = flightHolder != null && jadeHolder != null
                && !ArtifactService.mayUse(flightStack, flightHolder, context.player().getUUID())
                && !ArtifactService.mayUse(ownedSword, flightHolder, stranger)
                && ArtifactService.mayUse(ownedSword, flightHolder, context.player().getUUID())
                && ArtifactService.mayUse(plainJade, jadeHolder, context.player().getUUID())
                && ArtifactService.mayUse(plainJade, jadeHolder, stranger)
                && ArtifactService.mayUse(jadeStack, jadeHolder, context.player().getUUID())
                && !ArtifactService.mayUse(jadeStack, jadeHolder, stranger)
                && !ArtifactService.hasOwner(plainJade) && ArtifactService.hasOwner(jadeStack);
        context.check("artifact roster ownership gate require_owner=true refuses, =false binds", ownershipGate);
    }

    // The container a stack declares, as the roster rows state it: the slots of the first storage ability it offers.
    private static int containerSlots(Provider access, ItemStack stack, FormulaContext formula) {
        return ArtifactService.abilities(access, stack).stream()
                .filter(ref -> ref.value().type() instanceof StorageAbilityType)
                .findFirst()
                .map(ref -> ArtifactService.storageSlots(stack, ref, formula))
                .orElse(0);
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }

    // One roster row: an item, and what every consumer must answer about the definition claiming it.
    private record ArtifactRow(Item item, String definition, int qi, int waterPower, int soulPower, int slots,
                               boolean curiosEquipable, boolean mount, boolean requireOwner) {
    }
}
