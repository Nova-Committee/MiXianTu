package com.iafenvoy.mxt.runtime.behavior;

/**
 * A Java-owned, parameterless behaviour selected directly by a datapack ID.
 * Datapacks may choose an ID but cannot construct or reflectively invoke behaviour code.
 */
@FunctionalInterface
public interface DomainBehavior {
    void execute(BehaviorContext context);
}
