package com.iafenvoy.mxt.runtime.forging;

import com.iafenvoy.mxt.data.forging.BlueprintBinding;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.forging.ToolBinding;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What one stack unlocks at the forge table: the declaration claiming it, plus whatever it carries itself through
 * {@code mxt:forging_methods} / {@code mxt:forging_blueprints}. The two are unioned and deduplicated by id, so a
 * second tool - or a stray sheet - can only ever add.
 */
public final class ForgingBindingService {
    private ForgingBindingService() {
    }

    public static Optional<ToolBinding> tool(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.TOOL_BINDING)
                .map(Reference::value), stack);
    }

    public static Optional<BlueprintBinding> blueprint(Provider access, ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return ItemMatcher.find(MxtDatapackRegistries.holders(access, MxtResourceKeys.BLUEPRINT_BINDING)
                .map(Reference::value), stack);
    }

    public static List<Holder<ForgingMethod>> methods(Provider access, ItemStack stack) {
        Set<Holder<ForgingMethod>> unlocked = new LinkedHashSet<>();
        tool(access, stack).ifPresent(binding -> unlocked.addAll(binding.methods()));
        List<Holder<ForgingMethod>> carried = stack.get(MxtDataComponents.FORGING_METHODS.get());
        if (carried != null) unlocked.addAll(carried);
        return List.copyOf(unlocked);
    }

    public static List<Holder<ForgingBlueprint>> blueprints(Provider access, ItemStack stack) {
        Set<Holder<ForgingBlueprint>> offered = new LinkedHashSet<>();
        blueprint(access, stack).ifPresent(binding -> offered.addAll(binding.blueprints()));
        List<Holder<ForgingBlueprint>> carried = stack.get(MxtDataComponents.FORGING_BLUEPRINTS.get());
        if (carried != null) offered.addAll(carried);
        return List.copyOf(offered);
    }

    // The slot rule asks this, so it answers the same question the two lists do rather than looking for a
    // component that is no longer written.
    public static boolean isTool(Provider access, ItemStack stack) {
        return !methods(access, stack).isEmpty();
    }

    public static boolean isBlueprint(Provider access, ItemStack stack) {
        return !blueprints(access, stack).isEmpty();
    }
}
