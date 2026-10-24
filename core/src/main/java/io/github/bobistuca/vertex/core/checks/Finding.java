package io.github.bobistuca.vertex.core.checks;

import io.github.bobistuca.vertex.core.model.Confidence;
import io.github.bobistuca.vertex.core.model.DetectionKind;
import java.util.Map;

/** Result of a check before it is bound to a player and timestamp. */
public record Finding(String checkId, DetectionKind kind, Confidence confidence, String reason,
                      Map<String, String> measurements) { }
