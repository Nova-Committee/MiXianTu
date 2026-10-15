package com.iafenvoy.mxt.probe;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.TechniqueAttachment;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.SecretRealmTokenComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilityGrantService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.testmod.MxtTestItems;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;

/**
 * The Qingxiao scenario items the probe bodies start from.
 *
 * <p>Everything the old {@code /mxt_test kit} leg handed out lives here instead: a test that needs the scenario asks
 * for it, so nothing has to be run by hand before the assertion. The kit is a fixture rather than a test of its own,
 * because giving an item proves nothing on its own.
 */
public final class ProbeKit {
    private ProbeKit() {
    }

    public static void scenario(ProbeContext context) {
        ServerPlayer player = context.player();
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        TechniqueAttachment techniques = player.getData(MxtAttachments.TECHNIQUE);
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        FormulaContext formula = FormulaContext.of(player);

        grantIdentity(context, player, techniques, formula);
        boolean realm = CultivationService.setRealm(spirit, context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.QI_REFINING))
                && CultivationService.setRealm(spirit, context.require(MxtResourceKeys.REALM_STAGE, ProbeDefinitions.SPIRIT_POWER_REFINING));
        context.check("kit sets the realm chain", realm);
        spirit.setCultivationProgress(requireProfile(context, ProbeDefinitions.QI), 80.0D);
        ensureResource(player, resources, context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.QI), 200.0D);
        ensureResource(player, resources, context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.SPIRIT_POWER), 80.0D);
        ensureResource(player, resources, context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.WATER_POWER), 80.0D);
        ensureResource(player, resources, context.require(MxtResourceKeys.RESOURCE, ProbeDefinitions.SOUL_POWER), 20.0D);

        // These attachments are mutable; explicit sync is handled by the attachment dispatcher.
        player.setData(MxtAttachments.RESOURCE_HOLDER, resources);
        player.setData(MxtAttachments.ABILITY_HOLDER, player.getData(MxtAttachments.ABILITY_HOLDER));

        give(player, MxtTestItems.QINGXIAO_SPIRIT_CRYSTAL.toStack(8));
        give(player, MxtItems.SPIRIT_STONE.toStack(3));
        give(player, new ItemStack(Items.DIAMOND_SWORD));
        give(player, new ItemStack(Items.HONEY_BOTTLE, 2));
        give(player, new ItemStack(Items.APPLE));
        give(player, formationPlate(context));
        give(player, secretRealmToken(context));
        give(player, contractScroll(context));
        // The artifact fixtures, handed over already bound: nothing in the test pack refines an item yet, and an
        // unowned artifact refuses flight, storage and mxt:owned_by.
        giveArtifact(context, Items.IRON_SWORD);
        giveArtifact(context, Items.AMETHYST_SHARD);
        giveArtifact(context, Items.PRISMARINE_SHARD);
        giveArtifact(context, Items.BELL);
        // The second vehicle, which is the three-seat sitting one: it is only claimable by an artifact nobody else
        // names, so it needs its own item.
        giveArtifact(context, Items.HEART_OF_THE_SEA);
        // Learning a manual is the only in-game way to a skill, and a blank jade slip teaches nothing: these carry
        // the two flight techniques, so holding one down for two seconds grants the skill.
        give(player, techniqueCarrier(context, "sword_control_manual"));
        give(player, techniqueCarrier(context, "offhand_flight_manual"));
    }

    /**
     * The realm chain the scenario grades on, which is this aura profile's own chain.
     */
    public static Holder<Aura> requireProfile(ProbeContext context, Identifier resource) {
        return MxtDatapackRegistries.holder(context.registries(), MxtResourceKeys.AURA, resource)
                .orElseThrow(() -> new IllegalStateException("Missing Qingxiao cultivation profile " + resource));
    }

    // The carrier the mod offers for a technique, which is what actually teaches it.
    public static ItemStack techniqueCarrier(ProbeContext context, String path) {
        return ItemBindingService.techniqueCarrier(context.registries(),
                context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.id(path)));
    }

    public static void grantIdentity(ProbeContext context, ServerPlayer player, TechniqueAttachment techniques,
                                     FormulaContext formula) {
        CultivationIdentityService.grantSpiritRoot(player, context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.ROOT));
        CultivationIdentityService.grantSpiritRoot(player, context.require(MxtResourceKeys.SPIRIT_ROOT, ProbeDefinitions.WATER_ROOT));
        CultivationIdentityService.grantPhysique(player, context.require(MxtResourceKeys.PHYSIQUE, ProbeDefinitions.PHYSIQUE), formula);
        Holder<Technique> technique = context.require(MxtResourceKeys.TECHNIQUE, ProbeDefinitions.TECHNIQUE);
        TechniqueService.learn(player, techniques, technique, formula);
        techniques.addLearnedTechnique(technique);
        AbilityGrantService.recalculate(player);
        ProbeDefinitions.TEST_ACTIVE_ABILITIES.forEach(abilityId ->
                player.getData(MxtAttachments.ABILITY_HOLDER).grant(context.require(MxtResourceKeys.ABILITY, abilityId),
                        ProbeDefinitions.TEST_ABILITY_SOURCE));
        // Granting an ability does not register its triggers by itself; the runtime index is only rebuilt where
        // the ability sources actually change.
        AbilityEventBridge.rebuildTriggerSubscriptions(player);
    }

    public static void ensureResource(LivingEntity holder, ResourceHolderAttachment resources, Holder<Resource> resource,
                                      double minimum) {
        FormulaContext context = ResourceService.formulaContext(holder, resource, FormulaContext.of(holder));
        ResourceService.initialize(resources, resource, context);
        double missing = minimum - resources.get(resource);
        if (missing > 0.0D) ResourceService.change(resources, resource, missing, context);
    }

    public static ItemStack formationPlate(ProbeContext context) {
        ItemStack stack = MxtItems.FORMATION_PLATE.toStack();
        // An empty allow list, which is the shipped plate: unrestricted unless the server option says otherwise,
        // and therefore bound to whatever the kit's own formation is.
        stack.set(MxtDataComponents.FORMATION_PLATE, new FormationPlateComponent(List.of(),
                Optional.of(context.require(MxtResourceKeys.FORMATION, ProbeDefinitions.FORMATION))));
        return stack;
    }

    public static ItemStack secretRealmToken(ProbeContext context) {
        ItemStack stack = MxtItems.SECRET_REALM_TOKEN.toStack();
        stack.set(MxtDataComponents.SECRET_REALM_TOKEN, new SecretRealmTokenComponent(
                Optional.of(context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM))));
        return stack;
    }

    public static ItemStack contractScroll(ProbeContext context) {
        ItemStack stack = MxtItems.CONTRACT_SCROLL.toStack();
        stack.set(MxtDataComponents.CONTRACT_SCROLL, new ContractScrollComponent(
                Optional.of(context.require(MxtResourceKeys.CONTRACT_TYPE, ProbeDefinitions.CONTRACT))));
        return stack;
    }

    // Gives one artifact fixture bound to the player, since the test pack has no other way to refine an item.
    public static void giveArtifact(ProbeContext context, Item item) {
        ItemStack stack = new ItemStack(item);
        ArtifactService.refine(stack, context.player());
        give(context.player(), stack);
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().placeItemBackInInventory(stack);
    }
}
