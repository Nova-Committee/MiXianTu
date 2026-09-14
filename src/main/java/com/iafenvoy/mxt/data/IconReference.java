package com.iafenvoy.mxt.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Optional;

/**
 * A single icon a definition can carry: either a 16x16 GUI texture or an item.
 *
 * <p>This is the one icon type the whole mod uses, on both sides. A definition only says what its icon
 * <em>is</em>; the drawing lives in {@code com.iafenvoy.mxt.render.IconRenderer}, so every screen shares
 * one item/texture branch instead of each writing its own.</p>
 *
 * <p>An item is kept as a {@link ItemStackTemplate} rather than a stack: a datapack registry is parsed
 * before item components are bound, and one reference is shared by every viewer, so the stack has to be
 * materialised by whoever draws it.</p>
 *
 * <p>The two branches are inlined rather than wrapped, so the JSON is the shape itself: a plain string
 * is a texture, an object is an item. {@link #CODEC} is a plain {@code either}, so the texture branch is
 * tried first and an object falls through to the item branch.</p>
 *
 * <p>That ordering is what makes a bare string mean "texture": both an {@link Identifier} and an
 * {@link ItemStackTemplate} accept a string, so whichever branch is tried first claims it. Writing an
 * item therefore needs the object form ({@code {"id": ...}}), and a string is never an item.</p>
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
     * The icon of a stack that exists, or empty for an empty stack - which is how a definition-derived
     * icon (a blueprint's result, a step's method) becomes a reference without a null check at the
     * call site.
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
    public String toString() {
        return "IconReference[" + this.texture().map(Identifier::toString)
                .or(() -> this.value.right().flatMap(template -> template.item().unwrapKey()).map(Object::toString))
                .orElse("?") + "]";
    }
}
