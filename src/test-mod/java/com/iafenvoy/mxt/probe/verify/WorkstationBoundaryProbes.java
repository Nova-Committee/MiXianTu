package com.iafenvoy.mxt.probe.verify;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.iafenvoy.jupiter.ConfigManager;
import com.iafenvoy.jupiter.ServerConfigManager;
import com.iafenvoy.jupiter.config.container.AbstractConfigContainer;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.registry.MxtCreativeTabs;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.alchemy.AlchemyHeatService;
import com.iafenvoy.mxt.runtime.talisman.BrushPigmentService;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkstationBoundaryProbes {
    private WorkstationBoundaryProbes() {
    }

    public static List<Probe> probes() {
        return List.of(
                Probe.of("verify/workstations/ownership", WorkstationBoundaryProbes::ownership),
                Probe.of("verify/workstations/config", WorkstationBoundaryProbes::config),
                Probe.of("verify/workstations/heat_sources", WorkstationBoundaryProbes::heatSources),
                Probe.of("verify/workstations/brush", WorkstationBoundaryProbes::brush));
    }

    private static void ownership(ProbeContext context) {
        Map<String, List<String>> blocks = Map.of(
                "mxt_alchemy", List.of("alchemy_furnace", "alchemy_furnace_casing", "alchemy_main_input", "alchemy_auxiliary_input", "alchemy_output"),
                "mxt_forging", List.of("forging_table"),
                "mxt_talisman", List.of("talisman_workstation"));
        Map<String, List<String>> tables = Map.of(
                "mxt_alchemy", List.of("alchemy_furnace", "alchemy_wall_material"),
                "mxt_forging", List.of("forging_blueprint", "forging_method", "tool_binding", "blueprint_binding"));
        blocks.forEach((mod, paths) -> paths.forEach(path -> {
            boolean loaded = ModList.get().isLoaded(mod);
            Identifier id = Identifier.fromNamespaceAndPath(mod, path);
            context.expect(path + " block", BuiltInRegistries.BLOCK.containsKey(id), loaded);
            context.expect(path + " item", BuiltInRegistries.ITEM.containsKey(id), loaded);
            Identifier legacy = Identifier.fromNamespaceAndPath("mxt", path);
            context.expect(path + " old block absent", BuiltInRegistries.BLOCK.containsKey(legacy), false);
            context.expect(path + " old item absent", BuiltInRegistries.ITEM.containsKey(legacy), false);
        }));
        tables.forEach((mod, paths) -> paths.forEach(path -> {
            ResourceKey<Registry<Object>> key = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath(mod, path));
            boolean loaded = ModList.get().isLoaded(mod);
            context.expect(path + " registry", context.registries().lookup(key).isPresent(), loaded);
            context.expect(path + " registry listing", MxtDatapackRegistries.registries().contains(key), loaded);
            ResourceKey<Registry<Object>> legacy = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("mxt", path));
            context.expect(path + " old registry absent", context.registries().lookup(legacy).isPresent(), false);
            context.expect(path + " old registry unlisted", MxtDatapackRegistries.registries().contains(legacy), false);
        }));
        context.expect("core heat registry", context.registries().lookup(MxtResourceKeys.HEAT_SOURCE).isPresent(), true);
        context.expect("core heat registry listed once", MxtDatapackRegistries.registries().stream().filter(MxtResourceKeys.HEAT_SOURCE::equals).count(), 1L);
        CreativeModeTab tab = MxtCreativeTabs.MAIN.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(context.level().enabledFeatures(), true, context.registries()));
        for (DeferredItem<? extends Item> item : List.of(MxtItems.TALISMAN_BRUSH, MxtItems.CINNABAR, MxtItems.BLANK_TALISMAN, MxtItems.TALISMAN, MxtItems.PILL, MxtItems.ALCHEMY_DREGS))
            context.expect("shared item " + item.getId(), tab.getDisplayItems().stream().anyMatch(stack -> stack.is(item.get())), true);
    }

    private static void config(ProbeContext context) {
        JsonObject core = JsonParser.parseString(MxtServerConfig.INSTANCE.serialize()).getAsJsonObject();
        context.expect("shared alchemy settings remain in core", core.getAsJsonObject("alchemy").keySet(),
                Set.of("herb_catch_up_ticks", "toxicity_decay_per_second"));
        context.expect("shared talisman settings remain in core", core.getAsJsonObject("talisman").keySet(),
                Set.of("use_cooldown", "brush_capacity", "pigment_per_item"));
        context.expect("drawing limits absent from core", core.has("drawing"), false);

        Map<String, String> files = Map.of(
                "mxt_alchemy", "alchemy-workstations.json",
                "mxt_forging", "forging-workstations.json",
                "mxt_talisman", "talisman-workstation.json");
        files.forEach((mod, file) -> {
            Identifier id = Identifier.fromNamespaceAndPath(mod, "server");
            boolean loaded = ModList.get().isLoaded(mod);
            AbstractConfigContainer config = ServerConfigManager.getConfig(id);
            context.expect(mod + " server config follows addon installation", config != null, loaded);
            context.expect(mod + " file handler follows addon installation", ConfigManager.getInstance().getConfigs()
                    .stream().filter(container -> container.getConfigId().equals(id)).count(), loaded ? 1L : 0L);
            if (config == null) return;
            context.expect(mod + " independent file", config.getPath(), file);
            boolean operator = ServerConfigManager.PermissionChecker.IS_OPERATOR.check(context.server(), context.player());
            context.expect(mod + " read permission", ServerConfigManager.checkPermission(id, context.server(), context.player(), false), operator);
            context.expect(mod + " write permission", ServerConfigManager.checkPermission(id, context.server(), context.player(), true), operator);

            String saved = config.serialize();
            JsonObject values = JsonParser.parseString(saved).getAsJsonObject();
            try {
                if (mod.equals("mxt_talisman")) {
                    context.expect("drawing category isolated", values.keySet(), Set.of("drawing"));
                    JsonObject drawing = values.getAsJsonObject("drawing");
                    context.expect("eight drawing settings", drawing.keySet(), Set.of("min_stroke_interval", "max_rejected_strokes",
                            "max_stroke_points", "max_session_points", "max_session_strokes", "pigment_rate", "pigment_floor", "pigment_cap"));
                    drawing.addProperty("min_stroke_interval", 50);
                    config.deserialize(values.toString());
                    JsonObject changed = JsonParser.parseString(config.serialize()).getAsJsonObject();
                    context.expect("drawing JSON updates apply", changed.getAsJsonObject("drawing").get("min_stroke_interval").getAsInt(), 50);
                } else context.expect(mod + " has no placeholder gameplay options", values.size(), 0);
                config.deserializeNbt(config.serializeNbt());
                context.expect(mod + " NBT round trip", JsonParser.parseString(config.serialize()), values);
                context.expect(mod + " does not change shared settings", JsonParser.parseString(MxtServerConfig.INSTANCE.serialize()), core);
            } finally {
                config.deserialize(saved);
            }
            context.expect(mod + " original values restored", JsonParser.parseString(config.serialize()), JsonParser.parseString(saved));
        });
    }

    private static void heatSources(ProbeContext context) {
        ServerLevel level = context.level();
        BlockPos pos = context.at(8, 1, 8);
        BlockState previous = level.getBlockState(pos);
        try {
            HeatSource tagged = RegistryCaches.HEAT_SOURCE.get(Blocks.REDSTONE_BLOCK);
            HeatSource override = RegistryCaches.HEAT_SOURCE.get(Blocks.LAPIS_BLOCK);
            assert tagged != null;
            assert override != null;
            context.expect("tag claim cached", tagged.maxTemperature(), 150.0D);
            context.expect("priority overrides tag", override.maxTemperature(), 80.0D);
            level.setBlockAndUpdate(pos, Blocks.REDSTONE_BLOCK.defaultBlockState());
            context.expect("tag temperature", AlchemyHeatService.maxTemperature(level, pos), 150.0D);
            context.expect("tag heating", AlchemyHeatService.heatingPerTick(level, pos), 40.0D);
            level.setBlockAndUpdate(pos, Blocks.LAPIS_BLOCK.defaultBlockState());
            context.expect("override temperature", AlchemyHeatService.maxTemperature(level, pos), 80.0D);
            context.expect("override heating", AlchemyHeatService.heatingPerTick(level, pos), 10.0D);
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            context.expect("unclaimed block", AlchemyHeatService.isHeatSource(level.getBlockState(pos)), false);
            context.expect("unclaimed temperature", AlchemyHeatService.maxTemperature(level, pos), 0.0D);
            context.expect("unclaimed heating", AlchemyHeatService.heatingPerTick(level, pos), 0.0D);
            BlockPos unloaded = new BlockPos(28_000_000, 64, 28_000_000);
            context.expect("remote chunk starts unloaded", level.isLoaded(unloaded), false);
            context.expect("unloaded temperature", AlchemyHeatService.maxTemperature(level, unloaded), 0.0D);
            context.expect("unloaded heating", AlchemyHeatService.heatingPerTick(level, unloaded), 0.0D);
            context.expect("heat lookup never loads chunk", level.isLoaded(unloaded), false);

            RegistryOps<JsonElement> ops = context.registries().createSerializationContext(JsonOps.INSTANCE);
            JsonElement json = JsonParser.parseString("""
                    {"blocks":["minecraft:redstone_block"],"max_temperature":150,"heating_per_tick":40}
                    """);
            HeatSource first = HeatSource.DIRECT_CODEC.parse(ops, json).getOrThrow();
            HeatSource second = HeatSource.DIRECT_CODEC.parse(ops, json).getOrThrow();
            context.expect("decoding creates fresh definitions", first == second, false);
            context.expect("priority defaults to zero", first.priority(), 0);
            for (String invalid : List.of(
                    "{\"blocks\":[],\"max_temperature\":150,\"heating_per_tick\":40}",
                    "{\"blocks\":[\"minecraft:redstone_block\"],\"max_temperature\":0,\"heating_per_tick\":40}",
                    "{\"blocks\":[\"minecraft:redstone_block\"],\"max_temperature\":150,\"heating_per_tick\":-1}"))
                context.expect("invalid definition rejected: " + invalid, HeatSource.DIRECT_CODEC.parse(ops, JsonParser.parseString(invalid)).error().isPresent(), true);
            context.note("OK shared heat lookup, priority, codecs and unloaded chunks");
        } finally {
            level.setBlockAndUpdate(pos, previous);
        }
    }

    private static void brush(ProbeContext context) {
        ItemStack brush = MxtItems.TALISMAN_BRUSH.toStack();
        context.expect("pigment tag remains in core", BrushPigmentService.isPigment(MxtItems.CINNABAR.toStack()), true);
        int capacity = BrushPigmentService.capacity();
        int filled = capacity / BrushPigmentService.perPortion() * BrushPigmentService.perPortion();
        context.expect("fill capacity", BrushPigmentService.dip(brush, Integer.MAX_VALUE), filled);
        context.expect("overfill refused", BrushPigmentService.dip(brush, 1), 0);
        context.expect("negative charge refused", BrushPigmentService.consume(brush, -1), false);
        context.expect("insufficient charge refused", BrushPigmentService.consume(brush, filled + 1), false);
        context.expect("refusals preserve pigment", BrushPigmentService.pigment(brush), filled);
        context.expect("exact charge", BrushPigmentService.consume(brush, filled), true);
        context.expect("empty pigment", BrushPigmentService.pigment(brush), 0);
    }
}
