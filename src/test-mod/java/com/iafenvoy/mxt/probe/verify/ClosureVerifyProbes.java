package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.attachment.TriggerCooldownAttachment;
import com.iafenvoy.mxt.data.SpriteIcon;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.action.builtin.item.AddAbilityAction;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesComponent;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.OriginsRenderData;
import com.iafenvoy.mxt.data.resourcebar.builtin.renderdata.TexturedRenderData;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.runtime.formation.FormationOwners;
import com.iafenvoy.mxt.runtime.formation.FormationService;
import com.iafenvoy.mxt.runtime.tribulation.TribulationService;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.ServerLevelData;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The behaviours the C-group audit closed, each asserted through the codec or the runtime a pack really goes
 * through: the field a skill may no longer write, the sprite type the bars use, and so on.
 */
public final class ClosureVerifyProbes {
    private ClosureVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/active_slot", ClosureVerifyProbes::activeSlot),
                Probe.of("verify/sprite_icons", ClosureVerifyProbes::spriteIcons),
                Probe.kitted("verify/trigger_gates", ClosureVerifyProbes::triggerGates),
                Probe.of("verify/item_ability_producer", ClosureVerifyProbes::itemAbilityProducer),
                Probe.of("verify/structure_check", ClosureVerifyProbes::structureCheck),
                Probe.kitted("verify/upkeep_bill", ClosureVerifyProbes::upkeepBill),
                Probe.of("verify/timeline_control", ClosureVerifyProbes::timelineControl),
                Probe.of("verify/owner_set", ClosureVerifyProbes::ownerSet));
    }

    private static void activeSlot(ProbeContext context) {
        String failure = verifyActiveSlot(RegistryOps.create(JsonOps.INSTANCE, context.registries()));
        if (failure != null) context.fail(failure);
    }

    private static void spriteIcons(ProbeContext context) {
        String failure = verifySpriteIcons();
        if (failure != null) context.fail(failure);
    }

    private static void triggerGates(ProbeContext context) {
        String failure = verifyTriggerGates(context);
        if (failure != null) context.fail(failure);
    }

    private static void itemAbilityProducer(ProbeContext context) {
        String failure = verifyItemAbilityProducer(context);
        if (failure != null) context.fail(failure);
    }

    private static void structureCheck(ProbeContext context) {
        String failure = verifyStructureCheck(RegistryOps.create(JsonOps.INSTANCE, context.registries()));
        if (failure != null) context.fail(failure);
    }

    private static void upkeepBill(ProbeContext context) {
        String failure = verifyUpkeepBill(context);
        if (failure != null) context.fail(failure);
    }

    private static void timelineControl(ProbeContext context) {
        String failure = verifyTimelineControl(context);
        if (failure != null) context.fail(failure);
    }

    private static void ownerSet(ProbeContext context) {
        String failure = verifyOwnerSet();
        if (failure != null) context.fail(failure);
    }

    // mxt:active no longer carries a slot, and a pack that still writes one keeps loading: which cell a skill sits
    // in is the player's own wheel layout, so the key is an unread field like any other.
    private static String verifyActiveSlot(RegistryOps<JsonElement> ops) {
        JsonObject plain = new JsonObject();
        plain.addProperty("type", "mxt:active");
        plain.addProperty("name", "probe");
        plain.addProperty("description", "probe");
        JsonObject slotted = plain.deepCopy();
        slotted.addProperty("slot", "utility");
        Ability plainAbility = Ability.DIRECT_CODEC.parse(ops, plain).result().orElse(null);
        Ability slottedAbility = Ability.DIRECT_CODEC.parse(ops, slotted).result().orElse(null);
        if (plainAbility == null) return "mxt:active without a slot no longer decodes";
        if (slottedAbility == null) return "mxt:active no longer ignores a slot the wheel layout owns";
        if (!slottedAbility.equals(plainAbility)) return "an ignored slot changed what mxt:active decoded to";
        return null;
    }

    // The bar sprites: a bare id keeps whatever the field always meant, a texture states its sheet and the size it
    // is drawn at, and the combinations that cannot be drawn are refused at load time.
    private static String verifySpriteIcons() {
        Identifier atlas = ProbeDefinitions.id("bar/fill");
        Identifier sheet = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/sheet.png");
        SpriteIcon bare = SpriteIcon.SPRITE_CODEC.parse(JsonOps.INSTANCE,
                JsonOps.INSTANCE.createString(atlas.toString())).result().orElse(null);
        if (bare == null || !bare.isSprite() || !atlas.equals(bare.id().orElse(null)))
            return "a bare sprite id no longer reads as a GUI sprite";
        SpriteIcon bareTexture = SpriteIcon.TEXTURE_CODEC.parse(JsonOps.INSTANCE,
                JsonOps.INSTANCE.createString(sheet.toString())).result().orElse(null);
        if (bareTexture == null || bareTexture.isSprite() || !sheet.equals(bareTexture.id().orElse(null)))
            return "a bare id no longer reads as a texture where that is what the field meant";

        JsonObject region = new JsonObject();
        region.addProperty("texture", sheet.toString());
        JsonObject box = new JsonObject();
        box.addProperty("u", 8);
        box.addProperty("v", 16);
        box.addProperty("texture_width", 512);
        box.addProperty("texture_height", 512);
        region.add("region", box);
        region.addProperty("width", 20);
        region.addProperty("height", 5);
        SpriteIcon sized = SpriteIcon.TEXTURE_CODEC.parse(JsonOps.INSTANCE, region).result().orElse(null);
        if (sized == null || sized.regionU() != 8 || sized.regionV() != 16 || sized.textureWidth() != 512
                || sized.resolvedWidth(71) != 20 || sized.resolvedHeight(8) != 5)
            return "a texture region with a declared size did not read back as it was written";

        JsonObject halfSized = region.deepCopy();
        halfSized.remove("height");
        if (SpriteIcon.TEXTURE_CODEC.parse(JsonOps.INSTANCE, halfSized).result().isPresent())
            return "a sprite icon accepted a width without a height";
        JsonObject atlasWithRegion = region.deepCopy();
        atlasWithRegion.addProperty("sprite", atlas.toString());
        atlasWithRegion.remove("texture");
        if (SpriteIcon.SPRITE_CODEC.parse(JsonOps.INSTANCE, atlasWithRegion).result().isPresent())
            return "a GUI sprite accepted a region, which its atlas already answers";
        if (SpriteIcon.SPRITE_CODEC.parse(JsonOps.INSTANCE, new JsonObject()).result().isPresent())
            return "a sprite icon with neither an id nor a region decoded";

        JsonObject textured = new JsonObject();
        textured.addProperty("background_sprite", atlas.toString());
        textured.add("fill_sprite", region);
        textured.addProperty("width", 71);
        textured.addProperty("height", 5);
        if (TexturedRenderData.CODEC.codec().parse(JsonOps.INSTANCE, textured).result().isEmpty())
            return "a textured bar no longer accepts a sprite pair";
        JsonObject origins = new JsonObject();
        origins.addProperty("sprite_location", sheet.toString());
        origins.addProperty("bar_index", 3);
        if (OriginsRenderData.CODEC.codec().parse(JsonOps.INSTANCE, origins).result().isEmpty())
            return "a boss bar no longer accepts its sheet";
        // Every boss-bar field is optional, so a textured_bar body always decodes as one holding all its defaults;
        // what must not happen is the GUI sprite being read as the sheet it cuts cells from.
        OriginsRenderData asBossBar = OriginsRenderData.CODEC.codec().parse(JsonOps.INSTANCE, textured).result().orElse(null);
        if (asBossBar != null && !OriginsRenderData.DEFAULT_TEXTURE.equals(asBossBar.sheet().id().orElse(null)))
            return "a boss bar read a GUI sprite as the sheet it cuts cells from: " + asBossBar.sheet().id().orElse(null);
        return null;
    }

    // A rule's chance and cooldown, driven through the real dispatcher on two fixtures on the block-break signal:
    // one fires once and is then held back by its own cooldown, and one never fires at all.
    private static String verifyTriggerGates(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        double previous = resources.get(probe);
        resources.set(probe, 0.0D, 0.0D, 100_000.0D, -1L, "closure");
        TriggerContext trigger = new TriggerContext().actor(player).level(level).formula(FormulaContext.of(player));
        long now = level.getGameTime();
        try {
            TriggerDispatcher.publish(TriggerSignals.BLOCK_BREAK, trigger, now);
            double first = resources.get(probe);
            if (!close(first, 1.0D))
                return "a rule whose cooldown had not started added " + first + " instead of 1";
            TriggerDispatcher.publish(TriggerSignals.BLOCK_BREAK, trigger, now);
            if (!close(resources.get(probe), first))
                return "a rule fired again inside its own cooldown";
            // The chance-0 fixture is held back by its own chance and not by the cooldown, which is what the third
            // publish proves: it lands after the 200-tick cooldown has run out, and still adds nothing.
            // The rule's gate reads the level's own clock rather than the timestamp it was published with, so the
            // clock is what has to move; one tick past the end, because the tick it ends on still counts as on cooldown.
            ServerLevelData clock = context.server().getWorldData().overworldData();
            long savedTime = clock.getGameTime();
            clock.setGameTime(now + 201L);
            TriggerDispatcher.publish(TriggerSignals.BLOCK_BREAK, trigger, level.getGameTime());
            double third = resources.get(probe) - first;
            clock.setGameTime(savedTime);
            if (!close(third, 1.0D))
                return "a publish past the cooldown added " + third + " instead of the 1 the cooldowned rule owes";
            TriggerCooldownAttachment cooldowns = player.getExistingData(MxtAttachments.TRIGGER_COOLDOWNS).orElse(null);
            if (cooldowns == null || !cooldowns.isOnCooldown(context.require(MxtResourceKeys.TRIGGER, ProbeDefinitions.id("cooldown_probe")), now + 200L))
                return "the cooldown the last publish armed was not recorded against its own rule";
            if (cooldowns.isOnCooldown(context.require(MxtResourceKeys.TRIGGER, ProbeDefinitions.id("chance_probe")), now + 200L))
                return "a rule that never fired was charged a cooldown anyway";
        } finally {
            resources.set(probe, previous, 0.0D, 100_000.0D, -1L, "probe");
        }
        return null;
    }

    // The component's dedicated producer: it adds to what the stack already carries, and adding the same ability
    // twice leaves one entry.
    private static String verifyItemAbilityProducer(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        Holder<Ability> ability = context.require(MxtResourceKeys.ABILITY, ProbeDefinitions.id("firebolt"));
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD);
        AddAbilityAction action = new AddAbilityAction(List.of(ability));
        action.execute(new ItemActionContext(player, stack, FormulaContext.of(player)));
        action.execute(new ItemActionContext(player, stack, FormulaContext.of(player)));
        ItemAbilitiesComponent component = stack.get(MxtDataComponents.ITEM_ABILITIES.get());
        if (component == null || component.abilities().size() != 1
                || !component.abilities().getFirst().is(ability))
            return "the item-ability action wrote " + (component == null ? "nothing" : component.abilities().size() + " entries");
        if (!ArtifactService.abilities(level.registryAccess(), stack).contains(ability))
            return "the ability the action wrote is not granted by the artifact runtime";
        return null;
    }

    // A formation may declare that its structure is not checked; a structure written beside that is then a field
    // this definition never reads, which is ignored rather than refused.
    private static String verifyStructureCheck(RegistryOps<JsonElement> ops) {
        JsonObject always = new JsonObject();
        always.addProperty("radius", 8);
        always.addProperty("structure_check", "always");
        if (Formation.DIRECT_CODEC.parse(ops, always).result().isEmpty())
            return "structure_check always was refused without a structure";
        JsonObject declared = always.deepCopy();
        JsonArray structure = new JsonArray();
        JsonObject block = new JsonObject();
        block.add("offset", JsonParser.parseString("[0, 0, 0]"));
        block.addProperty("state", "minecraft:stone");
        structure.add(block);
        declared.add("structure", structure);
        if (Formation.DIRECT_CODEC.parse(ops, declared).result().isEmpty())
            return "structure_check always no longer tolerates a structure it would never check";
        JsonObject neither = new JsonObject();
        neither.addProperty("radius", 8);
        if (Formation.DIRECT_CODEC.parse(ops, neither).result().isPresent())
            return "a formation with no structure and no structure_check decoded";
        return null;
    }

    // The bill a period will ask for, as the upkeep report prints it: the whole charge with nothing supplied, and
    // what the formation's own ground leaves once it has supplied some of it.
    private static String verifyUpkeepBill(ProbeContext context) {
        ServerPlayer player = context.player();
        JsonObject json = new JsonObject();
        json.addProperty("radius", 8);
        json.addProperty("structure_check", "always");
        json.add("maintenance_costs", JsonParser.parseString("[{\"id\": \"mxt_test:qi\", \"amount\": 4}]"));
        // The cost names a resource through a holder, so the parse needs the level's registries: plain JsonOps
        // cannot resolve it and the old leg threw here before it ever asserted anything.
        Formation definition = Formation.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, context.registries()), json).result()
                .orElseThrow(() -> new IllegalStateException("The upkeep fixture does not decode"));
        Holder<Aura> qi = context.require(MxtResourceKeys.AURA, ProbeDefinitions.QI);
        CostContext costs = CostContext.account(new ResourceHolderAttachment(), null, FormulaContext.of(player),
                CostOrigin.FORMATION_MAINTENANCE);
        Map<Identifier, Double> owed = FormationService.MaintainRule.remaining(definition, costs, Map.of());
        if (owed.size() != 1 || !close(owed.getOrDefault(ProbeDefinitions.QI, -1.0D), 4.0D))
            return "the upkeep bill with nothing supplied read " + owed;
        Map<Identifier, Double> covered = FormationService.MaintainRule.remaining(definition, costs, Map.of(qi, 1.5D));
        if (!close(covered.getOrDefault(ProbeDefinitions.QI, -1.0D), 2.5D))
            return "1.5 of supplied aura left the bill at " + covered;
        return null;
    }

    // Two timelines through the real service: one whose second beat branches over the third, and one whose wait
    // runs out on a deadline the difficulty scale is not allowed to stretch.
    private static String verifyTimelineControl(ProbeContext context) {
        ServerPlayer player = context.player();
        ServerLevel level = context.level();
        Holder<Resource> probe = context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.id("trigger_probe"));
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        TribulationAttachment data = player.getData(MxtAttachments.TRIBULATION);
        double previous = resources.get(probe);
        data.clear();
        resources.set(probe, 0.0D, 0.0D, 100_000.0D, -1L, "closure");
        try {
            Holder<Tribulation> branch = context.require(MxtResourceKeys.TRIBULATION, ProbeDefinitions.id("probe_branch"));
            if (!TribulationService.start(player, data, branch, level.getGameTime(), FormulaContext.of(player)).isSuccess())
                return "the branching timeline was refused";
            TribulationService.TickResult branchTick = TribulationService.tick(player, data, branch, level.getGameTime(), FormulaContext.of(player));
            if (branchTick.getData() != TribulationService.State.COMPLETED)
                return "the branching timeline ended as " + branchTick.getData();
            double branched = resources.get(probe);
            if (!close(branched, 1001.0D))
                return "the branch added " + branched + " instead of skipping the 100 it jumped over";

            data.clear();
            resources.set(probe, 0.0D, 0.0D, 100_000.0D, -1L, "closure");
            Holder<Tribulation> timeout = context.require(MxtResourceKeys.TRIBULATION, ProbeDefinitions.id("probe_wait_timeout"));
            if (!TribulationService.start(player, data, timeout, level.getGameTime(), FormulaContext.of(player)).isSuccess())
                return "the timed-wait timeline was refused";
            if (TribulationService.tick(player, data, timeout, level.getGameTime(), FormulaContext.of(player)).getData()
                    != TribulationService.State.RUNNING)
                return "a wait with a two-tick timeout did not hold the run on its first tick";
            TribulationService.TickResult waitTick = TribulationService.tick(player, data, timeout, level.getGameTime(), FormulaContext.of(player));
            if (waitTick.getData() != TribulationService.State.COMPLETED)
                return "a wait that ran out finished as " + waitTick.getData();
            if (!close(resources.get(probe), 10.0D))
                return "the beat after a timed-out wait added " + resources.get(probe) + " instead of 10";

            // A branch that jumps back onto itself is a cycle: one tick may only consume so many beats, so the
            // run is parked instead of hanging the server thread, and the beat it never reaches adds nothing.
            data.clear();
            resources.set(probe, 0.0D, 0.0D, 100_000.0D, -1L, "closure");
            Holder<Tribulation> cycle = context.require(MxtResourceKeys.TRIBULATION, ProbeDefinitions.id("probe_branch_cycle"));
            if (!TribulationService.start(player, data, cycle, level.getGameTime(), FormulaContext.of(player)).isSuccess())
                return "the cycling timeline was refused";
            TribulationService.TickResult cycleTick = TribulationService.tick(player, data, cycle, level.getGameTime(), FormulaContext.of(player));
            if (cycleTick.getData() != TribulationService.State.RUNNING)
                return "a cycling branch ended as " + cycleTick.getData() + " instead of parking the run";
            if (!close(resources.get(probe), 0.0D))
                return "a cycling branch reached the beat it loops over, adding " + resources.get(probe);
        } finally {
            data.clear();
            resources.set(probe, previous, 0.0D, 100_000.0D, -1L, "probe");
        }
        return null;
    }

    // A formation's owners are a set, and an instance saved by the version that allowed one owner still reads its
    // owner back. The instance itself cannot be built outside the runtime, so it is read through its own codec.
    private static String verifyOwnerSet() {
        UUID first = UUID.nameUUIDFromBytes("mxt-probe-owner-one".getBytes(StandardCharsets.UTF_8));
        UUID second = UUID.nameUUIDFromBytes("mxt-probe-owner-two".getBytes(StandardCharsets.UTF_8));
        JsonElement shared = FormationOwners.CODEC.encodeStart(JsonOps.INSTANCE, new FormationOwners(List.of(first, second)))
                .result().orElse(null);
        if (shared == null) return "a two-owner set could not be written";
        FormationOwners read = FormationOwners.CODEC.parse(JsonOps.INSTANCE, shared).result().orElse(null);
        if (read == null || read.ids().size() != 2 || !read.primary().orElseThrow().equals(first))
            return "a two-owner set did not read back";
        // The single-owner form is the one UUID vanilla writes everywhere (a four-int array), not a list of one.
        JsonElement single = FormationOwners.CODEC.encodeStart(JsonOps.INSTANCE, FormationOwners.of(first)).result().orElse(null);
        if (single == null || !single.isJsonArray() || single.getAsJsonArray().size() != 4
                || single.getAsJsonArray().get(0).isJsonArray())
            return "a single owner is no longer written as one UUID: " + single;

        JsonObject legacy = new JsonObject();
        legacy.addProperty("formation", ProbeDefinitions.id("formation_owner_probe").toString());
        legacy.addProperty("radius", 8);
        legacy.add("owner", UUIDUtil.CODEC.encodeStart(JsonOps.INSTANCE, first).result().orElseThrow());
        FormationInstance instance = FormationInstance.CODEC.parse(JsonOps.INSTANCE, legacy).result().orElse(null);
        if (instance == null || instance.owners().ids().size() != 1 || !instance.owners().ids().getFirst().equals(first))
            return "an instance saved with one owner did not read that owner back";
        if (!instance.addOwner(second) || instance.owners().ids().size() != 2)
            return "adding a second owner did not change the set";
        if (instance.addOwner(second)) return "adding the same owner twice changed the set again";
        if (!instance.removeOwner(first) || instance.owners().ids().size() != 1)
            return "removing an owner did not change the set";
        if (instance.removeOwner(first)) return "removing an owner that is not listed changed the set";
        return null;
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 1.0E-3D;
    }
}
