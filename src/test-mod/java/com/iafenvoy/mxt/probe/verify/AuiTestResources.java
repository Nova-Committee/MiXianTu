package com.iafenvoy.mxt.probe.verify;

import com.iafenvoy.mxt.MiXianTu;
import com.sighs.apricityui.init.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.NoSuchElementException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AuiTestResources {
    private static final Pattern IMPORT = Pattern.compile("@import\\s+\"([^\"]+)\"\\s*;");

    private AuiTestResources() {
    }

    static String read(String path) {
        return read(path, 0);
    }

    private static String read(String path, int depth) {
        if (depth > 8) throw new IllegalArgumentException("Cyclic stylesheet import: " + path);
        Class<?> owner = path.equals("apricityui/apricity/global.css") ? Document.class : MiXianTu.class;
        String text;
        try (InputStream input = owner.getResourceAsStream("/assets/" + path)) {
            if (input == null) throw new NoSuchElementException(path);
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        if (!path.endsWith(".css")) return text;
        Matcher imports = IMPORT.matcher(text);
        StringBuilder expanded = new StringBuilder();
        while (imports.find()) {
            String imported = Path.of(path).resolveSibling(imports.group(1)).normalize().toString().replace('\\', '/');
            imports.appendReplacement(expanded, Matcher.quoteReplacement(read(imported, depth + 1)));
        }
        return imports.appendTail(expanded).toString();
    }
}
