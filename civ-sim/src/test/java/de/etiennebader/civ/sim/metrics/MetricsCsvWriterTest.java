package de.etiennebader.civ.sim.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetricsCsvWriterTest {

    @Test
    @DisplayName("Kopfzeile, Werte mit Punkt als Dezimaltrenner und Zeilenende \\n")
    void writesHeaderAndRows() {
        StringWriter out = new StringWriter();
        MetricsCsvWriter writer = new MetricsCsvWriter(out);

        writer.accept(new DailyMetrics(1, 20, 2, 15, 5, 3, 10, 1, 12, 8, 1, 1000, 900, 100, 50, 30, 5,
                2.5, 0.66666, 4, 77));

        String[] lines = out.toString().split("\n", -1);
        assertEquals(3, lines.length);
        assertEquals(String.join(",", DailyMetrics.COLUMNS), lines[0]);
        assertEquals("1,20,2,15,5,3,10,1,12,8,1,1000,900,100,50,30,5,2.5000,0.6667,4,77", lines[1]);
        assertEquals("", lines[2]);
    }
}
