package com.iafenvoy.mxt.probe.verify;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.core.file.FileWatcher;
import com.iafenvoy.mxt.compat.ponder.PonderConfigCompatibility;
import com.iafenvoy.mxt.probe.Probe;
import com.iafenvoy.mxt.probe.ProbeContext;
import com.iafenvoy.mxt.testmod.MxtTestMod;
import net.createmod.catnip.api.config.definition.CatnipConfig;
import net.createmod.catnip.api.config.definition.ConfigValue;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class PonderConfigVerifyProbes {
    private PonderConfigVerifyProbes() {
    }

    public static List<Probe> probes() {
        return List.of(Probe.of("verify/formation_preview/config", PonderConfigVerifyProbes::config));
    }

    private static void config(ProbeContext context) {
        try (Fixture fixture = new Fixture()) {
            fixture.load();
            leg(context, "missing file gets defaults", !fixture.root.editingMode.get()
                    && !fixture.root.comfyReading.get() && fixture.root.options.count.get() == 3);
            leg(context, "missing file created and loaded", Files.isRegularFile(fixture.file()) && fixture.root.loads == 1);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }

        try (Fixture fixture = new Fixture()) {
            Files.writeString(fixture.file(), "editingMode = true\ncomfyReading = true\n[options]\ncount = 9\n");
            leg(context, "late config starts unloaded", fixture.config.getLoadedConfig() == null);
            fixture.load();
            leg(context, "file values override defaults", fixture.root.editingMode.get() && fixture.root.comfyReading.get());
            leg(context, "nested value initialized from file", fixture.root.options.count.get() == 9);
            leg(context, "normal loading event and file path retained", fixture.root.loads == 1
                    && fixture.config.getFullPath().equals(fixture.file()));

            fixture.root.editingMode.set(false);
            try (BufferedReader reader = Files.newBufferedReader(fixture.file())) {
                leg(context, "runtime changes saved to file", Boolean.FALSE.equals(new TomlParser().parse(reader).get("editingMode")));
            }
            IConfigSpec.ILoadedConfig loaded = fixture.config.getLoadedConfig();
            fixture.load();
            leg(context, "repeat load preserves config and changes", fixture.config.getLoadedConfig() == loaded
                    && fixture.root.loads == 1 && !fixture.root.editingMode.get());

            int reloads = fixture.root.reloads;
            ConfigTracker.acceptSyncedConfig(fixture.config,
                    "editingMode = true\ncomfyReading = false\n[options]\ncount = 5\n".getBytes(StandardCharsets.UTF_8));
            leg(context, "reload event updates live values", fixture.root.reloads > reloads && fixture.root.editingMode.get()
                    && !fixture.root.comfyReading.get() && fixture.root.options.count.get() == 5);

            fixture.tracker.unloadConfigs(ModConfig.Type.CLIENT);
            fixture.load();
            leg(context, "reopen reads saved file without resetting", fixture.root.loads == 2 && !fixture.root.editingMode.get()
                    && fixture.root.comfyReading.get() && fixture.root.options.count.get() == 9);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private static void leg(ProbeContext context, String name, boolean success) {
        context.check(name, success);
        context.note("ponder config " + name + ": " + (success ? "OK" : "MISMATCH"));
    }

    private static final class Fixture implements AutoCloseable {
        private final Root root = new Root();
        private final ConfigTracker tracker = new ConfigTracker();
        private final Path directory = Files.createTempDirectory("mxt-ponder-config-");
        private final IEventBus bus;
        private final Consumer<ModConfigEvent> listener;
        private final ModConfig config;

        private Fixture() throws IOException {
            ModContainer container = ModList.get().getModContainerById(MxtTestMod.MOD_ID).orElseThrow();
            this.bus = container.getEventBus();
            ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
            ModConfigSpec.BooleanValue editing = builder.define("editingMode", false);
            ModConfigSpec.BooleanValue comfy = builder.define("comfyReading", false);
            ModConfigSpec.IntValue count = builder.defineInRange(List.of("options", "count"), 3, 1, 10);
            ModConfigSpec spec = builder.build();
            bind(this.root.editingMode, editing);
            bind(this.root.comfyReading, comfy);
            bind(this.root.options.count, count);
            // Exercise the upstream first-load setter and callbacks against a real, isolated FML config.
            this.listener = event -> {
                if (event.getConfig().getSpec() != spec || event instanceof ModConfigEvent.Unloading) return;
                this.root.editingMode.set(editing.get());
                this.root.comfyReading.set(comfy.get());
                this.root.options.count.set(count.get());
                if (event instanceof ModConfigEvent.Loading) this.root.onLoad();
                else this.root.onReload();
            };
            this.bus.addListener(ModConfigEvent.class, this.listener);
            this.config = this.tracker.registerConfig(ModConfig.Type.CLIENT, spec, container, "probe-client.toml");
        }

        private static <T> void bind(ConfigValue<T> value, ModConfigSpec.ConfigValue<T> defined) {
            value.registerChangeCallback((_, next) -> {
                defined.set(next);
                defined.save();
            });
        }

        private Path file() {
            return this.directory.resolve(this.config.getFileName());
        }

        private void load() {
            PonderConfigCompatibility.load(this.root, this.config, this.directory);
        }

        @Override
        public void close() throws IOException {
            try {
                this.tracker.unloadConfigs(ModConfig.Type.CLIENT);
            } finally {
                this.bus.unregister(this.listener);
                FileWatcher.defaultInstance().removeWatchFuture(this.file()).orTimeout(5, TimeUnit.SECONDS).join();
                Files.deleteIfExists(this.file());
                for (int i = 1; i <= 5; i++) Files.deleteIfExists(this.directory.resolve("probe-client-" + i + ".toml.bak"));
                Files.delete(this.directory);
            }
        }
    }

    private static final class Root extends CatnipConfig.Root {
        private final ConfigValue<Boolean> editingMode = this.b(false, "editingMode");
        private final ConfigValue<Boolean> comfyReading = this.b(false, "comfyReading");
        private final Options options = this.nested(Options::new, "options", "Nested test values");
        private int loads;
        private int reloads;

        @Override
        public void onLoad() {
            super.onLoad();
            this.loads++;
        }

        @Override
        public void onReload() {
            super.onReload();
            this.reloads++;
        }
    }

    private static final class Options extends CatnipConfig.Nested {
        private final ConfigValue<Integer> count = this.i(3, 1, 10, "count");

        private Options(CatnipConfig parent, String name, String comment) {
            super(parent, name, comment);
        }
    }
}
