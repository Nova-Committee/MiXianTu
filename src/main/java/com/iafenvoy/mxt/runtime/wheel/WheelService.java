package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.artifact.ability.ToggableArtifactAbility;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.artifact.ArtifactToggleService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The server's half of the wheel: what a submitted layout may contain, and what choosing a page's entry does.
 * Nothing from the client is trusted - the page a request names is re-read here, and the pipeline behind the
 * entry re-checks grant, cost and cooldown.
 */
public final class WheelService {
    /**
     * The largest cell number a client may remember. Cells are twelve to a page and pages only exist while the
     * thing they read exists, so this is far past any real wheel; it is here so a nonsense number cannot be
     * stored, not because the server has a use for the value.
     */
    public static final int MAX_ARMED = 4096;

    private WheelService() {
    }

    /** Each sector is empty or an id that exists in its kind's registry; anything else becomes empty. */
    public static WheelLayout sanitize(ServerPlayer player, @Nullable WheelLayout submitted) {
        if (submitted == null) return WheelLayout.EMPTY;
        RegistryAccess access = player.level().registryAccess();
        List<WheelSlot> sanitized = new ArrayList<>(WheelLayout.SLOTS);
        for (int sector = 0; sector < WheelLayout.SLOTS; sector++)
            sanitized.add(resolve(access, submitted.slot(sector)));
        return new WheelLayout(sanitized);
    }

    /**
     * One wheel request. The source travels with it and is re-read here, because that is what says the entry is
     * reachable at all: the configured source reads the player's saved layout, a derived one reads what the named
     * equipment grants right now. Nothing else about the request is trusted - grant, cost and cooldown are still
     * re-checked by the pipeline behind the entry.
     *
     * <p>A request that is dropped anyway, and an ability the pipeline then refuses, both say so: the first in
     * the log, the second on the player's action bar. The client cannot tell either of them apart from "the key
     * did nothing", and neither can the log - which is exactly the report this is here to answer.</p>
     */
    public static boolean trigger(ServerPlayer player, @Nullable WheelSource source, @Nullable WheelEntryKind kind, @Nullable Identifier id) {
        if (source == null || kind == null || id == null) return false;
        if (!WheelSources.offers(player, source, kind, id)) {
            MiXianTu.LOGGER.info("Dropping the wheel trigger {} {} from {} sent by {}: that source does not hold it",
                    source.getSerializedName(), id, kind.getSerializedName(), player.getGameProfile().name());
            player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.stale_entry"), true);
            return false;
        }
        return switch (kind) {
            case ABILITY -> use(player, id);
            case AURA -> burst(player, id);
            case ARTIFACT -> activate(player, source, id);
            case EMPTY -> false;
        };
    }

    /**
     * A remembered cell number, kept as sent. There is nothing to resolve here on purpose: the number is a
     * place, not an entry, so it stays valid - and finds the same slot again - while whatever used to fill it
     * is gone. Only a value outside the numbering is refused, since that is not a place at all.
     */
    public static Optional<Integer> sanitizeArmed(@Nullable Integer submitted) {
        if (submitted == null || submitted < 0 || submitted > MAX_ARMED) return Optional.empty();
        return Optional.of(submitted);
    }

    private static WheelSlot resolve(RegistryAccess access, WheelSlot slot) {
        if (slot == null || slot.isEmpty()) return WheelSlot.EMPTY;
        return slot.kind().exists(access, slot.id()) ? new WheelSlot(slot.kind(), slot.id()) : WheelSlot.EMPTY;
    }

    private static boolean use(ServerPlayer player, Identifier id) {
        Holder<Ability> ability = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id).orElse(null);
        if (ability == null) return false;
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        AbilityService.UseResult result = AbilityService.use(ability, ability.value(), player, abilities, resources,
                player.level().getGameTime(), FormulaContext.of(player));
        if (result.failure() != null) notifyRefusal(player, id, result);
        return true;
    }

    /** Fires one aura; a burst the player cannot pay for is refused the same way an ability is. */
    private static boolean burst(ServerPlayer player, Identifier id) {
        if (SpiritBurstService.fireOnce(player, id)) return true;
        MiXianTu.LOGGER.info("Refusing the wheel burst {} for {}: the burst did not go off",
                id, player.getGameProfile().name());
        player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.burst_failed"), true);
        return false;
    }

    /**
     * Presses one artifact capability, re-read from the page that claims it - which is what makes "the sword that
     * is in my hand right now" the only thing a cell can fire.
     *
     * <p>The request names the cell and never what should happen: the side that owns the state reads it and the
     * implementation decides, so a client that guessed wrong about which way a switch is cannot ask for an
     * impossible state, and no packet has to carry one. A refusal - the artifact is gone, or it will not let this
     * holder use it - is reported on the action bar and in the log, exactly like a refused cast.</p>
     */
    private static boolean activate(ServerPlayer player, WheelSource source, Identifier id) {
        ArtifactToggleService.Toggle toggle = ArtifactToggleService.find(WheelSources.toggles(player, source), id)
                .orElse(null);
        if (toggle == null) {
            MiXianTu.LOGGER.info("Dropping the wheel press {} from {} sent by {}: that source no longer declares it",
                    id, source.getSerializedName(), player.getGameProfile().name());
            player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.stale_entry"), true);
            return false;
        }
        ToggableArtifactAbility.Result result = toggle.activate();
        if (result.failure() == null) return true;
        String name = result.failure().name().toLowerCase(Locale.ROOT);
        MiXianTu.LOGGER.info("Refusing the wheel press {} on {} for {}: {}",
                id, source.getSerializedName(), player.getGameProfile().name(), name);
        player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.use_failed",
                Component.translatable("actionbar.mxt.artifact_skill." + name)), true);
        return false;
    }

    /**
     * Tells the caster why an ability that seemed usable did nothing - from the wheel there is nothing else to
     * read, and "the key did nothing" is otherwise the whole of what the player can report.
     */
    private static void notifyRefusal(ServerPlayer player, Identifier id, AbilityService.UseResult result) {
        String name = result.failure().name().toLowerCase(Locale.ROOT);
        MiXianTu.LOGGER.info("Refusing the wheel cast {} for {}: {}{}", id, player.getGameProfile().name(), name,
                result.failedResource() == null ? "" : " (" + result.failedResource() + ")");
        Component reason = result.failure() == AbilityService.Failure.INSUFFICIENT_RESOURCE && result.failedResource() != null
                ? Component.translatable("actionbar.mxt.ability.failure.insufficient_resource_named",
                DefinitionText.name(result.failedResource(), "resource"))
                : Component.translatable("actionbar.mxt.ability.failure." + name);
        player.sendSystemMessage(Component.translatable("actionbar.mxt.wheel.cast_failed", reason), true);
    }
}
