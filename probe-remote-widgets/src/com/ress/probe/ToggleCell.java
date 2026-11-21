package com.ress.probe;

import bitwig.obf.lIU; // an on/off value a widget shows and sets

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** An on/off value over a Bitwig parameter, for Bitwig's own toggle button. */
final class ToggleCell implements lIU {
    private static final Map<String, Method> GET = new ConcurrentHashMap<>();
    private static final Map<String, Method> SET = new ConcurrentHashMap<>();

    private final Object parameter;

    ToggleCell(Object parameter) {
        this.parameter = parameter;
    }

    Object parameter() {
        return parameter;
    }

    @Override
    public boolean getBoolValue() {
        return normalized() >= 0.5;
    }

    @Override
    public void setBoolValue(boolean on) {
        try {
            setter().invoke(parameter, on ? 1.0 : 0.0);
        } catch (Throwable t) {
            System.out.println("[toggle-cell] set failed: " + t);
        }
    }

    // ---- the rest of the value interface -------------------------------------------------

    @Override
    public Object getValue() {
        return getBoolValue();
    }

    @Override
    public void setValue(Object value) {
        if (value instanceof Boolean on) setBoolValue(on);
    }

    @Override
    public boolean isSettable() {
        return true;
    }

    @Override
    public boolean isObservable() {
        return true;
    }

    @Override
    public boolean canValueChangeAtRuntime() {
        return true;
    }

    @Override
    public bitwig.obf.lIU sCA(Object ignored) {
        return this;
    }

    @Override
    public bitwig.obf.lIU MgQ(Object ignored) {
        return this;
    }

    @Override
    public void addListener(bitwig.obf.NVD listener) {
        // prototype: the cell is rebuilt when the page is rebuilt; no live refresh yet
    }

    @Override
    public void removeListener(bitwig.obf.NVD listener) {
    }

    double normalized() {
        try {
            return ((Number) getter().invoke(parameter)).doubleValue();
        } catch (Throwable t) {
            return 0;
        }
    }

    private Method getter() {
        return GET.computeIfAbsent(parameter.getClass().getName(), k -> find(parameter.getClass(), "Heb"));
    }

    private Method setter() {
        return SET.computeIfAbsent(parameter.getClass().getName(), k -> find(parameter.getClass(), "Bqk", double.class));
    }

    private static Method find(Class<?> type, String name, Class<?>... args) {
        try {
            Method m = type.getMethod(name, args);
            m.setAccessible(true);
            return m;
        } catch (Throwable t) {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args.length) return m;
            }
            throw new IllegalStateException(name + " on " + type.getName(), t);
        }
    }
}
