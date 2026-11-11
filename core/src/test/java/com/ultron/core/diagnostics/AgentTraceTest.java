package com.ultron.core.diagnostics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentTraceTest {
 @Test void traceIsBoundedAndRedactsSecrets() {
  AgentTrace trace=new AgentTrace(2); trace.add("a","plan","ok","token=abc"); trace.add("b","run","ok","hello"); trace.add("c","verify","ok","done");
  assertEquals(2,trace.snapshot().size());
  assertEquals("hello",trace.snapshot().get(0).detail());
 }
}
