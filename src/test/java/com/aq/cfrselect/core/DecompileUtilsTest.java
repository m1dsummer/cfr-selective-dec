package com.aq.cfrselect.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DecompileUtilsTest {
    @Test
    public void sourceJavaEntryUsesTopLevelClassForInnerClasses() {
        assertEquals("com/acme/Outer.java",
                DecompileUtils.toSourceJavaEntry("com/acme/Outer$Inner.class"));
        assertEquals("com/acme/Outer.java",
                DecompileUtils.toSourceJavaEntry("com/acme/Outer$1.class"));
        assertEquals("com/acme/Outer.java",
                DecompileUtils.toSourceJavaEntry("com/acme/Outer$Inner$Nested.class"));
    }

    @Test
    public void sourceJavaEntryKeepsTopLevelClassName() {
        assertEquals("com/acme/App.java",
                DecompileUtils.toSourceJavaEntry("com/acme/App.class"));
    }
}
