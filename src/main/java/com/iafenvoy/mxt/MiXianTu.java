package com.iafenvoy.mxt;

import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.ServerConfigManager.PermissionChecker;
import com.iafenvoy.mxt.integration.MxtKubeJsEvents;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.integration.CuriosIntegration;
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

        MxtAttachments.REGISTRY.register(bus);
        MxtDataComponents.REGISTRY.register(bus);
        MxtItems.REGISTRY.register(bus);
        MxtBlocks.REGISTRY.register(bus);
        MxtBlockEntities.REGISTRY.register(bus);
        MxtMenus.REGISTRY.register(bus);
        MxtCreativeTabs.REGISTRY.register(bus);
        MxtEntityTypes.REGISTRY.register(bus);
        MxtParticleTypes.REGISTRY.register(bus);
        MxtRecipeSerializers.REGISTRY.register(bus);
        MxtLootFunctions.REGISTRY.register(bus);
        MxtLootConditions.REGISTRY.register(bus);
        MxtCriteriaTriggers.REGISTRY.register(bus);
        MxtAbilityTypes.REGISTRY.register(bus);
        MxtCurseTypes.REGISTRY.register(bus);
        MxtAbilityComponents.REGISTRY.register(bus);
        MxtAbilityTriggers.REGISTRY.register(bus);
        MxtNumberProviders.REGISTRY.register(bus);
        MxtAuraMaximums.REGISTRY.register(bus);
        MxtFormulaFunctions.REGISTRY.register(bus);
        MxtFormulaVariables.REGISTRY.register(bus);
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
        MxtBadges.REGISTRY.register(bus);
        MxtItemMatchers.REGISTRY.register(bus);
        MxtCreatureSpawnConditions.REGISTRY.register(bus);
        MxtCultivationConditions.REGISTRY.register(bus);

        MxtKubeJsEvents.register();
    }
}
