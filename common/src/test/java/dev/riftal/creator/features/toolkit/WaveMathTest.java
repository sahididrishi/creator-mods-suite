package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.toolkit.wave.WaveMath;
import org.junit.jupiter.api.Test;

import java.util.Random;

class WaveMathTest {

    @Test
    void ringPointsAreEquidistant() {
        int count = 24;
        double radius = 10.0D;
        double expected = 2.0D * radius * Math.sin(Math.PI / count);
        for (int i = 0; i < count; i++) {
            double a = WaveMath.ringAngle(i, count);
            double b = WaveMath.ringAngle(i + 1, count);
            double dx = WaveMath.offsetX(a, radius) - WaveMath.offsetX(b, radius);
            double dz = WaveMath.offsetZ(a, radius) - WaveMath.offsetZ(b, radius);
            assertEquals(expected, Math.sqrt(dx * dx + dz * dz), 1.0E-9D);
        }
    }

    @Test
    void ringPointsSitOnTheCircle() {
        for (int i = 0; i < 16; i++) {
            double angle = WaveMath.ringAngle(i, 16);
            double x = WaveMath.offsetX(angle, 7.5D);
            double z = WaveMath.offsetZ(angle, 7.5D);
            assertEquals(7.5D, Math.sqrt(x * x + z * z), 1.0E-9D);
        }
    }

    @Test
    void ringYawFacesTheCentre() {
        for (int i = 0; i < 24; i++) {
            double angle = WaveMath.ringAngle(i, 24);
            double x = WaveMath.offsetX(angle, 10.0D);
            double z = WaveMath.offsetZ(angle, 10.0D);
            float yaw = WaveMath.yawTowardCentre(x, z);
            // Vanilla facing vector for a yaw, with pitch 0.
            double lookX = -Math.sin(Math.toRadians(yaw));
            double lookZ = Math.cos(Math.toRadians(yaw));
            double toCentre = -x / 10.0D * lookX + -z / 10.0D * lookZ;
            assertTrue(toCentre > 0.999D, "point " + i + " faces " + toCentre + " away from the centre");
        }
    }

    @Test
    void discRadiusStaysInsideAndIsUniform() {
        Random random = new Random(1234L);
        double radius = 12.0D;
        double sum = 0.0D;
        int samples = 10_000;
        for (int i = 0; i < samples; i++) {
            double r = WaveMath.discRadius(random.nextDouble(), radius);
            assertTrue(r <= radius, "sample outside the disc: " + r);
            assertTrue(r >= 0.0D, "negative radius: " + r);
            sum += r;
        }
        // Mean radius of a uniform disc is 2r/3.
        assertEquals(2.0D * radius / 3.0D, sum / samples, 0.2D);
    }

    @Test
    void ringAngleWrapsAndSurvivesZeroCount() {
        assertEquals(WaveMath.ringAngle(0, 8), WaveMath.ringAngle(8, 8), 1.0E-12D);
        assertEquals(0.0D, WaveMath.ringAngle(3, 0), 1.0E-12D);
    }

    @Test
    void wrapDegreesMatchesVanillaRange() {
        assertEquals(0.0F, WaveMath.wrapDegrees(360.0F), 1.0E-4F);
        assertEquals(-90.0F, WaveMath.wrapDegrees(270.0F), 1.0E-4F);
        assertEquals(179.0F, WaveMath.wrapDegrees(-181.0F), 1.0E-4F);
    }

    @Test
    void modeParsesCommandLiterals() {
        assertEquals(WaveMath.Mode.RANDOM, WaveMath.Mode.parse("random"));
        assertEquals(WaveMath.Mode.RING, WaveMath.Mode.parse("ring"));
        assertEquals(WaveMath.Mode.RING, WaveMath.Mode.parse("nonsense"));
        assertEquals("random", WaveMath.Mode.RANDOM.key());
    }
}
