package com.iafenvoy.mxt.data;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;
import java.util.function.Function;

/**
 * One drawable sprite a resource bar can use: a GUI atlas sprite, or a rectangle of a texture with the sheet's own
 * size, optionally drawn at a declared size instead of the box the caller asks for.
 *
 * <p>The two codecs exist because a bare id kept meaning whatever the field it is written in always meant - a
 * texture for the {@code boss_bar} sheet, a GUI sprite for the {@code textured_bar} pair - and a sprite cannot
 * stand in for the sheet the boss bar cuts its cells from.
 */
public record SpriteIcon(Optional<Identifier> sprite, Optional<Identifier> texture, Optional<Region> region,
                         int width, int height) {
    // Both zero means "draw at the size the caller asks for".
    public static final Codec<SpriteIcon> SPRITE_CODEC = bare(true);
    public static final Codec<SpriteIcon> TEXTURE_CODEC = bare(false);
    // The middle ground every GUI texture is authored at, matching the renderers that came before this type.
    public static final int DEFAULT_TEXTURE_SIZE = 256;

    /**
     * Where a texture's usable area starts inside the file, and how big the file is: the pair a sprite does not
     * need, because the atlas already knows both.
     */
    public record Region(int u, int v, int textureWidth, int textureHeight) {
        public static final Codec<Region> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 8192).optionalFieldOf("u", 0).forGetter(Region::u),
                Codec.intRange(0, 8192).optionalFieldOf("v", 0).forGetter(Region::v),
                Codec.intRange(1, 8192).optionalFieldOf("texture_width", DEFAULT_TEXTURE_SIZE).forGetter(Region::textureWidth),
                Codec.intRange(1, 8192).optionalFieldOf("texture_height", DEFAULT_TEXTURE_SIZE).forGetter(Region::textureHeight)
        ).apply(i, Region::new));
    }

    private static final Codec<SpriteIcon> OBJECT_CODEC = RecordCodecBuilder.<SpriteIcon>create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("sprite").forGetter(SpriteIcon::sprite),
            Identifier.CODEC.optionalFieldOf("texture").forGetter(SpriteIcon::texture),
            Region.CODEC.optionalFieldOf("region").forGetter(SpriteIcon::region),
            Codec.intRange(0, 8192).optionalFieldOf("width", 0).forGetter(SpriteIcon::width),
            Codec.intRange(0, 8192).optionalFieldOf("height", 0).forGetter(SpriteIcon::height)
    ).apply(i, SpriteIcon::new)).validate(SpriteIcon::validate);

    public static SpriteIcon of(Identifier id, boolean sprite) {
        return sprite ? new SpriteIcon(Optional.of(id), Optional.empty(), Optional.empty(), 0, 0)
                : new SpriteIcon(Optional.empty(), Optional.of(id), Optional.empty(), 0, 0);
    }

    // A bare id means a sprite in one codec and a texture in the other, and the written form follows the same rule.
    private static Codec<SpriteIcon> bare(boolean spriteByDefault) {
        return Codec.either(Identifier.CODEC, OBJECT_CODEC).xmap(
                value -> value.map(id -> of(id, spriteByDefault), Function.identity()),
                icon -> icon.base(spriteByDefault)
                        .<Either<Identifier, SpriteIcon>>map(Either::left).orElseGet(() -> Either.right(icon)));
    }

    private static DataResult<SpriteIcon> validate(SpriteIcon icon) {
        if (icon.sprite().isPresent() == icon.texture().isPresent())
            return DataResult.error(() -> "A sprite icon names exactly one of sprite and texture");
        if (icon.sprite().isPresent() && icon.region().isPresent())
            return DataResult.error(() -> "A sprite icon cannot declare a region: an atlas sprite already says "
                    + "where it is in its sheet");
        if ((icon.width() == 0) != (icon.height() == 0))
            return DataResult.error(() -> "A sprite icon declares both width and height, or neither");
        return DataResult.success(icon);
    }

    public boolean isSprite() {
        return this.sprite().isPresent();
    }

    // The id that identifies this icon, whichever branch it took; empty only for the unset default.
    public Optional<Identifier> id() {
        return this.sprite().or(this::texture);
    }

    public int regionU() {
        return this.region().map(Region::u).orElse(0);
    }

    public int regionV() {
        return this.region().map(Region::v).orElse(0);
    }

    public int textureWidth() {
        return this.region().map(Region::textureWidth).orElse(DEFAULT_TEXTURE_SIZE);
    }

    public int textureHeight() {
        return this.region().map(Region::textureHeight).orElse(DEFAULT_TEXTURE_SIZE);
    }

    // The declared size, or the caller's box when the entry declares none.
    public int resolvedWidth(int fallback) {
        return this.width() > 0 ? this.width() : fallback;
    }

    public int resolvedHeight(int fallback) {
        return this.height() > 0 ? this.height() : fallback;
    }

    // The short written form, for an icon that is only an id: everything else needs the object form.
    private Optional<Identifier> base(boolean spriteByDefault) {
        if (this.region().isPresent() || this.width() > 0) return Optional.empty();
        return spriteByDefault ? this.sprite() : this.texture();
    }
}
