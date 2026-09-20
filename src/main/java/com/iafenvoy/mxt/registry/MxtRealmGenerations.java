package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.realm.RealmGeneration;
import com.iafenvoy.mxt.data.realm.RealmGeneration.Existing;
import com.iafenvoy.mxt.data.realm.RealmGeneration.Flat;
import com.iafenvoy.mxt.data.realm.RealmGeneration.Stem;
import com.iafenvoy.mxt.data.realm.RealmGeneration.Template;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The realm generation strategies a data pack can name in {@code generation.type}.
 */
@SuppressWarnings("unused")
public final class MxtRealmGenerations {
    public static final DeferredRegister<MapCodec<? extends RealmGeneration>> REGISTRY = DeferredRegister.create(MxtRegistries.REALM_GENERATION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends RealmGeneration>, MapCodec<Stem>> STEM = REGISTRY.register("stem", () -> Stem.CODEC);
    public static final DeferredHolder<MapCodec<? extends RealmGeneration>, MapCodec<Flat>> FLAT = REGISTRY.register("flat", () -> Flat.CODEC);
    public static final DeferredHolder<MapCodec<? extends RealmGeneration>, MapCodec<RealmGeneration.Void>> VOID = REGISTRY.register("void", () -> RealmGeneration.Void.CODEC);
    public static final DeferredHolder<MapCodec<? extends RealmGeneration>, MapCodec<Template>> TEMPLATE = REGISTRY.register("template", () -> Template.CODEC);
    public static final DeferredHolder<MapCodec<? extends RealmGeneration>, MapCodec<Existing>> EXISTING = REGISTRY.register("existing", () -> Existing.CODEC);
}
