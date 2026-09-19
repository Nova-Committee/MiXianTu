package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Matches any item that is a spirit herb carrying a named element or material tag. The tags belong to the herb
 * definition rather than to the item, so a declaration can say "a fire-aligned herb" without knowing which items
 * a data pack later binds to that herb, and an item that is added to the herb afterwards is covered by the same
 * file. Both fields are optional and every field that is given has to be present on the herb.
 * <p>
 * It lives beside {@link SpiritHerbService} rather than with the other built-in entries, because it is one of the
 * few entries that reads a data-pack registry instead of vanilla's item registry alone.
 */
public record HerbTagEntry(Optional<Identifier> element, Optional<Identifier> material) implements Entry {
    public static final MapCodec<HerbTagEntry> CODEC = RecordCodecBuilder.<HerbTagEntry>mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("element").forGetter(HerbTagEntry::element),
            Identifier.CODEC.optionalFieldOf("material").forGetter(HerbTagEntry::material)
    ).apply(i, HerbTagEntry::new)).validate(HerbTagEntry::validate);

    private static DataResult<HerbTagEntry> validate(HerbTagEntry entry) {
        return entry.element.isEmpty() && entry.material.isEmpty()
                ? DataResult.error(() -> "herb_tag needs element, material or both to ask about")
                : DataResult.success(entry);
    }

    @Override
    public boolean matches(ItemStack stack) {
        if (stack.isEmpty() || (this.element.isEmpty() && this.material.isEmpty())) return false;
        final SpiritHerb herb;
        try {
            herb = SpiritHerbService.find(stack).orElse(null);
        } catch (IllegalStateException exception) {
            // The herb registry only exists while a server runs, and a matcher is also evaluated on a client,
            // where the honest answer is "this is not known to be that herb".
            return false;
        }
        if (herb == null) return false;
        return this.element.map(herb.elementTags()::contains).orElse(true)
                && this.material.map(herb.materialTags()::contains).orElse(true);
    }

    @Override
    public MapCodec<HerbTagEntry> codec() {
        return CODEC;
    }
}
