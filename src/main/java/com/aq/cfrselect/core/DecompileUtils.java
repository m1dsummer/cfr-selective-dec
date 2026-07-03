package com.aq.cfrselect.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Shared utility methods for decompilation tasks.
 * Eliminates duplicate implementations across multiple classes.
 */
final class DecompileUtils {
    private DecompileUtils() {
    }

    static String toJavaEntry(String entryName) {
        return entryName.substring(0, entryName.length() - ".class".length()) + ".java";
    }

    static String toSourceJavaEntry(String entryName) {
        String withoutSuffix = entryName.substring(0, entryName.length() - ".class".length());
        int slash = withoutSuffix.lastIndexOf('/');
        String prefix = slash >= 0 ? withoutSuffix.substring(0, slash + 1) : "";
        String simpleName = slash >= 0 ? withoutSuffix.substring(slash + 1) : withoutSuffix;
        int innerMarker = simpleName.indexOf('$');
        if (innerMarker >= 0) {
            simpleName = simpleName.substring(0, innerMarker);
        }
        return prefix + simpleName + ".java";
    }

    static String toClassName(String entryName) {
        String withoutSuffix = entryName.substring(0, entryName.length() - ".class".length());
        return withoutSuffix.replace('/', '.').replace('\\', '.');
    }

    static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[32768];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
