package com.iiordanov.bVNC;
import com.undatech.opaque.ReconnectBackoff;
import org.junit.Test;
import static org.junit.Assert.*;
public class ReconnectBackoffTest {
    @Test public void initialBadAddressDoesNotRetryForever() {
        assertEquals(-1, new ReconnectBackoff().nextDelayMillis());
    }
    @Test public void retriesAreBoundedAndSuccessResetsDelay() {
        ReconnectBackoff policy = new ReconnectBackoff(); policy.connected();
        for (int expected : new int[]{1000, 2000, 4000, 8000, 16000, 30000, 30000, 30000}) assertEquals(expected, policy.nextDelayMillis());
        assertEquals(-1, policy.nextDelayMillis());
        policy.connected(); assertEquals(1000, policy.nextDelayMillis());
    }
    @Test public void explicitDisconnectStopsRetriesAndManualRetryStartsFresh() {
        ReconnectBackoff policy = new ReconnectBackoff(); policy.connected(); policy.stop();
        assertEquals(-1, policy.nextDelayMillis());
        policy.restart(); assertEquals(1000, policy.nextDelayMillis());
    }
}
