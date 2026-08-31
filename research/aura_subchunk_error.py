"""Study one aura block inside a 16^3 subchunk under a 1/r^2 kernel.

For every sampled player position, the cache approximation treats the one
source block as if it were at the subchunk centre. The exact value uses the
source block's real position. Source positions cover all 4096 block centres;
player positions are sampled from a one-block-thick radial shell.
"""

from pathlib import Path

import matplotlib.pyplot as plt
import numpy as np


ERROR_OUT = Path(__file__).with_name("aura_subchunk_error.png")
VALUE_OUT = Path(__file__).with_name("aura_subchunk_value.png")
SOURCE_COORDINATES = np.arange(-7.5, 8.0, 1.0)
SOURCE_POSITIONS = np.array(
    np.meshgrid(SOURCE_COORDINATES, SOURCE_COORDINATES, SOURCE_COORDINATES, indexing="ij")
).reshape(3, -1).T
SAMPLE_COUNT = 512


def unit_vectors(seed=42, count=SAMPLE_COUNT):
    rng = np.random.default_rng(seed)
    vectors = rng.normal(size=(count, 3))
    return vectors / np.linalg.norm(vectors, axis=1, keepdims=True)


def shell_samples(distance, directions, rng):
    low = max(0.1, distance - 0.5)
    radius = rng.uniform(low, distance + 0.5, len(directions))
    return directions * radius[:, None], radius


def values_for_distance(distance, directions, rng):
    queries, radii = shell_samples(distance, directions, rng)
    distances = np.linalg.norm(queries[:, None, :] - SOURCE_POSITIONS[None, :, :], axis=2)
    exact = 1.0 / np.maximum(distances, 1.0e-9) ** 2
    cached = 1.0 / radii**2
    error = np.abs(cached[:, None] - exact)
    return {
        "error_mean": error.mean(),
        "error_p95": np.quantile(error, 0.95),
        "value_mean": exact.mean(),
        "value_p95": np.quantile(exact, 0.95),
        "value_p99": np.quantile(exact, 0.99),
        "cached_mean": cached.mean(),
    }


def first_distance(distances, values, key, threshold):
    valid = np.flatnonzero(values[key] <= threshold)
    return distances[valid[0]] if len(valid) else None


def main():
    distances = np.arange(1.0, 513.0)
    directions = unit_vectors()
    rng = np.random.default_rng(42)
    results = {key: np.empty_like(distances) for key in (
        "error_mean", "error_p95", "value_mean", "value_p95", "value_p99", "cached_mean"
    )}
    for index, distance in enumerate(distances):
        values = values_for_distance(distance, directions, rng)
        for key, value in values.items():
            results[key][index] = value

    print("distance,error_mean,error_p95,value_mean,value_p95,value_p99,cached_mean")
    for distance in (1, 2, 4, 6, 8, 10, 12, 16, 20, 24, 32, 48, 64, 96, 128, 256, 512):
        index = distance - 1
        print(
            f"{distance},{results['error_mean'][index]:.9g},{results['error_p95'][index]:.9g},"
            f"{results['value_mean'][index]:.9g},{results['value_p95'][index]:.9g},"
            f"{results['value_p99'][index]:.9g},{results['cached_mean'][index]:.9g}"
        )
    for threshold in (1.0, 0.1, 0.01, 0.001):
        print(f"first mean error <= {threshold}: {first_distance(distances, results, 'error_mean', threshold)}")
        print(f"first P95 error <= {threshold}: {first_distance(distances, results, 'error_p95', threshold)}")
    for threshold in (1.0, 0.1, 0.01, 0.001):
        print(f"first mean actual value <= {threshold}: {first_distance(distances, results, 'value_mean', threshold)}")
        print(f"first P95 actual value <= {threshold}: {first_distance(distances, results, 'value_p95', threshold)}")

    plt.figure(figsize=(9, 5.5), dpi=160)
    plt.loglog(distances, results["error_mean"], label="mean absolute error")
    plt.loglog(distances, results["error_p95"], "--", label="P95 absolute error")
    for value, label in ((10.0, "error 10"), (1.0, "error 1"), (0.1, "error 0.1"), (0.01, "error 0.01")):
        plt.axhline(value, color="0.7", linewidth=0.7)
        plt.text(516, value, label, va="center", fontsize=8)
    plt.xlabel("distance from subchunk centre (blocks)")
    plt.ylabel("absolute error (aura units; one source block)")
    plt.title("One-block cache error under 1/r^2 falloff")
    plt.grid(True, which="both", alpha=0.25)
    plt.legend()
    plt.tight_layout()
    plt.savefig(ERROR_OUT)

    plt.figure(figsize=(9, 5.5), dpi=160)
    plt.loglog(distances, results["value_mean"], label="mean actual value")
    plt.loglog(distances, results["value_p95"], "--", label="P95 actual value")
    plt.loglog(distances, results["cached_mean"], ":", label="cached value")
    for value, label in ((1.0, "value 1"), (0.1, "value 0.1"), (0.01, "value 0.01")):
        plt.axhline(value, color="0.7", linewidth=0.7)
        plt.text(516, value, label, va="center", fontsize=8)
    plt.xlabel("distance from subchunk centre (blocks)")
    plt.ylabel("actual aura value (one source block)")
    plt.title("One-block actual aura value under 1/r^2 falloff")
    plt.grid(True, which="both", alpha=0.25)
    plt.legend()
    plt.tight_layout()
    plt.savefig(VALUE_OUT)


if __name__ == "__main__":
    main()
