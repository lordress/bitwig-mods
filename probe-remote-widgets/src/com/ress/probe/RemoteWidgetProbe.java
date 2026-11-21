package com.ress.probe;

import bml.api.Arg;
import bml.api.At;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;

import bitwig.obf.dwF; // a widget
import bitwig.obf.lIU; // an on/off value a widget shows and sets

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;

/**
 * Remote Controls: the parameter list rows and the remote cells get the widget their values call
 * for. One or two options is a toggle, three or more a selector, no options a knob.
 *
 * <p>The remote cells are built by {@code ila.IKw()}, whose instance is handed an on/off value
 * ({@code lIU}) when the parameter has one — that value is Bitwig's own, so a toggle built on it
 * needs no reflection. The list rows are built from an entry object by {@code lbk.Gef}.
 */
public final class RemoteWidgetProbe {
    private static final ThreadLocal<Object> NOTE = new ThreadLocal<>();

    private RemoteWidgetProbe() {
    }

    // ---- Remote Controls cells -------------------------------------------------------------

    @Hook(target = com.bitwig.flt.widget.core.device.ila.class, method = "Gef(Lf2c;LlIU;)V",
          at = At.ENTRY, id = "cell-value-entry")
    public static void noteValue(@This Object cell, @Arg(1) Object value) {
        if (value instanceof lIU) NOTE.set(value);
    }

    @Hook(target = com.bitwig.flt.widget.core.device.ila.class, method = "IKw()LdwF;",
          at = At.WRAP_RETURN, id = "cell-build-return")
    public static dwF cell(@Return dwF stock) {
        Object value = NOTE.get();
        NOTE.remove();
        if (value instanceof lIU onOff) {
            try {
                bitwig.obf.dBC button = bitwig.obf.dBC.Gef(onOff);
                try {
                    button.Gef(bitwig.obf.Xyf.Gef);
                } catch (Throwable ignored) {
                }
                // The cell is a label + widget composite (duF): keep its own label supplier.
                java.util.function.Supplier<?> label = labelOf(stock);
                if (label != null) {
                    dwF cell = (dwF) bitwig.obf.duF.beI(label, button);
                    log("remote cell stock=" + stock.getClass().getName()
                            + " label=" + label.getClass().getName());
                    return cell;
                }
                log("remote button stock=" + stock.getClass().getName()
                        + " -> " + button.getClass().getName() + " value=" + value.getClass().getName());
                return (dwF) button;
            } catch (Throwable t) {
                log("remote failed: " + t);
            }
        }
        return stock;
    }

    // ---- Parameter list rows ---------------------------------------------------------------

    @Hook(target = com.bitwig.flt.widget.core.device.lbk.class, method = "Gef(LWrh;)LdwF;",
          at = At.ENTRY, id = "row-note")
    public static void rowNote(@Arg(0) Object entry) {
        NOTE.set(entry);
    }

    @Hook(target = com.bitwig.flt.widget.core.device.lbk.class, method = "Gef(LWrh;)LdwF;",
          at = At.WRAP_RETURN, id = "row-return")
    public static dwF row(@Return dwF stock) {
        Object entry = NOTE.get();
        NOTE.remove();
        if (entry == null) return stock;
        try {
            int kind = kind(entry);
            String name = name(entry);
            String[] names = Table.names(name);
            if (names == null) names = stepNames(entry);
            ControlType.Widget widget = ControlType.of(names, kind);
            log("row " + widget + " '" + name + "' kind=" + kind + " names="
                    + (names == null ? "none" : names.length));
            if (widget == ControlType.Widget.KNOB) return stock;
            return widget == ControlType.Widget.SELECT ? (dwF) dropdown(entry, names) : (dwF) button(entry, name);
        } catch (Throwable t) {
            log("row failed: " + t);
            return stock;
        }
    }

    private static java.util.function.Supplier<?> labelOf(Object stock) {
        for (java.lang.reflect.Field f : stock.getClass().getDeclaredFields()) {
            if (java.util.function.Supplier.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    return (java.util.function.Supplier<?>) f.get(stock);
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static bitwig.obf.dBC button(Object entry, String name) {
        bitwig.obf.dBC button = bitwig.obf.dBC.Gef(new ToggleCell(entry));
        button.BBg(name);
        try {
            button.Gef(bitwig.obf.Xyf.Gef);
        } catch (Throwable ignored) {
        }
        return button;
    }

    private static bitwig.obf.dDu dropdown(Object entry, String[] names) {
        bitwig.obf.dDu menu = bitwig.obf.dDu.FOm();
        menu.beI(new ChoiceCell(entry, names, Table.values(names.length)), names);
        return menu;
    }

    private static int kind(Object entry) {
        try {
            Method m = entry.getClass().getMethod("aD_");
            return ((Number) m.invoke(entry)).intValue();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static String[] stepNames(Object entry) {
        try {
            Method m = entry.getClass().getMethod("fmw");
            Object v = m.invoke(entry);
            if (v instanceof String[] names && names.length > 0) return names;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String name(Object entry) {
        try {
            Method m = entry.getClass().getMethod("bMd");
            Object v = m.invoke(entry);
            if (v instanceof String s && !s.isEmpty()) return s;
        } catch (Throwable ignored) {
        }
        String text = String.valueOf(entry);
        if (!text.isEmpty() && !text.contains("@")) return text;
        return entry.getClass().getSimpleName();
    }

    static void logLine(String line) {
        log(line);
    }

    private static void log(String line) {
        try {
            Files.writeString(Path.of(System.getProperty("user.home"), "Library", "Logs", "Bitwig",
                            "remote-widget-probe.log"),
                    LocalTime.now() + " [cell] " + line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable ignored) {
        }
    }
}
