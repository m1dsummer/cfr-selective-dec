package com.aq.cfrselect.core;

import com.aq.cfrselect.cli.CliOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/** A top-level Java source file and all class files that belong to it. */
final class DecompileUnit {
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    final DecompileTask primary;
    final List<DecompileTask> members;
    final String sourceEntry;

    private DecompileUnit(DecompileTask primary, List<DecompileTask> members, String sourceEntry) {
        this.primary = primary;
        this.members = members;
        this.sourceEntry = sourceEntry;
    }

    static List<DecompileUnit> group(List<DecompileTask> tasks) {
        Map<String, DecompileTask> tasksByEntry = new LinkedHashMap<String, DecompileTask>();
        for (DecompileTask task : tasks) {
            tasksByEntry.put(task.entryName, task);
        }
        Map<String, List<DecompileTask>> families = new LinkedHashMap<String, List<DecompileTask>>();
        for (DecompileTask task : tasks) {
            DecompileTask root = findRoot(task, tasksByEntry);
            String sourceEntry = DecompileUtils.toJavaEntry(root.entryName);
            List<DecompileTask> members = families.get(sourceEntry);
            if (members == null) {
                members = new ArrayList<DecompileTask>();
                families.put(sourceEntry, members);
            }
            members.add(task);
        }

        List<DecompileUnit> result = new ArrayList<DecompileUnit>(families.size());
        for (Map.Entry<String, List<DecompileTask>> family : families.entrySet()) {
            List<DecompileTask> members = family.getValue();
            String primaryEntry = family.getKey().substring(0,
                    family.getKey().length() - ".java".length()) + ".class";
            DecompileTask primary = members.get(0);
            for (DecompileTask member : members) {
                member.sourceEntryName = family.getKey();
                if (primaryEntry.equals(member.entryName)) {
                    primary = member;
                }
            }
            result.add(new DecompileUnit(primary, members, family.getKey()));
        }
        return result;
    }

    private static DecompileTask findRoot(DecompileTask task,
                                          Map<String, DecompileTask> tasksByEntry) {
        DecompileTask current = task;
        Set<String> seen = new HashSet<String>();
        while (current.outerEntryName != null && seen.add(current.entryName)) {
            DecompileTask outer = tasksByEntry.get(current.outerEntryName);
            if (outer == null) break;
            current = outer;
        }
        return current;
    }

    String sourceKey() {
        return primary.inputSource.sourceKey() + "\u0000"
                + primary.outputDir.toAbsolutePath().normalize();
    }

    Path outputTarget() {
        return primary.outputDir.resolve(sourceEntry).toAbsolutePath().normalize();
    }

    int classCount() {
        return members.size();
    }

    String fingerprint(CliOptions options) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
        update(digest, "cache-schema=2\n");
        update(digest, "cfr=0.152\n");
        update(digest, "hideutf=false\n");
        update(digest, "encoding=" + options.outputEncoding + "\n");
        update(digest, "source=" + sourceEntry + "\n");

        List<DecompileTask> sortedMembers = new ArrayList<DecompileTask>(members);
        Collections.sort(sortedMembers, new Comparator<DecompileTask>() {
            @Override
            public int compare(DecompileTask a, DecompileTask b) {
                return a.entryName.compareTo(b.entryName);
            }
        });
        for (DecompileTask member : sortedMembers) {
            update(digest, member.entryName + "\n");
            update(digest, String.valueOf(member.outerEntryName) + "\n");
            update(digest, member.inputSource.fingerprint() + "\n");
        }
        byte[] bytes = digest.digest();
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            int unsigned = value & 0xff;
            result.append(HEX[unsigned >>> 4]);
            result.append(HEX[unsigned & 0x0f]);
        }
        return result.toString();
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
    }
}
