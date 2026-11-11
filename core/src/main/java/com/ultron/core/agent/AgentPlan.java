package com.ultron.core.agent;
import java.util.List;
/** Immutable bounded plan. */
public record AgentPlan(String goal,List<AgentStep> steps,int maxSteps){
 public AgentPlan{goal=goal==null?"":goal.trim();steps=steps==null?List.of():List.copyOf(steps);if(steps.size()>20)throw new IllegalArgumentException("Plans limited to 20 steps");if(maxSteps<1||maxSteps>20||steps.size()>maxSteps)throw new IllegalArgumentException("Invalid maxSteps");}
}
