package com.iafenvoy.mxt.probe.world;

import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.probe.ProbeDefinitions;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.world.SecretRealmEntryLocator.Arrival;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.phys.Vec3;

import java.util.List;

// Registered only on a physical client; the disposable body never joins the client world.
public final class SecretRealmClientProbes {
    private SecretRealmClientProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("world/secret_realm/client_boundary", SecretRealmClientProbes::boundary));
    }

    private static void boundary(ProbeContext context) {
        ClientLevel level = Minecraft.getInstance().level;
        assert level != null;
        Pig traveller = new Pig(EntityType.PIG, level);
        Holder<SecretRealm> definition = context.require(MxtResourceKeys.SECRET_REALM, ProbeDefinitions.TRIAL_REALM);
        SecretRealmRecord selected = SecretRealmService.plan(definition, 0, 1L, 0L);
        Arrival arrival = new Arrival(Vec3.ZERO, 0, 0);
        context.expect("client explicit entry is refused", SecretRealmService.enter(traveller, context.server(), definition).getFailure().orElse(null), Failures.SERVER_ONLY);
        context.expect("client portal entry is refused", SecretRealmService.enterFromPortal(traveller, definition, Vec3.ZERO).getFailure().orElse(null), Failures.SERVER_ONLY);
        context.expect("client instance entry is refused", SecretRealmService.enterInstance(traveller, selected, arrival, null).getFailure().orElse(null), Failures.SERVER_ONLY);
        context.expect("client coordinate travel is refused", SecretRealmService.moveToPosition(traveller, context.level(), arrival).getFailure().orElse(null), Failures.SERVER_ONLY);
        context.expect("client exit is refused", SecretRealmService.exit(traveller).getFailure().orElse(null), Failures.SERVER_ONLY);
        context.check("client orphan check does nothing", !SecretRealmService.returnIfOrphaned(traveller));
        context.check("refusals create no travel attachment", traveller.getExistingData(MxtAttachments.SECRET_REALM_TRAVEL).isEmpty());
    }
}
