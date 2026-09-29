package com.iafenvoy.mxt.screen;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Names and seeds the editable alchemy pages. ApricityUI resolves pages from {@code <gameDir>/apricity}
 * (a mod jar's own asset namespace is not scanned), so the bundled copies are written out once.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AlchemyUiPages {
    private static final String FOLDER = "mxt/alchemy";
    private static final String[] FILES = {
            "alchemy.css", "monitor.html", "main_input.html", "auxiliary_input.html", "output.html"
    };

    private AlchemyUiPages() {
    }

    public static String path(AlchemyFurnaceMenu.View view) {
        return FOLDER + "/" + name(view) + ".html";
    }

    public static String name(AlchemyFurnaceMenu.View view) {
        return switch (view) {
            case MONITOR -> "monitor";
            case MAIN -> "main_input";
            case AUXILIARY -> "auxiliary_input";
            case OUTPUT -> "output";
        };
    }

    public static Path directory() {
        return FMLPaths.GAMEDIR.get().resolve("apricity").resolve(FOLDER).toAbsolutePath().normalize();
    }

    /** Copies every missing bundled page; an existing file is never replaced. */
    public static void seedMissing() {
        Path directory = directory();
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            MiXianTu.LOGGER.error("Could not create alchemy page directory {}", directory, exception);
            return;
        }
        for (String file : FILES) {
            Path destination = directory.resolve(file);
            if (Files.exists(destination)) continue;
            String resource = "/assets/" + MiXianTu.MOD_ID + "/apricity/" + FOLDER + "/" + file;
            try (InputStream input = AlchemyUiPages.class.getResourceAsStream(resource)) {
                if (input == null) {
                    MiXianTu.LOGGER.error("Bundled alchemy page {} is missing", resource);
                    continue;
                }
                Files.copy(input, destination);
            } catch (FileAlreadyExistsException ignored) {
                // Another writer created the editable file after the existence check.
            } catch (IOException exception) {
                MiXianTu.LOGGER.error("Could not seed alchemy page {}", file, exception);
            }
        }
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(AlchemyUiPages::seedMissing);
    }
}
