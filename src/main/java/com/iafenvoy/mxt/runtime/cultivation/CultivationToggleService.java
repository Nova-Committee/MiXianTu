package com.iafenvoy.mxt.runtime.cultivation;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The enable/disable module for spirit roots and physiques: the server-authoritative way to switch a held root
 * or physique off without giving it up, and the definition of what "off" means.
 *
 * <p>Off is not removed. The body still holds it - {@code mxt:has_spirit_root} still names it, removal still
 * works, and switching it back on restores everything it gave - but nothing it provides applies: no element, no
 * cultivation multiplier, no granted ability, no passive attribute, and no rule it states about other roots.
 * That is why every reader of the two collections asks for the <em>active</em> ones.</p>
 *
 * <p>What this module deliberately does not have is a way for a player to reach it. There is no command, no key
 * binding and no screen: the state is stored, synced, saved and honoured, and how a pack or a game mode lets a
 * cultivator operate it is left to whoever wants the feature.</p>
 *
 * <p>The storage lives in {@link SpiritIdentityAttachment}, beside the collections it is about, because a toggle
 * has to travel with the things it toggles - a synced, saved copy of a body that lost its switches would read as
 * "everything is on" the moment it crossed a dimension.</p>
 */
public final class CultivationToggleService {
    private CultivationToggleService() {
    }

    /**
     * Switches one held spirit root on or off, and re-derives what the body is granted so the abilities the
     * root handed out are released or handed back in the same step.
     */
    public static Result setSpiritRootEnabled(LivingEntity entity, Holder<SpiritRoot> root, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (!spirit.spiritRoots().contains(root)) return Result.rejected(Failure.NOT_HELD);
        if (!spirit.setSpiritRootEnabled(root, enabled)) return Result.unchangedToggle();
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedToggle();
    }

    public static Result setPhysiqueEnabled(LivingEntity entity, Holder<Physique> physique, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        if (!spirit.physiques().contains(physique)) return Result.rejected(Failure.NOT_HELD);
        if (!spirit.setPhysiqueEnabled(physique, enabled)) return Result.unchangedToggle();
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedToggle();
    }

    public static boolean isSpiritRootEnabled(LivingEntity entity, Holder<SpiritRoot> root) {
        return entity.getData(MxtAttachments.SPIRIT_IDENTITY).isSpiritRootEnabled(root);
    }

    public static boolean isPhysiqueEnabled(LivingEntity entity, Holder<Physique> physique) {
        return entity.getData(MxtAttachments.SPIRIT_IDENTITY).isPhysiqueEnabled(physique);
    }

    /**
     * Switches everything the body holds on or off at once, which is the shape a "seal your cultivation" effect
     * wants; it answers how many toggles that changed.
     */
    public static Result setAllEnabled(LivingEntity entity, boolean enabled) {
        SpiritIdentityAttachment spirit = entity.getData(MxtAttachments.SPIRIT_IDENTITY);
        int changed = 0;
        for (Holder<SpiritRoot> root : List.copyOf(spirit.spiritRoots()))
            if (spirit.setSpiritRootEnabled(root, enabled)) changed++;
        for (Holder<Physique> physique : List.copyOf(spirit.physiques()))
            if (spirit.setPhysiqueEnabled(physique, enabled)) changed++;
        if (changed == 0) return Result.unchangedToggle();
        CultivationGrantService.recalculate(entity, spirit, entity.getData(MxtAttachments.ABILITY_HOLDER));
        return Result.changedToggle();
    }

    public enum Failure {NOT_HELD, SERVER_ONLY}

    /**
     * What a toggle did: whether it changed anything, and why it did nothing when it did not.
     */
    public record Result(boolean changed, @Nullable Failure failure) {
        private static Result changedToggle() {
            return new Result(true, null);
        }

        private static Result unchangedToggle() {
            return new Result(false, null);
        }

        private static Result rejected(Failure failure) {
            return new Result(false, failure);
        }
    }
}
