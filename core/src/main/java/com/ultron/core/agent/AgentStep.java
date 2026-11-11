package com.ultron.core.agent;
import java.util.Map;
/** One declarative step in a cross-platform task plan. */
public record AgentStep(String id,String toolId,String objective,Map<String,String> arguments){
 public AgentStep{if(id==null||id.isBlank())throw new IllegalArgumentException("Step id required");if(toolId==null||toolId.isBlank())throw new IllegalArgumentException("Tool id required");objective=objective==null?"":objective;arguments=arguments==null?Map.of():Map.copyOf(arguments);}
}
