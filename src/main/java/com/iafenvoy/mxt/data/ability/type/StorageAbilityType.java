package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.registry.MxtMenus;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageContainer;
import com.iafenvoy.mxt.runtime.artifact.ArtifactStorageService;
import com.iafenvoy.saturn.data.ability.*;
import com.iafenvoy.saturn.data.storage.DataStorageCollector;
import com.iafenvoy.saturn.data.storage.builtin.ContainerDataStorage;
import com.iafenvoy.saturn.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.saturn.runtime.resulting.Failure;
import com.iafenvoy.saturn.runtime.resulting.Failures;
import com.iafenvoy.saturn.runtime.resulting.Result;
import com.iafenvoy.saturn.runtime.resulting.failures.AbilityFailure;
import com.iafenvoy.saturn.util.DefinitionText;
import com.iafenvoy.saturn.util.formula.NumberProvider;
import com.iafenvoy.saturn.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * A one-shot inventory ability with item-local or player-local contents, selected by its registered type.
 */
public record StorageAbilityType(NumberProvider slots,
                                 NumberProvider cooldown,
                                 boolean shared) implements AbilityType, Togglable, CooldownSource {
    public static final MapCodec<StorageAbilityType> CODEC = codecFor(false);
    public static final MapCodec<StorageAbilityType> SHARED_CODEC = codecFor(true);

    private static MapCodec<StorageAbilityType> codecFor(boolean shared) {
        return RecordCodecBuilder.mapCodec(i -> i.group(
                NumberProvider.CODEC.fieldOf("slots").forGetter(StorageAbilityType::slots),
                NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(StorageAbilityType::cooldown)
        ).apply(i, (slots, cooldown) -> new StorageAbilityType(slots, cooldown, shared)));
    }

    @Override
    public MapCodec<StorageAbilityType> codec() {
        return this.shared ? SHARED_CODEC : CODEC;
    }

    // Its press goes through the shared gate, so it can carry a cooldown; the container is the item host's kind.
    @Override
    public void createComponents(Ability ability, DataStorageCollector collector) {
        AbilityType.super.createComponents(ability, collector);
        collector.add(CooldownDataStorage.INSTANCE);
        if (!this.shared) collector.add(ContainerDataStorage.INSTANCE);
    }

    @Override
    public Optional<Failure> canActivate(ToggleContext context) {
        if (!(context.holder() instanceof ServerPlayer player)) return Optional.of(AbilityFailure.UNAVAILABLE);
        ItemStack carrier = context.carrier();
        if (carrier == null || !ArtifactStorageContainer.isCarried(player, carrier))
            return Optional.of(AbilityFailure.NO_CARRIER);
        // No slot is the slots formula's own answer, not a state the press cannot describe.
        if (ArtifactService.storageSlots(carrier, context.ability(), context.formula()) <= 0)
            return Optional.of(Failures.INVALID_FORMULA);
        if (!ArtifactStorageService.hasAccess(player.level().registryAccess(), carrier, context.ability(), player))
            return Optional.of(AbilityFailure.NOT_OWNED);
        return Optional.empty();
    }

    @Override
    public Result<Void> activate(ToggleContext context) {
        Optional<Failure> refusal = this.canActivate(context);
        if (refusal.isPresent()) return Result.failure(refusal.get());
        ServerPlayer player = (ServerPlayer) context.holder();
        ItemStack carrier = context.carrier();
        int capacity = ArtifactService.storageSlots(carrier, context.ability(), context.formula());
        Component title = Component.translatable("screen.mxt.artifact_storage", DefinitionText.nameOrId(context.ability()));
        ArtifactStorageContainer contents = new ArtifactStorageContainer(player, carrier, context.ability(), capacity);
        int rows = capacity / ArtifactService.STORAGE_COLUMNS;
        player.openMenu(new SimpleMenuProvider((id, inventory, _) -> new ChestMenu(MxtMenus.ARTIFACT_STORAGE.get(), id, inventory, contents, rows) {
                    @Override
                    public @NonNull ItemStack quickMoveStack(@NonNull Player player, int index) {
                        if (this.getSlot(index).getItem() == carrier) return ItemStack.EMPTY;
                        return super.quickMoveStack(player, index);
                    }

                    @Override
                    protected @NonNull Slot addSlot(@NonNull Slot slot) {
                        if (slot.container == contents) slot = new Slot(contents, slot.getSlotIndex(), slot.x, slot.y) {
                            @Override
                            public boolean mayPlace(@NonNull ItemStack stack) {
                                return stack != carrier;
                            }
                        };
                        return super.addSlot(slot);
                    }
                }, title),
                // The client rebuilds the same menu shape from the row count, which is all it needs: the slots
                // themselves arrive through the menu sync.
                buffer -> buffer.writeVarInt(rows));
        return Result.success();
    }
}
