import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * LightUpLocalSearchSolver
 *
 * A local-search solver for the Light Up / Akari puzzle.
 * This is intentionally NOT a CSP backtracking solver.
 *
 * Algorithm used:
 *   Random-restart Simulated Annealing with single-cell flip neighbors.
 *
 * Input format:
 *   R C
 *   <row 1>
 *   ...
 *   <row R>
 *
 * Symbols:
 *   .  white cell
 *   #  black wall
 *   0-4 numbered black wall
 *
 * Usage:
 *   javac LightUpLocalSearchSolver.java
 *   java LightUpLocalSearchSolver puzzle.txt
 *
 * Optional parameters:
 *   java LightUpLocalSearchSolver puzzle.txt --restarts 200 --steps 300000 --seed 7
 */
public class LightUpLocalSearchSolver {
    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    private static final long WEIGHT_UNLIT = 100L;
    private static final long WEIGHT_CONFLICT = 500L;
    private static final long WEIGHT_CLUE = 1000L;

    private final int rows;
    private final int cols;
    private final char[][] grid;
    private final int[][] idAt;
    private final ArrayList<Cell> variables = new ArrayList<>();
    private final ArrayList<Clue> clues = new ArrayList<>();

    private int variableCount;
    private int clueCountTotal;

    private int[] rowSegmentOf;
    private int[] colSegmentOf;
    private int rowSegmentCount;
    private int colSegmentCount;
    private int[][] rowSegmentCells;
    private int[][] colSegmentCells;

    private int[][] varsAroundClue;
    private int[][] adjacentCluesOfVar;

    private final Random random;

    private State bestStateEver;
    private long statesEvaluated = 0;

    private record Cell(int r, int c) {}
    private record Clue(int r, int c, int value) {}

    public LightUpLocalSearchSolver(char[][] grid, long seed) {
        this.rows = grid.length;
        this.cols = grid.length == 0 ? 0 : grid[0].length;
        this.grid = grid;
        this.idAt = new int[rows][cols];
        this.random = new Random(seed);

        indexBoard();
        precomputeSegments();
        precomputeClues();
    }

    private void indexBoard() {
        for (int r = 0; r < rows; r++) {
            Arrays.fill(idAt[r], -1);
        }

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                char ch = grid[r][c];
                if (ch == '.') {
                    idAt[r][c] = variables.size();
                    variables.add(new Cell(r, c));
                } else if (ch == '#') {
                    // Plain black cell.
                } else if (ch >= '0' && ch <= '4') {
                    clues.add(new Clue(r, c, ch - '0'));
                } else {
                    throw new IllegalArgumentException("Invalid character in puzzle: '" + ch + "'");
                }
            }
        }

        variableCount = variables.size();
        clueCountTotal = clues.size();
    }

    private boolean isBlack(int r, int c) {
        char ch = grid[r][c];
        return ch == '#' || (ch >= '0' && ch <= '4');
    }

    private boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    private void precomputeSegments() {
        rowSegmentOf = new int[variableCount];
        colSegmentOf = new int[variableCount];
        Arrays.fill(rowSegmentOf, -1);
        Arrays.fill(colSegmentOf, -1);

        ArrayList<ArrayList<Integer>> rowLists = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                if (isBlack(r, c)) {
                    c++;
                    continue;
                }
                ArrayList<Integer> segment = new ArrayList<>();
                while (c < cols && !isBlack(r, c)) {
                    int id = idAt[r][c];
                    if (id >= 0) {
                        rowSegmentOf[id] = rowLists.size();
                        segment.add(id);
                    }
                    c++;
                }
                rowLists.add(segment);
            }
        }

        ArrayList<ArrayList<Integer>> colLists = new ArrayList<>();
        for (int c = 0; c < cols; c++) {
            int r = 0;
            while (r < rows) {
                if (isBlack(r, c)) {
                    r++;
                    continue;
                }
                ArrayList<Integer> segment = new ArrayList<>();
                while (r < rows && !isBlack(r, c)) {
                    int id = idAt[r][c];
                    if (id >= 0) {
                        colSegmentOf[id] = colLists.size();
                        segment.add(id);
                    }
                    r++;
                }
                colLists.add(segment);
            }
        }

        rowSegmentCells = to2DIntArray(rowLists);
        colSegmentCells = to2DIntArray(colLists);
        rowSegmentCount = rowSegmentCells.length;
        colSegmentCount = colSegmentCells.length;
    }

    private void precomputeClues() {
        @SuppressWarnings("unchecked")
        ArrayList<Integer>[] clueNeighborLists = new ArrayList[clueCountTotal];
        @SuppressWarnings("unchecked")
        ArrayList<Integer>[] variableClueLists = new ArrayList[variableCount];

        for (int i = 0; i < clueCountTotal; i++) clueNeighborLists[i] = new ArrayList<>();
        for (int i = 0; i < variableCount; i++) variableClueLists[i] = new ArrayList<>();

        for (int clueIndex = 0; clueIndex < clueCountTotal; clueIndex++) {
            Clue clue = clues.get(clueIndex);
            for (int d = 0; d < 4; d++) {
                int nr = clue.r + DR[d];
                int nc = clue.c + DC[d];
                if (inBounds(nr, nc) && idAt[nr][nc] >= 0) {
                    int var = idAt[nr][nc];
                    clueNeighborLists[clueIndex].add(var);
                    variableClueLists[var].add(clueIndex);
                }
            }
        }

        varsAroundClue = to2DIntArray(Arrays.asList(clueNeighborLists));
        adjacentCluesOfVar = to2DIntArray(Arrays.asList(variableClueLists));
    }

    private static int[][] to2DIntArray(List<? extends List<Integer>> lists) {
        int[][] result = new int[lists.size()][];
        for (int i = 0; i < lists.size(); i++) {
            List<Integer> list = lists.get(i);
            result[i] = new int[list.size()];
            for (int j = 0; j < list.size(); j++) result[i][j] = list.get(j);
        }
        return result;
    }

    /**
     * Runs random-restart simulated annealing.
     * Returns true if an exact zero-cost solution is found.
     */
    public boolean solve(int restarts, int stepsPerRestart) {
        if (variableCount == 0) {
            State empty = new State();
            empty.recomputeAll();
            bestStateEver = empty;
            return empty.isSolution();
        }

        bestStateEver = null;
        statesEvaluated = 0;

        for (int restart = 1; restart <= restarts; restart++) {
            State state = randomInitialState(restart);
            updateBest(state);
            if (state.isSolution()) return true;

            double startTemperature = Math.max(10.0, state.weightedCost() / 10.0);
            double endTemperature = 0.01;

            for (int step = 1; step <= stepsPerRestart; step++) {
                int var = chooseVariableForNeighbor(state);
                long before = state.weightedCost();

                state.flip(var);
                long after = state.weightedCost();
                long delta = after - before;

                double progress = step / (double) stepsPerRestart;
                double temperature = startTemperature * Math.pow(endTemperature / startTemperature, progress);

                boolean accept = delta <= 0 || random.nextDouble() < Math.exp(-delta / temperature);
                if (!accept) {
                    state.flip(var); // undo rejected move
                } else {
                    updateBest(state);
                    if (state.isSolution()) return true;
                }
            }
        }

        return bestStateEver != null && bestStateEver.isSolution();
    }

    private State randomInitialState(int restart) {
        State state = new State();

        // Vary the initial bulb density by restart. This improves diversity.
        double[] probabilities = {0.15, 0.25, 0.35, 0.45, 0.55};
        double p = probabilities[restart % probabilities.length];

        for (int var = 0; var < variableCount; var++) {
            if (random.nextDouble() < p) state.bulb[var] = true;
        }

        state.recomputeAll();
        return state;
    }

    /**
     * Mostly chooses a variable involved in a current violation, but sometimes chooses a random variable.
     * This is still local search: it only chooses a neighboring state by flipping one variable.
     */
    private int chooseVariableForNeighbor(State state) {
        if (random.nextDouble() < 0.20) return random.nextInt(variableCount);

        int bestVar = random.nextInt(variableCount);
        int bestPain = -1;

        // Sample a small number of variables and choose the one that seems most problematic.
        int samples = Math.min(40, variableCount);
        for (int i = 0; i < samples; i++) {
            int var = random.nextInt(variableCount);
            int pain = localPain(state, var);
            if (pain > bestPain) {
                bestPain = pain;
                bestVar = var;
            }
        }

        return bestVar;
    }

    private int localPain(State state, int var) {
        int pain = 0;

        if (!state.isLit(var)) pain += 5;

        int rowBulbs = state.rowBulbCount[rowSegmentOf[var]];
        int colBulbs = state.colBulbCount[colSegmentOf[var]];
        if (state.bulb[var]) {
            pain += Math.max(0, rowBulbs - 1) * 4;
            pain += Math.max(0, colBulbs - 1) * 4;
        }

        for (int clueIndex : adjacentCluesOfVar[var]) {
            int required = clues.get(clueIndex).value;
            pain += Math.abs(state.clueBulbCount[clueIndex] - required) * 6;
        }

        return pain;
    }

    private void updateBest(State state) {
        statesEvaluated++;
        if (bestStateEver == null || state.weightedCost() < bestStateEver.weightedCost()) {
            bestStateEver = state.copy();
        }
    }

    public char[][] bestGrid() {
        if (bestStateEver == null) throw new IllegalStateException("Solver has not been run yet.");
        return gridForState(bestStateEver);
    }

    private char[][] gridForState(State state) {
        char[][] out = new char[rows][cols];
        for (int r = 0; r < rows; r++) out[r] = Arrays.copyOf(grid[r], cols);
        for (int var = 0; var < variableCount; var++) {
            Cell cell = variables.get(var);
            out[cell.r][cell.c] = state.bulb[var] ? 'B' : '.';
        }
        return out;
    }

    public long getStatesEvaluated() {
        return statesEvaluated;
    }

    public String bestViolationSummary() {
        if (bestStateEver == null) return "No state evaluated.";
        return "cost=" + bestStateEver.weightedCost()
                + ", unlitWhiteCells=" + bestStateEver.unlitCells
                + ", bulbConflictPairs=" + bestStateEver.conflictPairs
                + ", clueDeviation=" + bestStateEver.clueDeviation;
    }

    private final class State {
        boolean[] bulb = new boolean[variableCount];
        int[] rowBulbCount = new int[rowSegmentCount];
        int[] colBulbCount = new int[colSegmentCount];
        int[] clueBulbCount = new int[clueCountTotal];

        long unlitCells;
        long conflictPairs;
        long clueDeviation;

        State copy() {
            State copied = new State();
            copied.bulb = Arrays.copyOf(bulb, bulb.length);
            copied.rowBulbCount = Arrays.copyOf(rowBulbCount, rowBulbCount.length);
            copied.colBulbCount = Arrays.copyOf(colBulbCount, colBulbCount.length);
            copied.clueBulbCount = Arrays.copyOf(clueBulbCount, clueBulbCount.length);
            copied.unlitCells = unlitCells;
            copied.conflictPairs = conflictPairs;
            copied.clueDeviation = clueDeviation;
            return copied;
        }

        long weightedCost() {
            return WEIGHT_UNLIT * unlitCells
                    + WEIGHT_CONFLICT * conflictPairs
                    + WEIGHT_CLUE * clueDeviation;
        }

        boolean isSolution() {
            return unlitCells == 0 && conflictPairs == 0 && clueDeviation == 0;
        }

        boolean isLit(int var) {
            return rowBulbCount[rowSegmentOf[var]] + colBulbCount[colSegmentOf[var]] > 0;
        }

        void recomputeAll() {
            Arrays.fill(rowBulbCount, 0);
            Arrays.fill(colBulbCount, 0);
            Arrays.fill(clueBulbCount, 0);

            for (int var = 0; var < variableCount; var++) {
                if (bulb[var]) {
                    rowBulbCount[rowSegmentOf[var]]++;
                    colBulbCount[colSegmentOf[var]]++;
                    for (int clueIndex : adjacentCluesOfVar[var]) {
                        clueBulbCount[clueIndex]++;
                    }
                }
            }

            unlitCells = 0;
            for (int var = 0; var < variableCount; var++) {
                if (!isLit(var)) unlitCells++;
            }

            conflictPairs = 0;
            for (int count : rowBulbCount) conflictPairs += chooseTwo(count);
            for (int count : colBulbCount) conflictPairs += chooseTwo(count);

            clueDeviation = 0;
            for (int i = 0; i < clueCountTotal; i++) {
                clueDeviation += Math.abs(clueBulbCount[i] - clues.get(i).value);
            }
        }

        void flip(int var) {
            int rs = rowSegmentOf[var];
            int cs = colSegmentOf[var];

            long beforeUnlit = unlitInAffectedSegments(rs, cs);
            long beforeConflicts = chooseTwo(rowBulbCount[rs]) + chooseTwo(colBulbCount[cs]);
            long beforeClueDeviation = clueDeviationAround(var);

            if (bulb[var]) {
                bulb[var] = false;
                rowBulbCount[rs]--;
                colBulbCount[cs]--;
                for (int clueIndex : adjacentCluesOfVar[var]) clueBulbCount[clueIndex]--;
            } else {
                bulb[var] = true;
                rowBulbCount[rs]++;
                colBulbCount[cs]++;
                for (int clueIndex : adjacentCluesOfVar[var]) clueBulbCount[clueIndex]++;
            }

            long afterUnlit = unlitInAffectedSegments(rs, cs);
            long afterConflicts = chooseTwo(rowBulbCount[rs]) + chooseTwo(colBulbCount[cs]);
            long afterClueDeviation = clueDeviationAround(var);

            unlitCells += afterUnlit - beforeUnlit;
            conflictPairs += afterConflicts - beforeConflicts;
            clueDeviation += afterClueDeviation - beforeClueDeviation;
        }

        private long unlitInAffectedSegments(int rs, int cs) {
            long count = 0;
            for (int var : rowSegmentCells[rs]) {
                if (!isLit(var)) count++;
            }
            for (int var : colSegmentCells[cs]) {
                if (rowSegmentOf[var] != rs && !isLit(var)) count++;
            }
            return count;
        }

        private long clueDeviationAround(int var) {
            long total = 0;
            for (int clueIndex : adjacentCluesOfVar[var]) {
                int required = clues.get(clueIndex).value;
                total += Math.abs(clueBulbCount[clueIndex] - required);
            }
            return total;
        }
    }

    private static long chooseTwo(long n) {
        return n <= 1 ? 0 : n * (n - 1) / 2;
    }

    public static char[][] parseBoard(List<String> rawLines) {
        ArrayList<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            String cleaned = line.trim();
            if (!cleaned.isEmpty()) lines.add(cleaned);
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("Empty input.");

        String[] firstParts = lines.get(0).trim().split("\\s+");
        int start = 0;
        int expectedRows = -1;
        int expectedCols = -1;

        if (firstParts.length == 2 && isInteger(firstParts[0]) && isInteger(firstParts[1])) {
            expectedRows = Integer.parseInt(firstParts[0]);
            expectedCols = Integer.parseInt(firstParts[1]);
            start = 1;
        }

        ArrayList<String> boardLines = new ArrayList<>();
        for (int i = start; i < lines.size(); i++) boardLines.add(removeSpaces(lines.get(i)));

        if (expectedRows != -1 && boardLines.size() != expectedRows) {
            throw new IllegalArgumentException("Expected " + expectedRows + " rows, got " + boardLines.size() + ".");
        }
        if (boardLines.isEmpty()) throw new IllegalArgumentException("No board rows found.");

        int cols = boardLines.get(0).length();
        if (expectedCols != -1 && cols != expectedCols) {
            throw new IllegalArgumentException("Expected " + expectedCols + " columns, got " + cols + ".");
        }

        char[][] board = new char[boardLines.size()][cols];
        for (int r = 0; r < boardLines.size(); r++) {
            String row = boardLines.get(r);
            if (row.length() != cols) {
                throw new IllegalArgumentException("All rows must have the same length.");
            }
            board[r] = row.toCharArray();
        }
        return board;
    }

    private static String removeSpaces(String s) {
        return s.replaceAll("\\s+", "");
    }

    private static boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static void printGrid(char[][] board) {
        for (char[] row : board) System.out.println(new String(row));
    }

    private static int getIntOption(String[] args, String name, int defaultValue) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) return Integer.parseInt(args[i + 1]);
        }
        return defaultValue;
    }

    private static long getLongOption(String[] args, String name, long defaultValue) {
        for (int i = 0; i + 1 < args.length; i++) {
            if (args[i].equals(name)) return Long.parseLong(args[i + 1]);
        }
        return defaultValue;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java LightUpLocalSearchSolver puzzle.txt [--restarts N] [--steps N] [--seed N]");
            System.out.println("Or provide the puzzle through standard input.");
        }

        List<String> lines;
        int optionStart = 0;
        if (args.length > 0 && !args[0].startsWith("--")) {
            lines = Files.readAllLines(Path.of(args[0]));
            optionStart = 1;
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        }

        String[] options = Arrays.copyOfRange(args, optionStart, args.length);
        int restarts = getIntOption(options, "--restarts", 100);
        int steps = getIntOption(options, "--steps", 200_000);
        long seed = getLongOption(options, "--seed", System.currentTimeMillis());

        char[][] board = parseBoard(lines);
        LightUpLocalSearchSolver solver = new LightUpLocalSearchSolver(board, seed);

        long start = System.currentTimeMillis();
        boolean solved = solver.solve(restarts, steps);
        long end = System.currentTimeMillis();

        if (solved) {
            System.out.println("Solution found by local search:");
            printGrid(solver.bestGrid());
        } else {
            System.out.println("No exact solution found by local search.");
            System.out.println("Best state found:");
            printGrid(solver.bestGrid());
            System.out.println("Best violations: " + solver.bestViolationSummary());
            System.out.println("Note: local search is incomplete, so this does not prove the puzzle is unsolvable.");
        }

        System.out.println("States evaluated: " + solver.getStatesEvaluated());
        System.out.println("Time ms: " + (end - start));
        System.out.println("Seed: " + seed);
        System.out.println("Restarts: " + restarts + ", steps per restart: " + steps);
    }
}
