package io.demoguard.validation;

import io.demoguard.api.ReadinessStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReadinessReportTest {

    @Test
    void calculatesReadyWarningAndBlockedOutcomes() {
        assertReport(5, 100, ReadinessStatus.READY);
        assertReport(4, 80, ReadinessStatus.WARNING);
        assertReport(3, 60, ReadinessStatus.WARNING);
        assertReport(2, 40, ReadinessStatus.BLOCKED);
    }

    @Test
    void defensivelyCopiesResultLists() {
        List<String> findings = new ArrayList<>(List.of("initial"));
        ReadinessReport report = new ReadinessReport(4, 5, findings, List.of("fix it"));
        findings.add("later");

        assertEquals(List.of("initial"), report.getFindings());
        assertThrows(UnsupportedOperationException.class, () -> report.getFindings().add("mutation"));
    }

    @Test
    void rejectsImpossibleCheckCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReadinessReport(0, 0, List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ReadinessReport(6, 5, List.of(), List.of()));
    }

    private void assertReport(int passed, int expectedScore, ReadinessStatus expectedStatus) {
        ReadinessReport report = new ReadinessReport(passed, 5, List.of(), List.of());
        assertEquals(expectedScore, report.getScore());
        assertEquals(expectedStatus, report.getReadinessStatus());
        assertTrue(report.getFindings().isEmpty());
    }
}
