package com.ress.sq;

import bitwig.obf.NdS; // Drum/Hybrid mode
import bitwig.obf.cjx; // fixed widget size
import bitwig.obf.Hub; // a short MIDI message, status and two data bytes packed
import bitwig.obf.aEx; // the computer keyboard's note input
import bitwig.obf.ohx; // the receivers of one MIDI input port
import bitwig.obf.qPh; // a MIDI message
import bitwig.obf.usb; // the connection to the audio engine's MIDI
import bitwig.obf.aJI; // Detail Editor mode button
import bitwig.obf.aNi; // Detail Editor panel
import bitwig.obf.baN; // Detail Editor mode action
import bitwig.obf.bke; // Detail Editor controller
import bitwig.obf.dBC; // native icon toggle
import bitwig.obf.dXL; // native row or column
import bitwig.obf.dwF; // widget
import bitwig.obf.zfc; // icon sizing
import bitwig.obf.CNg; // mutable boolean observable
import bml.api.Arg;
import bml.api.At;
import bml.api.Bml;
import bml.api.Hook;
import bml.api.Return;
import bml.api.This;
import com.bitwig.base.geom.mLC;
import com.bitwig.flt.widget.core.timeline.WRA; // Detail Editor mode strip
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class RessSq {
    static final Bml.Mod MOD = Bml.mod("ress-sq");
    private static final Map<aNi, WeakReference<View>> VIEWS = new WeakHashMap<>();
    private static final Map<bke, Boolean> ACTIVE_EDITORS = new WeakHashMap<>();

    private RessSq() {}

    private static View view(aNi panel) {
        WeakReference<View> reference = VIEWS.get(panel);
        View view = reference == null ? null : reference.get();
        if (view == null) {
            view = new View(panel);
            VIEWS.put(panel, new WeakReference<>(view));
        }
        return view;
    }

    private static final AtomicIntegerArray HELD = new AtomicIntegerArray(128);
    /** The pitch of the most recent note-on, or -1 when nothing is held. */
    static volatile int pressed = -1;

    /**
     * Every short MIDI message Bitwig's reader hands to a port, and the computer
     * keyboard's notes. Only note on and off are read; the call is made as Bitwig
     * makes it, so nothing about the input changes.
     */
    static void read(int packed) {
        int status = packed & 0xFF;
        int data1 = (packed >> 8) & 0x7F;
        int data2 = (packed >> 16) & 0x7F;
        int kind = status & 0xF0;
        if (data1 < 0) {
            return;
        }
        if (kind == 0x90 && data2 > 0) {
            HELD.set(data1, 1);
            pressed = data1;
        } else if (kind == 0x80 || (kind == 0x90 && data2 == 0)) {
            HELD.set(data1, 0);
            if (RessSq.pressed == data1) {
                RessSq.pressed = -1;
                for (int key = 0; key < 128; key++) {
                    if (HELD.get(key) != 0) {
                        RessSq.pressed = key;
                        break;
                    }
                }
            }
        }
    }

    /** The pitch name of the note being played, empty when nothing is. */
    static String pressedLabel() {
        int key = pressed;
        return key < 0 ? "" : com.bitwig.base.sound.YjF.beI(key, true);
    }

    /**
     * Every short message a MIDI input port receives. capture-midi replaces the call
     * inside the reader thread; hooking the receiver instead means the two coexist
     * and neither depends on the other.
     */
    @Hook(target = ohx.class, method = "beI(LqPh;)V", at = At.ENTRY, id = "midi-in")
    public static void midiIn(@This ohx port, @Arg(0) qPh message) {
        try {
            if (message instanceof Hub shortMessage) {
                read(shortMessage.beI);
            }
        } catch (Throwable ignored) {
            // never stand in the way of the MIDI
        }
    }

    @Hook(target = usb.class, method = "beI(IIF)V", at = At.ENTRY, id = "keyboard-on")
    public static void keyboardOn(@Arg(0) int port, @Arg(1) int pitch, @Arg(2) float velocity) {
        try {
            read(0x90 | pitch << 8 | 100 << 16);
        } catch (Throwable ignored) {
            // never stand in the way of the keyboard
        }
    }

    @Hook(target = usb.class, method = "Gef(IIF)V", at = At.ENTRY, id = "keyboard-off")
    public static void keyboardOff(@Arg(0) int port, @Arg(1) int pitch, @Arg(2) float velocity) {
        try {
            read(0x80 | pitch << 8);
        } catch (Throwable ignored) {
            // never stand in the way of the keyboard
        }
    }

    @Hook(target = com.bitwig.flt.widget.core.timeline.WRA.class, method = "beI(Lbke;LJtD;)LdwF;", at = At.WRAP_RETURN)
    public static dwF addStepsButton(@Return dwF widget) {
        // Polarity's Step Sequencer builds its own button from this same call. BML
        // runs the hooks in mod-id order, so this one always runs first: wrapping
        // here would hide its button. Leave the strip alone and attach ours beside
        // its button at mount time instead, so both are visible.
        if (upstream()) {
            return widget;
        }
        if (!(widget instanceof aJI modeButton) || !(modeButton.UIe().tWW() instanceof NdS)
                || modeButton.KkD() != 1) {
            return widget;
        }
        try {
            dwF nativeButton = modeButton.n_(0);
            if (!(nativeButton instanceof dBC) && !(nativeButton instanceof dXL)) {
                return widget;
            }
            View view = view(modeButton.Anf);
            dBC steps = toggle(view, modeButton);

            if (nativeButton instanceof dXL column) {
                column.IvP(steps); // Scale Fold already put Drum/Hybrid and Scale in a column.
                return modeButton;
            }

            // Keep the native button inside its single-child widget. The mode strip has
            // not attached that widget yet, so it can be returned in a new column.
            dXL column = dXL.unz(1);
            column.ymo(0.0f);
            nativeButton.Gef(new cjx(22L));
            column.IvP(steps);
            column.BBg(modeButton, 0);
            return column;
        } catch (RuntimeException | LinkageError failure) {
            MOD.log("Could not add the Steps button", failure);
            return widget;
        }
    }

    /** The button itself, with RESS-SQ's own icon so the two are easy to tell apart. */
    private static dBC toggle(View view, aJI modeButton) {
        dBC steps = dBC.E2Q();
        dBC.beI(steps.vce(), view.active);
        steps.beI(modeButton.jl_().iOJ().beI("Borderless"));
        steps.Bqk(0.5f);
        steps.byf("notes_section_view.svg");
        steps.zzi("RESS-SQ Steps");
        steps.sCA(zfc.beI());
        steps.sCA(() -> view.toggle(modeButton));
        steps.Gef(new cjx(22L));
        return steps;
    }

    private static volatile Boolean upstream;

    /** True when Polarity's Step Sequencer is enabled in this BML install. */
    static boolean upstream() {
        Boolean known = upstream;
        if (known == null) {
            try {
                Bml.mod("step-sequencer").name();
                known = Boolean.TRUE;
            } catch (RuntimeException notEnabled) {
                known = Boolean.FALSE;
            }
            upstream = known;
        }
        return known;
    }

    /** The column that holds the Drum/Hybrid mode button, so a button can go next to it. */
    private static dXL modeButtonColumn(dwF widget, aJI[] found) {
        for (int i = 0; i < widget.KkD(); i++) {
            dwF child = widget.n_(i);
            if (child instanceof aJI button && button.KkD() == 1
                    && button.UIe().tWW() instanceof NdS
                    && widget instanceof dXL column) {
                found[0] = button;
                return column;
            }
            dXL deeper = modeButtonColumn(child, found);
            if (deeper != null) {
                return deeper;
            }
        }
        return null;
    }

    @Hook(target = aNi.class, method = "Gef(LHSH;Lbke;LbaN;)V", at = At.BEFORE_RETURN)
    public static void mount(@This aNi panel) {
        try {
            dXL layout = panel.GKF;
            dwF left = layout.n_(0).n_(0);
            dXL right = (dXL) layout.n_(1);
            if (right.KkD() != 2 || right.n_(1) != panel.mYH || left.KkD() != 2) {
                return;
            }
            ArrayList<dwF> nativeContent = new ArrayList<>();
            if (!collectHeaderContent(left.n_(1), nativeContent)) {
                MOD.log("Could not find the Detail Editor mode strip");
                return;
            }
            nativeContent.add(right.n_(0));
            nativeContent.add(right.n_(1));
            View view = view(panel);
            view.mount(right, nativeContent);
            if (upstream()) {
                // The other mod wrapped the mode button in a column at this point.
                // Put ours in that same column, so both buttons show side by side.
                aJI[] button = new aJI[1];
                dXL column = modeButtonColumn(left.n_(1), button);
                if (column != null && button[0] != null) {
                    view.attach(column, button[0]);
                } else {
                    MOD.log("Could not find the Drum/Hybrid button to add the RESS-SQ button");
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            MOD.log("Could not mount the Steps view", failure);
        }
    }

    private static boolean collectHeaderContent(dwF widget, List<dwF> content) {
        if (widget instanceof com.bitwig.flt.widget.core.timeline.WRA) {
            return true;
        }
        for (int i = 0; i < widget.KkD(); i++) {
            if (collectHeaderContent(widget.n_(i), content)) {
                for (int j = 0; j < widget.KkD(); j++) {
                    if (j != i) {
                        content.add(widget.n_(j));
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Hook(target = baN.class, method = "beI(LnNL;Lbke;Ljava/util/EnumSet;)V", at = At.ENTRY)
    public static void nativeMode(@Arg(1) bke editor) {
        close(editor);
    }

    @Hook(target = bke.class, method = "TaR()V", at = At.ENTRY)
    public static void pianoMode(@This bke editor) {
        close(editor);
    }

    @Hook(target = bke.class, method = "yX3()V", at = At.ENTRY)
    public static void drumMode(@This bke editor) {
        close(editor);
    }

    private static void close(bke editor) {
        setActive(editor, false);
    }

    private static void setActive(bke editor, boolean active) {
        if (active) {
            ACTIVE_EDITORS.put(editor, true);
        } else {
            ACTIVE_EDITORS.remove(editor);
        }
        for (WeakReference<View> reference : List.copyOf(VIEWS.values())) {
            View view = reference.get();
            if (view != null && view.panel.yX3() == editor) {
                view.setActive(active);
            }
        }
    }

    static final class View {
        final aNi panel;
        final CNg active = new CNg(false);
        private SqWidget steps;
        private dwF[] nativeContent;
        private boolean[] wasVisible;

        View(aNi panel) {
            this.panel = panel;
        }

        void mount(dXL right, List<dwF> content) {
            nativeContent = content.toArray(dwF[]::new);
            wasVisible = new boolean[nativeContent.length];
            steps = new SqWidget(panel.yX3(), this);
            steps.H_();
            steps.dYz(false);
            right.IvP(steps);
            setActive(ACTIVE_EDITORS.containsKey(panel.yX3()));
        }

        /** Adds the RESS-SQ button beside the other mod's, in the mode button's column. */
        void attach(dXL column, aJI button) {
            if (steps != null) {
                column.IvP(RessSq.toggle(this, button));
            }
        }

        void toggle(aJI button) {
            if (steps == null) {
                return;
            }
            bke editor = panel.yX3();
            if (active.getBoolValue()) {
                close(editor);
                return;
            }
            // Scale Fold uses Drum/Hybrid underneath. Selecting it through Bitwig also exits Scale Fold.
            if (editor.Xit() != null && editor.Xit().tWW() instanceof NdS) {
                panel.np2().beI(button.UIe().tWW(), editor, EnumSet.of(com.bitwig.base.geom.mLC.Gef));
            }
            RessSq.setActive(editor, true);
        }

        void setActive(boolean enabled) {
            if (steps == null || active.getBoolValue() == enabled) {
                return;
            }
            if (!enabled) {
                steps.deactivate();
            }
            active.setBoolValue(enabled);
            for (int i = 0; i < nativeContent.length; i++) {
                if (enabled) {
                    wasVisible[i] = nativeContent[i].KrW();
                }
                nativeContent[i].dYz(!enabled && wasVisible[i]);
            }
            steps.dYz(enabled);
        }
    }
}
