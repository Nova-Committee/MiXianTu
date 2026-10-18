package com.iafenvoy.mxt.registry.cache;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.iafenvoy.mxt.data.cultivation.DamageTypeClaim;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

public class DamageElementsCache extends RegistryCache<ListMultimap<Holder<DamageType>, DamageElementsCache.Claim>> {
    private final ListMultimap<Holder<DamageType>, Claim> data = LinkedListMultimap.create();

    @Override
    protected void build(RegistryAccess access) {
        Registry<Element> elements = access.lookupOrThrow(MxtResourceKeys.ELEMENT);
        Registry<DamageType> types = access.lookupOrThrow(Registries.DAMAGE_TYPE);
        for (Holder.Reference<Element> element : elements.listElements().toList()) this.claimAll(types, element);
    }

    @Override
    protected void clear() {
        this.data.clear();
    }

    @Override
    public ListMultimap<Holder<DamageType>, Claim> get() {
        return this.data;
    }

    private void claimAll(Registry<DamageType> types, Holder<Element> element) {
        for (DamageTypeClaim claim : element.value().damageTypes()) {
            // The claim's own number when it wrote one, the element's own default otherwise.
            double attachment = claim.attachment(element.value().damageAttachment());
            claim.type().left().ifPresentOrElse(type -> this.claim(type, element, attachment), () -> {
                TagKey<DamageType> tag = claim.type().right().orElseThrow();
                types.listElements().filter(type -> type.is(tag)).forEach(type -> this.claim(type, element, attachment));
            });
        }
    }

    //FIXME::
    private void claim(Holder<DamageType> type, Holder<Element> element, double attachment) {
        for (Claim existing : this.data.get(type)) if (existing.element().equals(element)) return;
        // Every claim multiplies downstream, so a second claimant for one type is worth a warning.
        if (this.data.containsKey(type))
            LOGGER.warn("Damage type {} is claimed by more than one element; every claim multiplies the element relation of such a strike", HolderHelper.id(type));
        this.data.put(type, new Claim(element, attachment));
    }

    // The number belongs to the claim, not to the element, so the index stores this rather than bare holders.
    public record Claim(Holder<Element> element, double attachment) {
    }
}
