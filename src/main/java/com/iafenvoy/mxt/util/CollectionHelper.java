package com.iafenvoy.mxt.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CollectionHelper {
    public static <T> boolean containsAllFast(List<T> source, List<T> allElements) {
        if (allElements.isEmpty()) return true;
        Set<T> set = new HashSet<>(source);
        for (T item : allElements)
            if (!set.contains(item))
                return false;
        return true;
    }
}
