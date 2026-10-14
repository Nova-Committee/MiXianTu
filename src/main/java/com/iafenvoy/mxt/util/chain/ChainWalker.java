package com.iafenvoy.mxt.util.chain;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public final class ChainWalker {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Build chains with given objects and next pointer.
     * The pointer is read through {@code nextGetter}, where null means the line ends here; whatever it hands back has
     * to be the very instance the list holds, since entries are compared by identity.
     * Note: pointer outside given list, fork and loop are not allowed. Each is logged where it is found, and the line
     * that runs into one is left out whole - a prefix is not the line the data describes.
     */
    public static <T, C> List<C> walk(List<T> list, UnaryOperator<T> nextGetter, Function<List<T>, C> chainConstructor) {
        Set<T> allObjects = newIdentitySet(), referencedObjects = newIdentitySet(), forkedObjects = newIdentitySet();
        allObjects.addAll(list);
        // Scan "next" reference
        for (T item : list) {
            T next = nextGetter.apply(item);
            if (next == null) continue;
            if (!allObjects.contains(next)) {
                LOGGER.error("Unknown object {} is referenced in chain build.", next);
                continue;
            }
            if (!referencedObjects.add(next)) {
                LOGGER.error("Object {} is referenced by multiple objects.", next);
                forkedObjects.add(next);
            }
        }
        // Find heads.
        // Heads are objects that are not referenced by any other object. If an object is referenced by another, it cannot be a head.
        // Read in the order the list gives, so which line is produced first does not depend on hash order.
        Set<T> visited = newIdentitySet();
        List<C> chainList = new ArrayList<>();
        for (T candidate : list) {
            if (referencedObjects.contains(candidate)) continue;
            if (visited.contains(candidate)) continue;
            // Find from head to tail
            List<T> chainNodes = new ArrayList<>();
            T current = candidate;
            Set<T> path = newIdentitySet();
            boolean whole = true;

            while (current != null) {
                // An unknown object and a fork were both reported by the scan above; a loop and an entry another line
                // already holds are reported here, since only the walk knows which line ran into them.
                if (!allObjects.contains(current)) {
                    whole = false;
                    break;
                }
                if (path.contains(current)) {
                    LOGGER.error("Loop detected in chain build, current: {}.", current);
                    whole = false;
                    break;
                }
                if (forkedObjects.contains(current)) {
                    whole = false;
                    break;
                }
                if (visited.contains(current)) {
                    LOGGER.error("Object {} is referenced by multiple chains.", current);
                    whole = false;
                    break;
                }
                path.add(current);
                visited.add(current);
                chainNodes.add(current);

                current = nextGetter.apply(current);
            }
            if (whole) chainList.add(chainConstructor.apply(chainNodes));
        }
        // Final check
        if (visited.size() != allObjects.size())
            LOGGER.error("Unreachable object(s) detected, this should not happened!");
        return List.copyOf(chainList);
    }

    private static <T> Set<T> newIdentitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
