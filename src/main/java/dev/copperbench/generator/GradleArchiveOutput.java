/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.generator;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Obtains the selected build's declared archive, never a JAR guessed from directory contents. */
final class GradleArchiveOutput {
    private GradleArchiveOutput() {}

    record Artifact(Path path, String sha256) {}

    record Request(Path root, UUID taskId, List<String> archiveTasks, Path script, Path receipt) {
        List<String> arguments(List<String> buildArguments) {
            List<String> result = new ArrayList<>(buildArguments);
            // Each export inspects live task state and writes a task-specific receipt. The init
            // action captures Project/task graph objects, so it must not enter configuration cache.
            result.add("--no-configuration-cache");
            result.add("--init-script"); result.add(script.toString());
            return List.copyOf(result);
        }

        Artifact artifact() throws IOException {
            WorkspaceExecutionSnapshot.rejectLinks(receipt);
            byte[] bytes;
            try (var input = Files.newInputStream(receipt)) { bytes = input.readNBytes(16 * 1024 + 1); }
            if (bytes.length > 16 * 1024) throw new IOException("The build artifact receipt exceeds its limit");
            try {
                JsonObject value = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!"1.0".equals(value.get("schemaVersion").getAsString())
                        || !taskId.toString().equals(value.get("taskId").getAsString())
                        || archiveTasks.stream().noneMatch(name -> (":" + name).equals(value.get("archiveTask").getAsString())))
                    throw new IOException("The artifact receipt does not identify this build's archive task");
                Path artifact = Path.of(value.get("artifactPath").getAsString()).toAbsolutePath().normalize();
                WorkspaceExecutionSnapshot.rejectLinks(artifact);
                if (!artifact.startsWith(root.resolve("build")) || !Files.isRegularFile(artifact)
                        || !artifact.getFileName().toString().endsWith(".jar"))
                    throw new IOException("The declared build archive is not a workspace build JAR");
                String digest = value.get("sha256").getAsString();
                if (!digest.matches("[a-f0-9]{64}") || !digest.equals(WorkspaceExecutionSnapshot.sha256(artifact)))
                    throw new IOException("The declared build archive changed after Gradle recorded it");
                return new Artifact(artifact, digest);
            } catch (RuntimeException invalid) { throw new IOException("The build artifact receipt is invalid", invalid); }
        }
    }

    static Request prepare(Path workspaceRoot, UUID taskId, List<String> archiveTasks) throws IOException {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        if (archiveTasks.isEmpty() || archiveTasks.stream().anyMatch(name -> !name.matches("[A-Za-z][A-Za-z0-9]*")))
            throw new IllegalArgumentException("Select declared root archive task names");
        Path directory = root.resolve(".copperbench/task-runs/export").resolve(taskId.toString());
        WorkspaceExecutionSnapshot.rejectLinks(directory);
        Files.createDirectories(directory);
        Path script = directory.resolve("archive-output.gradle"), receipt = directory.resolve("archive-output.json");
        WorkspaceExecutionSnapshot.rejectLinks(script); WorkspaceExecutionSnapshot.rejectLinks(receipt);
        String names = archiveTasks.stream().map(GradleArchiveOutput::literal).collect(java.util.stream.Collectors.joining(", "));
        String source = """
                import groovy.json.JsonOutput
                import java.security.MessageDigest
                import org.gradle.api.tasks.bundling.AbstractArchiveTask

                gradle.projectsEvaluated {
                    def project = gradle.rootProject
                    project.tasks.named('build').configure { buildTask ->
                        buildTask.doLast {
                            def archive = [%s].collect { project.tasks.findByName(it) }.find { it != null }
                            if (!(archive instanceof AbstractArchiveTask) || !archive.enabled
                                    || !gradle.taskGraph.hasTask(archive) || archive.state.failure != null || archive.state.noSource
                                    || (archive.state.skipped && !archive.state.upToDate && archive.state.skipMessage != 'FROM-CACHE')) {
                                throw new GradleException('Copperbench: the primary declared mod archive task is missing, disabled, skipped or outside this build')
                            }
                            def artifact = archive.archiveFile.get().asFile.canonicalFile
                            if (!artifact.isFile()) throw new GradleException('Copperbench: declared mod archive is missing')
                            def digest = MessageDigest.getInstance('SHA-256')
                            artifact.withInputStream { input ->
                                byte[] buffer = new byte[65536]
                                int count
                                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count)
                            }
                            def receipt = new File(%s)
                            if (receipt.exists()) throw new GradleException('Copperbench: artifact receipt already exists')
                            receipt.setText(JsonOutput.toJson([schemaVersion: '1.0', taskId: %s,
                                archiveTask: archive.path, artifactPath: artifact.path,
                                sha256: digest.digest().encodeHex().toString()]), 'UTF-8')
                        }
                    }
                }
                """.formatted(names, literal(receipt.toString()), literal(taskId.toString()));
        Files.writeString(script, source, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
        return new Request(root, taskId, List.copyOf(archiveTasks), script, receipt);
    }

    private static String literal(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
}
