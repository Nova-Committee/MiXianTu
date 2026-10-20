package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService;
import com.iafenvoy.mxt.runtime.economy.PlayerTradeService.RequestResult;
import com.iafenvoy.mxt.screen.menu.PlayerTradeMenu;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * One notion of "this process may use the doors kept for reproducing something", with two ways in: a development
 * build is always one, and a release server opens the same doors with a switch. Both ends are checked here - the
 * switch defaults to off, a development build is in dev mode anyway, and the doors read the same answer.
 */
public final class DevModeVerifyProbes {
    private DevModeVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/dev_mode", DevModeVerifyProbes::oneNotion));
    }

    private static void oneNotion(ProbeContext context) {
        context.expect("the debug switch ships off", MxtServerConfig.INSTANCE.debug.enabled.getValue(), false);
        // This run is a development build, so the switch has nothing to add: the answer has to be yes either way.
        context.expect("a development build is in dev mode", MiXianTu.isDevMode(), true);
        // The door that flag gates: one player as both sides of a trade, which a release server refuses with SELF.
        ServerPlayer player = context.player();
        RequestResult self = PlayerTradeService.request(player, player);
        context.expect("a self-trade opens in dev mode", self, RequestResult.STARTED);
        // Leaves no session behind for the probe that runs next, which shares this process.
        if (player.containerMenu instanceof PlayerTradeMenu menu) PlayerTradeService.onMenuClosed(player, menu);
    }
}
