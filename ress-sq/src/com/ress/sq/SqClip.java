package com.ress.sq;

import bitwig.obf.Ynv; // editor layer
import bitwig.obf.SZB; // native color
import bitwig.obf.AWo; // track editor layer
import bitwig.obf.XeT; // named drum pad
import bitwig.obf.bke; // Detail Editor controller
import bitwig.obf.bpF; // a clip's note editor layer
import bitwig.obf.YCD; // note drawing state
import bitwig.obf.ZkN; // a clip's note lane for one pitch and channel
import bitwig.obf.Wg; // track
import bitwig.obf.DrA; // MIDI clip
import bitwig.obf.PiT; // track's note and drum-pad model
import bml.pianoroll.Clip;
import bml.pianoroll.PianoRoll;
import com.bitwig.base.sound.YjF; // pitch names used in the native note rows
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

record SqClip(Clip clip, SqGrid.Range range, int channel, double velocity, List<Row> rows, SZB color,
        String stateKey) {
    private static final List<Row> CHROMATIC = chromaticRows();

    record Row(int key, String name) {}

    static SqClip read(bke editor) {
        bpF lane = clipLane(editor);
        if (lane == null || !(lane.mV_() instanceof DrA nativeClip) || !nativeClip.sSO()) {
            return null;
        }
        Clip clip = PianoRoll.clip(nativeClip);
        double start = nativeClip.dC_();
        double end = nativeClip.GwM();
        if (nativeClip.Xit()) {
            end = Math.max(end, nativeClip.vce().oiY());
        }
        if (clip == null || !Double.isFinite(start) || !Double.isFinite(end) || end <= start) {
            return null;
        }
        YCD drawing = lane.XnJ();
        double velocity = drawing == null ? 0.0 : drawing.Ktd();
        Wg track = track(lane, nativeClip);
        return new SqClip(clip, new SqGrid.Range(start, end), lane.mb_(),
                velocity > 0 ? velocity : 100.0 / 127.0, rows(track, nativeClip), nativeClip.IvP(),
                stateKey(track, start, end));
    }

    private static bpF clipLane(bke editor) {
        if (editor.Xit() == null) {
            return null;
        }
        if (editor.GwM() instanceof bpF focused) {
            return focused;
        }
        bpF onlyClip = null;
        for (Object layer : editor.jTT()) {
            if (!(layer instanceof bpF clip)) {
                continue;
            }
            if (onlyClip != null && onlyClip.mV_() != clip.mV_()) {
                return null;
            }
            onlyClip = clip;
        }
        return onlyClip;
    }

    private static Wg track(bpF lane, DrA clip) {
        Ynv layer = lane.tWW();
        Ynv trackLayer = layer == null ? null : layer.neH();
        return trackLayer instanceof AWo nativeTrack ? nativeTrack.gj_()
                : clip.sBA() instanceof Wg clipTrack ? clipTrack : null;
    }

    /**
     * A name for this clip that survives a reload: Bitwig exposes no clip id, so it
     * is the track's name and index plus the clip's start and end, in 96ths of a beat.
     */
    private static String stateKey(Wg track, double start, double end) {
        String name = "?";
        int index = -1;
        if (track != null) {
            try {
                String value = track.aG_();
                if (value != null && !value.isBlank()) {
                    name = value;
                }
            } catch (RuntimeException ignored) {
                // keep the fallback name
            }
            try {
                index = track.YfP();
            } catch (RuntimeException ignored) {
                index = -1;
            }
        }
        return name + "#" + index + "@" + Math.round(start * 96) + "-" + Math.round(end * 96);
    }

    private static List<Row> rows(Wg track, DrA clip) {
        if (track == null || !(track.yAy() instanceof PiT pads)) {
            return CHROMATIC;
        }
        TreeMap<Integer, Row> named = new TreeMap<>();
        for (Object item : pads.vlg()) {
            if (item instanceof XeT pad && pad.byf() >= 0 && pad.byf() < 128) {
                String pitch = com.bitwig.base.sound.YjF.beI(pad.byf(), true);
                String name = pad.beI();
                named.put(pad.byf(), new Row(pad.byf(), name == null || name.isBlank() || name.equals(pitch)
                        ? pitch : pitch + "  " + name));
            }
        }
        if (named.isEmpty()) {
            return CHROMATIC;
        }
        for (Object item : clip.scr().rw3().scr()) {
            if (item instanceof ZkN noteLane && !noteLane.beI().isEmpty()) {
                int key = noteLane.byf();
                named.putIfAbsent(key, new Row(key, com.bitwig.base.sound.YjF.beI(key, true)));
            }
        }
        return List.copyOf(named.descendingMap().values());
    }

    boolean chromatic() {
        return rows == CHROMATIC;
    }

    int playbackColumn(bke editor, int page, long frameTime) {
        if (!editor.BCC().aKN()) {
            return -1;
        }
        // The clip's native playback provider reports content time, including launcher and loop playback.
        DrA nativeClip = (DrA) clip.bitwigClip();
        double beat = nativeClip.Gef(frameTime);
        return beat == -1.0 ? -1 : range.playbackColumn(page, beat);
    }

    /** The page the transport is on, for the follow option; -1 when stopped. */
    int playingPage(bke editor, long frameTime) {
        if (!editor.BCC().aKN()) {
            return -1;
        }
        DrA nativeClip = (DrA) clip.bitwigClip();
        double beat = nativeClip.Gef(frameTime);
        return beat == -1.0 ? -1 : range.pageOf(beat);
    }

    List<SqGrid.Event<SqNote>> notes(double start, double end) {
        return clip.notesBetween(start, end).stream()
                .map(note -> new SqGrid.Event<>(SqNote.read(note), note.key(), note.channel(), note.start(), note.end(), note.velocity()))
                .toList();
    }

    private static List<Row> chromaticRows() {
        List<Row> rows = new ArrayList<>(128);
        for (int key = 127; key >= 0; key--) {
            rows.add(new Row(key, com.bitwig.base.sound.YjF.beI(key, true)));
        }
        return List.copyOf(rows);
    }
}
