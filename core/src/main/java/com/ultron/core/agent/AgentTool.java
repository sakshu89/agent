package com.ultron.core.agent;
import com.ultron.core.ActionResult;
import com.ultron.core.Observation;
import java.util.Map;
/** A named, permission-scoped operation exposed to the shared agent brain. */
public interface AgentTool {
 String id(); String description();
 boolean requiresConfirmation(Map<String,String> arguments);
 ActionResult execute(Map<String,String> arguments, Observation observation) throws Exception;
}
