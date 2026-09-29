package com.iafenvoy.mxt.event;

import com.iafenvoy.mxt.recipe.AlchemyRecipe;
import com.iafenvoy.mxt.recipe.AlchemyRecipe.Role;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyFailure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * KubeJS name remains {@code alchemyCraft}. Pre copies cannot change the bill; Post does not write the output slots.
 */
public abstract class AlchemyCraftEvent extends Event {
    private final RecipeHolder<AlchemyRecipe> recipe;
    private final BlockPos pos;
    private final UUID operator;

    protected AlchemyCraftEvent(RecipeHolder<AlchemyRecipe> recipe, BlockPos pos, UUID operator) {
        this.recipe = recipe;
        this.pos = pos.immutable();
        this.operator = operator;
    }

    public RecipeHolder<AlchemyRecipe> recipe() {
        return this.recipe;
    }

    public BlockPos pos() {
        return this.pos;
    }

    public UUID operator() {
        return this.operator;
    }

    public Optional<ServerPlayer> player() {
        return Optional.empty();
    }

    public static final class Pre extends AlchemyCraftEvent implements ICancellableEvent {
        private final List<InputCopy> inputs;
        private final ServerPlayer player;

        public Pre(RecipeHolder<AlchemyRecipe> recipe, BlockPos pos, ServerPlayer player, List<InputCopy> inputs) {
            super(recipe, pos, player.getUUID());
            this.player = player;
            List<InputCopy> copies = new ArrayList<>(inputs.size());
            for (InputCopy input : inputs) copies.add(new InputCopy(input.role(), input.stack().copy()));
            this.inputs = List.copyOf(copies);
        }

        /**
         * Role copies. Mutating a stack or the list does not change what start consumes.
         */
        public List<InputCopy> inputs() {
            return this.inputs;
        }

        @Override
        public Optional<ServerPlayer> player() {
            return Optional.of(this.player);
        }
    }

    public static final class Post extends AlchemyCraftEvent {
        private final boolean success;
        private final AlchemyFailure reason;
        private final List<ItemStack> outputs;
        private final ServerPlayer player;

        public Post(RecipeHolder<AlchemyRecipe> recipe, BlockPos pos, UUID operator, ServerPlayer player,
                    boolean success, AlchemyFailure reason, List<ItemStack> outputs) {
            super(recipe, pos, operator);
            this.player = player;
            this.success = success;
            this.reason = reason;
            List<ItemStack> copies = new ArrayList<>(outputs.size());
            for (ItemStack stack : outputs) if (!stack.isEmpty()) copies.add(stack.copy());
            this.outputs = List.copyOf(copies);
        }

        public boolean success() {
            return this.success;
        }

        public Optional<AlchemyFailure> reason() {
            return Optional.ofNullable(this.reason);
        }

        public List<ItemStack> outputs() {
            return this.outputs;
        }

        @Override
        public Optional<ServerPlayer> player() {
            return Optional.ofNullable(this.player);
        }
    }

    public record InputCopy(Role role, ItemStack stack) {
    }
}
