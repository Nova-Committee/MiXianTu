package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.layout.Size;
import com.sighs.apricityui.parser.HTML;
import com.sighs.apricityui.resource.async.style.StyleAsyncHandler;
import com.sighs.apricityui.viewport.ApricityViewport;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * The page paths of this mod's ApricityUI pages, plus the warm-up a host runs before creating one.
 *
 * <p>The pages ship in the jar under ApricityUI's own resource root, {@code assets/apricityui/apricity/mxt/}:
 * ApricityUI scans every namespace's {@code apricity/} folder but resolves page content as
 * {@code apricityui:apricity/<path>}, so a page under this mod's namespace would be listed and never open. Nothing is
 * written to the game directory, which stays what it is for a player - an override layer that wins over the jar.</p>
 */
public final class AuiPages {
    /**
     * The four furnace views, sharing one stylesheet.
     */
    public static final String ALCHEMY = "mxt/alchemy";
    /**
     * The in-game wheel and the editor {@code /wheel} opens, each with its own stylesheet. The HUD grid draws
     * itself and has no page.
     */
    public static final String WHEEL = "mxt/wheel";
    /**
     * The one theme every page links; it sits in its own folder beside the page folders, so a page links
     * {@code ../common/theme.css}.
     */
    public static final String COMMON = "mxt/common";
    /**
     * The forge table.
     */
    public static final String FORGING = "mxt/forging";
    /**
     * The spirit crafting table: the vanilla crafting grid plus the aura progress panel.
     */
    public static final String SPIRIT_CRAFTING = "mxt/spirit_crafting";
    /**
     * The economy screens: the two station views of the four station menus (sharing one stylesheet), the
     * exchange station, the cheque table and the two-player trade.
     */
    public static final String ECONOMY = "mxt/economy";
    /**
     * The player information panel.
     */
    public static final String INFORMATION = "mxt/information";
    /**
     * The formation structure preview: a page over the whole window, with the scene drawn by Java.
     */
    public static final String MULTIBLOCK = "mxt/multiblock";
    /**
     * The talisman drawing workstation: the formula list, the station slot and the paper the player traces on.
     */
    public static final String TALISMAN = "mxt/talisman";

    private static final String HTML_SUFFIX = ".html";

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

    public static String talismanPage() {
        return page(TALISMAN, "talisman");
    }

    public static String page(String folder, String name) {
        return folder + "/" + name + HTML_SUFFIX;
    }

    /**
     * The name of a page path - its last segment without the suffix - which is what a host names the page by in the
     * log; the path is the only thing a host names, the name follows from it.
     */
    public static String pageName(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        return name.endsWith(HTML_SUFFIX) ? name.substring(0, name.length() - HTML_SUFFIX.length()) : name;
    }

    /**
     * Prepares the stylesheets a page links, so its document is styled from the first frame; ApricityUI warms up
     * every page its scan found on each resource reload, so this is normally a cache hit. It also reads the page
     * back when its template is not in the table at all - which is what makes {@code Document.create} find it.
     */
    public static void warmUpStyles(String path) {
        HTML.TemplateResources template = templateResources(path);
        if (template == null) return;
        if (template.externalStyleSrcs().isEmpty()) return;
        StyleAsyncHandler.INSTANCE.warmUpTemplateStyles(template.path(), template.externalStyleSrcs(), template.inlineStyles(), warmUpViewport(path));
    }

    /**
     * The page's template resources; a page whose blueprint is not prepared yet - the usual first open of a page
     * added after the last reload - needs one template read for its stylesheet list, and reading one template touches
     * neither the others nor a document.
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
