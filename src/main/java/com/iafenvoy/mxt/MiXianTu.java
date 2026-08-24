package com.iafenvoy.mxt;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.ServerConfigManager.PermissionChecker;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.compat.CuriosIntegration;
import com.iafenvoy.mxt.registry.*;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(MiXianTu.MOD_ID)
public final class MiXianTu {
    public static final String MOD_ID = "mxt";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MiXianTu(IEventBus bus) {
        ConfigManager.getInstance().registerServerConfigHandler(MxtServerConfig.INSTANCE, PermissionChecker.IS_OPERATOR);

        CuriosIntegration.registerPredicates();

        MxtAbilityComponents.REGISTRY.register(bus);
        MxtAbilityTriggers.REGISTRY.register(bus);
        MxtAbilityTypes.REGISTRY.register(bus);
        MxtAbilityTargetSelectors.REGISTRY.register(bus);
        MxtCosts.REGISTRY.register(bus);
        MxtAttachments.REGISTRY.register(bus);
        MxtAuraMaximums.REGISTRY.register(bus);
        MxtBadges.REGISTRY.register(bus);
        MxtBiEntityActions.REGISTRY.register(bus);
        MxtBiEntityConditions.REGISTRY.register(bus);
        MxtBlockActions.REGISTRY.register(bus);
        MxtBlockConditions.REGISTRY.register(bus);
        MxtBlockEntities.REGISTRY.register(bus);
        MxtBlocks.REGISTRY.register(bus);
        MxtCreativeTabs.REGISTRY.register(bus);
        MxtCreatureSpawnConditions.REGISTRY.register(bus);
        MxtCriteriaTriggers.REGISTRY.register(bus);
        MxtCultivationConditions.REGISTRY.register(bus);
        MxtCurseTypes.REGISTRY.register(bus);
        MxtDamageConditions.REGISTRY.register(bus);
        MxtDataComponents.REGISTRY.register(bus);
        MxtEntityActions.REGISTRY.register(bus);
        MxtEntityConditions.REGISTRY.register(bus);
        MxtEntityTypes.REGISTRY.register(bus);
        MxtFormulaFunctions.REGISTRY.register(bus);
        MxtFormulaVariables.REGISTRY.register(bus);
        MxtItemActions.REGISTRY.register(bus);
        MxtItemConditions.REGISTRY.register(bus);
        MxtItemMatchers.REGISTRY.register(bus);
        MxtItems.REGISTRY.register(bus);
        MxtLootConditions.REGISTRY.register(bus);
        MxtLootFunctions.REGISTRY.register(bus);
        MxtMenus.REGISTRY.register(bus);
        MxtNumberProviders.REGISTRY.register(bus);
        MxtParticleTypes.REGISTRY.register(bus);
        MxtRecipeSerializers.REGISTRY.register(bus);
        MxtRecipeTypes.REGISTRY.register(bus);
        MxtResourceBarRenderers.REGISTRY.register(bus);
        MxtResourceBarContexts.REGISTRY.register(bus);
        MxtResourceBarVisibilities.REGISTRY.register(bus);
        MxtResourceValueProviders.REGISTRY.register(bus);
    }
}
