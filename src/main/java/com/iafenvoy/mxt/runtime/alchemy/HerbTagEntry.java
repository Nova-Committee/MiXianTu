package com.iafenvoy.mxt.runtime.alchemy;

import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.matcher.ItemMatcher.Entry;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Matches any item that is a spirit herb carrying a named element or material. The tags belong to the herb
 * definition rather than to the item, so a declaration can say "a fire-aligned herb" without knowing which items a
 * data pack later binds to that herb, and an item added to the herb afterwards is covered by the same file. Both
 * fields are optional, and every field given has to be present on the herb.
 * <p>
 * The element field speaks the element registry in both directions: an entry names one element and a {@code #} tag
 * names every element in it, and the herb's own {@code element_tags} is read the same way, so a pack never has to
 * guess which side the tag belongs on.
 */
public record HerbTagEntry(Optional<Either<Holder<Element>, TagKey<Element>>> element,
                           Optional<Identifier> material) implements Entry {
    public static final MapCodec<HerbTagEntry> CODEC = RecordCodecBuilder.<HerbTagEntry>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTag(MxtResourceKeys.ELEMENT).optionalFieldOf("element").forGetter(HerbTagEntry::element),
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
        try {
            SpiritHerb herb = SpiritHerbService.find(stack).orElse(null);
            if (herb == null) return false;
            // The element registry only exists while a server runs, and a matcher is also evaluated on a client,
            // where the honest answer is "not known to be that herb" - hence the fetch inside this block.
            Registry<Element> registry = MxtDatapackRegistries.registry(MxtResourceKeys.ELEMENT);
            return this.element.map(query -> Elements.aligned(registry, herb.elementTags(), query)).orElse(true)
                    && this.material.map(herb.materialTags()::contains).orElse(true);
        } catch (IllegalStateException exception) {
            return false;
        }
    }

    @Override
    public MapCodec<HerbTagEntry> codec() {
        return CODEC;
    }
}
