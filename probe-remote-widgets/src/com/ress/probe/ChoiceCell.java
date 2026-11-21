package com.ress.probe;

import bitwig.obf.uB1; // a value a widget shows and sets

/** A list of named values over a parameter, for Bitwig's own dropdown. */
final class ChoiceCell implements uB1 {
    private final Object parameter;
    private final String[] names;
    private final double[] values;

    ChoiceCell(Object parameter, String[] names, double[] values) {
        this.parameter = parameter;
        this.names = names;
        this.values = values;
    }

    @Override
    public Object getValue() {
        double current = Param.get(parameter);
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (Math.abs(values[i] - current) < Math.abs(values[best] - current)) best = i;
        }
        return names[best];
    }

    @Override
    public void setValue(Object value) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(value)) {
                Param.set(parameter, values[i]);
                return;
            }
        }
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
    public void addListener(bitwig.obf.NVD listener) {
        // prototype: the cell is redrawn when the page is rebuilt
    }

    @Override
    public void removeListener(bitwig.obf.NVD listener) {
    }
}
