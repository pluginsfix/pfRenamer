package pluginsfix.pfrenamer.domain;

import java.util.Objects;

public record ReplacementRule(String target, String replacement) {
    public ReplacementRule {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(replacement, "replacement cannot be null");
        if (target.isEmpty()) {
            throw new IllegalArgumentException("target cannot be empty");
        }
    }
}
