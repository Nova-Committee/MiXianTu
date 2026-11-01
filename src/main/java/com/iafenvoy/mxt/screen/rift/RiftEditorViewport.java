package com.iafenvoy.mxt.screen.rift;

import com.iafenvoy.mxt.data.rift.RiftShape;
import com.iafenvoy.mxt.data.rift.RiftVertex;

public final class RiftEditorViewport {
    private int width = 320, height = 256;
    private double centerU, centerV = 1.2, scale = 48;

    public boolean resize(double width, double height) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width < 1 || height < 1) return false;
        double reduction = Math.min(1, 4096 / Math.max(width, height));
        int w = Math.max(1, (int) Math.round(width * reduction)), h = Math.max(1, (int) Math.round(height * reduction));
        if (this.width == w && this.height == h) return false;
        this.width = w;
        this.height = h;
        return true;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public double scale() {
        return this.scale;
    }

    public double x(double u) {
        return this.width / 2.0 + (u - this.centerU) * this.scale;
    }

    public double y(double v) {
        return this.height / 2.0 - (v - this.centerV) * this.scale;
    }

    public RiftVertex local(double x, double y) {
        return new RiftVertex((x - this.width / 2.0) / this.scale + this.centerU,
                (this.height / 2.0 - y) / this.scale + this.centerV);
    }

    public void pan(RiftVertex from, RiftVertex to) {
        this.centerU -= to.u() - from.u();
        this.centerV -= to.v() - from.v();
    }

    public void zoom(double steps) {
        if (Double.isFinite(steps)) this.scale = Math.clamp(this.scale * Math.pow(1.15, steps), 4, 512);
    }

    public void fit(RiftShape shape) {
        if (shape.vertices().isEmpty()) return;
        double minU = Double.POSITIVE_INFINITY, maxU = Double.NEGATIVE_INFINITY;
        double minV = Double.POSITIVE_INFINITY, maxV = Double.NEGATIVE_INFINITY;
        for (RiftVertex point : shape.vertices()) {
            if (!Double.isFinite(point.u()) || !Double.isFinite(point.v())) return;
            minU = Math.min(minU, point.u());
            maxU = Math.max(maxU, point.u());
            minV = Math.min(minV, point.v());
            maxV = Math.max(maxV, point.v());
        }
        this.centerU = (minU + maxU) / 2;
        this.centerV = (minV + maxV) / 2;
        this.scale = Math.clamp(Math.min(Math.max(1, this.width - 32) / Math.max(0.25, maxU - minU),
                Math.max(1, this.height - 32) / Math.max(0.25, maxV - minV)), 4, 512);
    }
}
