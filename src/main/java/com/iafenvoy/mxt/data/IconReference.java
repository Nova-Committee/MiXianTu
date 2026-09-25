package com.iafenvoy.mxt.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * A single icon a definition can carry: either a 16x16 GUI texture or an item. The item branch is an
 * {@link ItemStackTemplate} because datapack registries are parsed before item components are bound and one
 * reference is shared; {@link #CODEC} puts the texture branch first, so a bare string always means texture.
 */
public record IconReference(Either<Identifier, ItemStackTemplate> value) {
    public static final Codec<IconReference> CODEC = Codec.either(
            Identifier.CODEC,
            ItemStackTemplate.CODEC
    ).xmap(IconReference::new, IconReference::value);

    public static IconReference texture(Identifier texture) {
        return new IconReference(Either.left(texture));
    }

    public static IconReference item(ItemStackTemplate item) {
        return new IconReference(Either.right(item));
    }

    /**
     * The icon of a non-empty stack, or empty for an empty stack, so call sites need no null check.
     */
    public static Optional<IconReference> of(ItemStack stack) {
        return stack.isEmpty() ? Optional.empty() : Optional.of(item(ItemStackTemplate.fromNonEmptyStack(stack)));
    }

    public Optional<Identifier> texture() {
        return this.value.left();
    }

    public Optional<ItemStackTemplate> item() {
        return this.value.right();
    }

    /**
     * A fresh stack for this icon, or empty when it draws a texture. The caller owns the result.
     */
    public Optional<ItemStack> stack() {
        return this.value.right().map(ItemStackTemplate::create);
    }

    @Override
    public @NonNull String toString() {
        // Deliberately shallow: the item branch holds a registry holder, which need not be resolvable here.
        return "IconReference[" + this.texture().map(Identifier::toString)
                .or(() -> this.value.right().flatMap(template -> template.item().unwrapKey()).map(Object::toString))
                .orElse("?") + "]";
    }
}
