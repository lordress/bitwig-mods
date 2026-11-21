package com.ress.probe;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Named value sets for plug-ins that publish no steps, keyed by parameter name. Values are
 * normalised (0 to 1) and spread evenly over the list.
 */
final class Table {
    private static final Map<String, String[]> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("Clear", new String[]{"Off", "On"});
        NAMES.put("Mode", new String[]{
                "Andromeda", "Hydra", "Sagittarius", "Gemini", "Lyra", "Capricorn", "Centaurus",
                "Great Annihilator", "Triangulum", "Large Magellanic Cloud", "Cirrus Major",
                "Cirrus Minor", "Cassiopeia", "Orion", "Aquarius", "Pisces", "Scorpio", "Libra",
                "Leo", "Virgo"});
    }

    private Table() {
    }

    static String[] names(String parameter) {
        return NAMES.get(parameter);
    }

    static double[] values(int count) {
        double[] out = new double[count];
        for (int i = 0; i < count; i++) out[i] = count == 1 ? 0 : i / (double) (count - 1);
        return out;
    }
}
