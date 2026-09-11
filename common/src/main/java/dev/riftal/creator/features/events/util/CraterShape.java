package dev.riftal.creator.features.events.util;

import java.util.ArrayList;
import java.util.List;

/**
 * The meteor crater, as pure integer geometry so it can be unit tested without a level.
 *
 * <p>An offset is "inside" when it is within {@code radius} of the centre, and "rim" when it is
 * inside but within one block of the surface of that sphere. The caller turns inside cells into air
 * and rim cells into scorched stone.
 */
public final class CraterShape {

    /** One block offset from the crater centre. */
    public record Offset(int x, int y, int z) {

        /** Squared distance from the crater centre. */
        public int distanceSq() {
            return x * x + y * y + z * z;
        }
    }

    /**
     * Every offset strictly inside the crater sphere.
     *
     * @param radius crater radius in blocks, clamped to 1..16
     */
    public static List<Offset> inside(int radius) {
        int r = clampRadius(radius);
        int rSq = r * r;
        List<Offset> out = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    if (x * x + y * y + z * z <= rSq) {
                        out.add(new Offset(x, y, z));
                    }
                }
            }
        }
        return out;
    }

    /**
     * The shell of the crater - the cells that get scorched stone rather than air.
     *
     * @param radius crater radius in blocks, clamped to 1..16
     */
    public static List<Offset> rim(int radius) {
        int r = clampRadius(radius);
        int outer = r * r;
        int inner = (r - 1) * (r - 1);
        List<Offset> out = new ArrayList<>();
        for (Offset offset : inside(r)) {
            int d = offset.distanceSq();
            if (d > inner && d <= outer) {
                out.add(offset);
            }
        }
        return out;
    }

    /** How many blocks the crater carves. Cheap enough to assert on in a test. */
    public static int insideCount(int radius) {
        return inside(radius).size();
    }

    private static int clampRadius(int radius) {
        return radius < 1 ? 1 : Math.min(radius, 16);
    }

    private CraterShape() {
    }
}
