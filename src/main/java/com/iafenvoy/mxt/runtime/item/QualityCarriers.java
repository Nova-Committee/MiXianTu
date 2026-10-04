package com.iafenvoy.mxt.runtime.item;

import com.iafenvoy.mxt.data.item.ContractScrollComponent;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.data.item.PillComponent;
import com.iafenvoy.mxt.data.item.SecretRealmTokenComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;

/**
 * Every component that names a definition, and therefore lets a stack read that definition's own tier. The list is
 * the whole membership rule: a definition type joins it by implementing {@code QualityProvider} and having its
 * carrier registered here, and nothing else is ever scanned. A list-form carrier is absent by design - a
 * {@code mxt:talisman} carrier holds several bills, so there is no single definition on it to ask.
 */
final class QualityCarriers {
    private QualityCarriers() {
    }

    static void register() {
        QualityService.carry(MxtDataComponents.TECHNIQUE);
        QualityService.carry(MxtDataComponents.ALCHEMY_FURNACE);
        QualityService.carry(MxtDataComponents.ALCHEMY_WALL_MATERIAL);
        QualityService.carry(MxtDataComponents.SPIRIT_ROOT);
        QualityService.carry(MxtDataComponents.PHYSIQUE);
        QualityService.carry(MxtDataComponents.PILL, PillComponent::pill);
        QualityService.carry(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent::formation);
        QualityService.carry(MxtDataComponents.SECRET_REALM_TOKEN, SecretRealmTokenComponent::realm);
        QualityService.carry(MxtDataComponents.CONTRACT_SCROLL, ContractScrollComponent::contractType);
    }
}
