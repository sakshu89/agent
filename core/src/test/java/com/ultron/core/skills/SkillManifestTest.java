package com.ultron.core.skills;
import com.ultron.core.agent.AgentStep;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SkillManifestTest {
 @Test void requiresVersionAndBoundedSteps() {
  var step=new AgentStep("s1","read_screen","Read screen",Map.of());
  var skill=new SkillManifest("screen.read",1,"Read current screen","local",Set.of("screen.read"),List.of(step),"");
  assertEquals(1,skill.version()); assertThrows(IllegalArgumentException.class,()->new SkillManifest("screen.read",0,"","",Set.of(),List.of(step),""));
 }
}
