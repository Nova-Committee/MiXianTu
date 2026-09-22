package com.iafenvoy.mxt.runtime.talisman;

import com.iafenvoy.mxt.api.UseItemAuraAccess;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.SpiritStorageComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.event.AbilityUseEvent.Post;
import com.iafenvoy.mxt.item.TalismanItem;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.Failure;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour.Entry;
import com.iafenvoy.mxt.runtime.spirit.SpiritPour;
import com.iafenvoy.mxt.runtime.spirit.SpiritSource;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The talisman carrier's own half of the pour: what a carrier takes to fill, and what happens once it is full.
 * <p>
 * A definition bills aura in {@code aura_cost}, and the carrier's capacity is that bill - summed over every
 * definition written on it, one entry per aura. So "full" is not a number somebody picked: it is exactly what
 * the invocation is going to cost, and pouring it in is how the carrier is loaded. The pour itself is the
 * spirit module's ({@link UseItemAuraAccess#pour}), and a carrier takes one unit a tick at one for one, which
 * makes the bill its pouring time as well as its price.
 * <p>
 * Firing is the carrier's answer to being charged ({@link UseItemAuraAccess#onCharged}) and the same answer a
 * right-click gets once there is nothing left to pour, so a carrier billed nothing at all is still usable. It
 * does not have to be in anybody's hands to fire: a display stand reports the same moment from where it stands
 * (see {@link SpiritSource}), which is what makes the position a parameter rather than the holder.
 * <p>
 * The invocation is an ordinary ability use with one thing changed: the carrier answers for the grant. Every
 * other gate still applies - the ability's own condition, word, cooldown, charges and costs, and both use
 * events - which is what keeps a talisman from being a way around them. What the carrier cannot carry is an
 * ability that needs a caster at all: see {@link AbilityService#useCarried}.
 */
public final class TalismanService {
    private TalismanService() {
    }

    /**
     * What this stack's inscribed definitions bill, one entry per aura, in the order the definitions were
     * written. That order is what decides which aura a pour fills next, so it is a list rather than a map with
     * an iteration order of its own.
     * <p>
     * The bill is read against the empty context, because it has to be: what a carrier takes is also how long a
     * pour lasts, and the client sizes the same gesture from the same stack. A bill that cannot be priced
     * without a holder therefore prices to nothing and is left out - a carrier billed nothing at all is fired by
     * a click rather than poured into, the same as one whose {@code aura_cost} is absent.
     */
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

    /**
     * Whether this stack is a carrier with nothing left to pour - which includes one that was billed nothing at
     * all, so a free talisman is fired by the same click as a filled one.
     */
    public static boolean ready(Provider registries, ItemStack stack) {
        if (!(stack.getItem() instanceof UseItemAuraAccess access)) return false;
        SpiritPour pour = access.pour(registries, stack).orElse(null);
        return pour == null || pour.full();
    }

    /**
     * Fires what is written on a carrier and spends one of them. The one entry every trigger shares - a hand
     * ({@link TalismanItem#use}) and a carrier that filled itself
     * ({@link TalismanItem#onCharged}) - so neither way in can drift from the other.
     * <p>
     * Two rules, one per way in, and both read off the source rather than decided here: the use window belongs
     * to a hand, so a carrier in one is rate-limited and leaves the window that rate-limits it, while a placed
     * carrier is outside every window; and spending belongs to the way in, so a hand keeps what a creative hand
     * would not spend while a placed carrier is always spent. See {@link SpiritSource#consumedByHand()}.
     * <p>
     * A hand still charges the window for the <em>attempt</em> rather than for the firing: an ability that
     * refused costs it, while a carrier that is blank or uncharged never became an attempt - and a click refused
     * for the window itself starts nothing.
     * <p>
     * The window is filed against a copy taken before the invocation, because firing burns the stack it was made
     * from and vanilla reads the cooldown group off the stack: the emptied one would file it under
     * {@code minecraft:air}.
     */
    public static boolean invokeOnUse(SpiritSource source, ItemStack stack) {
        ItemStack beforeUse = stack.copy();
        Attempt attempt = attempt(source, stack);
        if (source.consumedByHand() && attempt.attempted()) applyCooldown(source.actor(), beforeUse);
        return attempt.fired();
    }

    /**
     * Whether the carrier's own mode lets it fire the moment it is full. Asking this is the caller's job - the
     * item answers its own "I was written to" report with it, because firing on being filled is a thing a
     * carrier does rather than a thing a writer does. {@code STORE} is a carrier that only accumulates: it is
     * filled to its bill and sits there, waiting for a hand or for a switch back.
     */
    public static boolean autoFires(ItemStack stack) {
        return component(stack).mode() == TriggerMode.FIRE;
    }

    /**
     * The switch a hand makes with a sneaking use: a carrier that fires on its own stops doing so, and one that
     * stores starts. A full carrier in {@code STORE} is not switched at all - it fires instead, because a stored
     * charge exists to be spent and asking for the firing mode is the clearest way to say so.
     *
     * @return whether anything was asked of the carrier, which is what tells a click that fired from one that
     * only changed a mode.
     */
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

    /**
     * Whether the hand may pour into this carrier right now - the answer {@link UseItemAuraAccess#canPourInto}
     * gives for it. False inside the use window, because the aura a tick would move would buy a charge the click
     * is not going to spend.
     * <p>
     * A full carrier is left out by the caller rather than here: nothing is missing, so the pour has nothing to
     * add and the gesture's own "already full" answer is the one to give.
     * <p>
     * Deliberately not asked by a display stand: that carrier is a placed object, so the stand fills it whatever
     * window the thrower is inside - see {@link #invokeOnUse}.
     */
    public static boolean canFireFrom(@Nullable LivingEntity holder, ItemStack stack) {
        return !coolingDown(holder, stack);
    }

    /**
     * One invocation, and whether it got as far as being one. The two answers differ exactly where the use
     * cooldown reads them: every ability refusing still means the carrier was used, while a carrier that is
     * blank, uncharged, inert or still cooling down was never attempted - and says so.
     */
    private static Attempt attempt(SpiritSource source, ItemStack stack) {
        LivingEntity holder = source.actor();
        // Nothing living is answerable for it: an ability needs somebody to pay, to be credited and to answer for
        // it, and there is nobody. The carrier stays charged, which is what a click by a person can use.
        if (holder == null || source.level().isClientSide() || !(stack.getItem() instanceof TalismanItem))
            return Attempt.NOT_AN_ATTEMPT;
        // Two rules, one per way in. The use window is about the hand alone: in a hand the invocation is
        // rate-limited and leaves the window that rate-limits it, while a carrier standing on a display stand is
        // outside every window - it does not read one and does not leave one. The burn is about where the carrier
        // is: a hand keeps what a creative hand would not spend, a placed carrier is spent whenever it fires.
        // The source's own flag says which of the two this is, so nothing has to infer it from whether an actor
        // happened to be passed.
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
        int fired = 0;
        Failure failure = null;
        for (Holder<Ability> ability : abilities) {
            UseResult result = AbilityService.useCarried(ability, ability.value(), holder, holderAbilities, resources, gameTime, context, source.position());
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
        // What was poured in was spent on the invocation, and a placed carrier is spent with it. A stack of them
        // is a stack of one-shot carriers, so what is left starts empty rather than inheriting the charge.
        stack.remove(MxtDataComponents.SPIRIT_STORAGE);
        if (inHand) {
            // A hand spends one carrier per invocation unless it is creative, where vanilla's own rule already
            // answers that the stack is not the thing being spent.
            if (!(holder instanceof Player player) || !player.hasInfiniteMaterials()) stack.consume(1, holder);
        } else {
            stack.shrink(1);
        }
        say(holder, Component.translatable("actionbar.mxt.talisman.invoked", fired));
        return Attempt.FIRED;
    }

    /**
     * What one invocation did. An attempt is what the use cooldown is charged for, so the two answers are kept
     * apart rather than flattened into a boolean the caller would have to guess at.
     */
    private record Attempt(boolean attempted, boolean fired) {
        private static final Attempt NOT_AN_ATTEMPT = new Attempt(false, false);
        private static final Attempt REFUSED = new Attempt(true, false);
        private static final Attempt FIRED = new Attempt(true, true);
    }

    /**
     * Whether this carrier is still on the cooldown its own use set. Vanilla's tracker is per player and keyed by
     * the item, and every carrier is the same item - so one window covers every carrier a player holds, which is
     * the point of it. A group of its own is what {@code cooldown_group} is for.
     */
    private static boolean coolingDown(@Nullable LivingEntity holder, ItemStack stack) {
        return useCooldownTicks() > 0 && holder instanceof Player player && player.getCooldowns().isOnCooldown(stack);
    }

    /**
     * Vanilla's own tracker through vanilla's own call, with the window coming from the server option instead of
     * from a component. The group is read off the stack - the item's {@code use_cooldown} component if it has
     * one, its registry key if not - so the stack handed in has to be one that still holds what was used. The
     * server announces the window to the client by itself.
     */
    private static void applyCooldown(@Nullable LivingEntity holder, ItemStack stackBeforeUse) {
        int ticks = useCooldownTicks();
        if (ticks <= 0 || !(holder instanceof Player player)) return;
        player.getCooldowns().addCooldown(stackBeforeUse, ticks);
    }

    private static int useCooldownTicks() {
        return MxtServerConfig.INSTANCE.talisman.useCooldown.getValue();
    }

    /**
     * What is written on one carrier, in the order it was written.
     */
    public static List<Holder<Talisman>> inscribed(ItemStack stack) {
        return component(stack).talismans();
    }

    /**
     * The carrier's own component, with the defaults a missing one means: nothing inscribed, and the firing
     * mode - which is also what every carrier written before the mode existed decodes as.
     */
    private static TalismanComponent component(ItemStack stack) {
        return stack.getOrDefault(MxtDataComponents.TALISMAN, TalismanComponent.EMPTY);
    }

    /**
     * Every ability the inscribed definitions name, with the tags they accept expanded, once each: a carrier
     * that names the same ability twice fires it once.
     */
    private static List<Holder<Ability>> abilities(List<Holder<Talisman>> written) {
        Registry<Ability> registry = MxtDatapackRegistries.registry(MxtResourceKeys.ABILITY);
        return written.stream()
                .flatMap(talisman -> RegistryCodecs.resolve(talisman.value().abilities(), registry))
                .distinct().toList();
    }

    /**
     * The whole bill of a stack, as the pour reads it: one entry per aura, in declaration order, with whatever
     * has been poured into it so far.
     */
    public static List<Entry> entries(ItemStack stack) {
        Map<Holder<Aura>, Integer> bill = bill(stack);
        if (bill.isEmpty()) return List.of();
        SpiritStorageComponent charge = stack.getOrDefault(MxtDataComponents.SPIRIT_STORAGE, SpiritStorageComponent.EMPTY);
        List<Entry> entries = new ArrayList<>(bill.size());
        bill.forEach((aura, capacity) -> entries.add(new Entry(aura,
                Math.min(capacity, Math.max(0, charge.get(aura))), capacity)));
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
