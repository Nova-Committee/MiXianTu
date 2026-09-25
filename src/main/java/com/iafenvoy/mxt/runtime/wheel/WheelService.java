package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.api.WheelEntryKind;
import com.iafenvoy.mxt.api.WheelSource;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Abilities;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.creature.ContractBehaviorService;
import com.iafenvoy.mxt.runtime.creature.ContractBells;
import com.iafenvoy.mxt.runtime.creature.ContractFeedback;
import com.iafenvoy.mxt.runtime.creature.ContractService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The server's half of the wheel: what a submitted layout may contain, and what choosing a page's entry does.
 * Nothing from the client is trusted - the page a request names is re-read here, and the pipeline behind the entry
 * re-checks grant, cost and cooldown.
 *
 * <p>A press is one call into {@link AbilityActivationService}, so the wheel, the command and the script bridge
 * cannot drift apart; the wheel only adds the carrier the page it was asked about names.
 */
public final class WheelService {
    // Cells are twelve to a page and pages only exist while what they read exists, so this is far past any real
    // wheel: it is here so a nonsense number cannot be stored, not because the server uses the value.
    public static final int MAX_ARMED = 4096;

    private WheelService() {
    }

    // Each sector is empty or an id that exists in its kind's registry; anything else becomes empty.
    public static WheelLayout sanitize(ServerPlayer player, @Nullable WheelLayout submitted) {
        if (submitted == null) return WheelLayout.EMPTY;
        RegistryAccess access = player.level().registryAccess();
        List<WheelSlot> sanitized = new ArrayList<>(WheelLayout.SLOTS);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++)
            sanitized.add(resolve(access, submitted.slot(sector)));
        return new WheelLayout(sanitized);
    }

    // The source is re-read here because it is what says the entry is reachable at all; grant, cost and cooldown
    // are still re-checked by the pipeline behind the entry. A dropped request and a refused ability both say so,
    // because neither the client nor the log could otherwise tell either from "the key did nothing".
    public static boolean trigger(ServerPlayer player, @Nullable WheelSource source, @Nullable WheelEntryKind kind, @Nullable Identifier id) {
        return trigger(player, source, kind, id, Optional.empty());
    }

    // A filled-in direction is the same request asked the other way round: it names the state it wants, so there is
    // no cell behind it and the page it came from is not what decides - the ability's own gate still is.
    public static boolean trigger(ServerPlayer player, @Nullable WheelSource source, @Nullable WheelEntryKind kind,
                                  @Nullable Identifier id, Optional<Boolean> enabled) {
        if (source == null || kind == null || id == null) return false;
        if (enabled.isPresent()) return kind.directed(player, source, id, enabled.get());
        if (!WheelSources.offers(player, source, kind, id)) {
            MiXianTu.LOGGER.info("Dropping the wheel trigger {} {} from {} sent by {}: that source does not hold it",
                    source.id(), id, kind.id(), player.getGameProfile().name());
            player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.stale_entry"), true);
            return false;
        }
        // What a press does belongs to the kind, so a kind a content mod registered is served by its own code
        // rather than by a branch here.
        return kind.trigger(player, source, id);
    }

    // Asking for a state a switch is already in is a no-op rather than a take-off followed by a landing, which is
    // what lets a screen re-send its intent without watching the state.
    static boolean directed(ServerPlayer player, WheelSource source, Identifier id, boolean wanted) {
        Holder<Ability> ability = Abilities.resolve(player.level().registryAccess(), id).orElse(null);
        if (ability == null) {
            MiXianTu.LOGGER.info("Dropping the directed wheel request {} from {}: the ability is no longer registered",
                    id, player.getGameProfile().name());
            player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.stale_entry"), true);
            return false;
        }
        // Only a switch has a state to ask for; a one-shot keeps the press channel, which carries no direction.
        Optional<Boolean> state = AbilityActivationService.state(player, ability);
        if (state.isEmpty())
            return refuse(player, id, Togglable.Failure.UNAVAILABLE, null, "directed", "actionbar.mxt.wheel.use_failed");
        if (state.get() == wanted) return true;
        ItemStack carrier = WheelSources.carrier(player, source, id).orElse(null);
        return activate(player, ability, carrier, id, "directed");
    }

    // The number is a place, not an entry, so it stays valid - and finds the same slot again - while whatever used
    // to fill it is gone. Only a value outside the numbering is refused, since that is not a place at all.
    public static Optional<Integer> sanitizeArmed(@Nullable Integer submitted) {
        if (submitted == null || submitted < 0 || submitted > MAX_ARMED) return Optional.empty();
        return Optional.of(submitted);
    }

    private static WheelSlot resolve(RegistryAccess access, WheelSlot slot) {
        if (slot == null || slot.isEmpty()) return WheelSlot.EMPTY;
        return slot.kind().exists(access, slot.id()) ? new WheelSlot(slot.kind(), slot.id()) : WheelSlot.EMPTY;
    }

    // One press. An ability that says it can be pressed goes through the shared entry point, which is where a
    // switch, a container and a cast are told apart; anything else keeps the plain cast path.
    static boolean press(ServerPlayer player, WheelSource source, Identifier id) {
        Holder<Ability> ability = Abilities.resolve(player.level().registryAccess(), id).orElse(null);
        if (ability == null) {
            // The same race WheelSources#offers exists for, one step later: registered when the source was read
            // and gone by the time the press arrived, which is rare enough to be worth saying rather than dropping.
            MiXianTu.LOGGER.info("Dropping the wheel press {} on {} from {}: the ability is no longer registered",
                    id, source.id(), player.getGameProfile().name());
            player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.stale_entry"), true);
            return false;
        }
        if (!AbilityActivationService.togglable(ability)) return use(player, ability);
        return activate(player, ability, WheelSources.carrier(player, source, id).orElse(null), id, "press");
    }

    private static boolean activate(ServerPlayer player, Holder<Ability> ability, @Nullable ItemStack carrier,
                                    Identifier id, String verb) {
        Togglable.Result result = AbilityActivationService.activate(player, ability, carrier);
        if (result.failure() == null) return true;
        return refuse(player, id, result.failure(), result.failedResource(), verb, "actionbar.mxt.wheel.use_failed");
    }

    // The fallback for an entry whose ability is not pressable at all, which a saved layout can name even though
    // the pool it was picked from only offers pressable ones.
    private static boolean use(ServerPlayer player, Holder<Ability> ability) {
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        AbilityService.UseResult result = AbilityService.use(ability, player, abilities, resources,
                player.level().getGameTime(), FormulaContext.of(player));
        if (result.failure() == null) return true;
        return refuse(player, HolderHelper.id(ability), AbilityActivationService.failureOf(result.failure()),
                result.failedResource(), "cast", "actionbar.mxt.wheel.cast_failed");
    }

    // A burst the player cannot pay for is refused the same way an ability is.
    static boolean burst(ServerPlayer player, Identifier id) {
        if (SpiritBurstService.fireOnce(player, id)) return true;
        MiXianTu.LOGGER.info("Refusing the wheel burst {} for {}: the burst did not go off",
                id, player.getGameProfile().name());
        player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.burst_failed"), true);
        return false;
    }

    // An order for the beast the player's bell is tuned to. The creature is re-read from the bell here, and the
    // record, the owner and the order itself are all re-checked behind that, so a stale bell orders nothing.
    static boolean order(ServerPlayer player, Identifier id) {
        ContractBehavior behavior = ContractBehaviors.byId(id).orElse(null);
        if (behavior == null) {
            // A layout or a hand-edited stack can name an order whose provider is gone; the same race the ability
            // path guards against, one registry-like list later.
            MiXianTu.LOGGER.info("Dropping the wheel order {} from {}: no such order is known",
                    id, player.getGameProfile().name());
            player.sendSystemMessage(ContractFeedback.of(ContractService.Failure.UNSUPPORTED_BEHAVIOR), true);
            return false;
        }
        Mob beast = ContractBells.beast(player).orElse(null);
        if (beast == null) {
            MiXianTu.LOGGER.info("Dropping the wheel order {} from {}: no loaded beast is tuned to that bell",
                    id, player.getGameProfile().name());
            player.sendSystemMessage(Component.translatable("actionbar.mxt.contract.no_beast"), true);
            return false;
        }
        ContractService.Result result = ContractBehaviorService.request(beast, player.getUUID(), behavior, false);
        if (!result.changed()) {
            MiXianTu.LOGGER.info("Refusing the wheel order {} for {}: {}", id, player.getGameProfile().name(),
                    result.failure());
            player.sendSystemMessage(ContractFeedback.of(result.failure()), true);
            return false;
        }
        player.sendSystemMessage(Component.translatable("actionbar.mxt.contract.ordered", beast.getDisplayName(),
                behavior.name()), true);
        return true;
    }

    // Both paths report the same way, because the client cannot tell a press from a cast: the reason comes out of
    // one table, the log keeps both the reason and the entry, and the action bar shows the reason to the player.
    private static boolean refuse(ServerPlayer player, Identifier id, Togglable.Failure failure,
                                  @Nullable Identifier failedResource, String verb, String messageKey) {
        String name = failure.name().toLowerCase(Locale.ROOT);
        MiXianTu.LOGGER.info("Refusing the wheel {} {} for {}: {}{}", verb, id, player.getGameProfile().name(), name,
                failedResource == null ? "" : " (" + failedResource + ")");
        Component reason = failure == Togglable.Failure.INSUFFICIENT_RESOURCE && failedResource != null
                ? Component.translatable("actionbar.mxt.ability.failure.insufficient_resource_named",
                DefinitionText.name(failedResource, "resource"))
                : Component.translatable("actionbar.mxt.ability.failure." + name);
        player.sendSystemMessage(Component.translatable(messageKey, reason), true);
        return false;
    }
}
