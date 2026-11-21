package com.ress.probe;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Asks a plug-in what it calls its value at a spread of normalised values, then reports the whole
 * set once every answer is in. Two names is a switch; many names that repeat between neighbouring
 * points is a list; a different name at every point is a continuous parameter.
 */
final class ValueSampler {
    private static final int POINTS = 32;
    private static final Set<String> STARTED = ConcurrentHashMap.newKeySet();

    private ValueSampler() {
    }

    static void sample(Object parameter, String name) {
        if (!STARTED.add(name)) return;
        try {
            Method text = parameter.getClass().getMethod("gNZ", double.class);
            Map<String, Double> values = new LinkedHashMap<>();
            AtomicInteger answered = new AtomicInteger();
            for (int k = 0; k < POINTS; k++) {
                double v = k / (double) (POINTS - 1);
                Object stage = text.invoke(parameter, v);
                if (!(stage instanceof CompletionStage<?> cs)) {
                    answered.incrementAndGet();
                    continue;
                }
                cs.thenAccept(answer -> {
                    synchronized (values) {
                        values.putIfAbsent(String.valueOf(answer), v);
                    }
                    if (answered.incrementAndGet() == POINTS) report(name, values);
                });
            }
        } catch (Throwable t) {
            log(name + ": sampling failed: " + t);
        }
    }

    private static void report(String name, Map<String, Double> values) {
        StringBuilder b = new StringBuilder();
        b.append('"').append(name).append("\": {");
        boolean first = true;
        for (Map.Entry<String, Double> e : values.entrySet()) {
            if (!first) b.append(", ");
            first = false;
            b.append('"').append(e.getKey().replace("\"", "\\\"")).append("\": ").append(e.getValue());
        }
        b.append('}');
        String kind = values.size() <= 1 ? "single" : values.size() <= 24 && values.size() < POINTS ? "discrete" : "continuous";
        log("values " + name + " -> " + values.size() + "/" + POINTS + " distinct (" + kind + "): " + b);
    }

    private static void log(String line) {
        try {
            Files.writeString(Path.of(System.getProperty("user.home"), "Library", "Logs", "Bitwig",
                            "remote-widget-probe.log"),
                    LocalTime.now() + " [sampler] " + line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }
}
