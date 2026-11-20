package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.testmod.MxtTestMod;
import net.createmod.catnip.api.client.gui.render.pip.GuiBlockEntityRenderState;
import net.createmod.catnip.api.client.gui.render.pip.GuiBlockModelRenderState;
import net.createmod.catnip.api.client.gui.render.pip.GuiFluidStateRenderState;
import net.createmod.catnip.impl.neoforge.service.NeoForgeClientHooksHelper;
import net.createmod.ponder.impl.client.gui.PonderSceneRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

import java.util.List;
import java.util.Set;

@EventBusSubscriber(modid = MxtTestMod.MOD_ID, value = Dist.CLIENT)
public final class PonderRenderVerifyProbes {
    private static volatile Set<Class<?>> factoriesAtCollection;
    private static boolean clientSetupSeen;
    private static volatile boolean collectedBeforeSetup;

    private PonderRenderVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/formation_preview/client_registration", PonderRenderVerifyProbes::registration));
    }

    @SubscribeEvent
    public static void collecting(RegisterPictureInPictureRenderersEvent event) {
        factoriesAtCollection = Set.copyOf(NeoForgeClientHooksHelper.PIP_RENDERERS.keySet());
        collectedBeforeSetup = !clientSetupSeen;
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        clientSetupSeen = true;
    }

    private static void registration(ProbeContext context) {
        Set<Class<?>> factories = factoriesAtCollection;
        leg(context, "NeoForge collection event observed", factories != null);
        assert factories != null;
        leg(context, "factories collected before client setup", collectedBeforeSetup);
        leg(context, "Ponder scene available at collection", factories.contains(PonderSceneRenderState.class));
        leg(context, "Catnip block model available at collection", factories.contains(GuiBlockModelRenderState.class));
        leg(context, "Catnip block entity available at collection", factories.contains(GuiBlockEntityRenderState.class));
        leg(context, "Catnip fluid available at collection", factories.contains(GuiFluidStateRenderState.class));
    }

    private static void leg(ProbeContext context, String name, boolean success) {
        context.check(name, success);
        context.note("ponder render " + name + ": " + (success ? "OK" : "MISMATCH"));
    }
}
