package com.aq.cfrselect.core;

import com.aq.cfrselect.cli.CliOptions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SelectiveDecompilerExecutorIntegrationTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    private int compilationSequence;

    @Test
    public void isolatesSameClassNameFromDifferentArchives() throws Exception {
        File input = temp.newFolder("input");
        compileJar(new File(input, "a.jar"), "Duplicate",
                "package com.acme; public class Duplicate { public String value() { return \"A_ONLY\"; } }");
        compileJar(new File(input, "b.jar"), "Duplicate",
                "package com.acme; public class Duplicate { public String value() { return \"B_ONLY\"; } }");
        File output = new File(temp.getRoot(), "output");
        Path staleOutput = output.toPath().resolve("input/a/com/acme/Duplicate.java");
        Files.createDirectories(staleOutput.getParent());
        Files.write(staleOutput, "STALE_OUTPUT".getBytes(StandardCharsets.UTF_8));

        int exitCode = run(input, output);

        assertEquals(0, exitCode);
        String aSource = read(output.toPath().resolve("input/a/com/acme/Duplicate.java"));
        String bSource = read(output.toPath().resolve("input/b/com/acme/Duplicate.java"));
        assertTrue(aSource.contains("A_ONLY"));
        assertFalse(aSource.contains("STALE_OUTPUT"));
        assertFalse(aSource.contains("B_ONLY"));
        assertTrue(bSource.contains("B_ONLY"));
        assertFalse(bSource.contains("A_ONLY"));

        List<String> summary = Files.readAllLines(output.toPath().resolve("summary.txt"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("source_units=2"));
        assertTrue(summary.contains("queue_tasks=2"));

        assertEquals(0, run(input, output));
        summary = Files.readAllLines(output.toPath().resolve("summary.txt"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("queue_tasks=0"));
        assertTrue(summary.contains("cache_hits=2"));

        compileJar(new File(input, "a.jar"), "Duplicate",
                "package com.acme; public class Duplicate { "
                        + "public String value() { return \"A_CHANGED\"; } }");
        assertEquals(0, run(input, output));
        aSource = read(output.toPath().resolve("input/a/com/acme/Duplicate.java"));
        bSource = read(output.toPath().resolve("input/b/com/acme/Duplicate.java"));
        assertTrue(aSource.contains("A_CHANGED"));
        assertTrue(bSource.contains("B_ONLY"));
        summary = Files.readAllLines(output.toPath().resolve("summary.txt"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("queue_tasks=1"));
        assertTrue(summary.contains("cache_hits=1"));
    }

    @Test
    public void decompilesOuterAndInnerClassesAsOneSourceUnit() throws Exception {
        File input = temp.newFolder("inner-input");
        compileJar(new File(input, "outer.jar"), "Outer",
                "package com.acme; public class Outer { "
                        + "public static class Inner { public String value() { return \"INNER_ONLY\"; } } }");
        File output = new File(temp.getRoot(), "inner-output");

        int exitCode = run(input, output);

        assertEquals(0, exitCode);
        String source = read(output.toPath().resolve("inner-input/outer/com/acme/Outer.java"));
        assertTrue(source.contains("class Inner"));
        assertTrue(source.contains("INNER_ONLY"));
        List<String> summary = Files.readAllLines(output.toPath().resolve("summary.txt"), StandardCharsets.UTF_8);
        assertTrue(summary.contains("source_units=1"));
        assertTrue(summary.contains("success=2"));
        assertTrue(summary.contains("queue_tasks=1"));
    }

    @Test
    public void keepsTopLevelDollarClassSeparateFromSimilarlyNamedClass() throws Exception {
        File input = temp.newFolder("dollar-input");
        compileJar(new File(input, "dollar.jar"),
                new String[] { "Foo", "Foo$Bar" },
                new String[] {
                        "package com.acme; public class Foo { "
                                + "public String value() { return \"FOO_ONLY\"; } }",
                        "package com.acme; public class Foo$Bar { "
                                + "public String value() { return \"DOLLAR_ONLY\"; } }"
                });
        File output = new File(temp.getRoot(), "dollar-output");

        assertEquals(0, run(input, output));

        Path sourceRoot = output.toPath().resolve("dollar-input/dollar/com/acme");
        assertTrue(read(sourceRoot.resolve("Foo.java")).contains("FOO_ONLY"));
        assertTrue(read(sourceRoot.resolve("Foo$Bar.java")).contains("DOLLAR_ONLY"));
        List<String> summary = Files.readAllLines(output.toPath().resolve("summary.txt"),
                StandardCharsets.UTF_8);
        assertTrue(summary.contains("source_units=2"));
        assertTrue(summary.contains("success=2"));
    }

    private int run(File input, File output) throws Exception {
        CliOptions options = CliOptions.parse(new String[] {
                "--input", input.getAbsolutePath(),
                "--output", output.getAbsolutePath(),
                "--packages", "com.acme",
                "--threads", "2"
        });
        return new SelectiveDecompiler(options).run();
    }

    private void compileJar(File jarFile, String className, String source) throws Exception {
        compileJar(jarFile, new String[] { className }, new String[] { source });
    }

    private void compileJar(File jarFile, String[] classNames, String[] sources) throws Exception {
        int sequence = ++compilationSequence;
        File sourceRoot = temp.newFolder("source-" + sequence + "-" + jarFile.getName());
        File classes = temp.newFolder("classes-" + sequence + "-" + jarFile.getName());
        String[] compilerArguments = new String[4 + classNames.length];
        compilerArguments[0] = "-encoding";
        compilerArguments[1] = "UTF-8";
        compilerArguments[2] = "-d";
        compilerArguments[3] = classes.getAbsolutePath();
        for (int i = 0; i < classNames.length; i++) {
            Path javaFile = sourceRoot.toPath().resolve("com/acme/" + classNames[i] + ".java");
            Files.createDirectories(javaFile.getParent());
            Files.write(javaFile, sources[i].getBytes(StandardCharsets.UTF_8));
            compilerArguments[4 + i] = javaFile.toString();
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull("Tests require a JDK, not a JRE", compiler);
        assertEquals(0, compiler.run(null, null, null, compilerArguments));

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(jarFile));
             Stream<Path> files = Files.walk(classes.toPath())) {
            for (Path file : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                String entryName = classes.toPath().relativize(file).toString().replace('\\', '/');
                zip.putNextEntry(new ZipEntry(entryName));
                Files.copy(file, zip);
                zip.closeEntry();
            }
        }
    }

    private String read(Path file) throws Exception {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }
}
