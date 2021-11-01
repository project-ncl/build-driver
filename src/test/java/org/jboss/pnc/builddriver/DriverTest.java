package org.jboss.pnc.builddriver;

import org.jboss.pnc.buildagent.api.Status;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DriverTest {

    @Test
    void overrideStatusFromLogsWhenIndyConnectionRefused() {
        // change to SYSTEM_ERROR
        StringBuilder builder = new StringBuilder();
        builder.append(
                "Connect to indy.test.svc.cluster.local:80 [indy.test.svc.cluster.local] failed: Connection refused (Connection refused)");
        Status updated = Driver.overrideStatusFromLogs(builder, Status.FAILED);
        assertSame(Status.SYSTEM_ERROR, updated);

        StringBuilder another = new StringBuilder();
        another.append(
                "Connect to indy.example.com:443 [indy.example.com] failed: Connection refused (Connection refused)");
        updated = Driver.overrideStatusFromLogs(builder, Status.FAILED);
        assertSame(Status.SYSTEM_ERROR, updated);
    }

    /**
     * If the status is not set to FAILED, then Driver.overrideStatusFromLogs shouldn't change the status
     */
    @Test
    void noOverrideStatusFromLogsForIndyWhenStatusNotFailed() {
        StringBuilder builder = new StringBuilder();
        builder.append(
                "Connect to indy.test.svc.cluster.local:80 [indy.test.svc.cluster.local] failed: Connection refused (Connection refused)");
        Status updated = Driver.overrideStatusFromLogs(builder, Status.COMPLETED);
        assertSame(Status.COMPLETED, updated);
    }

    @Test
    void noOverrideStatusFromLogsWhenNoMatchForIndy() {
        StringBuilder builder = new StringBuilder();
        builder.append("Everything went fine. Except all that 5G waves");
        Status updated = Driver.overrideStatusFromLogs(builder, Status.FAILED);
        assertSame(Status.FAILED, updated);
    }
}