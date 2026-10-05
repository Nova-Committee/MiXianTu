package com.iafenvoy.mxt.util.matcher.builtin;

import com.iafenvoy.mxt.data.quality.QualityRequirement;
import com.iafenvoy.mxt.runtime.item.QualityRequirements;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A matcher entry narrowing an item list to stacks whose tier satisfies a requirement. The item list is required,
 * because an entry with no item constraint could not express "paper of at least the third tier".
 */
public record QualityEntry(List<ItemMatcher.Entry> items, QualityRequirement requirement) implements ItemMatcher.Entry {
    public static final MapCodec<QualityEntry> CODEC = RecordCodecBuilder.<QualityEntry>mapCodec(i -> i.group(
            ItemMatcher.ENTRIES_CODEC.fieldOf("items").forGetter(QualityEntry::items),
            QualityRequirement.QUALITIES_FIELD.forGetter(entry -> entry.requirement().qualities()),
            QualityRequirement.MIN_QUALITY_FIELD.forGetter(entry -> entry.requirement().minQuality())
    ).apply(i, (items, qualities, minimum) -> new QualityEntry(items, new QualityRequirement(qualities, minimum))))
            .validate(QualityEntry::validate);

    private static DataResult<QualityEntry> validate(QualityEntry entry) {
        if (entry.items().isEmpty()) return DataResult.error(() -> "mxt:quality needs at least one item entry");
        return entry.requirement().isEmpty()
                ? DataResult.error(() -> "mxt:quality needs a quality list or a min_quality")
                : DataResult.success(entry);
    }

    @Override
    public boolean matches(ItemStack stack) {
        return ItemMatcher.matches(this.items, stack)
                && QualityRequirements.test(QualityRequirements.access(), stack, this.requirement);
    }

    // A requirement is always present (see the codec), and reading a tier reads the stack, so the per-item cache
    // never applies to this entry.
    @Override
    public boolean itemLevel() {
        return false;
    }

    @Override
    public MapCodec<QualityEntry> codec() {
        return CODEC;
    }
}
