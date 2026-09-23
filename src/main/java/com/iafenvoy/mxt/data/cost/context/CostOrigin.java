package com.iafenvoy.mxt.data.cost.context;


/**
 * Who is charging. Callers use it to tell two payments apart in logs, and to derive the resources a payment is
 * allowed to touch.
 */
public enum CostOrigin {
    ABILITY, CHANNEL_UPKEEP, BREAKTHROUGH, CULTIVATION, FORMATION_ACTIVATION, FORMATION_MAINTENANCE,
    FORGING, ARTIFACT_FLIGHT, ARTIFACT_UPKEEP, RECIPE, SCRIPT
}
