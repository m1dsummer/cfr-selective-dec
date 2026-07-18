package com.aq.cfrselect.core;

import java.io.IOException;
import java.nio.file.Path;

final class ZipInputSource implements InputSource {
    final Path archive;
    final String entryName;
    final long crc;
    final long size;
    final String containerFingerprint;
    // Prefix stripped by mapJarClassEntry (e.g. "BOOT-INF/classes/"), empty if none
    final String entryPrefix;

    ZipInputSource(Path archive, String entryName) {
        this(archive, entryName, -1L, -1L,
                archive.toAbsolutePath().normalize() + "|"
                        + archive.toFile().length() + "|" + archive.toFile().lastModified());
    }

    ZipInputSource(Path archive, String entryName, long crc, long size) {
        this(archive, entryName, crc, size,
                archive.toAbsolutePath().normalize() + "|"
                        + archive.toFile().length() + "|" + archive.toFile().lastModified());
    }

    ZipInputSource(Path archive, String entryName, long crc, long size,
                   String containerFingerprint) {
        this.archive = archive.toAbsolutePath().normalize();
        this.entryName = entryName;
        this.crc = crc;
        this.size = size;
        this.containerFingerprint = containerFingerprint;
        this.entryPrefix = entryPrefix(entryName);
    }

    private static String entryPrefix(String entryName) {
        if (entryName.startsWith("BOOT-INF/classes/")) return "BOOT-INF/classes/";
        if (entryName.startsWith("WEB-INF/classes/")) return "WEB-INF/classes/";
        return "";
    }

    @Override
    public Path directClassFile() {
        return null;
    }

    @Override
    public Path classPathRoot() {
        return null;
    }

    @Override
    public String sourceKey() {
        return "zip:" + archive;
    }

    @Override
    public String fingerprint() throws IOException {
        return "zip|" + containerFingerprint + "|" + entryName + "|" + crc + "|" + size;
    }
}
