package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.event.CurseRemoveEvent.Reason;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The cure side of the cleanse model: an antidote names the curse tags it removes, the tag files list the
 * curses, and the curse definition itself never declares what may cleanse it.
 * <p>
 * A held curse carrying <em>any one</em> of the listed tags is removed, which is what makes one antidote able to
 * answer several kinds of poison at once.
 */
public record RemoveCursesByTagAction(List<TagKey<Curse>> tags) implements EntityAction {
    public static final MapCodec<RemoveCursesByTagAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            TagKey.hashedCodec(MxtResourceKeys.CURSE).listOf().fieldOf("tags").forGetter(RemoveCursesByTagAction::tags)
    ).apply(i, RemoveCursesByTagAction::new));

    public RemoveCursesByTagAction {
        if (tags.isEmpty()) throw new IllegalArgumentException("tags must not be empty");
    }

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        long gameTime = entity.level().getGameTime();
        List<Holder<Curse>> matches = entity.getData(MxtAttachments.CURSE_HOLDER).instances().keySet().stream()
                .filter(curse -> this.tags.stream().anyMatch(curse::is))
                .toList();
        matches.forEach(curse -> CurseService.remove(entity, curse, Reason.CLEANSED, gameTime, ctx.formula()));
    }

    @Override
    public @NonNull MapCodec<RemoveCursesByTagAction> codec() {
        return CODEC;
    }
}
