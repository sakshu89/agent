package com.ultron.core.agent;
import java.util.*; import java.util.concurrent.ConcurrentHashMap;
/** Thread-safe capability registry. Duplicate IDs are rejected to avoid ambiguous dispatch. */
public final class ToolRegistry {
 private final Map<String,AgentTool> tools=new ConcurrentHashMap<>();
 public void register(AgentTool tool){Objects.requireNonNull(tool); if(tool.id()==null||tool.id().isBlank())throw new IllegalArgumentException("Tool id required"); if(tools.putIfAbsent(tool.id(),tool)!=null)throw new IllegalStateException("Tool already registered: "+tool.id());}
 public Optional<AgentTool> find(String id){return Optional.ofNullable(tools.get(id));}
 public List<AgentTool> all(){return tools.values().stream().sorted(Comparator.comparing(AgentTool::id)).toList();}
 public String describe(){return all().stream().map(t->t.id()+": "+t.description()).reduce("",(a,b)->a+b+"\n");}
}
