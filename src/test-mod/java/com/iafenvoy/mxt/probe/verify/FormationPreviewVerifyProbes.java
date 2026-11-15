package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.formation.Formation.RequiredBlock;
import com.iafenvoy.mxt.network.payload.FormationStructureS2CPayload;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.runtime.formation.FormationPreview;
import io.netty.buffer.Unpooled;
import net.createmod.catnip.api.level.wrapper.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.stream.IntStream;

public final class FormationPreviewVerifyProbes {
    private FormationPreviewVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/formation_preview/shape", FormationPreviewVerifyProbes::shape),
                Probe.of("verify/formation_preview/limits", FormationPreviewVerifyProbes::limits),
                Probe.of("verify/formation_preview/payload", FormationPreviewVerifyProbes::payload),
                Probe.of("verify/formation_preview/template", FormationPreviewVerifyProbes::template));
    }

    private static void template(ProbeContext context) {
        try (InputStream input = Objects.requireNonNull(MiXianTu.class.getResourceAsStream("/assets/mxt/ponder/formation_preview.nbt"))) {
            CompoundTag tag = NbtIo.readCompressed(input, NbtAccounter.create(1 << 20));
            StructureTemplate template = new StructureTemplate();
            template.load(BuiltInRegistries.BLOCK, tag);
            leg(context, "native Ponder template loadable", template.getSize().equals(new Vec3i(1, 1, 1)));
            leg(context, "native template has no fallback blocks", tag.getList("blocks").orElseThrow().isEmpty());
            leg(context, "native template has no entities", tag.getList("entities").orElseThrow().isEmpty());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        SchematicLevel world = new SchematicLevel(context.level());
        world.renderMode = true;
        FormationPreview preview = FormationPreview.prepare(List.of(
                new RequiredBlock(new BlockPos(-1, -5, -1), Blocks.STONE.defaultBlockState()),
                new RequiredBlock(new BlockPos(1, -2, 1), Blocks.OAK_STAIRS.defaultBlockState()))).orElseThrow();
        List<RequiredBlock> display = preview.displayBlocks();
        for (RequiredBlock block : display) world.setBlock(block.offset(), block.state(), Block.UPDATE_CLIENTS);
        leg(context, "dynamic scene has zero anchor", world.anchor.equals(BlockPos.ZERO));
        leg(context, "dynamic world expands beyond empty template", world.getBounds().getXSpan() == 5
                && world.getBounds().getYSpan() == 5 && world.getBounds().getZSpan() == 5);
        leg(context, "dynamic bounds contain every block", preview.blocks().stream().allMatch(block -> world.getBounds().isInside(block.offset())
                && world.getBlockState(block.offset()) == block.state()));
        leg(context, "floor fills the square footprint", display.size() == 27
                && display.stream().filter(block -> block.offset().getY() == 0).count() == 25);
        leg(context, "floor extends one block beyond every side", preview.basePlateSize() == 5
                && world.getBounds().minX() == -1 && world.getBounds().maxX() == 3
                && world.getBounds().minZ() == -1 && world.getBounds().maxZ() == 3
                && preview.blocks().stream().allMatch(block -> block.offset().getX() >= 0 && block.offset().getX() <= 2
                        && block.offset().getZ() >= 0 && block.offset().getZ() <= 2));
        leg(context, "floor alternates snow and white concrete", IntStream.range(-1, 4).allMatch(x ->
                IntStream.range(-1, 4).allMatch(z -> world.getBlockState(new BlockPos(x, 0, z))
                        .is((x + z) % 2 == 0 ? Blocks.SNOW_BLOCK : Blocks.WHITE_CONCRETE))));
        leg(context, "display floor stays out of definition and payload", preview.blocks().size() == 2
                && preview.blocks().stream().allMatch(block -> block.offset().getY() > 0)
                && FormationPreview.prepare(preview.blocks()).orElseThrow().equals(preview));
    }

    private static void shape(ProbeContext context) {
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST);
        List<RequiredBlock> input = List.of(new RequiredBlock(new BlockPos(-9, -5, 3), Blocks.DIRT.defaultBlockState()),
                new RequiredBlock(new BlockPos(-7, -2, 3), stairs),
                new RequiredBlock(new BlockPos(-9, -5, 3), Blocks.STONE.defaultBlockState()),
                new RequiredBlock(new BlockPos(1000, 1000, 1000), Blocks.AIR.defaultBlockState()));
        FormationPreview preview = FormationPreview.prepare(input).orElseThrow();
        leg(context, "visible blocks only, last state wins", preview.blocks().size() == 2
                && preview.blocks().getFirst().state().is(Blocks.STONE));
        leg(context, "negative origin and rectangular centring", preview.width() == 3 && preview.height() == 4
                && preview.depth() == 1 && preview.basePlateSize() == 5
                && preview.blocks().getFirst().offset().equals(new BlockPos(0, 1, 1)));
        leg(context, "sparse layers and state properties preserved", preview.blocks().getLast().offset().equals(new BlockPos(2, 4, 1))
                && preview.blocks().getLast().state() == stairs);
        leg(context, "input untouched", input.getFirst().offset().equals(new BlockPos(-9, -5, 3)));
        leg(context, "normalization is idempotent", FormationPreview.prepare(preview.blocks()).orElseThrow().equals(preview));
        List<RequiredBlock> removed = List.of(input.getFirst(), new RequiredBlock(input.getFirst().offset(), Blocks.AIR.defaultBlockState()));
        leg(context, "air can replace a prior state", FormationPreview.prepare(removed).isEmpty());
    }

    private static void limits(ProbeContext context) {
        leg(context, "empty shape", FormationPreview.prepare(List.of()).isEmpty());
        leg(context, "air only", FormationPreview.prepare(List.of(block(0, Blocks.AIR.defaultBlockState()))).isEmpty());
        leg(context, "64 block span accepted", FormationPreview.prepare(List.of(block(0), block(63))).isPresent());
        leg(context, "65 block span rejected", FormationPreview.prepare(List.of(block(0), block(64))).isEmpty());
        leg(context, "vertical span bounded", FormationPreview.prepare(List.of(block(0),
                new RequiredBlock(new BlockPos(0, 64, 0), Blocks.STONE.defaultBlockState()))).isEmpty());
        leg(context, "depth bounded", FormationPreview.prepare(List.of(block(0),
                new RequiredBlock(new BlockPos(0, 0, 64), Blocks.STONE.defaultBlockState()))).isEmpty());
        leg(context, "coordinate overflow rejected", FormationPreview.prepare(List.of(block(Integer.MIN_VALUE), block(Integer.MAX_VALUE))).isEmpty());
        leg(context, "large offset remains valid", FormationPreview.prepare(List.of(block(Integer.MAX_VALUE - 1), block(Integer.MAX_VALUE)))
                .orElseThrow().width() == 2);
        leg(context, "count bound checked before deduplication", FormationPreview.prepare(Collections.nCopies(FormationPreview.MAX_BLOCKS + 1, block(0))).isEmpty());
    }

    private static void payload(ProbeContext context) {
        FormationPreview preview = FormationPreview.prepare(List.of(block(Integer.MAX_VALUE - 2), block(Integer.MAX_VALUE))).orElseThrow();
        Component title = Component.translatable("mxt.formation.example.shape").append(Component.literal(" / shape"));
        FormationStructureS2CPayload original = new FormationStructureS2CPayload(title, preview.blocks());
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries(), ConnectionType.NEOFORGE);
        try {
            FormationStructureS2CPayload.STREAM_CODEC.encode(buffer, original);
            FormationStructureS2CPayload decoded = FormationStructureS2CPayload.STREAM_CODEC.decode(buffer);
            leg(context, "title and states round trip", decoded.equals(original));
            leg(context, "large source offsets normalized before packed BlockPos", decoded.structure().getLast().offset().equals(new BlockPos(2, 1, 1)));
            leg(context, "client accepts server layout", FormationPreview.prepare(decoded.structure()).orElseThrow().equals(preview));
            leg(context, "payload fully consumed", buffer.readableBytes() == 0);
        } finally {
            buffer.release();
        }
    }

    private static RequiredBlock block(int x) {
        return block(x, Blocks.STONE.defaultBlockState());
    }

    private static RequiredBlock block(int x, BlockState state) {
        return new RequiredBlock(new BlockPos(x, 0, 0), state);
    }

    private static void leg(ProbeContext context, String name, boolean success) {
        context.check(name, success);
        context.note("formation preview " + name + ": " + (success ? "OK" : "MISMATCH"));
    }
}
