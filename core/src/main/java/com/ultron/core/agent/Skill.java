package com.ultron.core.agent;
import java.util.List;
/** Reusable workflow; every step still passes through the orchestrator's permission gate. */
public record Skill(String id,String description,List<AgentStep> steps){public Skill{if(id==null||id.isBlank())throw new IllegalArgumentException("Skill id required");description=description==null?"":description;steps=steps==null?List.of():List.copyOf(steps);if(steps.isEmpty()||steps.size()>20)throw new IllegalArgumentException("Skill needs 1..20 steps");}}
