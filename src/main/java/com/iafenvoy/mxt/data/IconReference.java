package com.iafenvoy.mxt.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * A single icon a definition can carry: either a 16x16 GUI texture or an item; drawing lives in
 * {@code com.iafenvoy.mxt.render.IconRenderer}. An item is a {@link ItemStackTemplate} rather than a stack,
 * because a datapack registry is parsed before item components are bound and one reference is shared.
 * {@link #CODEC} puts the texture branch first, so a bare string always means texture.
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
     * The icon of a stack that exists, or empty for an empty stack, so a definition-derived icon needs no
     * null check at the call site.
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

    /**
     * The item branch holds a registry holder, so diagnostics must remain shallow.
     */
    @Override
    public @NonNull String toString() {
        return "IconReference[" + this.texture().map(Identifier::toString)
                .or(() -> this.value.right().flatMap(template -> template.item().unwrapKey()).map(Object::toString))
                .orElse("?") + "]";
    }
}
