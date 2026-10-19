package com.iafenvoy.mxt;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.ServerConfigManager.PermissionChecker;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.PickerCategories;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MiXianTu.MOD_ID)
public final class MiXianTu {
    public static final String MOD_ID = "mxt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MiXianTu(IEventBus bus) {
        // Before any world can load: the registry caches are only rebuilt if they were registered before the first
        // TagsUpdatedEvent, and nothing else touches that class this early.
        RegistryCaches.init();

        ConfigManager.getInstance().registerServerConfigHandler(MxtServerConfig.INSTANCE, PermissionChecker.IS_OPERATOR);

        CuriosIntegration.registerPredicates();

        MxtDataStorages.REGISTRY.register(bus);
        MxtTriggers.REGISTRY.register(bus);
        MxtAbilityTypes.REGISTRY.register(bus);
        MxtAbilityTargetSelectors.REGISTRY.register(bus);
        MxtCosts.REGISTRY.register(bus);
        MxtAttachments.REGISTRY.register(bus);
        MxtAuraMaximums.REGISTRY.register(bus);
        MxtBiEntityActions.REGISTRY.register(bus);
        MxtBiEntityConditions.REGISTRY.register(bus);
        MxtBlockActions.REGISTRY.register(bus);
        MxtBlockConditions.REGISTRY.register(bus);
        MxtBlockEntities.REGISTRY.register(bus);
        MxtBlocks.REGISTRY.register(bus);
        MxtCreativeTabs.REGISTRY.register(bus);
        MxtCriteriaTriggers.REGISTRY.register(bus);
        MxtCurseTypes.REGISTRY.register(bus);
        MxtDamageConditions.REGISTRY.register(bus);
        MxtDataComponents.REGISTRY.register(bus);
        MxtEntityActions.REGISTRY.register(bus);
        MxtEntityConditions.REGISTRY.register(bus);
        MxtEntityDataSerializers.REGISTRY.register(bus);
        MxtEntityTypes.REGISTRY.register(bus);
        MxtFormationActionTypes.REGISTRY.register(bus);
        MxtFormationStrokeTypes.REGISTRY.register(bus);
        MxtFormulaFunctions.REGISTRY.register(bus);
        MxtFormulaVariables.REGISTRY.register(bus);
        MxtIngredientTypes.REGISTRY.register(bus);
        MxtItemActions.REGISTRY.register(bus);
        MxtItemConditions.REGISTRY.register(bus);
        MxtItemMatchers.REGISTRY.register(bus);
        MxtItems.REGISTRY.register(bus);
        MxtLootConditions.REGISTRY.register(bus);
        MxtLootFunctions.REGISTRY.register(bus);
        MxtMenus.REGISTRY.register(bus);
        MxtMountRenders.REGISTRY.register(bus);
        MxtNumberProviders.REGISTRY.register(bus);
        MxtParticleTypes.REGISTRY.register(bus);
        MxtRecipeSerializers.REGISTRY.register(bus);
        MxtRecipeTypes.REGISTRY.register(bus);
        MxtSecretRealmGenerations.REGISTRY.register(bus);
        MxtTalismanTypes.REGISTRY.register(bus);
        MxtResourceBarRenderers.REGISTRY.register(bus);
        MxtResourceBarContexts.REGISTRY.register(bus);
        MxtResourceBarVisibilities.REGISTRY.register(bus);
        MxtResourceValueProviders.REGISTRY.register(bus);
        MxtTimelineEntries.REGISTRY.register(bus);

        // What the picker shows for this mod's registries; the picker itself owns the screen, the command and the
        // packet, so this is the only place our content meets it.
        PickerCategories.register();
    }
}
