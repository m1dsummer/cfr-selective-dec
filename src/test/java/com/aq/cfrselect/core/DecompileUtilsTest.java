package com.aq.cfrselect.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DecompileUtilsTest {
    @Test
    public void javaEntryPreservesDollarForTopLevelClassNames() {
        assertEquals("com/acme/Outer$Inner.java",
                DecompileUtils.toJavaEntry("com/acme/Outer$Inner.class"));
    }

    @Test
    public void javaEntryKeepsTopLevelClassName() {
        assertEquals("com/acme/App.java", DecompileUtils.toJavaEntry("com/acme/App.class"));
    }
}
