package com.aq.cfrselect.core;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class ArchiveLimitsTest {
    @Test(expected = IOException.class)
    public void limitedCopyStopsAfterBudgetIsExceeded() throws Exception {
        ArchiveLimits.copyLimited(new ByteArrayInputStream(new byte[] { 1, 2, 3, 4 }),
                new ByteArrayOutputStream(), 3L, "test-entry");
    }
}
