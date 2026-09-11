package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.util.SpawnRing;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Plan test 10: siege spawns land between 24 and 40 blocks out and are spread evenly around the
 * director, not bunched against the inner edge (which is what a linear radius roll would do).
 */
class SpawnRingDistributionTest {

    private static final double MIN_RADIUS = 24.0D;
    private static final double MAX_RADIUS = 40.0D;
    private static final int SAMPLES = 12_000;
    private static final int BINS = 12;

    @Test
    void everySampleLandsInsideTheAnnulus() {
        RandomSource random = RandomSource.create(0xE7E7L);
        Vec3 centre = new Vec3(12.5D, 70.0D, -300.5D);
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 point = SpawnRing.sample(random, centre, MIN_RADIUS, MAX_RADIUS);
            double dx = point.x - centre.x;
            double dz = point.z - centre.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            assertTrue(distance >= MIN_RADIUS - 1.0E-6D && distance <= MAX_RADIUS + 1.0E-6D,
                    "sample landed " + distance + " blocks out");
            assertEquals(centre.y, point.y, 1.0E-9D, "the ring is horizontal; the caller finds the surface");
        }
    }

    @Test
    void anglesAreUniformAroundTheCircle() {
        RandomSource random = RandomSource.create(4242L);
        Vec3 centre = Vec3.ZERO;
        int[] bins = new int[BINS];
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 point = SpawnRing.sample(random, centre, MIN_RADIUS, MAX_RADIUS);
            double angle = Math.atan2(point.z, point.x) + Math.PI;
            int bin = (int) (angle / (Math.PI * 2.0D) * BINS);
            bins[Math.min(bin, BINS - 1)]++;
        }
        double expected = (double) SAMPLES / BINS;
        double chiSquare = 0.0D;
        for (int count : bins) {
            double diff = count - expected;
            chiSquare += diff * diff / expected;
        }
        // 11 degrees of freedom: the 0.001 critical value is 31.26.
        assertTrue(chiSquare < 31.26D, "angles were not uniform, chi-square = " + chiSquare);
    }

    @Test
    void radiusIsUniformInAreaNotInRadius() {
        // Half the samples must fall inside the radius that splits the annulus by AREA, which is
        // sqrt((min^2 + max^2) / 2) = 33.29, not the arithmetic mean 32.
        double areaMedian = SpawnRing.radiusFor(0.5D, MIN_RADIUS, MAX_RADIUS);
        assertEquals(Math.sqrt((MIN_RADIUS * MIN_RADIUS + MAX_RADIUS * MAX_RADIUS) / 2.0D),
                areaMedian, 1.0E-9D);
        assertTrue(areaMedian > (MIN_RADIUS + MAX_RADIUS) / 2.0D);

        RandomSource random = RandomSource.create(99L);
        int inner = 0;
        for (int i = 0; i < SAMPLES; i++) {
            Vec3 point = SpawnRing.sample(random, Vec3.ZERO, MIN_RADIUS, MAX_RADIUS);
            if (Math.sqrt(point.x * point.x + point.z * point.z) < areaMedian) {
                inner++;
            }
        }
        double share = (double) inner / SAMPLES;
        assertTrue(Math.abs(share - 0.5D) < 0.02D,
                "half the spawns should sit inside the area median, got " + share);
    }

    @Test
    void degenerateBoundsAreRepaired() {
        assertEquals(MIN_RADIUS, SpawnRing.radiusFor(0.0D, MIN_RADIUS, MAX_RADIUS), 1.0E-9D);
        assertEquals(MAX_RADIUS, SpawnRing.radiusFor(1.0D, MIN_RADIUS, MAX_RADIUS), 1.0E-9D);
        assertEquals(MAX_RADIUS, SpawnRing.radiusFor(9.0D, MIN_RADIUS, MAX_RADIUS), 1.0E-9D,
                "a roll above 1 is clamped, not extrapolated");
        assertEquals(MIN_RADIUS, SpawnRing.radiusFor(-3.0D, MIN_RADIUS, MAX_RADIUS), 1.0E-9D);
        assertEquals(SpawnRing.radiusFor(0.3D, MIN_RADIUS, MAX_RADIUS),
                SpawnRing.radiusFor(0.3D, MAX_RADIUS, MIN_RADIUS), 1.0E-9D,
                "swapped bounds behave like ordered ones");
        assertEquals(0.0D, SpawnRing.radiusFor(0.0D, -5.0D, 0.0D), 1.0E-9D,
                "a negative inner radius is treated as zero");
    }
}
