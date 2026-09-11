package dev.riftal.creator.features.toolkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.riftal.creator.features.toolkit.take.TakeFormat;
import org.junit.jupiter.api.Test;

class TakeFormatTest {

    @Test
    void formatsRealTime() {
        assertEquals("00:00.0", TakeFormat.formatRta(0L));
        assertEquals("01:01.2", TakeFormat.formatRta(61_230L));
        assertEquals("59:59.9", TakeFormat.formatRta(3_599_990L));
        assertEquals("1:00:00.0", TakeFormat.formatRta(3_600_000L));
    }

    @Test
    void clampsNegativeDurations() {
        assertEquals("00:00.0", TakeFormat.formatRta(-5_000L));
    }

    @Test
    void padsTakeNumbers() {
        assertEquals("001", TakeFormat.takeNumber(1));
        assertEquals("042", TakeFormat.takeNumber(42));
        assertEquals("999", TakeFormat.takeNumber(999));
    }

    @Test
    void sanitizesWorldNames() {
        assertEquals("My_World__Ep_1", TakeFormat.sanitize("My World: Ep/1"));
        assertEquals("world", TakeFormat.sanitize(""));
        assertEquals("world", TakeFormat.sanitize(null));
    }

    @Test
    void writesIsoInstants() {
        assertEquals("1970-01-01T00:00:00Z", TakeFormat.isoInstant(0L));
    }
}
