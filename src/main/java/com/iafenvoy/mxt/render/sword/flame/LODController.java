package com.iafenvoy.mxt.render.sword.flame;

import com.iafenvoy.mxt.config.MxtClientConfig;

public final class LODController {
    private LODController() {
    }

    public static int level(double distanceSquared) {
        var config = MxtClientConfig.INSTANCE.flames;
        double near = config.nearDistance.getValue();
        double middle = Math.max(near, config.middleDistance.getValue());
        double far = Math.max(middle, config.farDistance.getValue());
        if (distanceSquared <= near * near) return 0;
        if (distanceSquared <= middle * middle) return 1;
        return distanceSquared <= far * far ? 2 : 3;
    }

    public static int particles(int level) {
        var config = MxtClientConfig.INSTANCE.flames;
        return switch (level) {
            case 0 -> config.nearParticles.getValue();
            case 1 -> config.middleParticles.getValue();
            default -> config.farParticles.getValue();
        };
    }
}
