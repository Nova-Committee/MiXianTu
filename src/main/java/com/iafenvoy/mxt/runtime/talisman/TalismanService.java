package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.CostTransaction;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostFailure;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.event.AbilityUseEvent.Post;
import com.iafenvoy.mxt.item.TalismanItem;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.Failure;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour.Entry;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * The talisman carrier's own half of the pour: what a carrier takes to fill, and what happens once it is full.
 * A carrier's capacity is its {@code aura_cost} bill summed over every definition written on it, so "full" is
 * exactly what the invocation will cost rather than a number somebody picked; the pour itself belongs to the
 * spirit module ({@link UseItemAuraAccess#pour}), and a carrier takes one unit a tick at one for one. Firing is
 * the carrier's answer both to being charged and to a right-click once there is nothing left to pour, so a
 * carrier billed nothing at all is still usable. The invocation is an ordinary ability use with one thing
 * changed - the carrier answers for the grant - which is what keeps a talisman from being a way around every
 * other gate. What it costs the carrier is its own wear when the definitions written on it declare any, and
 * the carrier itself one item at a time when none of them do.
 */
public final class TalismanService {
    private TalismanService() {
    }

    // One entry per aura, in the order the definitions were written, because that order decides which aura a
    // pour fills next - so a list, not a map with an iteration order of its own. Priced against the empty
    // context because it has to be: the bill is also how long a pour lasts, and the client sizes the same
    // gesture from the same stack.
    public static Map<Holder<Aura>, Integer> bill(ItemStack stack) {
        Map<Holder<Aura>, Integer> totals = new LinkedHashMap<>();
        for (Holder<Talisman> talisman : inscribed(stack)) {
            talisman.value().auraCost().forEach((aura, amount) -> {
                double cost = amount.evaluate(FormulaContext.EMPTY);
                if (!Double.isFinite(cost) || cost <= 0.0D) return;
                int units = cost >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.floor(cost);
                totals.merge(aura, units, TalismanService::add);
            });
        }
        return totals;
    }

    // A bill that cannot be priced without a holder prices to nothing and is left out, so this includes a
    // carrier billed nothing at all: a free talisman is fired by the same click as a filled one.
    public static boolean ready(Provider registries, ItemStack stack) {
        if (!(stack.getItem() instanceof UseItemAuraAccess access)) return false;
        SpiritPour pour = access.pour(registries, stack).orElse(null);
        return pour == null || pour.full();
    }

    // How much wear a carrier has in it, read off the stack first and off what is written on it second: a cap the
    // pack patched onto the stack overrides the definitions, and a carrier whose definitions declare none has no
    // wear at all - it is still spent whole, one item per invocation. Wear belongs to a single carrier, so a stack
    // of several never has any: vanilla refuses a stack that is both damageable and stackable, and a stacked
    // carrier stays exactly what it was - one item per invocation.
    public static int durability(ItemStack stack) {
        if (stack.getCount() > 1) return 0;
        return stack.has(DataComponents.MAX_DAMAGE) ? stack.getMaxDamage() : declaredDurability(stack);
    }

    // What one invocation takes off that, summed over the definitions that declared a durability: a carrier
    // written with two wearing ones wears as fast as both of them together. Definitions that declared none
    // ride along and cost nothing.
    public static int durabilityCost(ItemStack stack) {
        if (stack.getCount() > 1) return 0;
        int cost = 0;
        for (Holder<Talisman> talisman : inscribed(stack)) {
            Talisman written = talisman.value();
            if (written.durability() > 0) cost = add(cost, written.consume());
        }
        return cost;
    }

    // Puts the declared wear onto the stack in vanilla's own shape: the cap, a stack size of one and a damage
    // value that exists. All three are needed - a cap with a stack size above one is refused on the way out
    // ("Item cannot be both damageable and stackable"), and a stack without a damage component is not damageable
    // at all - so the framework never writes a partial one. A cap the stack already carries is kept as it is.
    public static void applyDurability(ItemStack stack) {
        if (stack.isEmpty() || stack.getCount() > 1) return;
        int cap = stack.has(DataComponents.MAX_DAMAGE) ? stack.getMaxDamage() : declaredDurability(stack);
        if (cap <= 0) return;
        stack.set(DataComponents.MAX_DAMAGE, cap);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
        stack.set(DataComponents.DAMAGE, stack.getOrDefault(DataComponents.DAMAGE, 0));
    }

    private static int declaredDurability(ItemStack stack) {
        int declared = 0;
        for (Holder<Talisman> talisman : inscribed(stack))
            declared = add(declared, Math.max(0, talisman.value().durability()));
        return declared;
    }

    // The tier a carrier made from this is written on: the first inscription that declares one, in the order they
    // were written, because a carrier holding several has no single tier of its own. Nothing is written onto the
    // stack for it - the quality module reads this as the definition's own default.
    public static Optional<Holder<ItemQuality>> quality(ItemStack stack) {
        for (Holder<Talisman> talisman : inscribed(stack)) {
            Optional<Holder<ItemQuality>> quality = talisman.value().quality();
            if (quality.isPresent()) return quality;
        }
        return Optional.empty();
    }

    // What one invocation costs the holder on top of what the carrier was filled with, in the order the
    // definitions were written. The list goes into the shared transaction untouched, so a price that cannot be
    // paid refuses the invocation instead of half-paying it - which is the threshold a "needs enough spirit
    // power" condition would have been.
    private static List<Cost> costs(List<Holder<Talisman>> written) {
        return written.stream().flatMap(talisman -> talisman.value().costs().stream()).toList();
    }

    // The one entry every trigger shares - a hand (TalismanItem#use) and a carrier that filled itself - so
    // neither way in can drift from the other; which rule applies is read off the source, never decided here.
    // A hand charges the window for the attempt rather than the firing. The window is filed against a copy
    // taken before the invocation, because firing burns the stack and vanilla reads the cooldown group off it.
    public static boolean invokeOnUse(SpiritSource source, ItemStack stack) {
        ItemStack beforeUse = stack.copy();
        Attempt attempt = attempt(source, stack);
        if (source.consumedByHand() && attempt.attempted()) applyCooldown(source.actor(), beforeUse);
        return attempt.fired();
    }

    // Whether the carrier's own mode lets it fire the moment it is full. STORE is a carrier that only
    // accumulates: filled to its bill and sitting there, waiting for a hand or for a switch back.
    public static boolean autoFires(ItemStack stack) {
        return component(stack).mode() == TriggerMode.FIRE;
    }

    // The switch a hand makes with a sneaking use: firing on its own stops, storing starts. A full carrier in
    // STORE is not switched at all - it fires instead, because a stored charge exists to be spent. The return
    // is whether anything was asked of the carrier, which tells a click that fired from one that only changed
    // a mode.
    public static boolean toggleMode(SpiritSource source, ItemStack stack) {
        if (inscribed(stack).isEmpty()) return false;
        TalismanComponent component = component(stack);
        boolean full = ready(source.level().registryAccess(), stack);
        if (component.mode() == TriggerMode.STORE && full) {
            // Set first: firing spends the stack, and this is the mode the rest of it is left in.
            stack.set(MxtDataComponents.TALISMAN, component.withMode(TriggerMode.FIRE));
            return invokeOnUse(source, stack);
        }
        TriggerMode next = component.mode().other();
        stack.set(MxtDataComponents.TALISMAN, component.withMode(next));
        LivingEntity holder = source.actor();
        if (holder != null) say(holder, Component.translatable("actionbar.mxt.talisman.mode." + next.key()));
        return false;
    }

    // False inside the use window, because the aura a tick would move would buy a charge the click is not going
    // to spend. A full carrier is left out by the caller rather than here, and a display stand never asks.
    public static boolean canFireFrom(@Nullable LivingEntity holder, ItemStack stack) {
        return !coolingDown(holder, stack);
    }

    // One invocation, and whether it got as far as being one: every ability refusing still means the carrier
    // was used, while a carrier that is blank, uncharged, inert or cooling down was never attempted.
    private static Attempt attempt(SpiritSource source, ItemStack stack) {
        LivingEntity holder = source.actor();
        // Nothing living is answerable for it: an ability needs somebody to pay, to be credited and to answer
        // for it. The carrier stays charged, which is what a click by a person can use.
        if (holder == null || source.level().isClientSide() || !(stack.getItem() instanceof TalismanItem))
            return Attempt.NOT_AN_ATTEMPT;
        // Two rules, one per way in. The use window is about the hand alone: a placed carrier on a display stand
        // is outside every window - it reads none and leaves none. The burn is about where the carrier is: a
        // hand keeps what a creative hand would not spend, a placed carrier is always spent. The source's own
        // flag says which this is, so nothing infers it from whether an actor happened to be passed.
        boolean inHand = source.consumedByHand();
        if (inHand && coolingDown(holder, stack)) {
            say(holder, Component.translatable("actionbar.mxt.talisman.cooldown"));
            return Attempt.NOT_AN_ATTEMPT;
        }
        List<Holder<Talisman>> written = inscribed(stack);
        if (written.isEmpty()) {
            say(holder, Component.translatable("actionbar.mxt.talisman.blank"));
            return Attempt.NOT_AN_ATTEMPT;
        }
        if (!ready(source.level().registryAccess(), stack)) {
            say(holder, Component.translatable("actionbar.mxt.talisman.not_charged"));
            return Attempt.NOT_AN_ATTEMPT;
        }
        List<Holder<Ability>> abilities = abilities(written);
        if (abilities.isEmpty()) {
            say(holder, Component.translatable("actionbar.mxt.talisman.inert"));
            return Attempt.NOT_AN_ATTEMPT;
        }

        AbilityAttachment holderAbilities = holder.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        long gameTime = holder.level().getGameTime();
        // Where it happened, in the same shape every other position travels in: the explicit formula values
        // block_x, block_y and block_z. The acting entity is still the actor, because that is who the abilities
        // belong to - the position says where, not who.
        FormulaContext context = FormulaContext.of(holder, Map.of("block_x", source.position().x(),
                "block_y", source.position().y(), "block_z", source.position().z()));
        // What the carrier's own definitions charge for one invocation, planned before anything happens: a price
        // the holder cannot pay refuses the invocation, which is where a "needs enough spirit power" threshold
        // lives now. Nothing is written by the plan, so a carrier whose abilities all refuse still costs nothing.
        CostContext costContext = CostContext.of(holder, context, CostOrigin.TALISMAN);
        CostTransaction.Planning price = CostTransaction.plan(costs(written), costContext);
        if (!price.ok()) {
            say(holder, Component.translatable("actionbar.mxt.talisman.failed", Component.translatable(
                    "actionbar.mxt.talisman.failure." + costFailure(price.failure()).name().toLowerCase(Locale.ROOT))));
            return Attempt.REFUSED;
        }
        int fired = 0;
        Failure failure = null;
        for (Holder<Ability> ability : abilities) {
            UseResult result = AbilityService.useCarried(ability, holder, holderAbilities, resources, gameTime, context, source.position());
            if (result.committed()) {
                fired++;
                NeoForge.EVENT_BUS.post(new Post(holder, ability, context, result.amounts()));
            } else if (result.failure() != null) {
                failure = result.failure();
            }
        }
        if (fired == 0) {
            Failure reason = failure == null ? Failure.INVALID_FORMULA : failure;
            say(holder, Component.translatable("actionbar.mxt.talisman.failed",
                    Component.translatable("actionbar.mxt.talisman.failure." + reason.name().toLowerCase(Locale.ROOT))));
            return Attempt.REFUSED;
        }
        CostTransaction.PayResult paid = CostTransaction.commit(price, costContext);
        if (!paid.paid()) {
            say(holder, Component.translatable("actionbar.mxt.talisman.failed", Component.translatable(
                    "actionbar.mxt.talisman.failure." + costFailure(paid.failure()).name().toLowerCase(Locale.ROOT))));
            return Attempt.REFUSED;
        }
        // What was poured in was spent on the invocation rather than left for the next one; what the invocation
        // takes off the carrier itself is spend's business. The charge is read first, because a carrier the wear
        // burns out from under it never got to spend it, and an empty stack has no components left to read.
        SpiritStorageComponent charge = stack.get(MxtDataComponents.SPIRIT_STORAGE);
        stack.remove(MxtDataComponents.SPIRIT_STORAGE);
        if (spend(stack, holder, inHand)) refund(charge, holder);
        say(holder, Component.translatable("actionbar.mxt.talisman.invoked", fired));
        return Attempt.FIRED;
    }

    // A price the holder cannot make is the same two answers every other cost gives: not enough of one resource,
    // or not enough of everything else. The carrier reports it with the ability failures it already has words for.
    private static Failure costFailure(CostFailure failure) {
        return failure == CostFailure.INSUFFICIENT_RESOURCE ? Failure.INSUFFICIENT_RESOURCE : Failure.INSUFFICIENT_COST;
    }

    // What a carrier was still holding when the wear burned it out, handed back to whoever set that invocation
    // off. The pour charged one unit of the aura's own resource for each unit it put in, so that is what comes
    // back; a definition that no longer resolves or an amount the resource will not take loses the aura with the
    // paper rather than failing the invocation that has already happened.
    private static void refund(@Nullable SpiritStorageComponent charge, LivingEntity holder) {
        if (charge == null || charge.isEmpty()) return;
        ResourceHolderAttachment resources = holder.getData(MxtAttachments.RESOURCE_HOLDER);
        charge.amounts().forEach((aura, units) -> {
            double amount = units * SpiritChargeService.POUR_COST_PER_UNIT;
            if (!Double.isFinite(amount) || amount <= 0.0D) return;
            Holder<Resource> resource = aura.value().resource();
            FormulaContext context = ResourceService.formulaContext(holder, resource, FormulaContext.of(holder));
            ResourceService.change(resources, resource, amount, context);
        });
    }

    // One invocation's share of the carrier, in one of two currencies: the wear its definitions declare, or the
    // carrier itself - one item off the stack, which is what a carrier with no wear to spend has always cost.
    // Wear that passes the cap destroys the carrier and leaves nothing behind, so the damage a stack shows is
    // always the wear of the item on top. A creative hand spends neither, which is vanilla's own answer that its
    // stack is not the thing being spent. True means the wear is what destroyed the carrier, which is the one
    // case where what was poured in is not spent on the invocation.
    private static boolean spend(ItemStack stack, LivingEntity holder, boolean inHand) {
        applyDurability(stack);
        boolean creative = inHand && holder instanceof Player player && player.hasInfiniteMaterials();
        int cap = durability(stack);
        int cost = durabilityCost(stack);
        if (cap > 0 && cost > 0) {
            if (creative) return false;
            int damage = stack.getDamageValue() + cost;
            if (damage < cap) {
                stack.setDamageValue(damage);
                return false;
            }
            stack.shrink(1);
            if (!stack.isEmpty()) stack.setDamageValue(0);
            return true;
        }
        if (!inHand) {
            // A placed carrier is always spent: nobody's creative mode is holding it.
            stack.shrink(1);
        } else if (!creative) {
            stack.consume(1, holder);
        }
        return false;
    }

    // An attempt is what the use cooldown is charged for, so the two answers are kept apart rather than
    // flattened into a boolean the caller would have to guess at.
    private record Attempt(boolean attempted, boolean fired) {
        private static final Attempt NOT_AN_ATTEMPT = new Attempt(false, false);
        private static final Attempt REFUSED = new Attempt(true, false);
        private static final Attempt FIRED = new Attempt(true, true);
    }

    // Vanilla's tracker is per player and keyed by the item, and every carrier is the same item, so one window
    // covers every carrier a player holds. A group of its own is what `cooldown_group` is for.
    private static boolean coolingDown(@Nullable LivingEntity holder, ItemStack stack) {
        return useCooldownTicks() > 0 && holder instanceof Player player && player.getCooldowns().isOnCooldown(stack);
    }

    // Vanilla's own tracker through vanilla's own call, with the window from the server option instead of a
    // component. The group is read off the stack handed in, so it has to be one that still holds what was used.
    private static void applyCooldown(@Nullable LivingEntity holder, ItemStack stackBeforeUse) {
        int ticks = useCooldownTicks();
        if (ticks <= 0 || !(holder instanceof Player player)) return;
        player.getCooldowns().addCooldown(stackBeforeUse, ticks);
    }

    private static int useCooldownTicks() {
        return MxtServerConfig.INSTANCE.talisman.useCooldown.getValue();
    }

    public static List<Holder<Talisman>> inscribed(ItemStack stack) {
        return component(stack).talismans();
    }

    // Defaults a missing component means: nothing inscribed, and the firing mode - which is also what every
    // carrier written before the mode existed decodes as.
    private static TalismanComponent component(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.TALISMAN, TalismanComponent.EMPTY);
    }

    // With the tags they accept expanded, once each: a carrier that names the same ability twice fires it once.
    private static List<Holder<Ability>> abilities(List<Holder<Talisman>> written) {
        Registry<Ability> registry = MxtDatapackRegistries.registry(MxtResourceKeys.ABILITY);
        return written.stream()
                .flatMap(talisman -> RegistryCodecs.resolve(talisman.value().abilities(), registry))
                .distinct().toList();
    }

    // The whole bill of a stack as the pour reads it: one entry per aura, in declaration order, with whatever
    // has been poured in so far.
    public static List<Entry> entries(ItemStack stack) {
        Map<Holder<Aura>, Integer> bill = bill(stack);
        if (bill.isEmpty()) return List.of();
        SpiritStorageComponent charge = stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
        List<Entry> entries = new ArrayList<>(bill.size());
        bill.forEach((aura, capacity) -> entries.add(new Entry(aura,
                (int) Math.clamp(Math.floor(charge.get(aura)), 0.0D, capacity), capacity)));
        return List.copyOf(entries);
    }

    private static int add(int first, int second) {
        long total = (long) first + second;
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static void say(LivingEntity holder, Component line) {
        if (holder instanceof ServerPlayer player) player.sendSystemMessage(line, true);
    }
}
