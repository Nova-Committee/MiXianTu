package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.screen.ApricityScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.NonNull;

/**
 * The page host for an interface with no menu: {@link AuiWrappedScreen} over ApricityUI's {@link ApricityScreen},
 * which is every page this mod draws outside a container (the information panel, the structure preview and the two
 * wheel pages).
 * <p>
 * ApricityUI owns the document (it builds one on every {@code init}, follows the viewport, drops it on close and
 * submits it to the renderer) and the input around it (Ctrl +/-/0 zoom, the reload key). All this adds is the page
 * contract and the vanilla grey plate behind the page. A subclass only names its page, resolves the contract and
 * writes its own frame.
 */
public abstract class AuiScreen extends ApricityScreen implements AuiWrappedScreen {
    private final AuiWrappedScreen.State state = new AuiWrappedScreen.State();
    /**
     * The page path as given; ApricityUI keeps its own copy, and this one is what {@link #auiPreparePage} needs on
     * every {@code init} - before the document exists.
     */
    private final String pagePath;

    protected AuiScreen(String pagePath) {
        super(pagePath);
        this.pagePath = pagePath;
    }

    @Override
    public AuiWrappedScreen.State auiState() {
        return this.state;
    }

    @Override
    protected void init() {
        this.auiPreparePage(this.pagePath);
        super.init();
        this.auiPageOpened();
    }

    /**
     * The vanilla grey plate, not the branch {@code super} would take: a plain {@code Screen} answers false to
     * {@code isInGameUi()}, so the vanilla one would blur the HUD and the world behind the page. The wheel menu
     * overrides this with nothing at all - it is read while the world is still running.
     */
    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        AuiStyles.extract(this, graphics);
    }

    @Override
    public void removed() {
        this.auiClose();
        super.removed();
    }
}
