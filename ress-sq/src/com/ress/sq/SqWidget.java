package com.ress.sq;

import bitwig.obf.SZB; // native color
import bitwig.obf.bke; // Detail Editor controller
import bitwig.obf.dLf; // mouse press
import bitwig.obf.dLg; // mouse release
import bitwig.obf.dLp; // mouse wheel
import bitwig.obf.dLk; // mouse drag
import bitwig.obf.dsB; // subscription to a window's display frames
import bitwig.obf.dtt; // native mouse gesture
import bitwig.obf.dwF; // native widget
import bitwig.obf.eqw; // modifiers
import bitwig.obf.eqx; // mouse button
import bml.pianoroll.Edit;
import bml.pianoroll.Listening;
import bml.pianoroll.Note;
import bml.pianoroll.NoteSelection;
import bml.pianoroll.PianoRoll;
import bml.pianoroll.Refused;
import com.bitwig.base.geom.ijY; // widget size
import com.bitwig.base.sound.YjF; // native pitch names
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class SqWidget extends dwF {
    private static final SZB BACKGROUND = new SZB(0.117f, 0.121f, 0.128f, 1f);
    private static final SZB STRIP = new SZB(0.155f, 0.163f, 0.172f, 1f);
    private static final SZB CELL = new SZB(0.207f, 0.219f, 0.229f, 1f);
    private static final SZB BEAT = new SZB(0.252f, 0.263f, 0.272f, 1f);
    // RESS-SQ's own palette: violet steps, amber playhead, red eraser.
    private static final SZB ACCENT = new SZB(0.66f, 0.42f, 0.96f, 1f);
    private static final SZB PLAYHEAD = new SZB(0.98f, 0.71f, 0.32f, 1f);
    private static final SZB ERASE = new SZB(0.95f, 0.36f, 0.32f, 1f);
    private static final int TOOLS = 3;
    private static final int WHEEL_ROWS = 3;
    private static final long REFRESH_INTERVAL = 150_000_000L;

    private final bke editor;
    private final RessSq.View view;
    private SqClip target;
    private List<SqGrid.Event<SqNote>> notes = List.of();
    private Parameter parameter = Parameter.VELOCITY;
    private int page;
    private int firstRow;
    private int playingColumn = -1;
    private boolean follow = true;
    /** Rows the grid is showing, in display order: all rows, or only the marked ones when folded. */
    private List<Integer> shownRows = List.of();
    private final Set<Integer> markedRows = new HashSet<>();
    private final Set<Integer> mutedRows = new HashSet<>();
    private final Set<Integer> soloRows = new HashSet<>();
    private final Map<Integer, String> rowNames = new HashMap<>();
    /** Velocities the mute replaced, so unmuting puts them back. */
    private final Map<Note, Double> savedVelocity = new HashMap<>();
    private Set<Integer> mutedKeys = Set.of();
    private boolean fold;
    private int pickerRow = -1;
    private NoteSelection selection;
    private Listening selectionListening;
    private final java.util.Random random = new java.util.Random();
    private CellGesture gesture;
    private dsB frames;
    private Listening changes;
    private boolean subscribing;
    private long nextRefresh;
    private long lastFrameTime;

    private enum Parameter {
        VELOCITY("Velocity", 1, 127, 1),
        OFFSET("Offset", Integer.MIN_VALUE, Integer.MAX_VALUE, 12),
        LENGTH("Length", Integer.MIN_VALUE, Integer.MAX_VALUE, 8),
        REPEAT("Repeat", 1, 128, 8),
        PITCH("Pitch", 0, 127, 8),
        CHANCE("Chance", 0, 100, 1);

        final String label;
        final int min;
        final int max;
        final int pixelsPerUnit;

        Parameter(String label, int min, int max, int pixelsPerUnit) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.pixelsPerUnit = pixelsPerUnit;
        }

        int read(SqGrid.Event<SqNote> event) {
            return switch (this) {
                case VELOCITY -> SqGrid.velocity127(event.velocity());
                case REPEAT -> event.note().repeatCount();
                case CHANCE -> (int) Math.round(event.note().chance() * 100);
                case PITCH -> event.key();
                case OFFSET, LENGTH -> 0; // drag delta, so an existing off-grid value is preserved
            };
        }

        String display(int value) {
            return switch (this) {
                case CHANCE -> value + "%";
                case REPEAT -> value == 1 ? "Off" : Integer.toString(value);
                case PITCH -> com.bitwig.base.sound.YjF.beI(value, true);
                default -> Integer.toString(value);
            };
        }

        boolean changesPlacement() {
            return this == OFFSET || this == LENGTH || this == PITCH;
        }

        SqGrid.Placement placement(SqGrid.Event<SqNote> note, double start, double clipEnd, int value) {
            return switch (this) {
                case OFFSET -> SqGrid.offset(note, start, clipEnd, value);
                case LENGTH -> SqGrid.length(note, clipEnd, value);
                case PITCH -> new SqGrid.Placement(value, note.start(), note.end());
                default -> new SqGrid.Placement(note.key(), note.start(), note.end());
            };
        }

        String displayPlacement(SqGrid.Placement placement, double start) {
            return switch (this) {
                case OFFSET -> "+" + Math.clamp(Math.round((placement.start() - start) / SqGrid.STEP * 100), 0, 99) + "%";
                case LENGTH -> String.format(java.util.Locale.ROOT, "%.2f step", (placement.end() - placement.start()) / SqGrid.STEP);
                case PITCH -> display(placement.key());
                default -> throw new IllegalStateException();
            };
        }

        void apply(SqNote note, int value) {
            switch (this) {
                case VELOCITY -> PianoRoll.edit("Set Step Velocity").setVelocity(note.note(), value / 127.0).apply();
                case REPEAT -> note.setRepeatCount(value);
                case CHANCE -> note.setChance(value);
                case OFFSET, LENGTH, PITCH -> throw new IllegalStateException();
            }
        }
    }

    private static final Parameter[] PARAMETERS = Parameter.values();

    SqWidget(bke editor, RessSq.View view) {
        this.editor = editor;
        this.view = view;
    }

    @Override public com.bitwig.base.geom.ijY mL_() { return com.bitwig.base.geom.ijY.beI(); }
    @Override public com.bitwig.base.geom.ijY nj_() { return com.bitwig.base.geom.ijY.Gef(); }
    @Override public dwF neH() { return this; }

    void deactivate() {
        gesture = null;
        playingColumn = -1;
        if (frames != null) {
            frames.beI();
            frames = null;
        }
        if (changes != null) {
            changes.stop();
            changes = null;
        }
        if (selectionListening != null) {
            selectionListening.stop();
            selectionListening = null;
            selection = null;
        }
        nextRefresh = 0;
    }

    @Override
    public void iy_() {
        deactivate();
        super.iy_();
    }

    private void watch() {
        if (frames != null || subscribing) {
            return;
        }
        subscribing = true;
        PianoRoll.later(() -> {
            subscribing = false;
            if (frames == null && view.active.getBoolValue() && TUb()) {
                changes = PianoRoll.onChange(() -> nextRefresh = 0);
                selectionListening = PianoRoll.onSelectionChange(selected -> selection = selected);
                frames = dsB.beI(frame -> {
                    if (!view.active.getBoolValue() || !TUb()) {
                        deactivate();
                    } else {
                        lastFrameTime = frame.sCA();
                        if (System.nanoTime() - nextRefresh >= 0) {
                            refresh();
                        }
                        followPlayhead();
                        int column = target == null ? -1 : target.playbackColumn(editor, page, lastFrameTime);
                        if (column != playingColumn) {
                            playingColumn = column;
                            OCx();
                        }
                    }
                }, this);
                refresh();
            }
        });
    }

    private void refresh() {
        nextRefresh = System.nanoTime() + REFRESH_INTERVAL;
        try {
            SqClip current = SqClip.read(editor);
            boolean clipChanged = target == null ? current != null
                    : current == null || !target.clip().equals(current.clip());
            if (gesture != null && !gesture.matches(current)) {
                gesture = null;
                OCx();
            }
            if (clipChanged) {
                playingColumn = -1;
                page = 0;
                firstRow = current != null && current.chromatic() ? 63 : 0;
            }
            shownRows = computeShown(current);
            if (current != null) {
                page = Math.min(page, current.range().pages() - 1);
                firstRow = Math.min(firstRow, maxFirstRow());
            }
            List<SqGrid.Event<SqNote>> currentNotes = current == null ? List.of() : current.notes(
                    current.range().beat(page, 0), Math.min(current.range().end(), current.range().beat(page, SqGrid.STEPS)));
            if (!Objects.equals(target, current) || !notes.equals(currentNotes)) {
                target = current;
                notes = currentNotes;
                OCx();
            }
            recomputeMuted();
        } catch (RuntimeException | LinkageError failure) {
            deactivate();
            target = null;
            notes = List.of();
            RessSq.MOD.log("Could not read the active clip", failure);
        }
    }

    private double labels() { return Math.min(wGt(156), rtK() * 0.33); }
    private double column() { return Math.max(1, (rtK() - labels()) / SqGrid.STEPS); }
    private double gridRight() { return labels() + SqGrid.STEPS * column(); }
    private double rowHeight() { return Math.max(wGt(13), Math.min(wGt(26), column() * 0.50)); }
    private double top() { return wGt(48); }
    private double bottom() { return Math.max(top(), cBV() - wGt(25)); }
    private double toolsWidth() { return wGt(104); }
    private double toolsStart() { return rtK() - toolsWidth(); }
    private double toolWidth() { return toolsWidth() / TOOLS; }
    private double tabStart() { return Math.max(wGt(65), Math.min(labels() + wGt(3), rtK() - toolsWidth() - PARAMETERS.length * wGt(52))); }
    private double tabWidth() { return Math.min(wGt(70), Math.max(1, (rtK() - toolsWidth() - tabStart()) / PARAMETERS.length)); }
    private int visibleRows() { return Math.max(1, (int) Math.ceil((bottom() - top()) / rowHeight())); }
    private int maxFirstRow() { return Math.max(0, shownRows.size() - visibleRows()); }

    private List<Integer> computeShown(SqClip clip) {
        if (clip == null) {
            return List.of();
        }
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < clip.rows().size(); i++) {
            if (!fold || markedRows.contains(clip.rows().get(i).key())) {
                shown.add(i);
            }
        }
        return List.copyOf(shown);
    }

    private int slotAt(double y) {
        if (y < top() || y >= bottom()) {
            return -1;
        }
        int slot = firstRow + (int) Math.floor((y - top()) / rowHeight());
        return slot >= 0 && slot < shownRows.size() ? slot : -1;
    }

    private int keyAt(double y) {
        int slot = slotAt(y);
        return slot < 0 || target == null ? -1 : target.rows().get(shownRows.get(slot)).key();
    }

    private double muteLeft() { return labels() - wGt(30); }
    private double soloLeft() { return labels() - wGt(15); }

    private int cellAt(double x, double y) {
        int column = (int) Math.floor((x - labels()) / column());
        int slot = slotAt(y);
        if (column < 0 || column >= SqGrid.STEPS || slot < 0 || target == null) {
            return -1;
        }
        return shownRows.get(slot) * SqGrid.STEPS + column;
    }

    @Override
    public void beI(com.bitwig.graphics.ijY graphics) {
        if (!view.active.getBoolValue()) {
            return;
        }
        watch();
        graphics.ZRB();
        try {
            graphics.beI(kI_().beI(fxI(), this));
            SZB text = mWG().beI(this);
            rectangle(graphics, 0, 0, rtK(), cBV(), BACKGROUND);
            rectangle(graphics, 0, 0, rtK(), wGt(26), STRIP);
            graphics.beI(wGt(9), wGt(17), "RESS-SQ  ·  16 Steps  ·  1/16", text);
            if (target == null) {
                graphics.beI(wGt(9), wGt(45), "Open a single editable MIDI clip to use Steps.", text);
                return;
            }
            paintControls(graphics, text);
            if (playingColumn >= 0) {
                rectangle(graphics, labels() + playingColumn * column(), wGt(44), column() - wGt(2), wGt(2), PLAYHEAD);
            }
            paintGrid(graphics, text);
        } finally {
            graphics.jTs();
        }
    }

    private void paintControls(com.bitwig.graphics.ijY graphics, SZB text) {
        double right = gridRight() - wGt(6);
        button(graphics, right - wGt(126), wGt(4), "‹", page > 0, text);
        graphics.beI(right - wGt(96), wGt(17), (page + 1) + " / " + target.range().pages(), text);
        button(graphics, right - wGt(26), wGt(4), "›", page + 1 < target.range().pages(), text);
        for (int column = 0; column < SqGrid.STEPS; column += 4) {
            graphics.beI(labels() + column * column() + wGt(3), wGt(41), Integer.toString(column + 1), text.beI(0.6f));
        }
        rectangle(graphics, 0, bottom(), rtK(), wGt(25), STRIP);
        double foldX = wGt(4);
        rectangle(graphics, foldX, bottom() + wGt(3), wGt(46), wGt(18), fold ? BEAT : CELL);
        if (fold) {
            rectangle(graphics, foldX, bottom() + wGt(20), wGt(46), wGt(1), ACCENT);
        }
        graphics.beI(foldX + wGt(8), bottom() + wGt(16), "FOLD", text.beI(fold ? 1f : 0.7f));
        for (Parameter tab : PARAMETERS) {
            double x = tabStart() + tab.ordinal() * tabWidth();
            boolean selected = parameter == tab;
            rectangle(graphics, x, bottom() + wGt(3), tabWidth() - wGt(2), wGt(18), selected ? BEAT : CELL);
            if (selected) {
                rectangle(graphics, x, bottom() + wGt(20), tabWidth() - wGt(2), wGt(1), ACCENT);
            }
            graphics.beI(x + wGt(7), bottom() + wGt(16), tab.label, text.beI(selected ? 1f : 0.72f));
        }
        if (gesture != null && gesture.dragged) {
            graphics.beI(tabStart() + PARAMETERS.length * tabWidth() + wGt(8), bottom() + wGt(16),
                    gesture.display(), text);
        }
        if (pickerRow >= 0) {
            paintPicker(graphics, text);
        }
        // RESS-SQ's own tools: follow the playhead, randomise, clear this page.
        String[] labels = {"FLW", "RND", "CLR"};
        boolean[] on = {follow, true, true};
        for (int i = 0; i < TOOLS; i++) {
            double x = toolsStart() + i * toolWidth();
            rectangle(graphics, x + wGt(1), bottom() + wGt(3), toolWidth() - wGt(3), wGt(18),
                    i == 0 && follow ? BEAT : CELL);
            if (i == 0 && follow) {
                rectangle(graphics, x + wGt(1), bottom() + wGt(20), toolWidth() - wGt(3), wGt(1), ACCENT);
            }
            graphics.beI(x + wGt(5), bottom() + wGt(15), labels[i], text.beI(on[i] ? 0.85f : 0.5f));
        }
    }

    /** Every step cell the grid is showing on this page, row by row. */
    private void visitPageCells(java.util.function.Consumer<SqGrid.Cell<SqNote>> visitor) {
        if (target == null) {
            return;
        }
        int rows = Math.min(shownRows.size(), firstRow + visibleRows());
        for (int slot = firstRow; slot < rows; slot++) {
            int key = target.rows().get(shownRows.get(slot)).key();
            for (int column = 0; column < SqGrid.STEPS; column++) {
                double start = target.range().beat(page, column);
                double end = Math.min(start + SqGrid.STEP, target.range().end());
                if (end > start) {
                    visitor.accept(SqGrid.cell(notes, key, target.channel(), start, end));
                }
            }
        }
    }

    private void randomiseVelocities() {
        try {
            Edit edit = PianoRoll.edit("Randomise Step Velocities");
            int[] count = {0};
            Set<Note> chosen = selectedNotes();
            visitPageCells(cell -> {
                if (cell.note() != null && (chosen.isEmpty() || chosen.contains(cell.note().note().note()))) {
                    // Keep it musical: a jittered band rather than noise across the whole range.
                    double velocity = 0.40 + random.nextDouble() * 0.60;
                    edit.setVelocity(cell.note().note().note(), velocity);
                    count[0]++;
                }
            });
            if (count[0] == 0) {
                PianoRoll.notification(chosen.isEmpty()
                        ? "RESS-SQ: no steps on this page."
                        : "RESS-SQ: none of the selected notes are steps on this page.");
                return;
            }
            edit.apply();
        } catch (Refused refused) {
            PianoRoll.notification(refused.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            RessSq.MOD.log("Could not randomise the steps", failure);
        }
    }

    private void clearPage() {
        try {
            Edit edit = PianoRoll.edit("Clear Steps");
            int[] count = {0};
            visitPageCells(cell -> {
                if (cell.note() != null) {
                    edit.remove(cell.note().note().note());
                    count[0]++;
                }
            });
            if (count[0] == 0) {
                PianoRoll.notification("RESS-SQ: no steps on this page.");
                return;
            }
            edit.apply();
        } catch (Refused refused) {
            PianoRoll.notification(refused.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            RessSq.MOD.log("Could not clear the page", failure);
        }
    }

    private void followPlayhead() {
        if (target == null || !follow) {
            return;
        }
        int wanted = target.playingPage(editor, lastFrameTime);
        if (wanted >= 0 && wanted != page) {
            page = wanted;
            playingColumn = -1;
            refresh();
        }
    }

    private void button(com.bitwig.graphics.ijY graphics, double x, double y, String label, boolean enabled, SZB text) {
        rectangle(graphics, x, y, wGt(25), wGt(18), CELL);
        graphics.beI(x + wGt(9), y + wGt(13), label, text.beI(enabled ? 1f : 0.3f));
    }

    private void paintGrid(com.bitwig.graphics.ijY graphics, SZB text) {
        SZB color = target.color() == null || target.color().Anf() == 0f ? ACCENT : target.color().beI();
        int rows = Math.min(shownRows.size() - firstRow, visibleRows());
        for (int slot = 0; slot < rows; slot++) {
            int index = shownRows.get(firstRow + slot);
            SqClip.Row pitch = target.rows().get(index);
            double y = top() + slot * rowHeight();
            double height = Math.min(rowHeight() - wGt(2), bottom() - y);
            if (height <= 0) {
                break;
            }
            boolean muted = isMuted(pitch.key());
            rectangle(graphics, 0, y, labels() - wGt(2), height, STRIP);
            paintRowLabel(graphics, pitch, y, height, text, muted);
            for (int column = 0; column < SqGrid.STEPS; column++) {
                paintCell(graphics, pitch, index * SqGrid.STEPS + column, y, height, color, text, muted);
            }
        }
    }

    private void paintCell(com.bitwig.graphics.ijY graphics, SqClip.Row row, int index, double y,
            double height, SZB color, SZB text, boolean muted) {
        if (muted) {
            color = color.beI(0.30f);
            text = text.beI(0.55f);
        }
        int column = index % SqGrid.STEPS;
        double x = labels() + column * column();
        double width = Math.max(0, column() - wGt(2));
        double start = target.range().beat(page, column);
        double end = Math.min(start + SqGrid.STEP, target.range().end());
        if (end <= start) {
            rectangle(graphics, x, y, width, height, STRIP);
            return;
        }
        SqGrid.Cell<SqNote> cell = SqGrid.cell(notes, row.key(), target.channel(), start, end);
        rectangle(graphics, x, y, width, height, column % 4 == 0 ? BEAT : CELL);
        if (column == playingColumn) {
            rectangle(graphics, x, y, width, height, PLAYHEAD.beI(0.36f));
        }
        boolean pending = gesture != null && gesture.pending.get(index);
        if (cell.starts() > 0) {
            rectangle(graphics, x, y, width, height, color.beI(0.45f));
            if (cell.note() == null) {
                graphics.beI(x + wGt(4), y + Math.min(height - 1, wGt(15)), Integer.toString(cell.starts()), text);
            } else {
                boolean dragging = gesture != null && gesture.dragged && gesture.origin == index;
                SqNote note = cell.note().note();
                double velocity = dragging && parameter == Parameter.VELOCITY ? gesture.value / 127.0 : cell.note().velocity();
                double chance = dragging && parameter == Parameter.CHANCE ? gesture.value / 100.0 : note.chance();
                double fill = height * (parameter == Parameter.CHANCE ? chance : velocity);
                fill = Math.min(height, Math.max(wGt(2), fill));
                rectangle(graphics, x, y + height - fill, width, fill, color);
                int repeats = dragging && parameter == Parameter.REPEAT ? gesture.value : note.repeatCount();
                paintRepeats(graphics, x, y, width, height, repeats);
                if (height >= wGt(15) && width >= wGt(34)) {
                    String label = null;
                    if (parameter == Parameter.CHANCE) {
                        label = Parameter.CHANCE.display((int) Math.round(chance * 100));
                    } else if (parameter.changesPlacement()) {
                        int value = dragging ? gesture.value : parameter.read(cell.note());
                        SqGrid.Placement placement = parameter.placement(cell.note(), start, target.range().end(), value);
                        label = parameter.displayPlacement(placement, start);
                    }
                    if (label != null) {
                        graphics.beI(x + wGt(5), y + wGt(13), label, text);
                    }
                }
            }
        } else if (pending && !gesture.erase) {
            double fill = height * gesture.source.velocity();
            rectangle(graphics, x, y, width, height, color.beI(0.45f));
            rectangle(graphics, x, y + height - fill, width, fill, color.beI(0.85f));
        } else if (cell.continuation()) {
            rectangle(graphics, x, y + height * 0.45, width, height * 0.16, color.beI(0.55f));
        }
        boolean erasing = gesture != null && cell.note() != null && cell.note().equals(gesture.erased.get(index));
        if (erasing || column == playingColumn && cell.starts() > 0) {
            SZB outline = erasing ? ERASE : text;
            if (erasing) {
                rectangle(graphics, x, y, width, height, BACKGROUND.beI(0.55f));
            }
            double edge = Math.min(wGt(2), Math.min(width, height) / 2);
            rectangle(graphics, x, y, width, edge, outline);
            rectangle(graphics, x, y + height - edge, width, edge, outline);
            rectangle(graphics, x, y, edge, height, outline);
            rectangle(graphics, x + width - edge, y, edge, height, outline);
        }
    }

    private void paintRowLabel(com.bitwig.graphics.ijY graphics, SqClip.Row pitch, double y,
            double height, SZB text, boolean muted) {
        int key = pitch.key();
        double dot = Math.min(wGt(7), Math.max(wGt(4), height - wGt(4)));
        rectangle(graphics, wGt(2), y + (height - dot) / 2, dot, dot,
                markedRows.contains(key) ? ACCENT : CELL);
        pill(graphics, muteLeft(), y, height, "M", mutedRows.contains(key), text);
        pill(graphics, soloLeft(), y, height, "S", soloRows.contains(key), text);
        if (height >= wGt(11)) {
            String name = rowLabel(pitch);
            int limit = Math.max(3, (int) ((muteLeft() - wGt(14)) / Math.max(1, wGt(7))));
            graphics.beI(wGt(12), y + height * 0.5 + wGt(4),
                    name.length() > limit ? name.substring(0, Math.max(1, limit - 1)) + "…" : name,
                    text.beI(muted ? 0.45f : 1f));
        }
    }

    private void pill(com.bitwig.graphics.ijY graphics, double x, double y, double height,
            String label, boolean on, SZB text) {
        rectangle(graphics, x, y + wGt(1), wGt(13), Math.max(wGt(3), height - wGt(2)), on ? ACCENT : CELL);
        graphics.beI(x + wGt(4), y + height * 0.5 + wGt(3), label, text.beI(on ? 1f : 0.55f));
    }

    /** The row's name: the mod's own label when it has one, else Bitwig's pad or pitch name. */
    private String rowLabel(SqClip.Row pitch) {
        String custom = rowNames.get(pitch.key());
        if (custom == null) {
            return pitch.name();
        }
        String name = pitch.name();
        int cut = name.indexOf("  ");
        String note = cut > 0 ? name.substring(0, cut) : name;
        return note + " " + custom;
    }

    private void paintRepeats(com.bitwig.graphics.ijY graphics, double x, double y, double width, double height, int repeats) {
        int segments = Math.min(repeats, (int) (width / Math.max(1, wGt(4))));
        for (int i = 1; i < segments; i++) {
            rectangle(graphics, x + i * width / segments, y + height * 0.16, wGt(1), height * 0.68, BACKGROUND.beI(0.9f));
        }
    }

    private static void rectangle(com.bitwig.graphics.ijY graphics, double x, double y, double width, double height, SZB color) {
        if (width > 0 && height > 0) {
            graphics.beI(x, y, width, height, color);
        }
    }

    @Override
    public dtt beI(eqx button, double x, double y, eqw modifiers, int clicks) {
        if (!view.active.getBoolValue() || target == null) {
            return null;
        }
        if (button == eqx.sCA || button == eqx.beI && modifiers.beI()) {
            int index = cellAt(x, y);
            return index < 0 ? null : new CellGesture(target, index, x, y, null, true);
        }
        if (button != eqx.beI) {
            return null;
        }
        if (pickerRow >= 0) {
            double px = pickerX();
            if (x >= px && x < px + pickerWidth() && y >= pickerY()) {
                int choice = (int) ((y - pickerY() - wGt(3)) / pickerLine());
                return control(() -> chooseName(Math.max(0, Math.min(PICKER_CLEAR, choice))));
            }
            return control(() -> {
                pickerRow = -1;
                OCx();
            });
        }
        if (y >= bottom() && x >= wGt(4) && x < wGt(52)) {
            return control(() -> {
                fold = !fold;
                shownRows = computeShown(target);
                firstRow = Math.min(firstRow, maxFirstRow());
                OCx();
            });
        }
        if (x < labels() - wGt(1) && y >= top() && y < bottom()) {
            int key = keyAt(y);
            if (key >= 0) {
                if (x < wGt(11)) {
                    return control(() -> toggleMark(key));
                }
                if (x >= muteLeft() && x < muteLeft() + wGt(14)) {
                    return control(() -> toggleMute(key));
                }
                if (x >= soloLeft() && x < soloLeft() + wGt(14)) {
                    return control(() -> toggleSolo(key));
                }
                return control(() -> {
                    pickerRow = key;
                    OCx();
                });
            }
        }
        double right = gridRight() - wGt(6);
        if (y < wGt(26) && x >= right - wGt(126) && x < right) {
            if (x <= right - wGt(101)) {
                return control(() -> changePage(-1));
            }
            if (x >= right - wGt(26)) {
                return control(() -> changePage(1));
            }
        }
        if (y >= bottom() && x >= toolsStart() && x < rtK()) {
            int tool = (int) ((x - toolsStart()) / toolWidth());
            return control(() -> {
                switch (tool) {
                    case 0 -> {
                        follow = !follow;
                        followPlayhead();
                    }
                    case 1 -> randomiseVelocities();
                    default -> clearPage();
                }
                OCx();
            });
        }
        if (y >= bottom() && x >= tabStart() && x < tabStart() + PARAMETERS.length * tabWidth()) {
            Parameter selected = PARAMETERS[(int) ((x - tabStart()) / tabWidth())];
            return control(() -> {
                parameter = selected;
                OCx();
            });
        }
        int index = cellAt(x, y);
        if (index < 0) {
            return null;
        }
        int column = index % SqGrid.STEPS;
        double start = target.range().beat(page, column);
        double end = Math.min(start + SqGrid.STEP, target.range().end());
        if (end <= start) {
            return null;
        }
        int key = target.rows().get(index / SqGrid.STEPS).key();
        SqGrid.Cell<SqNote> cell = SqGrid.cell(notes, key, target.channel(), start, end);
        return cell.note() != null || cell.canAdd() ? new CellGesture(target, index, x, y, cell.note(), false) : null;
    }

    private dtt control(Runnable action) {
        return new dtt() {
            @Override public void beI(dLg release) {
                try {
                    action.run();
                } finally {
                    kwH();
                }
            }
        };
    }

    private static final String[] RENAME_CHOICES = {
        "Kick", "Snare", "Clap", "Hat", "Open Hat", "Rim",
        "Tom", "Ride", "Crash", "Perc", "Bass", "Chord",
    };
    private static final int PICKER_CLEAR = RENAME_CHOICES.length;

    private double pickerX() { return wGt(4); }
    private double pickerY() { return top() + wGt(4); }
    private double pickerWidth() { return Math.min(labels() + wGt(90), rtK() - wGt(8)); }
    private double pickerLine() { return wGt(17); }
    private int pickerCount() { return RENAME_CHOICES.length + 1; }

    private void paintPicker(com.bitwig.graphics.ijY graphics, SZB text) {
        double x = pickerX();
        double y = pickerY();
        double height = Math.min(pickerCount() * pickerLine() + wGt(6), bottom() - y - wGt(2));
        rectangle(graphics, x, y, pickerWidth(), height, CELL);
        rectangle(graphics, x, y, pickerWidth(), wGt(1), ACCENT);
        for (int i = 0; i < pickerCount(); i++) {
            double line = y + wGt(3) + i * pickerLine();
            if (line + pickerLine() > y + height) {
                break;
            }
            graphics.beI(x + wGt(8), line + wGt(12),
                    i == PICKER_CLEAR ? "Clear" : RENAME_CHOICES[i], text);
        }
        graphics.beI(x + wGt(8), y - wGt(6), "Name " + rowName(pickerRow), text.beI(0.7f));
    }

    private void chooseName(int choice) {
        int key = pickerRow;
        pickerRow = -1;
        if (key < 0) {
            return;
        }
        if (choice == PICKER_CLEAR) {
            rowNames.remove(key);
        } else {
            rowNames.put(key, RENAME_CHOICES[choice]);
        }
        OCx();
    }

    private String rowName(int key) {
        if (target != null) {
            for (SqClip.Row row : target.rows()) {
                if (row.key() == key) {
                    return rowLabel(row);
                }
            }
        }
        return Integer.toString(key);
    }

    private boolean isMuted(int key) { return mutedKeys.contains(key); }

    private void recomputeMuted() {
        if (target == null) {
            mutedKeys = Set.of();
            return;
        }
        Set<Integer> effective = new LinkedHashSet<>(mutedRows);
        if (!soloRows.isEmpty()) {
            for (SqClip.Row row : target.rows()) {
                if (!soloRows.contains(row.key())) {
                    effective.add(row.key());
                }
            }
        }
        mutedKeys = Set.copyOf(effective);
    }

    private void toggleMark(int key) {
        if (!markedRows.remove(key)) {
            markedRows.add(key);
        }
        shownRows = computeShown(target);
        firstRow = Math.min(firstRow, maxFirstRow());
        OCx();
    }

    private void toggleMute(int key) {
        if (!mutedRows.remove(key)) {
            mutedRows.add(key);
        }
        recomputeMuted();
        applyMute("Mute " + rowName(key));
        OCx();
    }

    private void toggleSolo(int key) {
        if (!soloRows.remove(key)) {
            soloRows.add(key);
        }
        recomputeMuted();
        applyMute("Solo " + rowName(key));
        OCx();
    }

    /**
     * Muting silences a row by taking its notes to velocity 0 and keeps the velocity
     * each note had, so unmuting puts it back. One undo step for the whole row, and
     * nothing is removed from the clip.
     */
    private void applyMute(String label) {
        if (target == null) {
            return;
        }
        try {
            Edit edit = PianoRoll.edit(label);
            for (SqGrid.Event<SqNote> event : target.notes(target.range().start(), target.range().end())) {
                Note note = event.note().note();
                Double saved = savedVelocity.get(note);
                if (mutedKeys.contains(event.key())) {
                    if (saved == null) {
                        savedVelocity.put(note, event.velocity());
                        edit.setVelocity(note, 0);
                    }
                } else if (saved != null) {
                    savedVelocity.remove(note);
                    edit.setVelocity(note, saved);
                }
            }
            edit.apply();
        } catch (Refused refused) {
            PianoRoll.notification(refused.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            RessSq.MOD.log("Could not mute the row", failure);
        }
    }

    /** The notes selected in the editor, empty when the selection holds none. */
    private Set<Note> selectedNotes() {
        try {
            NoteSelection current = selection;
            return current == null ? Set.of() : Set.copyOf(current.notes());
        } catch (RuntimeException refused) {
            return Set.of();
        }
    }

    private void changePage(int delta) {
        if (target != null) {
            page = Math.max(0, Math.min(target.range().pages() - 1, page + delta));
            playingColumn = -1;
            refresh();
            OCx();
        }
    }

    /**
     * The mouse wheel scrolls the pitch rows. Bitwig delivers it as its own event
     * class; iFu() is the wheel delta, the same value Bitwig's own widgets compare
     * against a step threshold.
     */
    @Override
    public void beI(dLp event) {
        try {
            if (view.active.getBoolValue() && target != null) {
                int delta = event.iFu();
                if (delta != 0) {
                    scrollRows(-Integer.signum(delta) * WHEEL_ROWS);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            RessSq.MOD.log("Could not scroll the step rows", failure);
        }
    }

    private void scrollRows(int delta) {
        if (target != null) {
            firstRow = Math.max(0, Math.min(maxFirstRow(), firstRow + delta));
            OCx();
        }
    }

    private final class CellGesture extends dtt {
        private final SqClip source;
        private final int sourcePage = page;
        private final int origin;
        private final double pressX;
        private final double pressY;
        private final SqGrid.Event<SqNote> note;
        private final Parameter parameter = SqWidget.this.parameter;
        private final BitSet pending = new BitSet();
        private final Map<Integer, SqGrid.Event<SqNote>> erased = new HashMap<>();
        private final boolean erase;
        private int previous;
        private int value;
        private boolean dragged;
        private boolean moved;

        CellGesture(SqClip source, int index, double pressX, double pressY, SqGrid.Event<SqNote> note, boolean erase) {
            this.source = source;
            origin = index;
            previous = index;
            this.pressX = pressX;
            this.pressY = pressY;
            this.note = note;
            this.erase = erase;
            value = note == null ? 0 : parameter.read(note);
        }

        @Override public boolean jC_() { return true; }

        @Override
        public void beI(dLf press) {
            gesture = this;
            if (note == null) {
                addToStroke(origin);
                OCx();
            }
        }

        @Override
        public void beI(dLk move) {
            if (gesture == this) {
                dragTo(move.wLf(), move.dvk());
                OCx();
            }
        }

        private void dragTo(double x, double y) {
            if (note == null) {
                int index = cellAt(x, y);
                if (index < 0) {
                    previous = -1;
                } else {
                    if (previous < 0) {
                        addToStroke(index);
                    } else {
                        SqGrid.trace(previous, index, this::addToStroke);
                    }
                    previous = index;
                }
                return;
            }
            double dy = pressY - y;
            dragged |= Math.abs(dy) >= wGt(4);
            moved |= dragged || Math.abs(pressX - x) >= wGt(4);
            if (dragged) {
                value = Math.clamp(parameter.read(note) + (int) Math.round(dy / Math.max(1, wGt(parameter.pixelsPerUnit))),
                        parameter.min, parameter.max);
            }
        }

        private void addToStroke(int index) {
            double start = source.range().beat(sourcePage, index % SqGrid.STEPS);
            double end = Math.min(start + SqGrid.STEP, source.range().end());
            int key = source.rows().get(index / SqGrid.STEPS).key();
            if (end <= start) {
                return;
            }
            SqGrid.Cell<SqNote> cell = SqGrid.cell(notes, key, source.channel(), start, end);
            if (erase && cell.note() != null) {
                erased.putIfAbsent(index, cell.note());
                pending.set(index);
            } else if (!erase && cell.canAdd()) {
                pending.set(index);
            }
        }

        @Override
        public void beI(dLg release) {
            try {
                if (gesture == this && view.active.getBoolValue()) {
                    dragTo(release.wLf(), release.dvk());
                    commit();
                    refresh();
                }
            } catch (Refused refused) {
                PianoRoll.notification(refused.getMessage());
            } catch (RuntimeException | LinkageError failure) {
                RessSq.MOD.log("Could not edit the step", failure);
            } finally {
                kwH();
            }
        }

        private void commit() {
            SqClip current = SqClip.read(editor);
            if (!matches(current)) {
                return;
            }
            if (note == null) {
                commitStroke(current);
                return;
            }
            double start = current.range().beat(sourcePage, origin % SqGrid.STEPS);
            double end = Math.min(start + SqGrid.STEP, current.range().end());
            int key = current.rows().get(origin / SqGrid.STEPS).key();
            SqGrid.Cell<SqNote> cell = SqGrid.cell(current.notes(start, end), key, current.channel(), start, end);
            if (note.equals(cell.note())) {
                if (dragged && value != parameter.read(note)) {
                    if (parameter.changesPlacement()) {
                        commitPlacement(current, cell.note(), start);
                    } else {
                        parameter.apply(cell.note().note(), value);
                    }
                } else if (!moved) {
                    PianoRoll.edit("Remove Step").remove(cell.note().note().note()).apply();
                }
            }
        }

        private String display() {
            if (parameter.changesPlacement()) {
                double start = source.range().beat(sourcePage, origin % SqGrid.STEPS);
                return parameter.displayPlacement(parameter.placement(note, start, source.range().end(), value), start);
            }
            return parameter.display(value);
        }

        private void commitPlacement(SqClip current, SqGrid.Event<SqNote> event, double cellStart) {
            SqGrid.Placement placement = parameter.placement(event, cellStart, current.range().end(), value);
            if (placement.equals(new SqGrid.Placement(event.key(), event.start(), event.end()))) {
                return;
            }
            if (!SqGrid.canPlace(event, placement, current.notes(placement.start(), placement.end()))) {
                throw new Refused("This edit would overlap another note on the same pitch and MIDI channel.");
            }
            var note = event.note().note();
            Edit edit = PianoRoll.edit("Set Step " + parameter.label);
            switch (parameter) {
                case OFFSET -> edit.move(note, placement.start());
                case LENGTH -> edit.setEnd(note, placement.end());
                case PITCH -> edit.setKey(note, placement.key());
                default -> throw new IllegalStateException();
            }
            edit.apply();
        }

        private boolean matches(SqClip current) {
            return current != null && current.clip().equals(source.clip()) && current.range().equals(source.range())
                    && current.channel() == source.channel() && current.rows().equals(source.rows()) && page == sourcePage;
        }

        private void commitStroke(SqClip current) {
            SqGrid.Range range = current.range();
            List<SqGrid.Event<SqNote>> existing = current.notes(range.beat(sourcePage, 0),
                    Math.min(range.end(), range.beat(sourcePage, SqGrid.STEPS)));
            Edit edit = PianoRoll.edit(erase ? "Erase Steps" : pending.cardinality() == 1 ? "Add Step" : "Draw Steps");
            for (int index = pending.nextSetBit(0); index >= 0; index = pending.nextSetBit(index + 1)) {
                double start = range.beat(sourcePage, index % SqGrid.STEPS);
                double end = Math.min(start + SqGrid.STEP, range.end());
                int key = current.rows().get(index / SqGrid.STEPS).key();
                SqGrid.Cell<SqNote> cell = SqGrid.cell(existing, key, current.channel(), start, end);
                if (erase && erased.get(index).equals(cell.note())) {
                    edit.remove(cell.note().note().note());
                } else if (!erase && cell.canAdd()) {
                    edit.add(current.clip(), current.channel(), key, start, end - start, source.velocity(), null);
                }
            }
            edit.apply();
        }

        @Override
        public void unz() {
            if (gesture == this) {
                gesture = null;
                OCx();
            }
            super.unz();
        }
    }
}
