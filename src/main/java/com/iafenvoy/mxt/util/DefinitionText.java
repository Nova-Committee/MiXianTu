package com.iafenvoy.mxt.util;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.core.Holder;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
* Generate language key: {@code <registry path>.<registry namespace>.<id namespace>.<id path>}
* */
public final class DefinitionText {
    /**
     * What a definition's description key appends to its name key.
     */
    public static final String DESCRIPTION_SUFFIX = ".description";

    /**
     * What one minor stage of a realm appends to that realm's name key, followed by the stage's 0-based index.
     */
    public static final String MINOR_STAGE_SUFFIX = ".minor_stage.";

    // The category a registry's keys are built with, for code that has to build a key by hand.
    public static String category(Identifier registry) {
        return registry.getPath();
    }

    public static String key(ResourceKey<?> key) {
        return key(key.registry(), key.identifier());
    }

    public static String key(Identifier registry, Identifier id) {
        return key(category(registry), registry.getNamespace(), id);
    }

    // The one place a definition key is built.
    public static String key(String category, String registryNamespace, Identifier id) {
        return category + "." + registryNamespace + "." + id.toLanguageKey();
    }

    public static MutableComponent name(ResourceKey<?> key) {
        return Component.translatable(key(key));
    }

    public static MutableComponent name(Holder<?> holder) {
        return holder.unwrapKey().map(DefinitionText::name).orElseGet(() -> Component.literal("?"));
    }

    // A bare id names no registry, so this overload assumes the registry namespace this mod declares all of its
    // registries under. Only for paths holding an id and nothing else: a ledger key, a command argument, a payload.
    public static MutableComponent name(Identifier id, String category) {
        return Component.translatable(key(category, MiXianTu.MOD_ID, id));
    }

    /**
     * Any other text belonging to a definition, keyed off its name key: the description, one minor stage of a
     * realm. Nothing is stored, so a caller printing one of these to a tooltip should skip it unless
     * {@link #resolved} accepts it.
     */
    public static MutableComponent derived(ResourceKey<?> key, String suffix) {
        return Component.translatable(key(key) + suffix);
    }

    public static MutableComponent derived(Holder<?> holder, String suffix) {
        return holder.unwrapKey().map(key -> derived(key, suffix)).orElseGet(() -> Component.literal("?"));
    }

    /**
     * The description of a definition, which is its name key plus {@link #DESCRIPTION_SUFFIX}.
     */
    public static MutableComponent description(ResourceKey<?> key) {
        return derived(key, DESCRIPTION_SUFFIX);
    }

    public static MutableComponent description(Holder<?> holder) {
        return derived(holder, DESCRIPTION_SUFFIX);
    }

    public static MutableComponent minorStage(Holder<?> holder, int index) {
        return derived(holder, MINOR_STAGE_SUFFIX + index);
    }

    /**
     * Whether a definition's text reads as something rather than a bare key: a literal, or a defined key.
     */
    public static boolean resolved(Component text) {
        return !(text.getContents() instanceof TranslatableContents contents)
                || Language.getInstance().has(contents.getKey());
    }
}
