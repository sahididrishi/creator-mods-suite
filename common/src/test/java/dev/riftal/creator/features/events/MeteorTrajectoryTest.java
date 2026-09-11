package dev.riftal.creator.features.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.riftal.creator.features.events.events.MeteorEvent;
import dev.riftal.creator.features.events.util.CraterShape;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The straight-line approach and the crater it leaves. Plan tests 11 and 12.
 */
class MeteorTrajectoryTest {

    private static final Vec3 TARGET = new Vec3(100.0D, 64.0D, -40.0D);

    @Test
    void velocityPointsAtTheTargetAtTheDeclaredSpeed() {
        Vec3 start = TARGET.add(MeteorEvent.SPAWN_OFFSET);
        MeteorEvent.MeteorSpawn spawn = MeteorEvent.MeteorSpawn.of(start, TARGET);

        assertEquals(MeteorEvent.SPEED, spawn.velocity().length(), 1.0E-9D,
                "the boulder always moves at exactly SPEED blocks per tick");
        Vec3 wanted = TARGET.subtract(start).normalize();
        Vec3 actual = spawn.velocity().normalize();
        assertEquals(wanted.x, actual.x, 1.0E-9D);
        assertEquals(wanted.y, actual.y, 1.0E-9D);
        assertEquals(wanted.z, actual.z, 1.0E-9D);
        assertTrue(spawn.velocity().y < 0.0D, "the boulder comes down, never up");
    }

    @Test
    void simulatedFlightArrivesInsideTheFlightPhase() {
        Vec3 start = TARGET.add(MeteorEvent.SPAWN_OFFSET);
        MeteorEvent.MeteorSpawn spawn = MeteorEvent.MeteorSpawn.of(start, TARGET);

        // Exactly what MeteorEntity#tick does: add the velocity once per tick, and exactly what
        // MeteorEvent#hasArrived checks: the target height.
        Vec3 position = start;
        int ticks = 0;
        while (position.y > TARGET.y && ticks < 1000) {
            position = position.add(spawn.velocity());
            ticks++;
        }

        assertEquals(spawn.travelTicks(), ticks,
                "travelTicks() must predict the simulated flight exactly");
        assertTrue(ticks <= MeteorEvent.FLIGHT_TICKS,
                "the boulder arrived on tick " + ticks + " but the flight phase is only "
                        + MeteorEvent.FLIGHT_TICKS + " ticks");
        assertTrue(position.distanceTo(TARGET) < 2.0D,
                "arrived " + position.distanceTo(TARGET) + " blocks from the aim point");
    }

    @Test
    void aDegenerateAimPointStillFallsStraightDown() {
        MeteorEvent.MeteorSpawn spawn = MeteorEvent.MeteorSpawn.of(TARGET, TARGET);
        assertEquals(0.0D, spawn.velocity().x, 1.0E-9D);
        assertEquals(-MeteorEvent.SPEED, spawn.velocity().y, 1.0E-9D);
        assertEquals(0.0D, spawn.velocity().z, 1.0E-9D);
    }

    @Test
    void craterSphereIsTheRightSizeAndItsRimIsTheOuterShell() {
        int radius = 5;
        List<CraterShape.Offset> inside = CraterShape.inside(radius);
        // 4/3 * pi * 5^3 = 523.6 continuous; the integer lattice lands a little under that.
        assertTrue(inside.size() > 480 && inside.size() < 560,
                "r=5 crater had " + inside.size() + " cells");
        assertEquals(inside.size(), CraterShape.insideCount(radius));

        Set<CraterShape.Offset> interior = new HashSet<>(inside);
        for (CraterShape.Offset offset : CraterShape.rim(radius)) {
            assertTrue(interior.contains(offset), "every rim cell is also an interior cell");
            assertTrue(offset.distanceSq() > 16, "rim cells sit outside r-1");
            assertTrue(offset.distanceSq() <= 25, "rim cells sit inside r");
        }
        assertTrue(CraterShape.rim(radius).size() < inside.size(), "the rim is a strict subset");
    }

    @Test
    void craterRadiusIsClampedSoATypoCannotEatTheWorld() {
        assertEquals(CraterShape.insideCount(1), CraterShape.insideCount(0));
        assertEquals(CraterShape.insideCount(1), CraterShape.insideCount(-9));
        assertEquals(CraterShape.insideCount(16), CraterShape.insideCount(400));
        assertTrue(CraterShape.insideCount(5) < CraterShape.insideCount(6),
                "a bigger radius carves more blocks");
    }

    @Test
    void everyCraterCellIsDistinct() {
        List<CraterShape.Offset> inside = CraterShape.inside(4);
        assertEquals(inside.size(), new HashSet<>(inside).size(),
                "the carve must not visit the same block twice");
    }
}
