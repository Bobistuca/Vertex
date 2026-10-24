package io.github.bobistuca.vertex.core.config;

import java.util.List;

/** Thrown when a configuration fails validation. Carries every problem found. */
public final class ConfigException extends Exception {
    private static final long serialVersionUID = 1L;

    private final transient List<String> problems;

    public ConfigException(List<String> problems) {
        super(String.join("; ", problems));
        this.problems = List.copyOf(problems);
    }

    public List<String> problems() {
        return problems;
    }
}
