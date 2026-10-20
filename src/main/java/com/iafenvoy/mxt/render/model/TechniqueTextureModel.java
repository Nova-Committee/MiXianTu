package com.iafenvoy.mxt.render.model;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.mojang.logging.LogUtils;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.color.item.ItemTintSources;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.*;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterItemModelsEvent;
import org.joml.Matrix4fc;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * Draws a technique book's two pages from the technique the stack names, so one book model covers every technique.
 * A technique's own art sits in its own namespace, and a page no pack drew for falls back to that namespace's shared
 * page and then to the model's own texture. The pages are re-baked per technique, never re-pointed one quad at a
 * time, because both slots can start out sharing one texture.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class TechniqueTextureModel implements ItemModel {
    // The type id items/technique_book.json names. This is an item model type, not a datapack registry.
    public static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "technique_textures");
    // A technique's own page art, and the one page its whole namespace falls back to when it has none. The page a
    // model itself declares is the last resort, and reaching it is simply leaving the slot alone.
    private static final String PAGE_DIRECTORY = "item/technique/";
    private static final String PAGE_FALLBACK = "item/technique_book_page";
    private static final Logger LOGGER = LogUtils.getLogger();
    // One line per technique the first time a stack of it is drawn, which is a whole packful of lines at once.
    private static final LoggerDispatcher TRACE = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.DEBUG, LoggingLevel.NONE);
    private final ItemModel base;
    private final ModelBaker baker;
    private final ResolvedModel resolved;
    private final Pages pages;
    private final List<ItemTintSource> tints;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    // One entry per technique, and a resource reload bakes this whole model again, so nothing here needs invalidating.
    private final Map<Identifier, ItemModel> variants = new HashMap<>();

    private TechniqueTextureModel(ItemModel base, BakingContext context, ResolvedModel resolved, Pages pages, List<ItemTintSource> tints, ModelRenderProperties properties, Matrix4fc transformation) {
        this.base = base;
        this.baker = context.blockModelBaker();
        this.resolved = resolved;
        this.pages = pages;
        this.tints = tints;
        this.properties = properties;
        this.transformation = transformation;
        // A slot the model does not declare swallows that page without a trace, so report it once per bake.
        TextureSlots modelSlots = resolved.getTopTextureSlots();
        for (Optional<String> slot : List.of(pages.left(), pages.right()))
            if (slot.isPresent() && modelSlots.getMaterial(slot.get()) == null)
                LOGGER.error("Model {} declares no texture slot {}, so that technique page has nowhere to land", resolved.debugName(), slot.get());
    }

    @SubscribeEvent
    public static void register(RegisterItemModelsEvent event) {
        event.register(ID, Unbaked.CODEC);
    }

    @Override
    public void update(@NonNull ItemStackRenderState output, @NonNull ItemStack item, @NonNull ItemModelResolver resolver, @NonNull ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        this.modelFor(item).update(output, item, resolver, displayContext, level, owner, seed);
    }

    private ItemModel modelFor(ItemStack stack) {
        Holder<Technique> holder = stack.get(MxtDataComponents.TECHNIQUE);
        Identifier technique = holder == null ? null : holder.unwrapKey().map(ResourceKey::identifier).orElse(null);
        // Rendering is single-threaded, so a plain map is enough; a technique nothing was drawn for caches the base.
        return technique == null ? this.base : this.variants.computeIfAbsent(technique, this::fill);
    }

    private ItemModel fill(Identifier technique) {
        TextureSlots modelSlots = this.resolved.getTopTextureSlots();
        Identifier left = this.art(technique, PAGE_DIRECTORY + technique.getPath() + "_left");
        Identifier right = this.art(technique, PAGE_DIRECTORY + technique.getPath() + "_right");
        Identifier shared = this.art(technique, PAGE_FALLBACK);
        // A slot is filled once, and a page's own art goes first: one slot can serve both pages, in which case the
        // page that drew art keeps it and the shared page only covers what is left.
        Map<String, Identifier> pageArt = new LinkedHashMap<>();
        this.collect(this.stitched(left) ? left : null, this.pages.left(), pageArt);
        this.collect(this.stitched(right) ? right : null, this.pages.right(), pageArt);
        Identifier sharedArt = this.stitched(shared) ? shared : null;
        this.collect(sharedArt, this.pages.left(), pageArt);
        this.collect(sharedArt, this.pages.right(), pageArt);
        if (pageArt.isEmpty())
            TRACE.log("Technique {} has no page art, so its pages stay as the model draws them", technique);
        else
            pageArt.forEach((slot, texture) -> TRACE.log("Technique {} draws page slot {} from {}", technique, slot, texture));
        TextureSlots.Data.Builder overrides = new TextureSlots.Data.Builder();
        for (Map.Entry<String, Identifier> page : pageArt.entrySet())
            if (modelSlots.getMaterial(page.getKey()) != null)
                overrides.addTexture(page.getKey(), new Material(page.getValue()));
        TextureSlots.Resolver resolver = new TextureSlots.Resolver();
        resolver.addFirst(overrides.build());
        for (ResolvedModel current = this.resolved; current != null; current = current.parent())
            resolver.addLast(current.wrapped().textureSlots());
        // Not bakeTopGeometry: on the default state it hands back the geometry baked the first time, so a second bake
        // with other slots would silently return the model's own textures.
        QuadCollection quads = this.resolved.getTopGeometry()
                .bake(resolver.resolve(this.resolved), this.baker, BlockModelRotation.IDENTITY, this.resolved, this.resolved.getTopAdditionalProperties());
        return new CuboidItemModelWrapper(this.tints, quads, this.properties, this.transformation);
    }

    // The slot the page art is looked up at, in the technique's own namespace.
    private Identifier art(Identifier technique, String path) {
        return Identifier.fromNamespaceAndPath(technique.getNamespace(), path);
    }

    private void collect(@Nullable Identifier texture, Optional<String> replace, Map<String, Identifier> out) {
        if (texture == null || replace.isEmpty() || out.containsKey(replace.get())) return;
        out.put(replace.get(), texture);
    }

    // A texture only exists once the atlas stitched it, and the atlas is the authority on what a pack drew.
    private boolean stitched(Identifier texture) {
        Material.Baked baked = this.baker.materials().get(new Material(texture), this.resolved);
        return !Objects.equals(baked.sprite().contents().name(), MissingTextureAtlasSprite.getLocation());
    }

    public record Unbaked(Identifier model, Optional<Transformation> transformation, List<ItemTintSource> tints,
                          Pages pages) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model),
                Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation),
                ItemTintSources.CODEC.listOf().optionalFieldOf("tints", List.of()).forGetter(Unbaked::tints),
                Pages.CODEC.fieldOf("pages").forGetter(Unbaked::pages)
        ).apply(i, Unbaked::new));

        @Override
        public @NonNull MapCodec<? extends ItemModel.Unbaked> type() {
            return CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.model);
        }

        @Override
        public @NonNull ItemModel bake(BakingContext context, @NonNull Matrix4fc transformation) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel resolved = baker.getModel(this.model);
            TextureSlots textureSlots = resolved.getTopTextureSlots();
            QuadCollection quads = resolved.bakeTopGeometry(textureSlots, baker, BlockModelRotation.IDENTITY);
            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, textureSlots);
            Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
            return new TechniqueTextureModel(new CuboidItemModelWrapper(this.tints, quads, properties, modelTransform), context, resolved, this.pages, this.tints, properties, modelTransform);
        }
    }

    public record Pages(Optional<String> left, Optional<String> right) {
        public static final Codec<Pages> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("left").forGetter(Pages::left),
                Codec.STRING.optionalFieldOf("right").forGetter(Pages::right)
        ).apply(i, Pages::new));
    }
}
