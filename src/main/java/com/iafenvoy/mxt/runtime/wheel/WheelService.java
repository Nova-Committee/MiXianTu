package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.spirit.SpiritBurstService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The server's half of the wheel: what a submitted layout may contain, and what choosing a sector does.
 * Nothing from the client is trusted - the pipeline behind an entry re-checks grant, cost and cooldown.
 */
public final class WheelService {
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

    /** Empty or a slot whose id resolves; an unresolvable slot decays to "nothing armed" (caller logs). */
    public static Optional<WheelSlot> sanitizeSelection(ServerPlayer player, @Nullable WheelSlot submitted) {
        if (submitted == null || submitted.isEmpty()) return Optional.empty();
        return submitted.kind().exists(player.level().registryAccess(), submitted.id())
                ? Optional.of(submitted) : Optional.empty();
    }

    public static boolean trigger(ServerPlayer player, @Nullable WheelEntryKind kind, @Nullable Identifier id) {
        if (kind == null || id == null) return false;
        return switch (kind) {
            case ABILITY -> use(player, id);
            case AURA -> SpiritBurstService.fireOnce(player, id);
            case EMPTY -> false;
        };
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
        AbilityService.use(ability, ability.value(), player, abilities, resources, player.level().getGameTime(),
                FormulaContext.of(player));
        return true;
    }
}
