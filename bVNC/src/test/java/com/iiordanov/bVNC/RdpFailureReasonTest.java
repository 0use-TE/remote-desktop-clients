package com.iiordanov.bVNC;

import com.undatech.opaque.RdpFailureReason;
import org.junit.Test;
import static org.junit.Assert.*;

public class RdpFailureReasonTest {
    private long connectionError(int code) { return (long)(0x20000 | code) << 32; }
    @Test public void wrongPasswordAndAccountErrorsDoNotTriggerTransportRetries() {
        for (int code : new int[]{9,10,14,15,16,18,19,20,21,22,23,24,25,26,27})
            assertEquals(RdpFailureReason.Kind.AUTHENTICATION, RdpFailureReason.classify(connectionError(code)));
    }
    @Test public void dnsAndTransportFailuresRemainRecoverable() {
        for (int code : new int[]{0,4,5,6,8,13,17})
            assertEquals(RdpFailureReason.Kind.TRANSPORT, RdpFailureReason.classify(connectionError(code)));
    }
    @Test public void logoffAndReplacedSessionsRequireExplicitReconnect() {
        for (int info : new int[]{2,3,5,11,12})
            assertEquals(RdpFailureReason.Kind.SERVER_ENDED, RdpFailureReason.classify(info));
    }
    @Test public void serverCrashesCanRecoverAndPrivilegeFailuresStop() {
        assertEquals(RdpFailureReason.Kind.TRANSPORT, RdpFailureReason.classify(0x10));
        assertEquals(RdpFailureReason.Kind.TRANSPORT, RdpFailureReason.classify(1));
        assertEquals(RdpFailureReason.Kind.SERVER_ENDED, RdpFailureReason.classify((long)0x1000c << 32));
        assertEquals(RdpFailureReason.Kind.AUTHENTICATION, RdpFailureReason.classify(9));
    }
}
