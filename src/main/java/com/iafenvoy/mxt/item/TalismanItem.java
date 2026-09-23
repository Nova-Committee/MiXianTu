package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.api.AuraAccess;
import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour.Entry;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The carrier a talisman is written on. One item carries every talisman a data pack defines, because what is
 * written is a component, and the pour is declared here rather than by a shared {@code item_aura} definition
 * because only the stack knows what is written on it. A plain use fires what is written on a full carrier, a
 * sneaking use changes the mode that decides whether it fires the moment it is filled.
 */
public class TalismanItem extends Item implements UseItemAuraAccess {
    public TalismanItem(Properties properties) {
        super(properties);
    }

    // A carrier still short of its bill has already been armed by the hold module, and the cycle that starts is the
    // pour; this is the other half. The sneak tells the two apart, because both are the same gesture on the stack.
    @Override
    public @NonNull InteractionResult use(@NonNull Level level, @NonNull Player player, @NonNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.has(DataComponents.CONSUMABLE)) return super.use(level, player, hand);
        if (!(player instanceof ServerPlayer)) return InteractionResult.SUCCESS;
        SpiritSource source = SpiritSource.of(player);
        if (player.isShiftKeyDown()) {
            // A switch is not an invocation, so it reports success whether or not firing followed from it.
            TalismanService.toggleMode(source, stack);
            return InteractionResult.SUCCESS_SERVER;
        }
        return TalismanService.invokeOnUse(source, stack) ? InteractionResult.SUCCESS_SERVER : InteractionResult.FAIL;
    }

    // Open-ended like a crossbow's: the default Consumable duration would end the cycle the moment the carrier
    // filled, and the client would restart it while the button is still down. canPourInto ends the pour, not the clock.
    @Override
    public int getUseDuration(@NonNull ItemStack stack, @NonNull LivingEntity entity) {
        return APPROXIMATELY_INFINITE_USE_DURATION;
    }

    @Override
    public Object2IntMap<Holder<Aura>> getCapacity(@Nullable LivingEntity entity, ItemStack stack) {
        Object2IntMap<Holder<Aura>> capacities = new Object2IntOpenHashMap<>();
        for (Map.Entry<Holder<Aura>, Integer> entry : TalismanService.bill(stack).entrySet())
            capacities.put(entry.getKey(), entry.getValue().intValue());
        return capacities;
    }

    @Override
    public int insert(@Nullable LivingEntity entity, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        int capacity = this.getCapacity(entity, stack, aura);
        if (capacity <= 0) return amount;
        SpiritStorageComponent charge = this.store(stack);
        int stored = Mth.clamp(charge.get(aura), 0, capacity);
        int accepted = Math.min(amount, capacity - stored);
        if (!simulate && accepted > 0)
            stack.set(MxtDataComponents.SPIRIT_STORAGE, charge.with(aura, stored + accepted));
        return amount - accepted;
    }

    @Override
    public int extract(@Nullable LivingEntity entity, ItemStack stack, Holder<Aura> aura, int amount, boolean simulate) {
        AuraAccess.requireNonNegative(amount);
        int capacity = this.getCapacity(entity, stack, aura);
        if (capacity <= 0) return amount;
        SpiritStorageComponent charge = this.store(stack);
        int stored = Mth.clamp(charge.get(aura), 0, capacity);
        int extracted = Math.min(amount, stored);
        if (!simulate && extracted > 0)
            stack.set(MxtDataComponents.SPIRIT_STORAGE, charge.with(aura, stored - extracted));
        return amount - extracted;
    }

    // The same component a spirit stone keeps its charge in: both answer "how many units of which aura are in this
    // stack", and neither writes anything the other could misread.
    private SpiritStorageComponent store(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
    }

    // No registries and no context: a bill has to be priceable without a holder (see TalismanService).
    @Override
    public Optional<SpiritPour> pour(Provider registries, ItemStack stack) {
        List<Entry> entries = TalismanService.entries(stack);
        return entries.isEmpty() ? Optional.empty() : Optional.of(new SpiritPour(entries));
    }

    // Whether an attempt can be made is the invocation's answer, not this item's, so it is asked of it.
    @Override
    public boolean canPourInto(@Nullable LivingEntity holder, ItemStack stack) {
        return TalismanService.canFireFrom(holder, stack);
    }

    // Nothing is said while the store is not full: a pour reports every tick, and a message per tick would drown
    // the one that matters. The mode held next to the inscriptions decides whether it fires or is left charged.
    @Override
    public void onCharged(SpiritSource source, ItemStack stack) {
        if (!TalismanService.ready(source.level().registryAccess(), stack)) return;
        if (!TalismanService.autoFires(stack)) {
            if (source.actor() instanceof ServerPlayer player)
                player.sendSystemMessage(Component.translatable("actionbar.mxt.talisman.stored"), true);
            return;
        }
        TalismanService.invokeOnUse(source, stack);
    }
}
