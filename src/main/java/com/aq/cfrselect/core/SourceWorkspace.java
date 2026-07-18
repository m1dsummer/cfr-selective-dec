package com.aq.cfrselect.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Prepares one isolated class path for a source archive or class directory. */
final class SourceWorkspace {
    private final Path workspaceRoot;
    private final List<DecompileUnit> units;
    private final InputSource representative;
    private boolean prepared;
    private final Map<DecompileUnit, IOException> preparationFailures =
            new IdentityHashMap<DecompileUnit, IOException>();

    SourceWorkspace(Path workspaceRoot, List<DecompileUnit> units) {
        if (units.isEmpty()) {
            throw new IllegalArgumentException("Source workspace requires at least one unit");
        }
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.units = units;
        this.representative = units.get(0).primary.inputSource;
    }

    synchronized void prepare() {
        if (prepared) return;
        if (representative instanceof ZipInputSource) {
            prepareArchive((ZipInputSource) representative);
        }
        prepared = true;
    }

    synchronized IOException preparationFailure(DecompileUnit unit) {
        return preparationFailures.get(unit);
    }

    Path inputFor(DecompileUnit unit) {
        Path direct = unit.primary.inputSource.directClassFile();
        if (direct != null) {
            return direct.toAbsolutePath().normalize();
        }
        return safeWorkspaceTarget(unit.primary.entryName);
    }

    Path classPathRoot() {
        Path directRoot = representative.classPathRoot();
        return directRoot == null ? workspaceRoot : directRoot;
    }

    private void prepareArchive(ZipInputSource firstSource) {
        try {
            Files.createDirectories(workspaceRoot);
        } catch (IOException e) {
            markAllFailed(e);
            return;
        }

        ZipFile zip;
        try {
            zip = new ZipFile(firstSource.archive.toFile());
        } catch (IOException e) {
            markAllFailed(e);
            return;
        }
        try {
            for (DecompileUnit unit : units) {
                try {
                    prepareArchiveUnit(zip, firstSource, unit);
                } catch (IOException e) {
                    preparationFailures.put(unit, e);
                }
            }
        } finally {
            try {
                zip.close();
            } catch (IOException ignored) {
                // All required bytes have already been materialized.
            }
        }
    }

    private void prepareArchiveUnit(ZipFile zip, ZipInputSource firstSource,
                                    DecompileUnit unit) throws IOException {
        Map<String, String> entries = new LinkedHashMap<String, String>();
        Set<String> requiredEntries = new HashSet<String>();
        for (DecompileTask member : unit.members) {
            if (!(member.inputSource instanceof ZipInputSource)) {
                throw new IOException("Mixed input source types in " + unit.sourceKey());
            }
            ZipInputSource source = (ZipInputSource) member.inputSource;
            if (!firstSource.archive.equals(source.archive)) {
                throw new IOException("Mixed archives in " + unit.sourceKey());
            }
            entries.putIfAbsent(member.entryName, source.entryName);
            requiredEntries.add(member.entryName);
            if (member.outerEntryName != null) {
                entries.putIfAbsent(member.outerEntryName,
                        source.entryPrefix + member.outerEntryName);
            }
        }

        for (Map.Entry<String, String> item : entries.entrySet()) {
            ZipEntry entry = zip.getEntry(item.getValue());
            if (entry == null) {
                if (requiredEntries.contains(item.getKey())) {
                    throw new IOException("Missing zip entry: " + item.getValue()
                            + " in " + firstSource.archive);
                }
                continue;
            }
            Path target = safeWorkspaceTarget(item.getKey());
            if (entry.getSize() > ArchiveLimits.MAX_CLASS_BYTES) {
                throw new IOException("Class entry exceeds " + ArchiveLimits.MAX_CLASS_BYTES
                        + " bytes: " + item.getValue());
            }
            Files.createDirectories(target.getParent());
            try (InputStream in = zip.getInputStream(entry);
                 OutputStream out = Files.newOutputStream(target)) {
                ArchiveLimits.copyLimited(in, out, ArchiveLimits.MAX_CLASS_BYTES,
                        firstSource.archive + "!" + item.getValue());
            } catch (IOException e) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException cleanup) {
                    e.addSuppressed(cleanup);
                }
                throw e;
            }
        }
    }

    private void markAllFailed(IOException failure) {
        for (DecompileUnit unit : units) {
            preparationFailures.put(unit, failure);
        }
    }

    private Path safeWorkspaceTarget(String entryName) {
        Path target = workspaceRoot.resolve(entryName).toAbsolutePath().normalize();
        if (!target.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("Unsafe class entry path: " + entryName);
        }
        return target;
    }
}
