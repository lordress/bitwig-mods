package com.ress.probe;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads and writes a parameter's normalised value through whichever accessor its class has. */
final class Param {
    private static final String[] GETTERS = {"Heb", "Bqk", "aT_"};
    private static final String[] SETTERS = {"Bqk", "byf", "c_", "MgQ"};
    private static final Map<String, Method> G = new ConcurrentHashMap<>();
    private static final Map<String, Method> S = new ConcurrentHashMap<>();

    private Param() {
    }

    static double get(Object parameter) {
        Method m = G.computeIfAbsent(parameter.getClass().getName() + "#get", k -> find(parameter, GETTERS, double.class));
        try {
            return m == null ? 0 : ((Number) m.invoke(parameter)).doubleValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    static void set(Object parameter, double value) {
        Method m = S.computeIfAbsent(parameter.getClass().getName() + "#set", k -> find(parameter, SETTERS, double.class));
        try {
            if (m != null) m.invoke(parameter, value);
        } catch (Throwable t) {
            RemoteWidgetProbe.logLine("set failed: " + t);
        }
    }

    private static Method find(Object parameter, String[] names, Class<?> arg) {
        for (String name : names) {
            try {
                Method m = parameter.getClass().getMethod(name, arg);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored) {
                // try the next name
            }
        }
        return null;
    }
}
