package com.ress.sq;

import bitwig.obf.DrA; // native MIDI clip
import bitwig.obf.iC2; // a clip's loop range
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
    /** Purple, or Bitwig's orange; the header's swatch switches between them. */
    private static final SZB PURPLE = new SZB(0.66f, 0.42f, 0.96f, 1f);
    private static final SZB ORANGE = new SZB(1.00f, 0.54f, 0.06f, 1f);
    private static SZB ACCENT = PURPLE;
    private static final SZB PLAYHEAD = new SZB(0.98f, 0.71f, 0.32f, 1f);
    private static final SZB ERASE = new SZB(0.95f, 0.36f, 0.32f, 1f);
    // Lighter than a cell, so an unmarked row still shows its box.
    private static final SZB MARK_OFF = new SZB(0.40f, 0.42f, 0.45f, 1f);
    private static final int TOOLS = 4;
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
    /** Row state per clip, so switching clips and coming back finds it again. */
    private static final Map<String, RowState> STATES = new HashMap<>();
    private String stateKey;
    private RowState state = new RowState();

    private Set<Integer> mutedKeys = Set.of();

    /** Everything the label column remembers about one clip. */
    static final class RowState {
        final Set<Integer> marked = new HashSet<>();
        final Set<Integer> muted = new HashSet<>();
        final Set<Integer> solo = new HashSet<>();
        /** Rows whose notes the RND tool works on. */
        final Set<Integer> scoped = new HashSet<>();
        final Map<Integer, String> names = new HashMap<>();
        /** Velocities the mute replaced, so unmuting puts them back. */
        final Map<String, Double> savedVelocity = new HashMap<>();
        /** FOLD is part of the clip's state, so a rebuilt panel keeps it. */
        boolean fold;
    }
    private int pickerRow = -1;
    private final java.util.Random random = new java.util.Random();
    private CellGesture gesture;
    private dsB frames;
    private Listening changes;
    private boolean subscribing;
    private long nextRefresh;
    private long lastFrameTime;
    private int shownPressed = -2;
    private int wheelValue;
    private boolean wheelSeen;
    private boolean wheelLogged;

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
                case OFFSET -> Math.clamp(Math.round((placement.start() - start) / SqGrid.STEP * 100), -100, 99) + "%";
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

    /** Steps in a bar: 16 is a sixteenth note, 8 an eighth, 32 a thirty-second. */
    private int division = 16;
    /** Steps a page shows: sixteen or thirty-two. */
    private int columns = 16;

    SqWidget(bke editor, RessSq.View view) {
        this.editor = editor;
        this.view = view;
        try {
            division = switch (Integer.parseInt(RessSq.MOD.settings().get("grid", "16").trim())) {
                case 8 -> 8;
                case 32 -> 32;
                default -> 16;
            };
        } catch (NumberFormatException ignored) {
            division = 16;
        }
        try {
            columns = Integer.parseInt(RessSq.MOD.settings().get("steps", "16").trim()) == 32 ? 32 : 16;
        } catch (NumberFormatException ignored) {
            columns = 16;
        }
        applyGrid();
        if ("orange".equalsIgnoreCase(RessSq.MOD.settings().get("theme", "purple").trim())) {
            ACCENT = ORANGE;
        }
    }

    private void applyGrid() {
        SqGrid.configure(division, columns);
    }

    /** Cycles the grid: a sixteenth, an eighth, a thirty-second, and round again. */
    private void cycleDivision() {
        int next = switch (division) {
            case 16 -> 8;
            case 8 -> 32;
            default -> 16;
        };
        division = next;
        applyGrid();
        RessSq.MOD.settings().put("grid", Integer.toString(division));
        page = 0;
        refresh();
        OCx();
    }

    /** Switches the accent between the mod's purple and Bitwig's orange. */
    private void cycleTheme() {
        ACCENT = ACCENT == PURPLE ? ORANGE : PURPLE;
        RessSq.MOD.settings().put("theme", ACCENT == PURPLE ? "purple" : "orange");
        refresh();
        OCx();
    }

    /** Cycles how many steps a page shows, sixteen or thirty-two. */
    private void cycleColumns() {
        columns = columns == 16 ? 32 : 16;
        applyGrid();
        RessSq.MOD.settings().put("steps", Integer.toString(columns));
        page = 0;
        refresh();
        OCx();
    }

    private String divisionLabel() {
        return switch (division) {
            case 8 -> "1/8";
            case 32 -> "1/32";
            default -> "1/16";
        };
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
                frames = dsB.beI(frame -> {
                    if (!view.active.getBoolValue() || !TUb()) {
                        deactivate();
                    } else {
                        lastFrameTime = frame.sCA();
                        if (System.nanoTime() - nextRefresh >= 0) {
                            refresh();
                        }
                        followPlayhead();
                        if (RessSq.pressed != shownPressed) {
                            shownPressed = RessSq.pressed;
                            OCx();
                        }
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
                String key = current == null ? null : current.stateKey();
                if (key == null) {
                    state = new RowState();
                } else {
                    RowState known = STATES.get(key);
                    if (known == null && stateKey != null && current.clipIdentity().equals(identityOf(stateKey))) {
                        // The clip was lengthened or shortened: same clip, moved end.
                        known = state;
                        STATES.put(key, known);
                    } else if (known == null) {
                        known = loadState(key);
                        STATES.put(key, known);
                    }
                    state = known;
                }
                stateKey = key;
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

    private double labels() { return Math.min(wGt(240), rtK() * 0.44); }
    private double column() { return Math.max(1, (rtK() - labels()) / SqGrid.STEPS); }
    private double gridRight() { return labels() + SqGrid.STEPS * column(); }
    private double rowHeight() { return Math.max(wGt(13), Math.min(wGt(26), column() * 0.50)); }
    private double top() { return wGt(48); }
    private double bottom() { return Math.max(top(), cBV() - wGt(25)); }
    private double toolsWidth() { return wGt(130); }
    private double toolsStart() { return rtK() - toolsWidth(); }
    private double toolWidth() { return toolsWidth() / TOOLS; }
    private double tabStart() { return Math.max(wGt(150), Math.min(labels() + wGt(3), rtK() - toolsWidth() - PARAMETERS.length * wGt(52))); }
    private double tabWidth() { return Math.min(wGt(70), Math.max(1, (rtK() - toolsWidth() - tabStart()) / PARAMETERS.length)); }
    private int visibleRows() { return Math.max(1, (int) Math.ceil((bottom() - top()) / rowHeight())); }
    private int maxFirstRow() { return Math.max(0, shownRows.size() - visibleRows()); }

    private List<Integer> computeShown(SqClip clip) {
        if (clip == null) {
            return List.of();
        }
        List<Integer> shown = new ArrayList<>();
        for (int i = 0; i < clip.rows().size(); i++) {
            if (!state.fold || state.marked.contains(clip.rows().get(i).key())) {
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

    /** How long a painted note is: a sixteenth note, in beats. */
    private static final double NOTE_LENGTH = 0.25;
    private static final int PILL = 17;
    private static final int PILL_GAP = 4;
    private double pillsRight() { return labels() - wGt(7); }
    private double soloLeft() { return pillsRight() - wGt(PILL); }
    private double muteLeft() { return soloLeft() - wGt(PILL + PILL_GAP); }
    private double scopeLeft() { return muteLeft() - wGt(PILL + PILL_GAP); }
    private double renameLeft() { return wGt(4); }
    private double markLeft() { return wGt(4 + PILL + 4); }
    private double nameLeft() { return wGt(4 + PILL + 4 + 13); }
    /** RND and CLR touch the marked rows, or every row when none is marked. */
    private boolean inScope(int key) {
        return state.scoped.isEmpty() || state.scoped.contains(key);
    }

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
            graphics.beI(wGt(9), wGt(17), "RESS-SQ  ·  " + columns + " Steps  ·  " + divisionLabel(), text);
            if (target == null) {
                graphics.beI(wGt(9), wGt(45), "Open a single editable MIDI clip to use Steps.", text);
                return;
            }
            paintControls(graphics, text);
            if (playingColumn >= 0) {
                rectangle(graphics, labels() + playingColumn * column(), wGt(44), column() - wGt(2), wGt(2), PLAYHEAD);
            }
            paintGrid(graphics, text);
            if (pickerRow >= 0) {
                paintPicker(graphics, text);
            }
        } finally {
            graphics.jTs();
        }
    }

    private void paintControls(com.bitwig.graphics.ijY graphics, SZB text) {
        double right = gridRight() - wGt(6);
        rectangle(graphics, right - wGt(262), wGt(4), wGt(18), wGt(18), ACCENT);
        rectangle(graphics, right - wGt(224), wGt(4), wGt(42), wGt(18), CELL);
        graphics.beI(right - wGt(224) + centered(columns + " st", wGt(42)), wGt(17),
                columns + " st", text.beI(0.85f));
        rectangle(graphics, right - wGt(176), wGt(4), wGt(42), wGt(18), CELL);
        graphics.beI(right - wGt(176) + centered(divisionLabel(), wGt(42)), wGt(17),
                divisionLabel(), text.beI(0.85f));
        button(graphics, right - wGt(126), wGt(4), "‹", page > 0, text);
        String pages = (page + 1) + " / " + target.range().pages();
        graphics.beI(right - wGt(101) + centered(pages, wGt(50)), wGt(17), pages, text);
        button(graphics, right - wGt(26), wGt(4), "›", page + 1 < target.range().pages(), text);
        for (int column = 0; column < SqGrid.STEPS; column += 4) {
            graphics.beI(labels() + column * column() + wGt(3), wGt(41), Integer.toString(column + 1), text.beI(0.6f));
        }
        rectangle(graphics, 0, bottom(), rtK(), wGt(25), STRIP);
        double foldX = wGt(50);
        rectangle(graphics, foldX, bottom() + wGt(3), wGt(46), wGt(18), state.fold ? BEAT : CELL);
        if (state.fold) {
            rectangle(graphics, foldX, bottom() + wGt(20), wGt(46), wGt(1), ACCENT);
        }
        graphics.beI(foldX + wGt(8), bottom() + wGt(16), "FOLD", text.beI(state.fold ? 1f : 0.7f));
        // The MIDI key being played, once the fold button.
        String played = RessSq.pressedLabel();
        if (!played.isEmpty()) {
            rectangle(graphics, wGt(100), bottom() + wGt(3), wGt(46), wGt(18), BEAT);
            graphics.beI(wGt(100) + centered(played, wGt(46)), bottom() + wGt(16), played, text);
        }
        // Scroll the pitch rows a page at a time.
        button(graphics, upLeft(), bottom() + wGt(3), "▲", firstRow > 0, text);
        button(graphics, downLeft(), bottom() + wGt(3), "▼", firstRow < maxFirstRow(), text);
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
        // RESS-SQ's own tools: follow the playhead, generate, randomise, clear this page.
        String[] labels = {"FLW", "GEN", "RND", "CLR"};
        boolean[] on = {follow, true, true, true};
        for (int i = 0; i < TOOLS; i++) {
            double x = toolsStart() + i * toolWidth();
            rectangle(graphics, x + wGt(1), bottom() + wGt(3), toolWidth() - wGt(3), wGt(18),
                    i == 0 && follow ? BEAT : CELL);
            if (i == 0 && follow) {
                rectangle(graphics, x + wGt(1), bottom() + wGt(20), toolWidth() - wGt(3), wGt(1), ACCENT);
            }
            graphics.beI(x + centered(labels[i], toolWidth()), bottom() + wGt(15), labels[i],
                    text.beI(on[i] ? 0.8f : 0.5f));
        }
    }

    /** Every step cell the grid is showing on this page, row by row. */
    private interface CellVisitor {
        void accept(int key, SqGrid.Cell<SqNote> cell);
    }

    private void visitPageCells(CellVisitor visitor) {
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
                    visitor.accept(key, SqGrid.cell(notes, key, target.channel(), start, end));
                }
            }
        }
    }

    private void randomiseVelocities() {
        try {
            Edit edit = PianoRoll.edit("Randomise Step Velocities");
            int[] count = {0};
            visitPageCells((key, cell) -> {
                if (cell.note() != null && inScope(key)) {
                    // Keep it musical: a jittered band rather than noise across the whole range.
                    double velocity = 0.40 + random.nextDouble() * 0.60;
                    edit.setVelocity(cell.note().note().note(), velocity);
                    count[0]++;
                }
            });
            if (count[0] == 0) {
                PianoRoll.notification(state.scoped.isEmpty()
                        ? "RESS-SQ: no steps on this page."
                        : "RESS-SQ: no steps on the marked rows.");
                return;
            }
            edit.apply();
            refresh();
            OCx();
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
            visitPageCells((key, cell) -> {
                if (cell.note() != null && inScope(key)) {
                    edit.remove(cell.note().note().note());
                    count[0]++;
                }
            });
            if (count[0] == 0) {
                PianoRoll.notification(state.scoped.isEmpty()
                        ? "RESS-SQ: no steps on this page."
                        : "RESS-SQ: no steps on the marked rows.");
                return;
            }
            edit.apply();
            refresh();
            OCx();
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

    private double upLeft() { return wGt(4); }
    private double downLeft() { return wGt(26); }

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
        boolean scoped = state.scoped.contains(key);
        if (scoped) {
            rectangle(graphics, 0, y, labels() - wGt(2), height, ACCENT.beI(0.18f));
        }
        pill(graphics, renameLeft(), y, height, "N", pickerRow == key, text);
        rectangle(graphics, markLeft(), y + (height - dot) / 2, dot, dot,
                state.marked.contains(key) ? ACCENT : MARK_OFF);
        pill(graphics, scopeLeft(), y, height, "o", scoped, text);
        pill(graphics, muteLeft(), y, height, "M", state.muted.contains(key), text);
        pill(graphics, soloLeft(), y, height, "S", state.solo.contains(key), text);
        if (height >= wGt(11)) {
            String name = rowLabel(pitch);
            int limit = Math.max(3, (int) ((scopeLeft() - wGt(18)) / Math.max(1, wGt(7))));
            graphics.beI(nameLeft(), y + height * 0.5 + wGt(4),
                    name.length() > limit ? name.substring(0, Math.max(1, limit - 1)) + "…" : name,
                    text.beI(muted ? 0.45f : 1f));
        }
    }

    /** Centres a short label in a box of this width, by estimated glyph width. */
    private double centered(String label, double width) {
        double glyph = 0;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            glyph += c == 'M' || c == 'N' ? 9 : c == ' ' ? 4 : 6.5;
        }
        return Math.max(0, (width - wGt((long) Math.ceil(glyph))) / 2);
    }

    private void pill(com.bitwig.graphics.ijY graphics, double x, double y, double height,
            String label, boolean on, SZB text) {
        pill(graphics, x, y, height, label, on, text, false);
    }

    /** A pill with its own left and right padding, the glyph centred in it. */
    private void pill(com.bitwig.graphics.ijY graphics, double x, double y, double height,
            String label, boolean on, SZB text, boolean highlight) {
        SZB background = on ? ACCENT : highlight ? ACCENT.beI(0.35f) : CELL;
        rectangle(graphics, x, y + wGt(1), wGt(PILL), Math.max(wGt(3), height - wGt(2)), background);
        graphics.beI(x + centered(label, wGt(PILL)), y + height * 0.5 + wGt(3), label,
                text.beI(on || highlight ? 1f : 0.55f));
    }

    /** The row's name: the mod's own label when it has one, else Bitwig's pad or pitch name. */
    private String rowLabel(SqClip.Row pitch) {
        String custom = state.names.get(pitch.key());
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
        if (y >= bottom() && x >= upLeft() && x < upLeft() + wGt(25)) {
            return control(() -> scrollRows(-Math.max(1, visibleRows() - 1)));
        }
        if (y >= bottom() && x >= downLeft() && x < downLeft() + wGt(25)) {
            return control(() -> scrollRows(Math.max(1, visibleRows() - 1)));
        }
        if (y >= bottom() && x >= wGt(50) && x < wGt(96)) {
            return control(() -> {
                state.fold = !state.fold;
                shownRows = computeShown(target);
                firstRow = Math.min(firstRow, maxFirstRow());
                OCx();
            });
        }
        if (x < labels() - wGt(1) && y >= top() && y < bottom()) {
            int key = keyAt(y);
            if (key >= 0) {
                if (x >= renameLeft() && x < renameLeft() + wGt(PILL)) {
                    return control(() -> {
                        pickerRow = key;
                        OCx();
                    });
                }
                if (x >= markLeft() - wGt(2) && x < markLeft() + wGt(11)) {
                    return control(() -> toggleMark(key));
                }
                if (x >= scopeLeft() && x < scopeLeft() + wGt(PILL)) {
                    return control(() -> toggleScope(key));
                }
                if (x >= muteLeft() && x < muteLeft() + wGt(PILL)) {
                    return control(() -> toggleMute(key));
                }
                if (x >= soloLeft() && x < soloLeft() + wGt(PILL)) {
                    return control(() -> toggleSolo(key));
                }
                return null;
            }
        }
        double right = gridRight() - wGt(6);
        if (y < wGt(26) && x >= right - wGt(262) && x < right - wGt(244)) {
            return control(this::cycleTheme);
        }
        if (y < wGt(26) && x >= right - wGt(224) && x < right - wGt(182)) {
            return control(this::cycleColumns);
        }
        if (y < wGt(26) && x >= right - wGt(176) && x < right - wGt(134)) {
            return control(this::cycleDivision);
        }
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
                    case 1 -> generateScoped();
                    case 2 -> randomiseVelocities();
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
    private int pickerCount() { return RENAME_CHOICES.length + 1; }

    /** Rows shrink so the whole list, Clear included, fits the grid. */
    private double pickerLine() {
        double available = bottom() - pickerY() - wGt(10);
        return Math.min(wGt(17), Math.max(wGt(10), available / pickerCount()));
    }

    private double pickerHeight() { return pickerCount() * pickerLine() + wGt(6); }

    private void paintPicker(com.bitwig.graphics.ijY graphics, SZB text) {
        double x = pickerX();
        double y = pickerY();
        double height = pickerHeight();
        rectangle(graphics, x, y, pickerWidth(), height, CELL);
        rectangle(graphics, x, y, pickerWidth(), wGt(1), ACCENT);
        for (int i = 0; i < pickerCount(); i++) {
            double line = y + wGt(3) + i * pickerLine();
            graphics.beI(x + wGt(8), line + pickerLine() - wGt(5),
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
            state.names.remove(key);
        } else {
            state.names.put(key, RENAME_CHOICES[choice]);
        }
        save();
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

    private static String identityOf(String stateKey) {
        int cut = stateKey.lastIndexOf('|');
        return cut < 0 ? stateKey : stateKey.substring(0, cut);
    }

    private static String muteId(SqGrid.Event<SqNote> event) {
        return event.key() + "@" + Math.round(event.start() * 96);
    }

    private void recomputeMuted() {
        if (target == null) {
            mutedKeys = Set.of();
            return;
        }
        Set<Integer> effective = new LinkedHashSet<>(state.muted);
        if (!state.solo.isEmpty()) {
            for (SqClip.Row row : target.rows()) {
                if (!state.solo.contains(row.key())) {
                    effective.add(row.key());
                }
            }
        }
        mutedKeys = Set.copyOf(effective);
    }

    // ---- row state that survives a restart -------------------------------------

    private static String settingsPrefix(String stateKey) {
        String safe = stateKey.replaceAll("[^A-Za-z0-9_.-]", "_");
        return "clip." + safe + "." + Integer.toHexString(stateKey.hashCode());
    }

    private static RowState loadState(String stateKey) {
        RowState loaded = new RowState();
        try {
            bml.api.Settings settings = RessSq.MOD.settings();
            String prefix = settingsPrefix(stateKey);
            loaded.marked.addAll(parseInts(settings.get(prefix + "marked", "")));
            loaded.muted.addAll(parseInts(settings.get(prefix + "muted", "")));
            loaded.solo.addAll(parseInts(settings.get(prefix + "solo", "")));
            loaded.scoped.addAll(parseInts(settings.get(prefix + "scoped", "")));
            loaded.names.putAll(parseNames(settings.get(prefix + "names", "")));
            loaded.savedVelocity.putAll(parseValues(settings.get(prefix + "velocity", "")));
            loaded.fold = Boolean.parseBoolean(settings.get(prefix + "fold", "false"));
        } catch (RuntimeException | LinkageError failure) {
            RessSq.MOD.log("Could not load the row state", failure);
        }
        return loaded;
    }

    private void save() {
        if (stateKey == null) {
            return;
        }
        try {
            bml.api.Settings settings = RessSq.MOD.settings();
            String prefix = settingsPrefix(stateKey);
            settings.put(prefix + "marked", joinInts(state.marked));
            settings.put(prefix + "muted", joinInts(state.muted));
            settings.put(prefix + "solo", joinInts(state.solo));
            settings.put(prefix + "scoped", joinInts(state.scoped));
            settings.put(prefix + "names", joinNames(state.names));
            settings.put(prefix + "velocity", joinValues(state.savedVelocity));
            settings.put(prefix + "fold", Boolean.toString(state.fold));
        } catch (RuntimeException | LinkageError failure) {
            RessSq.MOD.log("Could not save the row state", failure);
        }
    }

    private static String joinInts(Set<Integer> keys) {
        StringBuilder out = new StringBuilder();
        for (int key : keys) {
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(key);
        }
        return out.toString();
    }

    private static Set<Integer> parseInts(String text) {
        Set<Integer> keys = new HashSet<>();
        for (String part : text.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                try {
                    keys.add(Integer.parseInt(trimmed));
                } catch (NumberFormatException ignored) {
                    // skip a damaged entry
                }
            }
        }
        return keys;
    }

    private static String joinNames(Map<Integer, String> names) {
        StringBuilder out = new StringBuilder();
        names.forEach((key, value) -> {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(key).append('=').append(value);
        });
        return out.toString();
    }

    private static Map<Integer, String> parseNames(String text) {
        Map<Integer, String> names = new HashMap<>();
        for (String part : text.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) {
                try {
                    names.put(Integer.parseInt(part.substring(0, split).trim()),
                            part.substring(split + 1).trim());
                } catch (NumberFormatException ignored) {
                    // skip a damaged entry
                }
            }
        }
        return names;
    }

    private static String joinValues(Map<String, Double> values) {
        StringBuilder out = new StringBuilder();
        values.forEach((key, value) -> {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(key).append('=').append(value);
        });
        return out.toString();
    }

    private static Map<String, Double> parseValues(String text) {
        Map<String, Double> values = new HashMap<>();
        for (String part : text.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) {
                try {
                    values.put(part.substring(0, split).trim(), Double.parseDouble(part.substring(split + 1).trim()));
                } catch (NumberFormatException ignored) {
                    // skip a damaged entry
                }
            }
        }
        return values;
    }

    private void toggleMark(int key) {
        if (!state.marked.remove(key)) {
            state.marked.add(key);
        }
        shownRows = computeShown(target);
        firstRow = Math.min(firstRow, maxFirstRow());
        save();
        OCx();
    }

    /**
     * Clears every row marked with o on this page and fills each with a fresh random
     * pattern: a random number of steps, in random columns, all in one Undo step. Cells
     * held by a note starting on an earlier page are skipped so a pattern never cuts one.
     */
    private void generateScoped() {
        if (target == null) {
            return;
        }
        if (state.scoped.isEmpty()) {
            PianoRoll.notification("RESS-SQ: mark rows with o first.");
            return;
        }
        try {
            double pageStart = target.range().beat(page, 0);
            double pageEnd = Math.min(target.range().end(), target.range().beat(page, SqGrid.STEPS));
            if (pageEnd <= pageStart) {
                return;
            }
            Edit edit = PianoRoll.edit("Generate Marked Rows");
            int added = 0;
            int rows = 0;
            StringBuilder keys = new StringBuilder();
            for (SqClip.Row row : target.rows()) {
                if (state.scoped.contains(row.key())) {
                    added += generateInto(edit, row.key(), pageStart, pageEnd);
                    rows++;
                    keys.append(row.key()).append(' ');
                }
            }
            RessSq.MOD.log("generate: scoped=" + state.scoped.size() + " rows=" + rows + " keys=" + keys);
            if (added == 0) {
                PianoRoll.notification("RESS-SQ: nothing to generate on the o rows.");
                return;
            }
            PianoRoll.notification("RESS-SQ: generated " + rows + " row" + (rows == 1 ? "" : "s"));
            edit.apply();
            refresh();
            OCx();
        } catch (Refused refused) {
            PianoRoll.notification(refused.getMessage());
        } catch (RuntimeException | LinkageError failure) {
            RessSq.MOD.log("Could not generate the marked rows", failure);
        }
    }

    /** Replaces one row's pattern inside a shared plan; returns how many notes it added. */
    private int generateInto(Edit edit, int key, double pageStart, double pageEnd) {
        for (SqGrid.Event<SqNote> event : target.notes(pageStart, pageEnd)) {
            if (event.key() == key && event.start() >= pageStart - 1e-9 && event.start() < pageEnd - 1e-9) {
                edit.remove(event.note().note());
            }
        }
        java.util.List<Integer> columns = new ArrayList<>();
        for (int column = 0; column < SqGrid.STEPS; column++) {
            double start = target.range().beat(page, column);
            double end = Math.min(start + SqGrid.STEP, target.range().end());
            boolean continuation = end > start
                    && SqGrid.cell(notes, key, target.channel(), start, end).continuation();
            if (!continuation) {
                columns.add(column);
            }
        }
        if (columns.isEmpty()) {
            return 0;
        }
        java.util.Collections.shuffle(columns, random);
        int wanted = 1 + random.nextInt(columns.size());
        int added = 0;
        for (int i = 0; i < wanted; i++) {
            int column = columns.get(i);
            double start = target.range().beat(page, column);
            double end = Math.min(start + SqGrid.STEP, target.range().end());
            if (end <= start) {
                continue;
            }
            edit.add(target.clip(), target.channel(), key, start, end - start, target.velocity(), null);
            added++;
        }
        return added;
    }

    /** Marks this row for RND and CLR; with no row marked they work on every row shown. */
    private void toggleScope(int key) {
        if (!state.scoped.remove(key)) {
            state.scoped.add(key);
        }
        save();
        OCx();
    }

    /**
     * Selects that key's notes in Bitwig's own note editor, so the group can be edited with
     * Bitwig's tools. The selection object's setter is tried and the result read back through
     * the piano-roll framework, which never changes anything.
     */
    private void toggleMute(int key) {
        if (!state.muted.remove(key)) {
            state.muted.add(key);
        }
        recomputeMuted();
        applyMute("Mute " + rowName(key));
        save();
        OCx();
    }

    private void toggleSolo(int key) {
        if (!state.solo.remove(key)) {
            state.solo.add(key);
        }
        recomputeMuted();
        applyMute("Solo " + rowName(key));
        save();
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
                String id = muteId(event);
                Double saved = state.savedVelocity.get(id);
                if (mutedKeys.contains(event.key())) {
                    if (saved == null) {
                        state.savedVelocity.put(id, event.velocity());
                        edit.setVelocity(note, 0);
                    }
                } else if (saved != null) {
                    state.savedVelocity.remove(id);
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
            int value = event.iFu();
            if (!wheelLogged) {
                wheelLogged = true;
                RessSq.MOD.log("wheel: id=" + event.fmw() + " value=" + value
                        + " x=" + event.wLf() + " y=" + event.dvk());
            }
            if (!view.active.getBoolValue() || target == null) {
                return;
            }
            // The payload is a running value on some mice and a notch count on others.
            int delta;
            if (Math.abs(value) <= 8) {
                delta = value;
            } else {
                if (!wheelSeen) {
                    wheelSeen = true;
                    wheelValue = value;
                    return;
                }
                delta = value - wheelValue;
                wheelValue = value;
            }
            if (delta != 0) {
                scrollRows(-Integer.signum(delta) * WHEEL_ROWS);
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
                // cellAt already gave a position in the clip's own rows: the slot it was drawn
                // on has been mapped through the shown rows there, so this is not a slot index.
                int rowIndex = index / SqGrid.STEPS;
                if (rowIndex < 0 || rowIndex >= current.rows().size()) {
                    continue;
                }
                int key = current.rows().get(rowIndex).key();
                SqGrid.Cell<SqNote> cell = SqGrid.cell(existing, key, current.channel(), start, end);
                if (erase && erased.get(index).equals(cell.note())) {
                    edit.remove(cell.note().note().note());
                } else if (!erase && cell.canAdd()) {
                    // With FOLD on, a stroke may only write to the keys it can show: anything
                    // folded away is left untouched, and no existing note is removed.
                    if (state.fold && !state.marked.contains(key)) {
                        continue;
                    }
                    // A new note is a sixteenth long by default; on a finer grid it takes the
                    // step's own length so it never runs past its cell.
                    edit.add(current.clip(), current.channel(), key, start,
                            Math.min(NOTE_LENGTH, SqGrid.STEP), source.velocity(), null);
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
