
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Simple GUI for the Light Up / Akari project.`
 *
 * This GUI works with the two solver classes:
 *   - LightUpCSPSolver.java
 *   - LightUpLocalSearchSolver.java
 *
 */
public class LightUpGUI extends JFrame {
    private final JTextArea puzzleInput = new JTextArea(12, 28);
    private final JTextArea messageBox = new JTextArea(6, 28);

    private final JTextField restartsField = new JTextField("100", 6);
    private final JTextField stepsField = new JTextField("200000", 8);
    private final JTextField seedField = new JTextField("42", 6);

    private final BoardPanel boardPanel = new BoardPanel();

    public LightUpGUI() {
        super("Light Up Puzzle Solver");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(950, 650));

        puzzleInput.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        puzzleInput.setText(samplePuzzle());

        messageBox.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        messageBox.setEditable(false);
        messageBox.setLineWrap(true);
        messageBox.setWrapStyleWord(true);

        JButton cspButton = new JButton("Solve with CSP");
        JButton localButton = new JButton("Solve with Local Search");
        JButton sampleButton = new JButton("Load Sample");
        JButton clearButton = new JButton("Clear");

        cspButton.addActionListener(e -> solveWithCSP());
        localButton.addActionListener(e -> solveWithLocalSearch());
        sampleButton.addActionListener(e -> puzzleInput.setText(samplePuzzle()));
        clearButton.addActionListener(e -> {
            puzzleInput.setText("");
            messageBox.setText("");
            boardPanel.setBoard(null);
        });

        JPanel leftPanel = new JPanel(new BorderLayout(8, 8));
        leftPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel inputPanel = new JPanel(new BorderLayout(4, 4));
        inputPanel.add(new JLabel("Puzzle input:"), BorderLayout.NORTH);
        inputPanel.add(new JScrollPane(puzzleInput), BorderLayout.CENTER);

        JPanel parameterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        parameterPanel.add(new JLabel("Restarts:"));
        parameterPanel.add(restartsField);
        parameterPanel.add(new JLabel("Steps:"));
        parameterPanel.add(stepsField);
        parameterPanel.add(new JLabel("Seed:"));
        parameterPanel.add(seedField);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 2, 6, 6));
        buttonPanel.add(cspButton);
        buttonPanel.add(localButton);
        buttonPanel.add(sampleButton);
        buttonPanel.add(clearButton);

        JPanel controls = new JPanel(new BorderLayout(4, 4));
        controls.add(parameterPanel, BorderLayout.NORTH);
        controls.add(buttonPanel, BorderLayout.CENTER);

        JPanel messagePanel = new JPanel(new BorderLayout(4, 4));
        messagePanel.add(new JLabel("Result:"), BorderLayout.NORTH);
        messagePanel.add(new JScrollPane(messageBox), BorderLayout.CENTER);

        leftPanel.add(inputPanel, BorderLayout.NORTH);
        leftPanel.add(controls, BorderLayout.CENTER);
        leftPanel.add(messagePanel, BorderLayout.SOUTH);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        rightPanel.add(new JLabel("Board view:"), BorderLayout.NORTH);
        rightPanel.add(boardPanel, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.35);
        add(splitPane);

        showOriginalBoard();
        pack();
        setLocationRelativeTo(null);
    }

    private void showOriginalBoard() {
        try {
            char[][] board = parseInputWithCSP();
            boardPanel.setBoard(board);
        } catch (Exception ignored) {
            boardPanel.setBoard(null);
        }
    }

    private void solveWithCSP() {
        messageBox.setText("Running CSP solver...\n");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private boolean solved;
            private char[][] resultBoard;
            private long nodes;
            private long timeMs;
            private String error;

            @Override
            protected Void doInBackground() {
                try {
                    char[][] board = parseInputWithCSP();
                    long start = System.nanoTime();

                    LightUpCSPSolver solver = new LightUpCSPSolver(board);
                    solved = solver.solve();

                    long end = System.nanoTime();
                    timeMs = (end - start) / 1_000_000;
                    nodes = solver.getNodesVisited();

                    resultBoard = solved ? solver.solutionGrid() : board;
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    messageBox.setText("Error: " + error);
                    return;
                }

                boardPanel.setBoard(resultBoard);

                if (solved) {
                    messageBox.setText(
                            "CSP found a solution.\n" +
                            "Time: " + timeMs + " ms\n" +
                            "Nodes visited: " + nodes + "\n\n" +
                            "CSP is complete, so if it finishes with no solution, the puzzle is unsolvable."
                    );
                } else {
                    messageBox.setText(
                            "CSP did not find a solution.\n" +
                            "Time: " + timeMs + " ms\n" +
                            "Nodes visited: " + nodes + "\n\n" +
                            "Because CSP backtracking is complete, this means the puzzle has no solution."
                    );
                }
            }
        };

        worker.execute();
    }

    private void solveWithLocalSearch() {
        messageBox.setText("Running Local Search solver...\n");

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private boolean solved;
            private char[][] resultBoard;
            private long states;
            private long timeMs;
            private String violations;
            private String error;

            @Override
            protected Void doInBackground() {
                try {
                    int restarts = Integer.parseInt(restartsField.getText().trim());
                    int steps = Integer.parseInt(stepsField.getText().trim());
                    long seed = Long.parseLong(seedField.getText().trim());

                    char[][] board = parseInputWithLocalSearch();
                    long start = System.nanoTime();

                    LightUpLocalSearchSolver solver = new LightUpLocalSearchSolver(board, seed);
                    solved = solver.solve(restarts, steps);

                    long end = System.nanoTime();
                    timeMs = (end - start) / 1_000_000;
                    states = solver.getStatesEvaluated();
                    resultBoard = solver.bestGrid();
                    violations = solver.bestViolationSummary();
                } catch (Exception ex) {
                    error = ex.getMessage();
                }
                return null;
            }

            @Override
            protected void done() {
                if (error != null) {
                    messageBox.setText("Error: " + error);
                    return;
                }

                boardPanel.setBoard(resultBoard);

                if (solved) {
                    messageBox.setText(
                            "Local Search found a solution.\n" +
                            "Time: " + timeMs + " ms\n" +
                            "States evaluated: " + states + "\n\n" +
                            "Local Search is not complete, but in this run it found a valid solution."
                    );
                } else {
                    messageBox.setText(
                            "Local Search did not find an exact solution.\n" +
                            "Time: " + timeMs + " ms\n" +
                            "States evaluated: " + states + "\n" +
                            "Best state violations: " + violations + "\n\n" +
                            "Important: this does not prove the puzzle is unsolvable. " +
                            "Try increasing restarts or steps."
                    );
                }
            }
        };

        worker.execute();
    }

    private char[][] parseInputWithCSP() {
        return LightUpCSPSolver.parseBoard(linesFromTextArea());
    }

    private char[][] parseInputWithLocalSearch() {
        return LightUpLocalSearchSolver.parseBoard(linesFromTextArea());
    }

    private List<String> linesFromTextArea() {
        String[] parts = puzzleInput.getText().split("\\R");
        ArrayList<String> lines = new ArrayList<>();
        for (String line : parts) {
            if (!line.trim().isEmpty()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static String samplePuzzle() {
        return String.join("\n",
                "5 5",
                "..#..",
                ".2...",
                "..#..",
                "...1.",
                "..#.."
        );
    }

    private static class BoardPanel extends JPanel {
        private char[][] board;

        BoardPanel() {
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(560, 560));
        }

        void setBoard(char[][] board) {
            this.board = board;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            if (board == null || board.length == 0) {
                g.setColor(Color.DARK_GRAY);
                g.drawString("No board to display.", 20, 25);
                return;
            }

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int rows = board.length;
            int cols = board[0].length;

            int margin = 20;
            int availableWidth = getWidth() - 2 * margin;
            int availableHeight = getHeight() - 2 * margin;
            int cellSize = Math.max(10, Math.min(availableWidth / cols, availableHeight / rows));

            int startX = (getWidth() - cellSize * cols) / 2;
            int startY = (getHeight() - cellSize * rows) / 2;

            boolean[][] lit = computeLitCells(board);

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int x = startX + c * cellSize;
                    int y = startY + r * cellSize;

                    char ch = board[r][c];

                    if (isBlack(ch)) {
                        g2.setColor(Color.BLACK);
                    } else if (ch == 'B') {
                        g2.setColor(new Color(255, 160, 200));
                    } else if (lit[r][c]) {
                        g2.setColor(new Color(255, 220, 235));
                    } else {
                        g2.setColor(Color.WHITE);
                    }

                    g2.fillRect(x, y, cellSize, cellSize);

                    g2.setColor(Color.GRAY);
                    g2.drawRect(x, y, cellSize, cellSize);

                    if (ch == 'B') {
                        drawBulb(g2, x, y, cellSize);
                    } else if (ch >= '0' && ch <= '4') {
                        g2.setColor(Color.WHITE);
                        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, cellSize / 2)));
                        drawCenteredString(g2, String.valueOf(ch), x, y, cellSize);
                    }
                }
            }

            g2.dispose();
        }

        private static void drawBulb(Graphics2D g2, int x, int y, int size) {
            int bulbSize = Math.max(8, (int) (size * 0.45));
            int bx = x + (size - bulbSize) / 2;
            int by = y + (size - bulbSize) / 2;

            g2.setColor(new Color(255, 210, 0));
            g2.fillOval(bx, by, bulbSize, bulbSize);

            g2.setColor(Color.BLACK);
            g2.drawOval(bx, by, bulbSize, bulbSize);

            g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, size / 4)));
            drawCenteredString(g2, "B", x, y, size);
        }

        private static void drawCenteredString(Graphics2D g2, String text, int x, int y, int size) {
            FontMetrics metrics = g2.getFontMetrics();
            int tx = x + (size - metrics.stringWidth(text)) / 2;
            int ty = y + ((size - metrics.getHeight()) / 2) + metrics.getAscent();
            g2.drawString(text, tx, ty);
        }

        private static boolean[][] computeLitCells(char[][] board) {
            int rows = board.length;
            int cols = board[0].length;
            boolean[][] lit = new boolean[rows][cols];

            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    if (board[r][c] == 'B') {
                        lightFrom(board, lit, r, c);
                    }
                }
            }

            return lit;
        }

        private static void lightFrom(char[][] board, boolean[][] lit, int r, int c) {
            int rows = board.length;
            int cols = board[0].length;

            lit[r][c] = true;

            int[] dr = {-1, 1, 0, 0};
            int[] dc = {0, 0, -1, 1};

            for (int d = 0; d < 4; d++) {
                int nr = r + dr[d];
                int nc = c + dc[d];

                while (nr >= 0 && nr < rows && nc >= 0 && nc < cols && !isBlack(board[nr][nc])) {
                    lit[nr][nc] = true;
                    nr += dr[d];
                    nc += dc[d];
                }
            }
        }

        private static boolean isBlack(char ch) {
            return ch == '#' || (ch >= '0' && ch <= '4');
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LightUpGUI().setVisible(true));
    }
}
