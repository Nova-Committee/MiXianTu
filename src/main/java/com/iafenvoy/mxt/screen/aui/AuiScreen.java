package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.init.Document;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

/**
 * The page host for an interface with no menu: {@link AuiWrappedScreen} over a plain {@link Screen}, which is every
 * page this mod draws outside a container (the information panel, the structure preview and the two wheel pages).
 * <p>
 * It holds the page state and the lifecycle - create on the first {@code init}, tick the style hold, drop the
 * document on {@code removed} - and draws the vanilla grey plate behind the page. A subclass only names its page,
 * resolves the contract and writes its own frame.
 */
public abstract class AuiScreen extends Screen implements AuiWrappedScreen {
    private final AuiWrappedScreen.State state = new AuiWrappedScreen.State();
    /**
     * The page this screen is on; null before {@code init} and after {@code removed}. Held here and not in the
     * state because {@code getLinkedDocument} is already the one answer to "which document is this screen on".
     */
    @Nullable
    private Document document;

    protected AuiScreen(Component title) {
        super(title);
    }

    /**
     * Document path of the page, e.g. {@code AuiPages.page(AuiPages.INFORMATION, "information")}.
     */
    protected abstract String pagePath();

    /**
     * Page name as the fallback error line prints it.
     */
    protected abstract String pageName();

    @Override
    public AuiWrappedScreen.State auiState() {
        return this.state;
    }

    @Override
    @Nullable
    public Document getLinkedDocument() {
        return this.document;
    }

    @Override
    public void auiSetDocument(@Nullable Document document) {
        this.document = document;
    }

    @Override
    protected void init() {
        super.init();
        this.auiInit(this.pagePath(), this.pageName());
    }

    @Override
    public void tick() {
        super.tick();
        this.auiTick();
    }

    /**
     * The vanilla grey plate, not the branch {@code super} would take: a plain {@link Screen} answers false to
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
