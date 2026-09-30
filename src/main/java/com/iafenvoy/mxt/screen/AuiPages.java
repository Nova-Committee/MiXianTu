package com.iafenvoy.mxt.screen;

import com.iafenvoy.mxt.MiXianTu;
import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The bundled ApricityUI pages and the one place they are seeded. ApricityUI resolves pages from
 * {@code <gameDir>/apricity} (a mod jar's own asset namespace is not scanned), so every page is written out
 * once and never replaced - from then on the copy on disk is the player's.
 *
 * <p>A changed page therefore only reaches an installation whose seeded copy was deleted, and this class
 * seeds every folder: a page added here needs nothing else to appear.</p>
 *
 * <p>Seeding and preparing both run once after every client resource reload, on the first tick it finishes
 * (see {@link #reloadPending}): that is the hook that runs after the bundled pages are readable, and it is
 * what keeps a screen's first open down to building one document.</p>
 */
@EventBusSubscriber(Dist.CLIENT)
public final class AuiPages {
    /**
     * The four furnace views, sharing one stylesheet.
     */
    public static final String ALCHEMY = "mxt/alchemy";
    public static final List<String> ALCHEMY_FILES =
            List.of("alchemy.css", "monitor.html", "main_input.html", "auxiliary_input.html", "output.html");
    /**
     * The in-game wheel and the editor {@code /wheel} opens, each with its own stylesheet. The HUD grid draws
     * itself and has no page.
     */
    public static final String WHEEL = "mxt/wheel";
    public static final List<String> WHEEL_FILES =
            List.of("wheel.css", "wheel.html", "wheel_config.css", "wheel_config.html");
    /**
     * The stylesheet the vanilla-skinned pages share; it sits beside the page folders, so a page links
     * {@code ../base.css}.
     */
    public static final String ROOT = "mxt";
    public static final List<String> ROOT_FILES = List.of("base.css");
    /**
     * The forge table.
     */
    public static final String FORGING = "mxt/forging";
    public static final List<String> FORGING_FILES = List.of("forging.css", "forging.html");
    /**
     * The spirit crafting table: the vanilla crafting grid plus the aura progress panel.
     */
    public static final String SPIRIT_CRAFTING = "mxt/spirit_crafting";
    public static final List<String> SPIRIT_CRAFTING_FILES =
            List.of("spirit_crafting.css", "spirit_crafting.html");
    /**
     * The economy screens: the two station views of the four station menus (sharing one stylesheet), the
     * exchange station, the cheque table and the two-player trade.
     */
    public static final String ECONOMY = "mxt/economy";
    public static final List<String> ECONOMY_FILES = List.of(
            "station.css", "station_customer.html", "station_owner.html",
            "exchange.css", "exchange.html",
            "cheque.css", "cheque.html",
            "trade.css", "trade.html");
    /**
     * The player information panel.
     */
    public static final String INFORMATION = "mxt/information";
    public static final List<String> INFORMATION_FILES = List.of("information.css", "information.html");
    /**
     * The formation structure preview: a page over the whole window, with the scene drawn by Java.
     */
    public static final String MULTIBLOCK = "mxt/multiblock";
    public static final List<String> MULTIBLOCK_FILES = List.of("structure.css", "structure.html");

    private static final String HTML_SUFFIX = ".html";

    /**
     * One bundled folder and the files seeded into it.
     */
    private record Folder(String path, List<String> files) {
    }

    private static final List<Folder> FOLDERS = List.of(
            new Folder(ROOT, ROOT_FILES),
            new Folder(ALCHEMY, ALCHEMY_FILES),
            new Folder(WHEEL, WHEEL_FILES),
            new Folder(FORGING, FORGING_FILES),
            new Folder(SPIRIT_CRAFTING, SPIRIT_CRAFTING_FILES),
            new Folder(ECONOMY, ECONOMY_FILES),
            new Folder(INFORMATION, INFORMATION_FILES),
            new Folder(MULTIBLOCK, MULTIBLOCK_FILES));

    /**
     * Every bundled page, derived from the folder table so a page added to a file list is prepared as well.
     */
    private static final List<String> PAGES = pages();

    private AuiPages() {
    }

    public static String alchemyPage(String name) {
        return page(ALCHEMY, name);
    }

    public static String wheelPage() {
        return page(WHEEL, "wheel");
    }

    public static String wheelConfigPage() {
        return page(WHEEL, "wheel_config");
    }

    public static String economyPage(String name) {
        return page(ECONOMY, name);
    }

    public static String multiblockPage() {
        return page(MULTIBLOCK, "structure");
    }

    public static String page(String folder, String name) {
        return folder + "/" + name + HTML_SUFFIX;
    }

    public static Path directory(String folder) {
        return FMLPaths.GAMEDIR.get().resolve("apricity").resolve(folder).toAbsolutePath().normalize();
    }

    /**
     * Copies every missing bundled file of one folder; an existing file is never replaced.
     */
    public static void seed(String folder, List<String> files) {
        Path directory = directory(folder);
        try {
            Files.createDirectories(directory);
        } catch (IOException exception) {
            MiXianTu.LOGGER.error("Could not create page directory {}", directory, exception);
            return;
        }
        for (String file : files) {
            Path destination = directory.resolve(file);
            if (Files.exists(destination)) continue;
            String resource = "/assets/" + MiXianTu.MOD_ID + "/apricity/" + folder + "/" + file;
            try (InputStream input = AuiPages.class.getResourceAsStream(resource)) {
                if (input == null) {
                    MiXianTu.LOGGER.error("Bundled page {} is missing", resource);
                    continue;
                }
                Files.copy(input, destination);
            } catch (FileAlreadyExistsException ignored) {
                // Another writer created the editable file after the existence check.
            } catch (IOException exception) {
                MiXianTu.LOGGER.error("Could not seed page {}", file, exception);
            }
        }
    }

    /**
     * Seeds every bundled folder; hosts also call this before creating their document, so the page is on disk
     * by the time ApricityUI resolves it even if the reload hook never ran.
     */
    public static void seedAll() {
        for (Folder folder : FOLDERS) seed(folder.path(), folder.files());
    }

    private static List<String> pages() {
        List<String> pages = new ArrayList<>();
        for (Folder folder : FOLDERS)
            for (String file : folder.files())
                if (file.endsWith(HTML_SUFFIX))
                    pages.add(page(folder.path(), file.substring(0, file.length() - HTML_SUFFIX.length())));
        return List.copyOf(pages);
    }

    /**
     * Set when a resource reload finishes, and by far the most common reason a page is not prepared; the work
     * itself runs on the next client tick rather than inside the listener. ApricityUI clears its compiled
     * stylesheets and prepares its own templates from its own reload listener, so preparing from ours could be
     * undone by a listener that happens to run after it - a tick later cannot be. It starts true so the first
     * tick after client startup prepares the pages even if the reload event never reaches this class.
     */
    private static volatile boolean reloadPending = true;

    /**
     * Re-arms the warm-up on every client resource reload (startup, F3+T, pack toggles).
     */
    private static final ResourceManagerReloadListener RELOAD = manager -> reloadPending = true;

    @SubscribeEvent
    public static void onRegisterReload(AddClientReloadListenersEvent event) {
        event.addListener(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aui_pages"), RELOAD);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!reloadPending) return;
        reloadPending = false;
        seedAll();
        try {
            warmUpAll();
        } catch (RuntimeException exception) {
            // Preparing the pages is an optimization: a failure here must not take the client down with it.
            MiXianTu.LOGGER.warn("Could not prepare the ApricityUI pages", exception);
        }
    }

    /**
     * Client ticks a page is held back from its host when {@link #warmUpStyles} could not prepare it: ApricityUI
     * parses a linked stylesheet on a worker thread and applies it on a later client tick, so a page drawn
     * before that shows its unstyled DOM - on a container page every cell then still sits at the document
     * origin, which is what the slot geometry read back. Two ticks cover the frame scheduler pass that applies
     * the stylesheet, whichever order that pass and the screen tick run in.
     */
    private static final int HOLD_TICKS = 2;

    /**
     * Per-screen hold for {@link #HOLD_TICKS}: restarted when a document is created, ticked from the host's own
     * tick hook, and queried from its extraction pass. A hold nobody ticks stays held.
     */
    public static final class StyleHold {
        private int ticks = HOLD_TICKS;

        /**
         * Starts the hold for a document that was just created; a page whose stylesheets were already prepared
         * by {@link #warmUpStyles} is ready at once.
         */
        public void restart(boolean stylesPrepared) {
            this.ticks = stylesPrepared ? 0 : HOLD_TICKS;
        }

        public void tick() {
            if (this.ticks > 0) this.ticks--;
        }

        public boolean held() {
            return this.ticks > 0;
        }
    }

    /**
     * Reads every page's template and prepares the stylesheets it links, so a screen's first open only builds
     * its document. Without it each page pays that work on the frame the player opens it - a template read plus
     * a stylesheet parse and compile, and the two held ticks that wait for the stylesheet to land.
     */
    public static void warmUpAll() {
        long start = System.nanoTime();
        int prepared = 0;
        for (String page : PAGES) if (warmUpStyles(page)) prepared++;
        MiXianTu.LOGGER.info("Prepared {} of {} ApricityUI pages for first open ({}ms)", prepared, PAGES.size(),
                (System.nanoTime() - start) / 1_000_000L);
    }

    /**
     * Prepares the stylesheets a page links, so its document is styled from the first frame: ApricityUI only
     * prepares the templates its startup scan can see, and the rescan that finds the pages seeded into the game
     * directory prepares nothing - without this, every document is built with its stylesheet still in flight.
     * Answers false when the page could not be prepared, which is what {@link StyleHold} then covers.
     */
    public static boolean warmUpStyles(String path) {
        HTML.TemplateResources template = templateResources(path);
        if (template == null) return false;
        if (template.externalStyleSrcs().isEmpty()) return true;
        return StyleAsyncHandler.INSTANCE.warmUpTemplateStyles(template.path(), template.externalStyleSrcs(), template.inlineStyles(), warmUpViewport(path)) > 0;
    }

    /**
     * The page's template resources; a page whose blueprint is not prepared yet - the usual first open - needs
     * one template read for its stylesheet list, and reading one template touches neither the others nor a
     * document.
     */
    @Nullable
    private static HTML.TemplateResources templateResources(String path) {
        HTML.TemplateResources prepared = findTemplate(path);
        if (prepared != null) return prepared;
        if (!HTML.reload(path)) return null;
        return findTemplate(path);
    }

    @Nullable
    private static HTML.TemplateResources findTemplate(String path) {
        for (HTML.TemplateResources template : HTML.preparedTemplateResources()) {
            if (template.path().equals(path)) return template;
        }
        return null;
    }

    /**
     * The viewport ApricityUI would compile a page for; a different one only costs a recompile, since the
     * compiled stylesheet cache is keyed by viewport.
     */
    private static Size warmUpViewport(String path) {
        try {
            ApricityViewport viewport = ApricityViewport.spec(path).createState(path)
                    .resolve(Minecraft.getInstance().getWindow());
            return new Size(viewport.layoutWidth(), viewport.layoutHeight());
        } catch (RuntimeException | LinkageError exception) {
            return new Size(1024, 768);
        }
    }
}
