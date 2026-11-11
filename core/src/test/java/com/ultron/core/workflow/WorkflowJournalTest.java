package com.ultron.core.workflow;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class WorkflowJournalTest {
 @Test void roundTripsAndMarksInterruptedWorkForReview() throws Exception {
  Path p=Files.createTempFile("ultron-journal", ".log");
  try { WorkflowJournal j=new WorkflowJournal(p);
   j.append(new WorkflowJournal.Event("task-1","step-1",WorkflowJournal.State.RUNNING,Instant.parse("2026-01-01T00:00:00Z"),"token=secret"));
   assertEquals("token=[REDACTED]",j.readAll().get(0).detail());
   assertEquals(WorkflowJournal.State.RECOVERY_REQUIRED,j.recoverySnapshot().get("task-1/step-1"));
  } finally { Files.deleteIfExists(p); }
 }
 @Test void ignoresTornTail() throws Exception {
  Path p=Files.createTempFile("ultron-journal", ".log");
  try { Files.writeString(p,"broken-tail"); assertTrue(new WorkflowJournal(p).readAll().isEmpty()); }
  finally { Files.deleteIfExists(p); }
 }
}
