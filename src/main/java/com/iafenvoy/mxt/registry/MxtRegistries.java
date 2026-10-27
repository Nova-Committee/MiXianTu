package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.iafenvoy.mxt.data.aura.AuraMaximum;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationStroke;
import com.iafenvoy.mxt.data.secretrealm.SecretRealmGeneration;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;

import java.util.LinkedList;
import java.util.List;

/**
 * The code registries MiXianTu still owns. Everything generic - actions, conditions, costs, storage, triggers, the
 * formula, resource bars - lives in Saturn now; what remains here is the vocabulary only this mod has.
 */
@EventBusSubscriber
public final class MxtRegistries {
    private static final List<DefaultedRegistry<?>> REGISTRIES = new LinkedList<>();

    public static final DefaultedRegistry<MapCodec<? extends TalismanType>> TALISMAN_TYPE = create("empty", MxtResourceKeys.TALISMAN_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends CurseType>> CURSE_TYPE = create("empty", MxtResourceKeys.CURSE_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends AuraMaximum>> AURA_MAXIMUM_TYPE = create("initial_multiplier", MxtResourceKeys.AURA_MAXIMUM_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends MountRender>> MOUNT_RENDER_TYPE = create("item", MxtResourceKeys.MOUNT_RENDER_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends FormationActionType>> FORMATION_ACTION_TYPE = create("empty", MxtResourceKeys.FORMATION_ACTION_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends FormationStroke>> FORMATION_STROKE_TYPE = create("circle", MxtResourceKeys.FORMATION_STROKE_TYPE);
    public static final DefaultedRegistry<MapCodec<? extends SecretRealmGeneration>> SECRET_REALM_GENERATION_TYPE = create("stem", MxtResourceKeys.SECRET_REALM_GENERATION_TYPE);

    private static <T> DefaultedRegistry<T> create(String defaultKey, ResourceKey<? extends Registry<T>> key) {
        DefaultedRegistry<T> registry = new DefaultedMappedRegistry<>(defaultKey, key, Lifecycle.stable(), false);
        REGISTRIES.add(registry);
        return registry;
    }

    @SubscribeEvent
    public static void newRegistries(NewRegistryEvent event) {
        REGISTRIES.forEach(event::register);
    }
}
