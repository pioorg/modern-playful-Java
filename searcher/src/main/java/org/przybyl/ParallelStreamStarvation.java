/*
 * Copyright 2025 Piotr Przybył
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.przybyl;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Goal:
 * 1. Kick off one parallel stream that launches *long-running, CPU-bound* tasks.
 * That stream occupies every worker in the common ForkJoinPool.
 * 2. While it is still running, start a second parallel stream.
 * Because the pool is already saturated, the second stream falls back to
 * the calling thread (main) and effectively runs **sequentially**,
 * until work-staling allows gradually the usage goes equal.
 */
public class ParallelStreamStarvation {

    private static long cpuHeavy(long durationMillis) {
        long start = System.currentTimeMillis();
        long end = start + durationMillis;
        while (System.currentTimeMillis() < end) {
            Math.sin(Math.random()); // pointless work, this is a sinful demo in general
        }
        return System.currentTimeMillis() - start;
    }

    private static void log(String label, int id, String phase, long elapsed) {
        String thread = Thread.currentThread().getName();
        if (phase.equals("START")) {
            System.out.printf("%s %02d %s on %s%n", label, id, phase, thread);
        } else {
            System.out.printf("%s %02d %s on %s (%.3f s)%n", label, id, phase, thread, elapsed / 1_000.0);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        // feel free to change these and observe the results ;-)
        int taskDurationMillis = 10_000;
        int cpus = Runtime.getRuntime().availableProcessors();
        int secondStreamElements = cpus / 2;
        long startTime = System.nanoTime();
        System.out.printf("Detected %d cores; commonPool parallelism = %d%n", cpus, ForkJoinPool.commonPool().getParallelism());

        /* ---------------- 1) FIRST parallel stream (hogs the pool) ---------------- */
        List<Integer> firstBatch = IntStream.range(0, cpus * 2).boxed().collect(Collectors.toList());

        Thread longStreamThread = new Thread(() -> {
            firstBatch.parallelStream().forEach(i -> {
                log("FIRST ", i, "START", 0);
                long dur = cpuHeavy(taskDurationMillis);
                log("FIRST ", i, "END  ", dur);
            });
        }, "long-stream-runner");

        longStreamThread.setDaemon(true);
        // UNCOMMENT THIS TO SEE THE MAGIC HAPPEN
        // longStreamThread.start();   // kick off the hijacker
        Thread.sleep(200);          // let it capture every worker

        /* ---------------- 2) SECOND parallel stream (starved) --------------------- */
        List<Integer> secondBatch = IntStream.range(0, secondStreamElements).boxed().collect(Collectors.toList());

        long t0 = System.currentTimeMillis();
        System.out.println("\n--- starting SECOND stream ---");

        secondBatch.parallelStream().forEach(i -> {
            log("SECOND", i, "START", 0);
            long dur = cpuHeavy(taskDurationMillis);
            log("SECOND", i, "END  ", dur);
        });

        System.out.printf("--- SECOND stream finished in %.3f s ---\n", (System.currentTimeMillis() - t0) / 1_000.0);
        long end = System.nanoTime();

        System.out.printf("We had %d CPU cores to run %d tasks (each taking %d milliseconds) using a parallel stream, yet the whole program run for about %d seconds%n", cpus, secondStreamElements, taskDurationMillis, (end - startTime) / 1_000_000_000L);
    }
}