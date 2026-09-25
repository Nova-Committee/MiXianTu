package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.artifact.ItemAbilitiesComponent;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * Writes abilities onto the acted stack's {@code mxt:item_abilities} component, keeping whatever it already
 * carried. This is that component's dedicated producer: a crafting, claim or loot action can hand a stack an
 * ability without the pack having to spell out a raw component patch.
 *
 * <p>Only named abilities, no tags: the component is read back one id at a time by the artifact runtime, which
 * resolves a tag only where a definition declares one.
 */
public record AddAbilityAction(List<Holder<Ability>> abilities) implements ItemAction {
    public static final MapCodec<AddAbilityAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ability.CODEC.listOf().fieldOf("abilities").forGetter(AddAbilityAction::abilities)
    ).apply(i, AddAbilityAction::new));

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        ItemStack stack = ctx.stack();
        if (stack.isEmpty() || this.abilities.isEmpty()) return;
        ItemAbilitiesComponent component = stack.getOrDefault(MxtDataComponents.ITEM_ABILITIES.get(),
                new ItemAbilitiesComponent(List.of()));
        LinkedHashSet<Identifier> ids = new LinkedHashSet<>(component.abilities());
        this.abilities.forEach(ability -> ids.add(HolderHelper.id(ability)));
        stack.set(MxtDataComponents.ITEM_ABILITIES.get(), new ItemAbilitiesComponent(List.copyOf(ids)));
    }

    @Override
    public @NonNull MapCodec<AddAbilityAction> codec() {
        return CODEC;
    }
}
