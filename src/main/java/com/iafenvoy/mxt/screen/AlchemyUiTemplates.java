package com.iafenvoy.mxt.screen;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.resource.FileResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.IResourceProvider;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceInstance;
import com.lowdragmc.lowdraglib2.editor.resource.ResourceProviderType;
import com.lowdragmc.lowdraglib2.editor.resource.UIResource;
import com.lowdragmc.lowdraglib2.gui.ui.UITemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.io.File;

/**
 * Seeds missing bundled templates and registers that absolute directory with the editor.
 * UIResource.buildBuiltin does not post LoadBuiltin, so registration uses client setup and resource reload.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AlchemyUiTemplates {
    public static final String MONITOR = "alchemy_monitor";
    public static final String MAIN_INPUT = "alchemy_main_input";
    public static final String AUXILIARY_INPUT = "alchemy_auxiliary_input";
    public static final String OUTPUT = "alchemy_output";
    private static final String[] NAMES = {MONITOR, MAIN_INPUT, AUXILIARY_INPUT, OUTPUT};
    private static final ResourceManagerReloadListener RELOAD = AlchemyUiTemplates::onReload;

    private AlchemyUiTemplates() {
    }

    public static String name(AlchemyFurnaceMenu.View view) {
        return switch (view) {
            case MONITOR -> MONITOR;
            case MAIN -> MAIN_INPUT;
            case AUXILIARY -> AUXILIARY_INPUT;
            case OUTPUT -> OUTPUT;
        };
    }

    public static Path directory() {
        return Platform.getGamePath().resolve("ldlib2").resolve("assets").resolve(MiXianTu.MOD_ID).resolve("ui")
                .toAbsolutePath().normalize();
    }

    public static void seedMissing() {
        Path directory = directory();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            MiXianTu.LOGGER.error("Could not create alchemy UI directory {}", directory, exception);
            return;
        }
        for (String name : NAMES) {
            Path target = directory.resolve(name + ".ui.nbt");
            if (Files.exists(target)) continue;
            String resource = "/assets/" + MiXianTu.MOD_ID + "/ui/" + name + ".ui.nbt";
            try (InputStream input = AlchemyUiTemplates.class.getResourceAsStream(resource)) {
                if (input == null) {
                    MiXianTu.LOGGER.error("Bundled alchemy template {} is missing", resource);
                    continue;
                }
                Files.copy(input, target);
            } catch (FileAlreadyExistsException ignored) {
                // Another writer created the editable file after the existence check.
            } catch (IOException exception) {
                MiXianTu.LOGGER.error("Could not seed alchemy template {}", name, exception);
            }
        }
    }

    /**
     * Attaches one writable provider for {@link #directory()} if that directory is not already listed.
     * Existing files are never replaced.
     */
    public static void ensureEditorProvider() {
        synchronized (AlchemyUiTemplates.class) {
            seedMissing();
            try {
                ResourceInstance<UITemplate> instance = UIResource.INSTANCE.getResourceInstance();
                File directory = directory().toFile();
                if (attached(instance, directory)) return;
                FileResourceProvider<UITemplate> provider = new FileResourceProvider<>(instance, directory);
                provider.setName(MiXianTu.MOD_ID);
                instance.addBuiltinProvider(provider);
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.error("Could not register alchemy UI editor provider", exception);
            }
        }
    }

    @Nullable
    public static UITemplate load(AlchemyFurnaceMenu.View view) {
        ensureEditorProvider();
        Path file = directory().resolve(name(view) + ".ui.nbt");
        try {
            CompoundTag root = NbtIo.read(file);
            if (root == null) return null;
            Tag data = root.get("data");
            if (data == null) return null;
            return UITemplate.CODEC.parse(Platform.getFrozenRegistry().createSerializationContext(NbtOps.INSTANCE), data)
                    .resultOrPartial(error -> MiXianTu.LOGGER.error("Could not read alchemy template {}: {}", file, error))
                    .orElse(null);
        } catch (IOException exception) {
            MiXianTu.LOGGER.error("Could not read alchemy template {}", file, exception);
            return null;
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AlchemyUiTemplates::ensureEditorProvider);
    }

    @SubscribeEvent
    public static void onReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy_ui"), RELOAD);
    }

    private static void onReload(@NonNull ResourceManager manager) {
        ensureEditorProvider();
    }

    private static boolean attached(ResourceInstance<UITemplate> instance, File directory) {
        return contains(instance.getBuiltinProviders(), directory) || contains(instance.getCustomProviders(), directory);
    }

    private static boolean contains(Map<ResourceProviderType, List<IResourceProvider<UITemplate>>> providers, File directory) {
        for (List<IResourceProvider<UITemplate>> group : providers.values()) {
            for (IResourceProvider<UITemplate> provider : group) {
                if (provider instanceof FileResourceProvider<?> file && sameFile(file.resourceLocation, directory)) return true;
            }
        }
        return false;
    }

    private static boolean sameFile(File left, File right) {
        if (left.equals(right)) return true;
        try {
            return left.getCanonicalFile().equals(right.getCanonicalFile());
        } catch (IOException exception) {
            return false;
        }
    }
}
