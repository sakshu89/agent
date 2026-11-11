package com.ultron.core.agent;
import com.ultron.core.*;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AgentOrchestratorTest {
 static class FakePlatform implements PlatformController {
  int version=0;
  public String platformId(){return "test";}
  public List<Capability> capabilities(){return List.of();}
  public Observation observe(){return new Observation("test",""+version,Instant.now(),Map.of());}
  public ActionResult execute(PlatformAction a){version++;return new ActionResult(true,"ok",""+version);}
 }
 private AgentTool tool(FakePlatform p, boolean confirm){return new AgentTool(){
  public String id(){return "test";} public String description(){return "test";}
  public boolean requiresConfirmation(Map<String,String> a){return confirm;}
  public ActionResult execute(Map<String,String> a,Observation o){p.version++;return new ActionResult(true,"done",""+p.version);}
 };}
 @Test void executesAndVerifiesSteps(){FakePlatform p=new FakePlatform();ToolRegistry r=new ToolRegistry();r.register(tool(p,false));AgentOrchestrator a=new AgentOrchestrator(p,r,(t,args)->true,null);AgentPlan plan=new AgentPlan("two",List.of(new AgentStep("1","test","first",Map.of()),new AgentStep("2","test","second",Map.of())),2);assertTrue(a.execute(plan).success());assertEquals(2,p.version);}
 @Test void requiresConfirmation(){FakePlatform p=new FakePlatform();ToolRegistry r=new ToolRegistry();r.register(tool(p,true));AgentOrchestrator a=new AgentOrchestrator(p,r,(t,args)->false,null);assertFalse(a.execute(new AgentPlan("send",List.of(new AgentStep("1","test","send",Map.of())),1)).success());assertEquals(0,p.version);}
 @Test void duplicateStepIdsAreBlocked(){FakePlatform p=new FakePlatform();ToolRegistry r=new ToolRegistry();r.register(tool(p,false));AgentOrchestrator a=new AgentOrchestrator(p,r,(t,args)->true,null);AgentPlan plan=new AgentPlan("dup",List.of(new AgentStep("same","test","one",Map.of()),new AgentStep("same","test","two",Map.of())),2);assertFalse(a.execute(plan).success());assertEquals(1,p.version);}
}
