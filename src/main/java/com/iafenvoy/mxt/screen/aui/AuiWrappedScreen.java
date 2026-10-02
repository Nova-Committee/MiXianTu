package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * What every page host in this mod shares: the bind of the page contract it names, the fallback line for a page
 * that does not match, and the element helpers that bind is written with.
 * <p>
 * The behaviour is here and the data is in {@link State}, because the two hosts it is written for - the container
 * host and the plain one - already extend different vanilla classes and so cannot share a base class. The document
 * is the exception: {@link AuiLinkedScreen} already owns that value, so each host keeps one field behind
 * {@link #auiSetDocument} and every helper below reads it back through {@link AuiLinkedScreen#getLinkedDocument()}.
 * A host only names its page, resolves the contract in {@code bindPage} and writes whatever changes each frame.
 */
public interface AuiWrappedScreen extends AuiLinkedScreen {
    /**
     * The state this host binds into; the two base screens each hold one.
     */
    State auiState();

    /**
     * Takes the document the host holds. The interface cannot hold it itself, and a second copy here would be a
     * second answer to "which document is this screen on".
     */
    void auiSetDocument(@Nullable Document document);

    /**
     * Resolves every id the page contract names, binds the slot cells and binds the page's listeners. A piece the
     * page does not provide throws {@link NoSuchElementException} naming it - {@link #getOrThrow} is that throw -
     * and {@link #auiRebind} turns it into the fallback line instead of a crash.
     */
    void bindPage() throws NoSuchElementException;

    /**
     * The page's name, as the fallback line prints it; it follows from the path the host opened.
     */
    default String pageName() {
        return this.auiState().pageName;
    }

    /**
     * Seeds the bundled pages, builds the document and binds the contract; the host passes the path of the page it
     * wants, once, from its {@code init}.
     * <p>
     * A window resize re-enters {@code init} with the same DOM, and ApricityUI appends listeners without ever
     * deduping them: a resize therefore only applies the viewport. A document that really was rebuilt is caught by
     * the generation check in {@link #auiPageWritable()}, which is the one thing that has to happen per frame.
     */
    default void auiInit(String pagePath) {
        State state = this.auiState();
        state.pageName = AuiPages.pageName(pagePath);
        Document current = this.getLinkedDocument();
        if (current != null) {
            current.applyViewport(true);
            return;
        }
        AuiPages.seedAll();
        boolean stylesPrepared = AuiPages.warmUpStyles(pagePath);
        Document created = Document.create(pagePath);
        if (created == null) {
            state.pageError = this.pageMissing();
            return;
        }
        this.auiSetDocument(created);
        state.styleHold.restart(stylesPrepared);
        this.auiRebind();
    }

    /**
     * Resolves the page contract again; a refresh (hot reload) replaces every element, which is when the page's
     * listeners have to be bound again. A page that does not match the contract costs the page, not the screen.
     */
    default void auiRebind() {
        this.auiClearBindings();
        State state = this.auiState();
        state.pageError = null;
        Document current = this.getLinkedDocument();
        if (current == null) return;
        try {
            this.bindPage();
        } catch (NoSuchElementException exception) {
            this.invalidate(exception.getMessage());
            return;
        }
        state.pageBound = true;
        state.boundGeneration = current.getRefreshGeneration();
        this.onPageBound();
    }

    /**
     * Drops the bindings, then lets the host drop what it cached about them; a host that remembers elements of its
     * own overrides this, clears them and calls {@code super} last.
     */
    default void auiClearBindings() {
        State state = this.auiState();
        state.pageBound = false;
        state.boundGeneration = Long.MIN_VALUE;
        this.onBindingsCleared();
    }

    /**
     * Runs after a successful bind and after every rebind; the place to push the first state.
     */
    default void onPageBound() {
    }

    /**
     * Drops the state a host caches about the page; every element it remembered is dead after a rebind.
     */
    default void onBindingsCleared() {
    }

    /**
     * Ticks the hold that keeps a page off screen while its stylesheet is still in flight; call it from the host's
     * own tick hook.
     */
    default void auiTick() {
        this.auiState().styleHold.tick();
    }

    /**
     * Drops the document; call it from the host's {@code removed}. The page outlives the screen unless it is
     * unregistered here: the renderer draws every document, so a leaked one keeps drawing the panel over the game.
     */
    default void auiClose() {
        this.auiClearBindings();
        Document current = this.getLinkedDocument();
        if (current == null) return;
        current.remove();
        this.auiSetDocument(null);
    }

    /**
     * Whether this frame may draw at all: false while the page's stylesheet is still in flight, because a page
     * drawn before it lands has no layout. A page error answers true, so its fallback line is drawn.
     */
    default boolean auiReadyToDraw() {
        State state = this.auiState();
        return state.pageError != null || !state.styleHold.held();
    }

    /**
     * Rebinds when a hot reload replaced the DOM and answers whether the contract is bound: the frame a rebind
     * happens on answers false, so the pass that called it leaves the fresh page alone until the next frame.
     */
    default boolean auiPageWritable() {
        Document current = this.getLinkedDocument();
        if (current == null) return false;
        State state = this.auiState();
        if (current.getRefreshGeneration() != state.boundGeneration) {
            this.auiRebind();
            return false;
        }
        return state.pageBound;
    }

    /**
     * Whether the contract is bound. A page that never bound - a missing blueprint or a missing node - answers
     * false, which is what the input gates key on.
     */
    default boolean auiBound() {
        return this.auiState().pageBound;
    }

    /**
     * The line for a page whose blueprint was not found; the wheel's page spells this its own way.
     */
    default Component pageMissing() {
        return Component.translatable("screen.mxt.page.missing", this.auiState().pageName);
    }

    /**
     * The line for a page that was built but does not match the contract; the message is the missing piece.
     */
    default Component pageInvalid(String missing) {
        return Component.translatable("screen.mxt.page.invalid", this.auiState().pageName, missing);
    }

    /**
     * Draws the fallback line in the middle of the screen. Each host calls it where its own page cannot cover it -
     * on the background plate, or after the page has been submitted.
     */
    default void extractPageError(GuiGraphicsExtractor graphics, Font font, int width, int height) {
        State state = this.auiState();
        Component error = state.pageError;
        if (error == null) return;
        if (state.errorWidth != width) {
            state.errorLines = font.split(error, Math.max(40, width - 40));
            state.errorWidth = width;
        }
        int y = height / 2 - state.errorLines.size() * 5;
        for (FormattedCharSequence line : state.errorLines) {
            graphics.text(font, line, (width - font.width(line)) / 2, y, 0xFFFF5555, false);
            y += 10;
        }
    }

    // ------------------------------------------------------------------ element helpers

    /**
     * Resolves one scroll list: its own element plus the row cells its binding names. Every gap is a fault naming
     * the piece that is missing - the page, the list, a row - so the caller writes no null check at all, and a
     * binding reads the page through the same host instead of carrying a document around.
     */
    default <E> AuiScrollList<E> scrollList(String prefix, String rootId, int count, AuiScrollList.Binding<E> binding) {
        Element root = this.getOrThrow(rootId);
        return new AuiScrollList<>(this, root, binding.bind(this, prefix, count), binding);
    }

    /**
     * The element with that id, or a fault naming it. The thrown message is the missing piece as the fallback line
     * prints it, not a sentence.
     */
    default Element getOrThrow(String id) {
        Element element = this.auiDocument().getElementById(id);
        if (element == null) throw this.missing(id);
        return element;
    }

    /**
     * The element with that id, or null; for the parts of a page that are allowed to be absent.
     */
    @Nullable
    default Element find(String id) {
        Document current = this.getLinkedDocument();
        return current == null ? null : current.getElementById(id);
    }

    /**
     * The same for an element whose tag the Java side depends on: the wrong tag reads as a missing one, and the
     * message says which tag was expected.
     */
    default <E extends Element> E getOrThrow(String id, Class<E> type) {
        Element element = this.auiDocument().getElementById(id);
        if (!type.isInstance(element)) {
            throw this.missing(element == null
                    ? id : id + " (" + type.getSimpleName().toLowerCase(Locale.ROOT) + ")");
        }
        return type.cast(element);
    }

    /**
     * A contract violation that is not one missing id - a container with no cells, a list whose rows are not
     * there - written the way {@link #getOrThrow} writes its own.
     */
    default NoSuchElementException missing(String descriptor) {
        return new NoSuchElementException(descriptor);
    }

    /**
     * Applies text and its colour; the page is written to, never read back.
     */
    default void text(@Nullable Element element, Component text) {
        if (element == null) return;
        AuiElements.setText(element, text.getString());
        TextColor color = text.getStyle().getColor();
        if (color == null) return;
        AuiElements.style(element, "color", AuiStyles.hex(color.getValue()));
    }

    default void click(@Nullable Element element, Runnable action) {
        if (element == null) return;
        element.addEventListener("click", _ -> action.run());
    }

    /**
     * The document a bind runs against. The helpers are only reached from one, so a null here is itself the
     * violation rather than a crash.
     */
    private Document auiDocument() {
        Document current = this.getLinkedDocument();
        if (current == null) throw this.missing("document");
        return current;
    }

    /**
     * Turns a contract violation into the fallback line. The bindings are dropped a second time because a bind
     * that threw has already written part of what it resolved; the generation is kept so it is reported once.
     */
    private void invalidate(String missing) {
        State state = this.auiState();
        Document current = this.getLinkedDocument();
        long generation = current == null ? Long.MIN_VALUE : current.getRefreshGeneration();
        this.auiClearBindings();
        state.boundGeneration = generation;
        state.pageError = this.pageInvalid(missing);
    }

    /**
     * A text node of the page plus the element behind it, for the hosts that also write that element's rectangle.
     */
    record Label(Element element) {
        public void show(String value) {
            AuiElements.setText(this.element, value);
        }
    }

    /**
     * One host's page state: everything the bind of that document produced. The document itself is not here - the
     * host holds it, see {@link #auiSetDocument}. The fields are package-private because both hosts live in this
     * package and no page may reach them.
     */
    final class State {
        /**
         * The page's name for the fallback line, read off the path the host opened.
         */
        String pageName = "";
        @Nullable
        Component pageError;
        List<FormattedCharSequence> errorLines = List.of();
        int errorWidth = -1;
        final AuiPages.StyleHold styleHold = new AuiPages.StyleHold();
        /**
         * Whether the contract is bound; a page that failed its bind stays unbound until the DOM is rebuilt.
         */
        boolean pageBound;
        /**
         * The generation the bindings came from: a different one means a hot reload replaced every element.
         */
        long boundGeneration = Long.MIN_VALUE;
    }
}
