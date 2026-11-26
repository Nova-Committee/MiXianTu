package com.iafenvoy.mxt.compat.ponder;

import net.createmod.catnip.api.config.CatnipConfigRegistry;
import net.createmod.catnip.api.config.ConfigId;
import net.createmod.catnip.api.config.ConfigSide;
import net.createmod.catnip.api.config.definition.CatnipConfig;
import net.createmod.catnip.api.config.definition.ConfigValue;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

// Ponder/Catnip 1.0.52 register client configs too late and cannot initialize their first values.
// Recheck both upstream issues on upgrades and remove this bridge once they are fixed.
@EventBusSubscriber(Dist.CLIENT)
public final class PonderConfigCompatibility {
    private PonderConfigCompatibility() {
    }

    @SubscribeEvent
    public static void loadComplete(FMLLoadCompleteEvent event) {
        event.enqueueWork(() -> {
            for (String modId : List.of("catnip", "ponder")) {
                Optional<CatnipConfig.Root> definition = CatnipConfigRegistry.INSTANCE.get(new ConfigId(modId, ConfigSide.CLIENT));
                if (definition.isEmpty()) continue;
                for (ModConfig config : ModConfigs.getModConfigs(modId))
                    if (config.getType() == ModConfig.Type.CLIENT)
                        load(definition.get(), config, FMLPaths.CONFIGDIR.get());
            }
        });
    }

    public static void load(CatnipConfig.Root definition, ModConfig config, Path directory) {
        synchronized (config) {
            if (config.getLoadedConfig() != null) return;
            initializeDefaults(definition);
            // Ponder registers after FML's config pass; load only its missing file with normal events and watching.
            try {
                Access.OPEN_CONFIG.invokeExact(config, directory, (Path) null);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Cannot load late config " + config.getFileName(), failure);
            }
        }
    }

    private static void initializeDefaults(CatnipConfig config) {
        // Catnip's first load calls set(), which rejects null; seed only values that have never been initialized.
        for (ConfigValue<?> value : config.values()) Access.VALUE.compareAndSet(value, null, value.defaultValue);
        for (CatnipConfig.Nested nested : config.nested().values()) initializeDefaults(nested);
    }

    @SuppressWarnings("UnstableApiUsage")
    private static final class Access {
        private static final VarHandle VALUE;
        private static final MethodHandle OPEN_CONFIG;

        static {
            try {
                VALUE = MethodHandles.privateLookupIn(ConfigValue.class, MethodHandles.lookup()).findVarHandle(ConfigValue.class, "value", Object.class);
                OPEN_CONFIG = MethodHandles.privateLookupIn(ConfigTracker.class, MethodHandles.lookup()).findStatic(ConfigTracker.class, "openConfig", MethodType.methodType(void.class, ModConfig.class, Path.class, Path.class));
            } catch (ReflectiveOperationException failure) {
                throw new ExceptionInInitializerError(failure);
            }
        }
    }
}
