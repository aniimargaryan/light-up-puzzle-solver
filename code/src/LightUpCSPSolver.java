import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * CSP solver for the Light Up / Akari puzzle.
 *
 * Input format:
 *   Either:
 *     R C
 *     <R rows using '.', '#', '0'..'4'>
 *   or only the grid rows, with no size line.
 *
 * Symbols:
 *   '.' = white cell
 *   '#' = black cell without a number
 *   '0'..'4' = numbered black cell
 *
 * Output symbols:
 *   'B' = bulb
 *   '.' = empty white cell
 *   black cells are printed unchanged
 */
public class LightUpCSPSolver {
    private final int rows;
    private final int cols;
    private final char[][] grid;
    private final int[][] idAt;
    private final ArrayList<Cell> variables = new ArrayList<>();
    private final ArrayList<Clue> clues = new ArrayList<>();

    // For each white-cell variable id:
    // illuminate[id] contains all white cells lit by a bulb at id, including id itself.
    // conflicts[id] contains all white cells that cannot also contain a bulb if id has a bulb.
    private int[][] illuminate;
    private int[][] conflicts;
    private int[][] adjacentCluesOfVar;
    private int[][] varsAroundClue;
    private int[] degree;

    // assignment[id] is -1 unknown, 0 empty, 1 bulb.
    private int[] assignment;
    private long nodesVisited = 0;

    private static final int UNKNOWN = -1;
    private static final int EMPTY = 0;
    private static final int BULB = 1;

    private static final int[] DR = {-1, 1, 0, 0};
    private static final int[] DC = {0, 0, -1, 1};

    private record Cell(int r, int c) {}
    private record Clue(int r, int c, int value) {}

    public LightUpCSPSolver(char[][] grid) {
        this.rows = grid.length;
        this.cols = grid.length == 0 ? 0 : grid[0].length;
        this.grid = grid;
        this.idAt = new int[rows][cols];
        for (int r = 0; r < rows; r++) {
            Arrays.fill(idAt[r], -1);
        }
        indexBoard();
        precomputeRelations();
        this.assignment = new int[variables.size()];
        Arrays.fill(assignment, UNKNOWN);
    }

    private void indexBoard() {
        for (int r = 0; r < rows; r++) {
            if (grid[r].length != cols) {
                throw new IllegalArgumentException("All rows must have the same length.");
            }
            for (int c = 0; c < cols; c++) {
                char ch = grid[r][c];
                if (ch == '.') {
                    int id = variables.size();
                    idAt[r][c] = id;
                    variables.add(new Cell(r, c));
                } else if (ch == '#') {
                    // Unnumbered black cell.
                } else if (ch >= '0' && ch <= '4') {
                    clues.add(new Clue(r, c, ch - '0'));
                } else {
                    throw new IllegalArgumentException("Invalid character '" + ch + "' at row " + (r + 1) + ", column " + (c + 1));
                }
            }
        }
    }

    private boolean isBlack(int r, int c) {
        return grid[r][c] == '#' || (grid[r][c] >= '0' && grid[r][c] <= '4');
    }

    private boolean inBounds(int r, int c) {
        return 0 <= r && r < rows && 0 <= c && c < cols;
    }

    private void precomputeRelations() {
        int n = variables.size();
        illuminate = new int[n][];
        conflicts = new int[n][];
        adjacentCluesOfVar = new int[n][];
        varsAroundClue = new int[clues.size()][];
        degree = new int[n];

        ArrayList<ArrayList<Integer>> cluesForVar = new ArrayList<>();
        for (int i = 0; i < n; i++) cluesForVar.add(new ArrayList<>());

        for (int clueIndex = 0; clueIndex < clues.size(); clueIndex++) {
            Clue clue = clues.get(clueIndex);
            ArrayList<Integer> around = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int nr = clue.r + DR[d];
                int nc = clue.c + DC[d];
                if (inBounds(nr, nc) && idAt[nr][nc] >= 0) {
                    int var = idAt[nr][nc];
                    around.add(var);
                    cluesForVar.get(var).add(clueIndex);
                }
            }
            varsAroundClue[clueIndex] = toIntArray(around);
        }

        for (int id = 0; id < n; id++) {
            Cell cell = variables.get(id);
            LinkedHashSet<Integer> lit = new LinkedHashSet<>();
            lit.add(id);
            for (int d = 0; d < 4; d++) {
                int nr = cell.r + DR[d];
                int nc = cell.c + DC[d];
                while (inBounds(nr, nc) && !isBlack(nr, nc)) {
                    lit.add(idAt[nr][nc]);
                    nr += DR[d];
                    nc += DC[d];
                }
            }
            illuminate[id] = toIntArray(lit);

            ArrayList<Integer> conf = new ArrayList<>();
            for (int other : illuminate[id]) {
                if (other != id) conf.add(other);
            }
            conflicts[id] = toIntArray(conf);
            adjacentCluesOfVar[id] = toIntArray(cluesForVar.get(id));
            degree[id] = conflicts[id].length + adjacentCluesOfVar[id].length * 3;
        }
    }

    private static int[] toIntArray(Collection<Integer> values) {
        int[] result = new int[values.size()];
        int i = 0;
        for (int value : values) result[i++] = value;
        return result;
    }

    public boolean solve() {
        nodesVisited = 0;
        return backtrack();
    }

    public long getNodesVisited() {
        return nodesVisited;
    }

    private boolean backtrack() {
        nodesVisited++;
        if (!isConsistentPartial()) return false;

        int var = selectUnassignedVariableMRV();
        if (var == -1) return isCompleteSolution();

        int[] order = preferredValueOrder(var);
        for (int value : order) {
            if (canAssign(var, value)) {
                assignment[var] = value;
                if (backtrack()) return true;
                assignment[var] = UNKNOWN;
            }
        }
        return false;
    }

    private int selectUnassignedVariableMRV() {
        int bestVar = -1;
        int bestDomainSize = Integer.MAX_VALUE;
        int bestDegree = -1;

        for (int id = 0; id < assignment.length; id++) {
            if (assignment[id] != UNKNOWN) continue;

            int domainSize = 0;
            if (canAssign(id, BULB)) domainSize++;
            if (canAssign(id, EMPTY)) domainSize++;

            if (domainSize == 0) return id;
            if (domainSize < bestDomainSize || (domainSize == bestDomainSize && degree[id] > bestDegree)) {
                bestVar = id;
                bestDomainSize = domainSize;
                bestDegree = degree[id];
                if (bestDomainSize == 1 && bestDegree == Integer.MAX_VALUE) break;
            }
        }
        return bestVar;
    }

    private int[] preferredValueOrder(int var) {
        if (!isLit(var) && canAssign(var, BULB)) {
            return new int[]{BULB, EMPTY};
        }
        return new int[]{EMPTY, BULB};
    }

    private boolean canAssign(int var, int value) {
        if (assignment[var] != UNKNOWN) return false;
        assignment[var] = value;
        boolean ok = isConsistentPartial();
        assignment[var] = UNKNOWN;
        return ok;
    }

    private boolean isConsistentPartial() {
        return noBulbConflicts() && clueBoundsHold() && everyWhiteCellCanStillBeLit();
    }

    private boolean noBulbConflicts() {
        for (int id = 0; id < assignment.length; id++) {
            if (assignment[id] != BULB) continue;
            for (int other : conflicts[id]) {
                if (assignment[other] == BULB) return false;
            }
        }
        return true;
    }

    private boolean clueBoundsHold() {
        for (int clueIndex = 0; clueIndex < clues.size(); clueIndex++) {
            int bulbs = 0;
            int unknown = 0;
            for (int var : varsAroundClue[clueIndex]) {
                if (assignment[var] == BULB) bulbs++;
                else if (assignment[var] == UNKNOWN) unknown++;
            }
            int required = clues.get(clueIndex).value;
            if (bulbs > required) return false;
            if (bulbs + unknown < required) return false;
        }
        return true;
    }

    private boolean everyWhiteCellCanStillBeLit() {
        for (int cell = 0; cell < assignment.length; cell++) {
            if (isLit(cell)) continue;

            boolean possible = false;
            for (int candidate : illuminate[cell]) {
                if (couldStillBecomeBulb(candidate)) {
                    possible = true;
                    break;
                }
            }
            if (!possible) return false;
        }
        return true;
    }

    private boolean isLit(int cell) {
        for (int candidate : illuminate[cell]) {
            if (assignment[candidate] == BULB) return true;
        }
        return false;
    }

    private boolean couldStillBecomeBulb(int var) {
        if (assignment[var] == BULB) return true;
        if (assignment[var] != UNKNOWN) return false;

        for (int other : conflicts[var]) {
            if (assignment[other] == BULB) return false;
        }

        for (int clueIndex : adjacentCluesOfVar[var]) {
            int bulbs = 0;
            for (int near : varsAroundClue[clueIndex]) {
                if (assignment[near] == BULB) bulbs++;
            }
            if (bulbs + 1 > clues.get(clueIndex).value) return false;
        }
        return true;
    }

    private boolean isCompleteSolution() {
        for (int value : assignment) {
            if (value == UNKNOWN) return false;
        }
        if (!noBulbConflicts()) return false;
        for (int clueIndex = 0; clueIndex < clues.size(); clueIndex++) {
            int bulbs = 0;
            for (int var : varsAroundClue[clueIndex]) {
                if (assignment[var] == BULB) bulbs++;
            }
            if (bulbs != clues.get(clueIndex).value) return false;
        }
        for (int id = 0; id < assignment.length; id++) {
            if (!isLit(id)) return false;
        }
        return true;
    }

    public char[][] solutionGrid() {
        char[][] out = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            out[r] = Arrays.copyOf(grid[r], cols);
        }
        for (int id = 0; id < assignment.length; id++) {
            Cell cell = variables.get(id);
            out[cell.r][cell.c] = assignment[id] == BULB ? 'B' : '.';
        }
        return out;
    }

    public static char[][] parseBoard(List<String> rawLines) {
        ArrayList<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) lines.add(trimmed);
        }
        if (lines.isEmpty()) throw new IllegalArgumentException("The input is empty.");

        int start = 0;
        int r = -1;
        int c = -1;
        String[] first = lines.get(0).split("\\s+");
        if (first.length == 2 && isInteger(first[0]) && isInteger(first[1])) {
            r = Integer.parseInt(first[0]);
            c = Integer.parseInt(first[1]);
            start = 1;
            if (lines.size() - start != r) {
                throw new IllegalArgumentException("Expected " + r + " grid rows, but found " + (lines.size() - start) + ".");
            }
        } else {
            r = lines.size();
            c = removeSpaces(lines.get(0)).length();
        }

        char[][] board = new char[r][c];
        for (int i = 0; i < r; i++) {
            String row = removeSpaces(lines.get(start + i));
            if (row.length() != c) {
                throw new IllegalArgumentException("Row " + (i + 1) + " has length " + row.length() + ", expected " + c + ".");
            }
            board[i] = row.toCharArray();
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
        for (char[] row : board) {
            System.out.println(new String(row));
        }
    }

    public static void main(String[] args) throws Exception {
        List<String> lines;
        if (args.length > 0) {
            lines = Files.readAllLines(Path.of(args[0]));
        } else {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            lines = new ArrayList<>();
            String line;
            while ((line = br.readLine()) != null) lines.add(line);
        }

        try {
            char[][] board = parseBoard(lines);
            LightUpCSPSolver solver = new LightUpCSPSolver(board);
            if (solver.solve()) {
                System.out.println("Solution:");
                printGrid(solver.solutionGrid());
                System.out.println("Nodes visited: " + solver.getNodesVisited());
            } else {
                System.out.println("No solution exists for this puzzle.");
                System.out.println("Nodes visited: " + solver.getNodesVisited());
            }
        } catch (IllegalArgumentException ex) {
            System.err.println("Input error: " + ex.getMessage());
            System.exit(1);
        }
    }
}
