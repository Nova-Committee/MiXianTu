package com.iafenvoy.mxt.screen.ponder;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.ponder.PonderPickingCompatibility;
import com.iafenvoy.mxt.compat.ponder.PonderTooltipCompatibility;
import com.iafenvoy.mxt.data.formation.Formation.RequiredBlock;
import com.iafenvoy.mxt.runtime.formation.FormationPreview;
import com.mojang.blaze3d.platform.Window;
import net.createmod.catnip.api.client.gui.ScreenOpener;
import net.createmod.ponder.api.client.PonderIndex;
import net.createmod.ponder.api.client.registration.PonderPlugin;
import net.createmod.ponder.api.client.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.impl.client.gui.PonderUI;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(Dist.CLIENT)
public final class FormationPonder {
    private static final Identifier COMPONENT = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "formation_plate");
    private static final PonderPlugin PLUGIN = new PonderPlugin() {
        @Override
        public String getModId() {
            return MiXianTu.MOD_ID;
        }

        @Override
        public void registerScenes(PonderSceneRegistrationHelper<Identifier> helper) {
            FormationPonderScene content = current;
            if (content != null) helper.addStoryBoard(COMPONENT, "formation_preview", content::program);
        }
    };
    private static FormationPonderScene current;
    private static PonderUI activeScreen;

    private FormationPonder() {
    }

    public static void register() {
        PonderIndex.addPlugin(PLUGIN);
    }

    public static void open(Component title, List<RequiredBlock> structure) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        Optional<FormationPreview> prepared = FormationPreview.prepare(structure);
        if (prepared.isEmpty()) {
            minecraft.gui.getChat().addClientSystemMessage(Component.translatable("command.mxt.formation.show.unsupported", FormationPreview.MAX_SPAN));
            return;
        }
        current = new FormationPonderScene(title.copy(), prepared.get());
        PonderIndex.reload();
        PonderUI screen = PonderUI.of(COMPONENT);
        screen.getSubject().set(DataComponents.CUSTOM_NAME, title.copy());
        activeScreen = screen;
        ScreenOpener.clearStack();
        // transitionTo initializes Catnip's scale animation; open() alone leaves it magnified at 1.5x.
        ScreenOpener.transitionTo(screen);
        ScreenOpener.clearStack();
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Pre event) {
        if (event.getScreen() == activeScreen) PonderPickingCompatibility.update(activeScreen);
    }

    @SubscribeEvent
    public static void tooltip(RenderTooltipEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (activeScreen == null || minecraft.screen != activeScreen) return;
        Window window = minecraft.getWindow();
        PonderTooltipCompatibility.reposition(event,
                (int) (minecraft.mouseHandler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth()),
                (int) (minecraft.mouseHandler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight()));
    }

    @SubscribeEvent
    public static void closed(ScreenEvent.Closing event) {
        if (event.getScreen() == activeScreen) clear();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    private static void clear() {
        if (current == null) return;
        current = null;
        activeScreen = null;
        PonderIndex.reload();
    }
}
