package com.iafenvoy.mxt.probe;

import com.iafenvoy.mxt.probe.ability.MessageProbes;
import com.iafenvoy.mxt.probe.ability.PickerProbes;
import com.iafenvoy.mxt.probe.ability.SwordAuraProbes;
import com.iafenvoy.mxt.probe.alchemy.AlchemyGateProbes;
import com.iafenvoy.mxt.probe.alchemy.AlchemyProbes;
import com.iafenvoy.mxt.probe.alchemy.PillProbes;
import com.iafenvoy.mxt.probe.artifact.ArtifactProbes;
import com.iafenvoy.mxt.probe.combat.DamageProbes;
import com.iafenvoy.mxt.probe.combat.ElementProbes;
import com.iafenvoy.mxt.probe.creature.ContractProbes;
import com.iafenvoy.mxt.probe.creature.PerchProbes;
import com.iafenvoy.mxt.probe.creature.ProgressionProbes;
import com.iafenvoy.mxt.probe.cultivation.CultivationProbes;
import com.iafenvoy.mxt.probe.cultivation.IdentityProbes;
import com.iafenvoy.mxt.probe.formation.FormationProbes;
import com.iafenvoy.mxt.probe.herb.HerbProbes;
import com.iafenvoy.mxt.probe.item.DrawingProbes;
import com.iafenvoy.mxt.probe.item.JadeSlipProbes;
import com.iafenvoy.mxt.probe.item.QualityProbes;
import com.iafenvoy.mxt.probe.item.WeaponBindingProbes;
import com.iafenvoy.mxt.probe.lifespan.LifespanProbes;
import com.iafenvoy.mxt.probe.screen.InformationProbes;
import com.iafenvoy.mxt.probe.talisman.StationProbes;
import com.iafenvoy.mxt.probe.talisman.TalismanProbes;
import com.iafenvoy.mxt.probe.verify.AbilityCooldownVerifyProbes;
import com.iafenvoy.mxt.probe.verify.AuraVerifyProbes;
import com.iafenvoy.mxt.probe.verify.BlockAuraRefreshVerifyProbes;
import com.iafenvoy.mxt.probe.verify.ClosureVerifyProbes;
import com.iafenvoy.mxt.probe.verify.CloseableResourcesVerifyProbes;
import com.iafenvoy.mxt.probe.verify.CostStoreVerifyProbes;
import com.iafenvoy.mxt.probe.verify.CostSettlementVerifyProbes;
import com.iafenvoy.mxt.probe.verify.CostVerifyProbes;
import com.iafenvoy.mxt.probe.verify.CultivationDistributionVerifyProbes;
import com.iafenvoy.mxt.probe.verify.DevModeVerifyProbes;
import com.iafenvoy.mxt.probe.verify.EconomyVerifyProbes;
import com.iafenvoy.mxt.probe.verify.FriendVerifyProbes;
import com.iafenvoy.mxt.probe.verify.GeneratedNameVerifyProbes;
import com.iafenvoy.mxt.probe.verify.LightBeamParticleVerifyProbes;
import com.iafenvoy.mxt.probe.verify.MinorStageVerifyProbes;
import com.iafenvoy.mxt.probe.verify.ProfilerVerifyProbes;
import com.iafenvoy.mxt.probe.verify.ProfilerChartVerifyProbes;
import com.iafenvoy.mxt.probe.verify.ServerProfilingVerifyProbes;
import com.iafenvoy.mxt.probe.verify.ProfilerGuiVerifyProbes;
import com.iafenvoy.mxt.probe.verify.ScopeVerifyProbes;
import com.iafenvoy.mxt.probe.verify.StoreVerifyProbes;
import com.iafenvoy.mxt.probe.verify.TalismanSessionVerifyProbes;
import com.iafenvoy.mxt.probe.verify.TalismanVerifyProbes;
import com.iafenvoy.mxt.probe.verify.TargetSelectorVerifyProbes;
import com.iafenvoy.mxt.probe.verify.TickDispatcherVerifyProbes;
import com.iafenvoy.mxt.probe.verify.WheelVerifyProbes;
import com.iafenvoy.mxt.probe.world.RiftProbes;
import com.iafenvoy.mxt.probe.world.SecretRealmProbes;
import com.iafenvoy.mxt.testmod.MxtTestMod;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
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
    private static final List<Supplier<List<Probe>>> DOMAINS = List.of(
            SmokeProbes::probes,
            MessageProbes::probes,
            FormationProbes::probes,
            PickerProbes::probes,
            SwordAuraProbes::probes,
            AlchemyGateProbes::probes,
            AlchemyProbes::probes,
            PillProbes::probes,
            ArtifactProbes::probes,
            DamageProbes::probes,
            ElementProbes::probes,
            ContractProbes::probes,
            PerchProbes::probes,
            ProgressionProbes::probes,
            CultivationProbes::probes,
            IdentityProbes::probes,
            HerbProbes::probes,
            DrawingProbes::probes,
            JadeSlipProbes::probes,
            QualityProbes::probes,
            WeaponBindingProbes::probes,
            LifespanProbes::probes,
            InformationProbes::probes,
            StationProbes::probes,
            TalismanProbes::probes,
            AbilityCooldownVerifyProbes::probes,
            AuraVerifyProbes::probes,
            BlockAuraRefreshVerifyProbes::probes,
            LightBeamParticleVerifyProbes::probes,
            CloseableResourcesVerifyProbes::probes,
            ClosureVerifyProbes::probes,
            CostVerifyProbes::probes,
            CostStoreVerifyProbes::probes,
            CostSettlementVerifyProbes::probes,
            CultivationDistributionVerifyProbes::probes,
            TickDispatcherVerifyProbes::probes,
            EconomyVerifyProbes::probes,
            GeneratedNameVerifyProbes::probes,
            MinorStageVerifyProbes::probes,
            ProfilerVerifyProbes::probes,
            ServerProfilingVerifyProbes::probes,
            ProfilerGuiVerifyProbes::probes,
            ProfilerChartVerifyProbes::probes,
            TalismanVerifyProbes::probes,
            TalismanSessionVerifyProbes::probes,
            ScopeVerifyProbes::probes,
            FriendVerifyProbes::probes,
            StoreVerifyProbes::probes,
            DevModeVerifyProbes::probes,
            TargetSelectorVerifyProbes::probes,
            WheelVerifyProbes::probes,
            RiftProbes::probes,
            SecretRealmProbes::probes
    );

    private ProbeTests() {
    }

    public static List<Probe> all() {
        List<Probe> probes = new ArrayList<>();
        DOMAINS.forEach(domain -> probes.addAll(domain.get()));
        return List.copyOf(probes);
    }

    public static void registerFunctions(RegisterEvent event) {
        for (Probe probe : all())
            event.register(
                    Registries.TEST_FUNCTION, probe.functionKey().identifier(),
                    () -> helper -> ProbeContext.run(probe, helper));
    }

    public static void registerTests(RegisterGameTestsEvent event) {
        for (Probe probe : all()) {
            Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                    Identifier.fromNamespaceAndPath(MxtTestMod.MOD_ID, "isolated/" + probe.id().getPath()),
                    new TestEnvironmentDefinition.AllOf(List.of()));
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, probe.structure(),
                    probe.maxTicks(), 0, true, Rotation.NONE, false, 1, 1, true, 0);
            event.registerTest(probe.id(), new FunctionGameTestInstance(probe.functionKey(), data));
        }
    }
}
