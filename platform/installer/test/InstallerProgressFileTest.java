/* Copyright (C) 2026 Copperbench contributors; SPDX-License-Identifier: GPL-3.0-only */
package dev.copperbench.installer;

import com.sun.nio.file.ExtendedOpenOption;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Properties;
import java.util.concurrent.*;

/** Windows regression for a scanner or reader temporarily denying replacement. */
public final class InstallerProgressFileTest {
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name").startsWith("Windows")) throw new IllegalStateException("Windows is required");
        Path directory = Files.createTempDirectory("copperbench-progress-test-");
        Path file = directory.resolve("progress.properties");
        Properties original = new Properties(); original.setProperty("done", "1");
        Properties next = new Properties(); next.setProperty("done", "2");
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try {
            InstallerMain.saveProperties(file, original);
            Future<?> publication;
            try (FileChannel lock = FileChannel.open(file, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
                publication = worker.submit(() -> {
                    try { InstallerMain.saveProperties(file, next); }
                    catch (Exception error) { throw new CompletionException(error); }
                });
                Thread.sleep(180);
                if (publication.isDone()) publication.get();
                if (!InstallerMain.loadProperties(file).getProperty("done").equals("1"))
                    throw new AssertionError("Locked progress was replaced");
            }
            publication.get(3, TimeUnit.SECONDS);
            if (!InstallerMain.loadProperties(file).getProperty("done").equals("2"))
                throw new AssertionError("The publication did not recover after the lock was released");

            long started = System.nanoTime();
            try (FileChannel lock = FileChannel.open(file, StandardOpenOption.READ, ExtendedOpenOption.NOSHARE_DELETE)) {
                try {
                    InstallerMain.saveProperties(file, original);
                    throw new AssertionError("A permanent lock must remain an explicit error");
                } catch (AccessDeniedException expected) { }
            }
            if (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) > 3000)
                throw new AssertionError("Replacement retries exceeded their bound");
            if (!InstallerMain.loadProperties(file).getProperty("done").equals("2"))
                throw new AssertionError("A failed publication changed the previous progress");
            try (var files = Files.list(directory)) {
                if (files.count() != 1) throw new AssertionError("Temporary publication file was not cleaned up");
            }
            System.out.println("PASS: transient replacement lock recovers; permanent lock is bounded and preserves the previous file");
        } finally {
            worker.shutdownNow(); worker.awaitTermination(3, TimeUnit.SECONDS);
            InstallerMain.deleteScratch(directory);
        }
    }
}
