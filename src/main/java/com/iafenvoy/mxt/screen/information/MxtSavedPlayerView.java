package com.iafenvoy.mxt.screen.information;

import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.LifeSpanService;
import com.iafenvoy.saturn.data.information.model.SavedPlayerViewContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads MiXianTu's rows out of a player's saved attachments, so a view of a body that is not loaded still answers what
 * the save can prove. Rows that need the live body keep the saved facts only: no breakthrough readiness, no current
 * minor stage, and every held curse listed because its display condition cannot be evaluated.
 *
 * <p>The attachments are decoded through their own codecs, which is the same shape the server writes: NeoForge stores
 * each attachment under its registry id inside {@code neoforge:attachments}.
 */
public final class MxtSavedPlayerView {
    private static final String ATTACHMENTS = "neoforge:attachments";

    private MxtSavedPlayerView() {
    }

    public static Map<String, MxtPlayerView.Row> read(SavedPlayerViewContext context) {
        CompoundTag attachments = context.player().getCompound(ATTACHMENTS).orElseGet(CompoundTag::new);
        RegistryOps<Tag> ops = context.registries().createSerializationContext(NbtOps.INSTANCE);
        CultivationAttachment cultivation = decode(attachments, MxtAttachments.CULTIVATION, CultivationAttachment.CODEC, ops);
        LifespanAttachment lifespan = decode(attachments, MxtAttachments.LIFESPAN, LifespanAttachment.CODEC, ops);
        Map<String, MxtPlayerView.Row> rows = new LinkedHashMap<>();
        MxtPlayerView.realm(cultivation, null, rows);
        MxtPlayerView.lifespan(lifespan == null ? LifeSpanService.UNACCOUNTED : lifespan.lifespanRemaining(), lifespan == null ? LifeSpanService.UNACCOUNTED : lifespan.lifespanTotal(), rows);
        MxtPlayerView.progress(cultivation, null, rows);
        SpiritIdentityAttachment identity = decode(attachments, MxtAttachments.SPIRIT_IDENTITY, SpiritIdentityAttachment.CODEC, ops);
        MxtPlayerView.spiritRoots(identity, rows);
        MxtPlayerView.physiques(identity, rows);
        MxtPlayerView.techniques(decode(attachments, MxtAttachments.TECHNIQUE, TechniqueAttachment.CODEC, ops), rows);
        MxtPlayerView.curses(decode(attachments, MxtAttachments.CURSE_HOLDER, CurseHolderAttachment.CODEC, ops), null,
                context.meta().capturedGameTime().orElse(null), rows);
        return rows;
    }

    // A missing attachment and one that fails to decode both leave the row out: the save cannot prove it either way.
    @Nullable
    private static <T> T decode(CompoundTag attachments, DeferredHolder<AttachmentType<?>, AttachmentType<T>> type,
                                MapCodec<T> codec, RegistryOps<Tag> ops) {
        return attachments.getCompound(type.getId().toString())
                .flatMap(tag -> codec.codec().parse(ops, tag).result())
                .orElse(null);
    }
}
