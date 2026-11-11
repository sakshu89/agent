package com.ultron.core;

import org.junit.jupiter.api.Test;
import java.security.SecureRandom;
import static org.junit.jupiter.api.Assertions.*;

class SecureEnvelopeTest {
    @Test void authenticatedPayloadRoundTrips() throws Exception {
        byte[] key = new byte[32]; new SecureRandom().nextBytes(key);
        String envelope = SecureEnvelope.seal(key,"pairing-approved-task");
        assertEquals("pairing-approved-task",SecureEnvelope.open(key,envelope));
        byte[] wrong = new byte[32]; new SecureRandom().nextBytes(wrong);
        assertThrows(Exception.class, () -> SecureEnvelope.open(wrong,envelope));
    }
}
