package com.ultron.core.skills;

import com.ultron.core.agent.AgentStep;
import java.util.*;

/** Versioned skill metadata. A skill is declarative; execution still uses the normal permission/verification path. */
public record SkillManifest(String id, int version, String description, String author,
                            Set<String> requiredCapabilities, List<AgentStep> steps, String contentHash) {
    public SkillManifest {
        if (id == null || !id.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{1,79}")) throw new IllegalArgumentException("Invalid skill id");
        if (version < 1) throw new IllegalArgumentException("Skill version must be positive");
        description = Objects.toString(description, ""); author = Objects.toString(author, "local");
        requiredCapabilities = requiredCapabilities == null ? Set.of() : Set.copyOf(requiredCapabilities);
        steps = steps == null ? List.of() : List.copyOf(steps);
        if (steps.isEmpty() || steps.size() > 20) throw new IllegalArgumentException("Skills require 1..20 steps");
        contentHash = Objects.toString(contentHash, "");
    }
}
