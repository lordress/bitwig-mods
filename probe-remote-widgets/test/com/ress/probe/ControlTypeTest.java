package com.ress.probe;

import java.util.ArrayList;
import java.util.List;

/** The cardinality rules, checked without Bitwig. */
public final class ControlTypeTest {
    private static final List<String> FAILURES = new ArrayList<>();

    public static void main(String[] args) {
        check("no names, plugin param", null, 8, ControlType.Widget.KNOB);
        check("no names, continuous", null, 5, ControlType.Widget.KNOB);
        check("no names, bitwig switch", null, 4, ControlType.Widget.BUTTON);
        check("two names", new String[]{"Off", "On"}, 8, ControlType.Widget.BUTTON);
        check("one name", new String[]{"Off"}, 8, ControlType.Widget.BUTTON);
        check("twenty names", Table.names("Mode"), 8, ControlType.Widget.SELECT);
        check("three names", new String[]{"Low", "Mid", "High"}, 5, ControlType.Widget.SELECT);
        check("numbers as names", new String[]{"0", "1", "2"}, 8, ControlType.Widget.SELECT);
        if (FAILURES.isEmpty()) System.out.println("CONTROLTYPE_OK 8 checks");
        else { FAILURES.forEach(System.out::println); System.exit(1); }
    }

    private static void check(String label, String[] names, int kind, ControlType.Widget expected) {
        ControlType.Widget got = ControlType.of(names, kind);
        if (got != expected) FAILURES.add(label + ": expected " + expected + " got " + got);
    }
}
