package com.iafenvoy.mxt.data.trigger;

/**
 * Keys for the payload values a trigger carries beyond the fixed context fields. Numbers travel as formula
 * values instead, because a formula is allowed to read them; these keys carry the objects a formula cannot
 * hold - holders, dimension keys, entity collections - and are read back through
 * {@link TriggerContext#payload(String)}.
 *
 * <p>They exist because ported vanilla triggers keep vanilla's own instance codec and only rebuild the
 * arguments vanilla's call site would have passed, so the payload has to survive the trip from the hook to
 * the matcher without being reduced to a number.</p>
 */
public final class TriggerPayload {
    public static final String POTION = "mxt:potion";
    public static final String DIMENSION_FROM = "mxt:dimension_from";
    public static final String DIMENSION_TO = "mxt:dimension_to";
    public static final String PARENT = "mxt:parent";
    public static final String PARTNER = "mxt:partner";
    public static final String CHILD = "mxt:child";
    public static final String LIGHTNING = "mxt:lightning";
    public static final String BYSTANDERS = "mxt:bystanders";
    public static final String EFFECT_SOURCE = "mxt:effect_source";
    public static final String ITEMS = "mxt:items";
    /**
     * The place a polled trigger measures from: where a levitation started, where a fall started, where a
     * vehicle entered lava, where the player entered the nether, or the position an explosion impulse threw
     * the player from.
     */
    public static final String START_POSITION = "mxt:start_position";
    public static final String BLOCK_STATE = "mxt:block_state";
    public static final String CHANGED_ITEM = "mxt:changed_item";
    /**
     * The stack that was in the slot before the change. Vanilla's durability criterion reads the damage its
     * item carried when its own hook ran, and a poll can only recover that from the previous snapshot.
     */
    public static final String PREVIOUS_ITEM = "mxt:previous_item";
    public static final String CAUSE = "mxt:cause";
    public static final String THROWER = "mxt:thrower";

    private TriggerPayload() {
    }
}
