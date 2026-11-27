package com.iafenvoy.mxt.probe;

import com.iafenvoy.mxt.probe.ability.MessageProbes;
import com.iafenvoy.mxt.probe.alchemy.PillProbes;
import com.iafenvoy.mxt.probe.artifact.ArtifactProbes;
import com.iafenvoy.mxt.probe.artifact.ArtifactMountClientProbes;
import com.iafenvoy.mxt.probe.combat.DamageProbes;
import com.iafenvoy.mxt.probe.combat.ElementProbes;
import com.iafenvoy.mxt.probe.creature.ContractProbes;
import com.iafenvoy.mxt.probe.creature.CreatureProfileProbes;
import com.iafenvoy.mxt.probe.creature.PerchProbes;
import com.iafenvoy.mxt.probe.creature.ProgressionProbes;
import com.iafenvoy.mxt.probe.cultivation.CultivationProbes;
import com.iafenvoy.mxt.probe.cultivation.IdentityProbes;
import com.iafenvoy.mxt.probe.cultivation.SpiritStoneBagProbes;
import com.iafenvoy.mxt.probe.cultivation.SpiritStoneBagTooltipProbes;
import com.iafenvoy.mxt.probe.formation.FormationProbes;
import com.iafenvoy.mxt.probe.herb.HerbProbes;
import com.iafenvoy.mxt.probe.item.IdentificationProbes;
import com.iafenvoy.mxt.probe.item.JadeSlipProbes;
import com.iafenvoy.mxt.probe.item.QualityProbes;
import com.iafenvoy.mxt.probe.item.WeaponBindingProbes;
import com.iafenvoy.mxt.probe.lifespan.LifespanProbes;
import com.iafenvoy.mxt.probe.lifespan.ReincarnationOptionalProbes;
import com.iafenvoy.mxt.probe.verify.*;
import com.iafenvoy.mxt.probe.world.RiftAnchorProbes;
import com.iafenvoy.mxt.probe.world.RiftProbes;
import com.iafenvoy.mxt.probe.world.SecretRealmProbes;
import com.iafenvoy.mxt.probe.world.SecretRealmClientProbes;
import com.iafenvoy.mxt.probe.world.SecretRealmTravelProbes;
import com.iafenvoy.mxt.probe.world.SoulProbes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The one place the probe domains are listed, and the two registrations that turn them into game tests.
 *
 * <p>The function registry is filled from {@code RegisterEvent} because the built in {@code test_function} registry is
 * unfrozen for mod loading; the test instances themselves are built from {@code RegisterGameTestsEvent}, which only
 * fires when the game test server (or a development launch) is starting. Both go through vanilla's
 * {@code minecraft:function} instance type, so the {@code test_instance} registry stays encodable for the client.
 *
 * <p>Every probe gets a test environment of its own. {@code GameTestBatchFactory} groups tests by environment and runs
 * each group at the same time, so a distinct environment per probe is what keeps a probe that writes the server config,
 * the level clock or a whole dimension from racing another one.
 */
public final class ProbeTests {
    private static final List<Supplier<List<Probe>>> DOMAINS = List.<Supplier<List<Probe>>>of(
            MessageProbes::probes,
            FormationProbes::probes,
            FormationPreviewVerifyProbes::probes,
            PonderConfigVerifyProbes::probes,
            PillProbes::probes,
            ArtifactProbes::probes,
            DamageProbes::probes,
            ElementProbes::probes,
            ContractProbes::probes,
            CreatureProfileProbes::probes,
            PerchProbes::probes,
            ProgressionProbes::probes,
            CultivationProbes::probes,
            SpiritStoneBagProbes::probes,
            IdentityProbes::probes,
            HerbProbes::probes,
            JadeSlipProbes::probes,
            IdentificationProbes::probes,
            ItemUseCycleVerifyProbes::probes,
            QualityProbes::probes,
            WorkstationBoundaryProbes::probes,
            WeaponBindingProbes::probes,
            ReincarnationOptionalProbes::probes,
            AbilityCooldownVerifyProbes::probes,
            SaturnBoundaryProbes::probes,
            AuraVerifyProbes::probes,
            AuraHudVerifyProbes::probes,
            BreakthroughPaymentVerifyProbes::probes,
            ResourceBarVerifyProbes::probes,
            HoldAnimationVerifyProbes::probes,
            AuraDoubleVerifyProbes::probes,
            AuraHandlerVerifyProbes::probes,
            AuiBindingVerifyProbes::probes,
            AuiGeometryVerifyProbes::probes,
            AuiThemeVerifyProbes::probes,
            BlockAuraRefreshVerifyProbes::probes,
            LightBeamParticleVerifyProbes::probes,
            StormCloudVerifyProbes::probes,
            WorldEffectsVerifyProbes::probes,
            ClosureVerifyProbes::probes,
            CostVerifyProbes::probes,
            CostStoreVerifyProbes::probes,
            CostSettlementVerifyProbes::probes,
            CultivationDistributionVerifyProbes::probes,
            TickDispatcherVerifyProbes::probes,
            EconomyVerifyProbes::probes,
            SecurityBoundaryProbes::probes,
            MinorStageVerifyProbes::probes,
            ServerProfilingVerifyProbes::probes,
            ProfilerGuiVerifyProbes::probes,
            ProfilerChartVerifyProbes::probes,
            TalismanVerifyProbes::probes,
            ScopeVerifyProbes::probes,
            LootTypeVerifyProbes::probes,
            RegistryMatchVerifyProbes::probes,
            FriendVerifyProbes::probes,
            StoreVerifyProbes::probes,
            DevModeVerifyProbes::probes,
            TargetSelectorVerifyProbes::probes,
            WheelVerifyProbes::probes,
            RiftProbes::probes,
            SoulProbes::probes,
            RiftAnchorProbes::probes,
            SecretRealmProbes::probes,
            SecretRealmTravelProbes::probes
    );

    private ProbeTests() {
    }

    public static List<Probe> all() {
        List<Probe> probes = new ArrayList<>();
        DOMAINS.forEach(domain -> probes.addAll(domain.get()));
        if (FMLEnvironment.getDist().isClient()) {
            probes.addAll(ArtifactMountClientProbes.probes());
            probes.addAll(PonderRenderVerifyProbes.probes());
            probes.addAll(PonderSceneVerifyProbes.probes());
            probes.addAll(SecretRealmClientProbes.probes());
            probes.addAll(SpiritStoneBagTooltipProbes.probes());
        }
        if (ModList.get().isLoaded("reincarnation")) probes.addAll(LifespanProbes.probes());
        return List.copyOf(probes);
    }

    public static void registerFunctions(RegisterEvent event) {
        registerFunctions(event, all());
    }

    public static void registerFunctions(RegisterEvent event, List<Probe> probes) {
        for (Probe probe : probes)
            event.register(Registries.TEST_FUNCTION, probe.functionKey().identifier(), () -> helper -> ProbeContext.run(probe, helper));
    }

    public static void registerTests(RegisterGameTestsEvent event) {
        registerTests(event, all());
    }

    public static void registerTests(RegisterGameTestsEvent event, List<Probe> probes) {
        for (Probe probe : probes) {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(probe.id().getNamespace(), "isolated/" + probe.id().getPath()), new TestEnvironmentDefinition.AllOf(List.of()));
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, probe.structure(), probe.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, true, 0);
            event.registerTest(probe.id(), new FunctionGameTestInstance(probe.functionKey(), data));
        }
    }
}
