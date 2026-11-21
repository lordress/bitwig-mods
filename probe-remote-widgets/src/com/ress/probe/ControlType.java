package com.ress.probe;

/**
 * Which widget a parameter should get, from what we can actually read about it.
 *
 * <p>Bitwig normalises every parameter to 0..1 and does not expose its display range, so the
 * "continuous bounds" test of a content-based heuristic has no input here: continuity is the
 * absence of a name list together with the parameter's kind code (5 continuous, 8 plug-in). What
 * remains is the cardinality rule, which is the one that matters: one or two options is a button,
 * three or more is a selector, and no options at all is a knob unless the parameter is one of
 * Bitwig's own discrete switches (kind 4).
 */
final class ControlType {
    enum Widget { BUTTON, KNOB, SELECT }

    private ControlType() {
    }

    /**
     * @param names the parameter's option names, or null when it publishes none
     * @param kind  4 is a Bitwig discrete switch, 5 continuous, 8 a plug-in parameter
     */
    static Widget of(String[] names, int kind) {
        if (names != null && names.length > 0) {
            return names.length <= 2 ? Widget.BUTTON : Widget.SELECT;
        }
        if (kind == 4) return Widget.BUTTON;
        return Widget.KNOB;
    }
}
