package com.iafenvoy.mxt.registry;

import net.neoforged.bus.api.IEventBus;

/**
 * Binds every deferred registry to the mod event bus in one place.
 */
public final class MxtRegistrations {
    private MxtRegistrations() {
    }

    public static void register(IEventBus bus) {
        MxtAttachments.REGISTRY.register(bus);
        MxtDataComponents.REGISTRY.register(bus);
        MxtItems.REGISTRY.register(bus);
        MxtBlocks.REGISTRY.register(bus);
        MxtBlockEntities.REGISTRY.register(bus);
        MxtMenus.REGISTRY.register(bus);
        MxtCreativeTabs.REGISTRY.register(bus);
        MxtEntityTypes.REGISTRY.register(bus);
        MxtRecipeSerializers.REGISTRY.register(bus);
        MxtLootFunctions.REGISTRY.register(bus);
        MxtLootConditions.REGISTRY.register(bus);
        MxtCriteriaTriggers.REGISTRY.register(bus);
        MxtAbilityTypes.REGISTRY.register(bus);
        MxtCurseTypes.REGISTRY.register(bus);
        MxtAbilityComponents.REGISTRY.register(bus);
        MxtAbilityTriggers.REGISTRY.register(bus);
        MxtNumberProviders.REGISTRY.register(bus);
        MxtValueModifiers.REGISTRY.register(bus);
        MxtResourceValueProviders.REGISTRY.register(bus);
        MxtEntityActions.REGISTRY.register(bus);
        MxtEntityConditions.REGISTRY.register(bus);
        MxtBiEntityActions.REGISTRY.register(bus);
        MxtBiEntityConditions.REGISTRY.register(bus);
        MxtBlockActions.REGISTRY.register(bus);
        MxtBlockConditions.REGISTRY.register(bus);
        MxtItemActions.REGISTRY.register(bus);
        MxtItemConditions.REGISTRY.register(bus);
        MxtDamageConditions.REGISTRY.register(bus);
        MxtResourceBarRenderers.REGISTRY.register(bus);
        MxtResourceBarVisibilities.REGISTRY.register(bus);
        MxtDomainBehaviors.FORGING.register(bus);
        MxtDomainBehaviors.FORMATION.register(bus);
        MxtDomainBehaviors.TRIBULATION.register(bus);
        MxtDomainBehaviors.CULTIVATION.register(bus);
        MxtDomainBehaviors.CONTRACT.register(bus);
        MxtDomainBehaviors.ALCHEMY.register(bus);
        MxtDomainBehaviors.REALM.register(bus);
        MxtDomainBehaviors.ARTIFACT.register(bus);
        MxtCreatureSpawnConditions.REGISTRY.register(bus);
        MxtCultivationConditions.REGISTRY.register(bus);
    }
}
