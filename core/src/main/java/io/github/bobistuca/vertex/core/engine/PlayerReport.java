package io.github.bobistuca.vertex.core.engine;

import java.util.Map;

/** Read-only detection state for the inspect command. */
public record PlayerReport(Map<String, Long> incidentsByCheck, long lastIncidentWallMillis, long analyzedWindows,
                           long skippedWindows, String lastWindowSummary) { }
