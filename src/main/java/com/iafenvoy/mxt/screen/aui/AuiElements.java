package com.iafenvoy.mxt.screen.aui;

import com.sighs.apricityui.init.Element;
import org.jetbrains.annotations.Nullable;

/**
 * The writes a page host makes to its elements: text, an inline style property and a class token. This is the only
 * implementation of them - a host, a scroll list and a row binding all come here.
 * <p>
 * Every write compares first: ApricityUI re-runs the page's style pass on any change, and rows are written on every
 * layout. A null element is ignored rather than checked by the caller, because the field behind it is nullable
 * while a host is between binds.
 */
public final class AuiElements {
    private AuiElements() {
    }

    public static void style(@Nullable Element element, String property, String value) {
        if (element == null) return;
        if (value.equals(element.getInlineStylePropertyValue(property))) return;
        element.setInlineStyleProperty(property, value);
    }

    public static void setText(@Nullable Element element, String text) {
        if (element == null) return;
        if (text.equals(element.getTextContent())) return;
        element.setTextContent(text);
    }

    public static void setClass(@Nullable Element element, String token, boolean present) {
        if (element == null) return;
        if (element.getClassList().contains(token) == present) return;
        element.getClassList().toggle(token, present);
    }
}
