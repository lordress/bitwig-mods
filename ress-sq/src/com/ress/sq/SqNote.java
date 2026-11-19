package com.ress.sq;

import bitwig.obf.c1N; // native MIDI note
import bml.pianoroll.Note;
import bml.pianoroll.Refused;
import com.bitwig.flt.document.api.document.ApiModificationProcessor;
import com.bitwig.flt.document.core.master.dEy; // native undoable change

record SqNote(Note note, int repeat, double chance) {
    static SqNote read(Note note) {
        c1N event = (c1N) note.bitwigNote();
        return new SqNote(note, event.IvP() ? event.neH() : 0, event.af() ? event.kwH() : 1.0);
    }

    int repeatCount() {
        if (repeat >= 0) {
            return repeat + 1;
        }
        // Negative native values express a beat interval instead of a division of the note.
        return Math.max(1, (int) Math.ceil((note.end() - note.start()) * (1 - repeat) / 4.0));
    }

    void setRepeatCount(int count) {
        edit("Set Step Repeat", event -> {
            event.gNZ(count - 1);
            event.byf(count > 1);
        });
    }

    void setChance(int percent) {
        edit("Set Step Chance", event -> {
            event.byf(percent / 100.0);
            event.BBg(percent < 100);
        });
    }

    private void edit(String label, java.util.function.Consumer<c1N> update) {
        c1N event = (c1N) note.bitwigNote();
        com.bitwig.flt.document.core.master.dEy change = new com.bitwig.flt.document.core.master.dEy();
        if (!change.beI(event, label)) {
            throw new Refused("The note could not be edited.");
        }
        try {
            ApiModificationProcessor.modify(() -> update.accept(event));
        } finally {
            if (!change.Gef()) {
                RessSq.MOD.log(label + ": Bitwig could not close the undo step");
            }
        }
    }
}
