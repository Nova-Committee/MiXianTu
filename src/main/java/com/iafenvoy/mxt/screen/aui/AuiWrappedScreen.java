package com.iafenvoy.mxt.screen.aui;

import com.iafenvoy.mxt.MiXianTu;
import com.sighs.apricityui.init.Document;
import com.sighs.apricityui.init.Element;
import com.sighs.apricityui.screen.AuiLinkedScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.NoSuchElementException;

/**
 * What every page host in this mod adds on top of ApricityUI's own hosts: the bind of the page contract it names,
 * and the element helpers that bind is written with.
 * <p>
 * The document is ApricityUI's: {@code ApricityScreen} and {@code ApricityContainerScreen} create it, resize its
 * viewport, drop it on close and submit it to the renderer, and {@link AuiLinkedScreen#getLinkedDocument()} is the
 * one answer to "which document is this screen on". A host therefore only prepares the page's stylesheets before the
 * document exists ({@link #auiPreparePage}), binds the contract once it does ({@link #auiPageOpened}), and writes
 * whatever changes each frame.
 * <p>
 * A page that is missing or does not match its contract costs the page, not the screen - and it tells the player
 * nothing: the bind simply does not happen, so the input gates stay closed, and one warning in the log names the
 * piece. Only an author ever sees this, which is why it is not drawn.
 */
public interface AuiWrappedScreen extends AuiLinkedScreen {
    /**
     * The state this host binds into; the two base screens each hold one.
     */
    State auiState();

    /**
     * Resolves every id the page contract names and binds the page's listeners (the slots are ApricityUI's, see
     * {@link #auiPageOpened()}). A piece the page does not provide throws {@link NoSuchElementException} naming it
     * - {@link #getOrThrow} is that throw - and {@link #auiRebind} logs it and binds nothing.
     */
    void bindPage() throws NoSuchElementException;

    /**
     * Prepares the stylesheets of the page about to be opened; the host calls it from its {@code init}
     * <em>before</em> the document is created, because ApricityUI attaches them while it creates it. Preparing them
     * up front is what keeps a page's first open down to building one document.
     */
    default void auiPreparePage(String pagePath) {
        AuiPages.warmUpStyles(pagePath);
    }

    /**
     * Binds the page the host just created; call it right after {@code super.init()} (which is where ApricityUI
     * builds the document). A window resize re-enters {@code init} and ApricityUI rebuilds the document there, so
     * this also runs per resize - ApricityUI appends listeners without ever deduping them, and only a fresh
     * document can be rebound safely.
     * <p>
     * A page ApricityUI could not create is not reported again here: its own {@code [AUI Document]} error already
     * says why.
     */
    default void auiPageOpened() {
        if (this.getLinkedDocument() == null) return;
        this.auiRebind();
    }

    /**
     * Resolves the page contract again; a refresh (hot reload) replaces every element, which is when the page's
     * listeners have to be bound again.
     */
    default void auiRebind() {
        this.auiClearBindings();
        State state = this.auiState();
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
     * Drops what the host cached about the page; call it from the host's {@code removed}. The document itself is
     * ApricityUI's and is already dropped there - the renderer draws every live document, so a host that rebuilt
     * one of its own would leak it onto the game.
     */
    default void auiClose() {
        this.auiClearBindings();
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
     * The element with that id, or a fault naming it. The thrown message is the missing piece as the log prints it,
     * not a sentence.
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
            throw this.missing(element == null ? id : id + " (" + type.getSimpleName().toLowerCase(Locale.ROOT) + ")");
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
     * Drops a bind that threw after having written part of what it resolved, and warns once: the generation is kept
     * so every later frame sees a bind that already ran, instead of retrying and logging per frame. The player is
     * told nothing - a page that does not match its contract is an authoring mistake.
     */
    private void invalidate(String missing) {
        State state = this.auiState();
        Document current = this.getLinkedDocument();
        long generation = current == null ? Long.MIN_VALUE : current.getRefreshGeneration();
        this.auiClearBindings();
        state.boundGeneration = generation;
        MiXianTu.LOGGER.warn("Page does not match its contract: {}", missing);
    }

    /**
     * A text node of the page plus the element behind it, for the hosts that also write that element's rectangle.
     */
    record Label(Element element) {
        public void show(String value) {
            AuiElements.setText(this.element, value);
        }
    }

    final class State {
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
