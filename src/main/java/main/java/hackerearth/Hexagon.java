package main.java.hackerearth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class Hexagon {
    public static void main(String[] args) throws IOException {
        final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(System.in));
        final int[][] board = new int[6][7];
        for (int i = 0; i < board.length; i++) {
            final String cols[] = bufferedReader.readLine().split(" ");
            for (int j = 0; j < board[i].length; j++) {
                board[i][j] = cols[j].charAt(0) - '0';
            }
        }
        final MinMax minMax = new MinMax(Integer.parseInt(bufferedReader.readLine()));
        System.out.println(minMax.iterativeSearchForBestMove(board, Integer.parseInt(bufferedReader.readLine())));
        System.out.println(minMax.eval + " " + minMax.depth + " "
                                   + minMax.moves + " " + minMax.computations + " " + minMax.cacheHits);
    }
}

class MinMax {
    public static final int MAX_DEPTH = 60, TERMINAL_DEPTH = 100;
    public static int TIME_OUT = 1000;
    public int computations = 0, depth = 1, moves = 0;
    public long eval = 0;
    static final int MAX_VALUE = 1000000, MIN_VALUE = -MAX_VALUE;
    private final long startTime = System.currentTimeMillis();
    private boolean test;
    private Configuration[] startConfigs;
    private final Move[][] killerMoves = new Move[MAX_DEPTH][2];
    private final int[][] efficiency = new int[MAX_DEPTH][2];
    private static final boolean nullSearchActivated = false;
    private final int currentDepth;
    public int cacheHits;
    private boolean timeOut;

    MinMax(final int currentDepth) {
        this.currentDepth = currentDepth;
    }

    public String iterativeSearchForBestMove(final int[][] game, final int player) {
        Board.setThoseWithinSight();
        final Board board = new Board(game);
        if (board.places[player] == 0) {
            throw new RuntimeException("No possible moves");
        }
        startConfigs = new Configuration[board.options[player]];
        for (int i = 0; i < startConfigs.length; i++) {
            startConfigs[i] = new Configuration(board.moves[player][i], board, 0, false);
        }
        Arrays.sort(startConfigs);
        Move bestMove = startConfigs[0].move;
        while (depth < MAX_DEPTH && !timeOut && depth + currentDepth <= TERMINAL_DEPTH) {
            bestMove = findBestMove(player, 0);
            depth++;
        }
        eval = startConfigs[0].strength;
        moves = board.places[player];
        return bestMove.describe();
    }

    private Move findBestMove(final int player, final int level) {
        long toTake = MIN_VALUE, toGive = MAX_VALUE;
        int max = MIN_VALUE;
        Move bestMove = startConfigs[0].move;
        try {
            final Map<Board, Integer> boards = new HashMap<>();
            for (final Configuration possibleConfig : startConfigs) {
                final int moveValue;
                if (boards.containsKey(possibleConfig.board)) {
                    cacheHits++;
                    moveValue = boards.get(possibleConfig.board);
                } else {
                    moveValue = evaluate(possibleConfig.board,
                                         flip(player),
                                         level,
                                         toTake,
                                         toGive,
                                         -possibleConfig.strength,
                                         false);
                    boards.put(possibleConfig.board, moveValue);
                }
                possibleConfig.strength = moveValue;
                if (player == 1) {
                    if (toTake < moveValue) {
                        toTake = moveValue;
                    }
                } else {
                    if (toGive > -moveValue) {
                        toGive = -moveValue;
                    }
                }
                if (moveValue > max) {
                    max = moveValue;
                    bestMove = possibleConfig.move;
                    if (Math.abs(max - MAX_VALUE) <= 100) {
                        break;
                    }
                }
                if (toTake >= toGive) {
                    if (possibleConfig.killer) {
                        if (killerMoves[level][0] == possibleConfig.move) {
                            efficiency[level][0]++;
                        } else {
                            efficiency[level][1]++;
                            if (efficiency[level][0] < efficiency[level][1]) {
                                final Move temp = killerMoves[level][0];
                                killerMoves[level][0] = killerMoves[level][1];
                                killerMoves[level][1] = temp;
                            }
                        }
                    } else {
                        if (killerMoves[level][0] == null) {
                            killerMoves[level][0] = possibleConfig.move;
                            efficiency[level][0] = 1;
                        } else if (killerMoves[level][1] == null) {
                            killerMoves[level][1] = possibleConfig.move;
                            efficiency[level][1] = 1;
                        }
                    }
                    break;
                } else if (possibleConfig.killer) {
                    if (killerMoves[level][0] == possibleConfig.move) {
                        efficiency[level][0]--;
                    } else {
                        efficiency[level][1]--;
                    }
                    if (efficiency[level][0] < efficiency[level][1]) {
                        final Move temp = killerMoves[level][0];
                        killerMoves[level][0] = killerMoves[level][1];
                        killerMoves[level][1] = temp;
                    }
                    if (efficiency[level][1] <= 0) {
                        efficiency[level][1] = 0;
                        killerMoves[level][1] = null;
                    }
                }
            }
        } catch (TimeoutException e) {
            timeOut = true;
        }
        Arrays.sort(startConfigs);
        return bestMove;
    }

    private int evaluate(final Board board,
                         final int player,
                         final int level,
                         final long a,
                         final long b,
                         final int heuristicValue,
                         final boolean isNullSearch) throws TimeoutException {
        long toTake = a, toGive = b;
        int max = MIN_VALUE;
        if (!test && System.currentTimeMillis() - startTime >= TIME_OUT) {
            throw new TimeoutException("Time out...");
        }
        if (board.isTerminated(player, level, currentDepth)) {
            max = (board.places[player] - board.places[MinMax.flip(player)]) * MAX_VALUE;
        } else if (level >= depth) {
            max = heuristicValue;
        } else {
            final Configuration[] configurations = new Configuration[board.options[player]];
            for (int i = 0; i < configurations.length; i++) {
                configurations[i] = new Configuration(board.moves[player][i],
                                                      board,
                                                      level,
                                                      isNullSearch);
            }
            Arrays.sort(configurations);
            final Map<Board, Integer> boards = new HashMap<>();
            for (final Configuration possibleConfig : configurations) {
                computations++;
                if (nullSearchActivated && !isNullSearch && !isEndGame(possibleConfig)) {
                    final int nullMoveValue = -evaluate(possibleConfig.board,
                                                        player,
                                                        level + 2,
                                                        player == 1 ? toTake : toGive - 1,
                                                        player == 1 ? toTake + 1 : toGive,
                                                        possibleConfig.strength,
                                                        true);
                    if (player == 1) {
                        if (nullMoveValue <= toTake) {
                            if (nullMoveValue > max) {
                                max = nullMoveValue;
                            }
                            continue;
                        }
                    } else {
                        if (nullMoveValue >= toGive) {
                            if (nullMoveValue > max) {
                                max = nullMoveValue;
                            }
                            continue;
                        }
                    }
                }
                final int moveValue;
                if (boards.containsKey(possibleConfig.board)) {
                    cacheHits++;
                    moveValue = boards.get(possibleConfig.board);
                } else {
                    moveValue = evaluate(possibleConfig.board,
                                         flip(player),
                                         level + 1,
                                         toTake,
                                         toGive,
                                         -possibleConfig.strength,
                                         isNullSearch);
                    boards.put(possibleConfig.board, moveValue);
                }
                if (player == 1) {
                    if (toTake < moveValue) {
                        toTake = moveValue;
                    }
                } else {
                    if (toGive > -moveValue) {
                        toGive = -moveValue;
                    }
                }
                if (moveValue > max) {
                    max = moveValue;
                    if (Math.abs(max - MAX_VALUE) <= 100) {
                        break;
                    }
                }
                if (toTake >= toGive) {
                    max = moveValue;
                    if (possibleConfig.killer) {
                        if (killerMoves[level][0] == possibleConfig.move) {
                            efficiency[level][0]++;
                        } else {
                            efficiency[level][1]++;
                            if (efficiency[level][0] < efficiency[level][1]) {
                                final Move temp = killerMoves[level][0];
                                killerMoves[level][0] = killerMoves[level][1];
                                killerMoves[level][1] = temp;
                            }
                        }
                    } else {
                        if (killerMoves[level][0] == null) {
                            killerMoves[level][0] = possibleConfig.move;
                            efficiency[level][0] = 1;
                        } else if (killerMoves[level][1] == null) {
                            killerMoves[level][1] = possibleConfig.move;
                            efficiency[level][1] = 1;
                        }
                    }
                    break;
                } else if (possibleConfig.killer) {
                    if (killerMoves[level][0] == possibleConfig.move) {
                        efficiency[level][0]--;
                    } else {
                        efficiency[level][1]--;
                    }
                    if (efficiency[level][0] < efficiency[level][1]) {
                        final Move temp = killerMoves[level][0];
                        killerMoves[level][0] = killerMoves[level][1];
                        killerMoves[level][1] = temp;
                    }
                    if (efficiency[level][1] <= 0) {
                        efficiency[level][1] = 0;
                        killerMoves[level][1] = null;
                    }
                }
            }
        }
        return -max;
    }

    private boolean isEndGame(final Configuration configuration) {
        return configuration.board.places[configuration.move.player] +
                configuration.board.places[MinMax.flip(configuration.move.player)] > 33;
    }

    private class Configuration implements Comparable<Configuration> {
        final Move move;
        final Board board;
        int strength;
        final boolean killer;

        private Configuration(final Move move,
                              final Board board,
                              final int level,
                              final boolean resultsFromNullSearch) {
            this.board = board.getCopy().play(move);
            if (!resultsFromNullSearch
                    && (move.equals(killerMoves[level][0])
                    || move.equals(killerMoves[level][1]))) {
                killer = true;
            } else {
                this.strength = this.board.heuristicValue(move.player);
                killer = false;
            }
            this.move = move;
        }

        @Override
        public int compareTo(Configuration o) {
            if (!killer && o.killer) {
                return +1;
            } else if (killer && !o.killer) {
                return -1;
            } else {
                return o.strength - strength;
            }
        }

        @Override
        public String toString() {
            return "Configuration{" +
                    "move=" + move +
                    ", board=" + board +
                    ", strength=" + strength +
                    ", killer=" + killer +
                    '}';
        }
    }

    static int flip(final int player) {
        return ~player & 3;
    }

    public void setTest(boolean test) {
        this.test = test;
    }
}

class Move {
    public static final int PRIME = 31;
    final int startX, startY, x, y, player;
    final boolean isAJump;

    public Move(final int startX, final int startY, final int x, final int y, final int player, final boolean isAJump) {
        this.startX = startX;
        this.startY = startY;
        this.x = x;
        this.y = y;
        this.player = player;
        this.isAJump = isAJump;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        else if (o == null || getClass() != o.getClass()) return false;
        final Move move = (Move) o;
        return startX == move.startX && startY == move.startY && x == move.x && y == move.y && player == move.player;
    }

    @Override
    public int hashCode() {
        return PRIME * (PRIME * (PRIME * (PRIME * startX + startY) + x) + y) + player;
    }

    String describe() {
        return startX + " " + startY + "\n" + x + " " + y;
    }

    @Override
    public String toString() {
        return "Move{" +
                "startX=" + startX +
                ", startY=" + startY +
                ", x=" + x +
                ", y=" + y +
                ", player=" + player +
                ", isAJump=" + isAJump +
                '}';
    }
}

class Board {
    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final int PLAYERS = 3;
    final int[][] board;
    final int places[];
    final int options[];
    final int stable[];
    final Move moves[][];
    private static final int neighbours[][][][] = new int[ROWS][COLS][2][];
    private static final int jumpables[][][][] = new int[ROWS][COLS][2][];
    public final int[] hashCode;

    Board(final int[][] board) {
        this.board = board;
        places = new int[PLAYERS];
        moves = new Move[PLAYERS][756];
        options = new int[PLAYERS];
        stable = new int[PLAYERS];
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                final int player = board[i][j];
                if (player != 0) {
                    places[player]++;
                    boolean isStable = true;
                    for (int k = 0; k < neighbours[i][j][0].length; k++) {
                        if (board[neighbours[i][j][0][k]][neighbours[i][j][1][k]] == 0) {
                            moves[player][options[player]++] = new Move(i,
                                                                        j,
                                                                        neighbours[i][j][0][k],
                                                                        neighbours[i][j][1][k],
                                                                        player,
                                                                        false);
                            isStable = false;
                        }
                    }
                    if (isStable) {
                        stable[player]++;
                    }
                    for (int k = 0; k < jumpables[i][j][0].length; k++) {
                        if (board[jumpables[i][j][0][k]][jumpables[i][j][1][k]] == 0) {
                            moves[player][options[player]++] = new Move(i,
                                                                        j,
                                                                        jumpables[i][j][0][k],
                                                                        jumpables[i][j][1][k],
                                                                        player,
                                                                        true);
                        }
                    }
                }
            }
        }
        this.hashCode = getHashCode();
    }

    private Board(final int[][] board,
                  final int[] places,
                  final int options[],
                  final Move[][] moves,
                  final int[] hashCode,
                  final int[] stable) {
        this.stable = new int[stable.length];
        this.board = new int[ROWS][COLS];
        this.places = new int[PLAYERS];
        this.options = new int[PLAYERS];
        this.hashCode = new int[hashCode.length];
        for (int i = 0; i < ROWS; i++) {
            System.arraycopy(board[i], 0, this.board[i], 0, COLS);
        }
        System.arraycopy(options, 0, this.options, 0, options.length);
        System.arraycopy(places, 0, this.places, 0, places.length);
        this.moves = new Move[PLAYERS][];
        for (int i = 1; i < PLAYERS; i++) {
            this.moves[i] = new Move[moves[i].length];
            System.arraycopy(moves[i], 0, this.moves[i], 0, options[i]);
        }
        System.arraycopy(hashCode, 0, this.hashCode, 0, hashCode.length);
        System.arraycopy(stable, 0, this.stable, 0, stable.length);
    }

    private int[] getHashCode() {
        int hashCode[] = new int[3];
        for (int box = 0; box < hashCode.length; box++) {
            for (int i = 0; i < 14; i++) {
                hashCode[box] |= board[(box << 1) + i / COLS][i % COLS] << (i << 1);
            }
        }
        return hashCode;
    }

    public Board undo(final Move move) {
        return this;
    }

    public Board play(final Move move) {
        final int removeToHere[][] = new int[2][ROWS * COLS];
        final int removeFromHere[][] = new int[2][ROWS * COLS];
        final int addConnectionsToHere[][] = new int[2][ROWS * COLS];
        final int addConnectionsFromHere[][] = new int[2][ROWS * COLS];
        int removeToHereCount = 0,
                removeFromHereCount = 0,
                addConnectionsToHereCount = 0,
                addConnectionsFromHereCount = 0;
        if (move.isAJump) {
            board[move.startX][move.startY] = 0;
            places[move.player]--;
            removeFromHere[0][removeFromHereCount] = move.startX;
            removeFromHere[1][removeFromHereCount] = move.startY;
            removeFromHereCount++;
            addConnectionsToHere[0][addConnectionsToHereCount] = move.startX;
            addConnectionsToHere[1][addConnectionsToHereCount] = move.startY;
            addConnectionsToHereCount++;
            //remove all connections from here to elsewhere
            //add all connections within range to this point
        }
        final int opponent = MinMax.flip(move.player);
        board[move.x][move.y] = move.player;
        places[move.player]++;
        addConnectionsFromHere[0][addConnectionsFromHereCount] = move.x;
        addConnectionsFromHere[1][addConnectionsFromHereCount] = move.y;
        addConnectionsFromHereCount++;
        removeToHere[0][removeToHereCount] = move.x;
        removeToHere[1][removeToHereCount] = move.y;
        removeToHereCount++;
        //add all connections from this point to elsewhere
        //remove all connections to here
        final int[][] neighbour = neighbours[move.x][move.y];
        for (int i = 0; i < neighbour[0].length; i++) {
            if (board[neighbour[0][i]][neighbour[1][i]] == opponent) {
                places[opponent]--;
                board[neighbour[0][i]][neighbour[1][i]] = move.player;
                places[move.player]++;
                removeFromHere[0][removeFromHereCount] = neighbour[0][i];
                removeFromHere[1][removeFromHereCount] = neighbour[1][i];
                removeFromHereCount++;
                addConnectionsFromHere[0][addConnectionsFromHereCount] = neighbour[0][i];
                addConnectionsFromHere[1][addConnectionsFromHereCount] = neighbour[1][i];
                addConnectionsFromHereCount++;
                //remove all connections from here to elsewhere
                //add all connections from this point to elsewhere
            }
        }
        final List<Move> distinctMoves[] = new List[PLAYERS];
        for (int i = 0; i < PLAYERS; i++) {
            distinctMoves[i] = new ArrayList<>();
        }
        for (int i = 1; i < PLAYERS; i++) {
            for (int j = 0; j < options[i]; j++) {
                distinctMoves[i].add(moves[i][j]);
            }
        }
        for (int i = 1; i < PLAYERS; i++) {
            for (int j = 0; j < removeFromHereCount; j++) {
                final int fJ = j;
                distinctMoves[i].removeIf(moveToBeDeleted -> moveToBeDeleted.startX == removeFromHere[0][fJ]
                        && moveToBeDeleted.startY == removeFromHere[1][fJ]);
            }
            for (int j = 0; j < removeToHereCount; j++) {
                final int fJ = j;
                distinctMoves[i].removeIf(moveToBeDeleted -> moveToBeDeleted.x == removeToHere[0][fJ]
                        && moveToBeDeleted.y == removeToHere[1][fJ]);
            }
        }
        for (int q = 0; q < addConnectionsToHereCount; q++) {
            final int i = addConnectionsToHere[0][q];
            final int j = addConnectionsToHere[1][q];
            final int[][] nearNeighbour = neighbours[i][j];
            for (int k = 0; k < nearNeighbour[0].length; k++) {
                final int currentPlayer = board[nearNeighbour[0][k]][nearNeighbour[1][k]];
                if (currentPlayer != 0) {
                    distinctMoves[currentPlayer].add(new Move(nearNeighbour[0][k],
                                                              nearNeighbour[1][k],
                                                              i,
                                                              j,
                                                              currentPlayer,
                                                              false));
                }
            }
            final int[][] extendedNeighbour = jumpables[i][j];
            for (int k = 0; k < extendedNeighbour[0].length; k++) {
                final int currentPlayer = board[extendedNeighbour[0][k]][extendedNeighbour[1][k]];
                if (currentPlayer != 0) {
                    distinctMoves[currentPlayer].add(new Move(extendedNeighbour[0][k],
                                                              extendedNeighbour[1][k],
                                                              i,
                                                              j,
                                                              currentPlayer,
                                                              true));
                }
            }
        }
        for (int q = 0; q < addConnectionsFromHereCount; q++) {
            final int i = addConnectionsFromHere[0][q];
            final int j = addConnectionsFromHere[1][q];
            final int[][] nearNeighbour = neighbours[i][j];
            for (int k = 0; k < nearNeighbour[0].length; k++) {
                if (board[nearNeighbour[0][k]][nearNeighbour[1][k]] == 0) {
                    distinctMoves[move.player].add(new Move(i,
                                                            j,
                                                            nearNeighbour[0][k],
                                                            nearNeighbour[1][k],
                                                            move.player,
                                                            false));
                }
            }
            final int[][] extendedNeighbour = jumpables[i][j];
            for (int k = 0; k < extendedNeighbour[0].length; k++) {
                if (board[extendedNeighbour[0][k]][extendedNeighbour[1][k]] == 0) {
                    distinctMoves[move.player].add(new Move(i,
                                                            j,
                                                            extendedNeighbour[0][k],
                                                            extendedNeighbour[1][k],
                                                            move.player,
                                                            true));
                }
            }
        }
        for (int i = 1; i < PLAYERS; i++) {
            options[i] = distinctMoves[i].size();
            for (int j = 0; j < options[i]; j++) {
                moves[i][j] = distinctMoves[i].get(j);
            }
        }
        final int[] hashCode = getHashCode();
        System.arraycopy(hashCode, 0, this.hashCode, 0, hashCode.length);
        return this;
    }

    public boolean isTerminated(final int player, final int level, final int currentDepth) {
        return options[player] == 0 || level + currentDepth >= MinMax.TERMINAL_DEPTH;
    }

    int heuristicValue(final int player) {
        final int opponent = MinMax.flip(player);
        return 100 * (int) Math.round(places[player] - places[opponent] + 0.2 * (options[player] - options[opponent]));
    }

    public static void setThoseWithinSight() {
        final int temps[][] = new int[2][6];
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                int count = 0;
                if (i > 0) {
                    temps[0][count] = i - 1;
                    temps[1][count] = j;
                    count++;
                    if (j % 2 == 0) {
                        if (j > 0) {
                            temps[0][count] = i - 1;
                            temps[1][count] = j - 1;
                            count++;
                        }
                        if (j < COLS - 1) {
                            temps[0][count] = i - 1;
                            temps[1][count] = j + 1;
                            count++;
                        }
                    }
                }
                if (i < ROWS - 1) {
                    temps[0][count] = i + 1;
                    temps[1][count] = j;
                    count++;
                    if (j % 2 == 1) {
                        if (j > 0) {
                            temps[0][count] = i + 1;
                            temps[1][count] = j - 1;
                            count++;
                        }
                        if (j < COLS - 1) {
                            temps[0][count] = i + 1;
                            temps[1][count] = j + 1;
                            count++;
                        }
                    }
                }
                if (j > 0) {
                    temps[0][count] = i;
                    temps[1][count] = j - 1;
                    count++;
                }
                if (j < COLS - 1) {
                    temps[0][count] = i;
                    temps[1][count] = j + 1;
                    count++;
                }
                neighbours[i][j][0] = new int[count];
                neighbours[i][j][1] = new int[count];
                System.arraycopy(temps[0], 0, neighbours[i][j][0], 0, count);
                System.arraycopy(temps[1], 0, neighbours[i][j][1], 0, count);
            }
        }

        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                final Set<Cell> tooClose = new HashSet<>();
                tooClose.add(new Cell(i, j));
                for (int k = 0; k < neighbours[i][j][0].length; k++) {
                    tooClose.add(new Cell(neighbours[i][j][0][k], neighbours[i][j][1][k]));
                }
                final Set<Cell> distantNeighbours = new HashSet<>();
                for (int k = 0; k < neighbours[i][j][0].length; k++) {
                    final int x = neighbours[i][j][0][k];
                    final int y = neighbours[i][j][1][k];
                    for (int l = 0; l < neighbours[x][y][0].length; l++) {
                        final Cell current = new Cell(neighbours[x][y][0][l], neighbours[x][y][1][l]);
                        if (!tooClose.contains(current)) {
                            distantNeighbours.add(current);
                        }
                    }
                }
                jumpables[i][j][0] = new int[distantNeighbours.size()];
                jumpables[i][j][1] = new int[distantNeighbours.size()];
                final List<Cell> distantNeighboursList = distantNeighbours.stream().collect(Collectors.toList());
                for (int k = 0; k < distantNeighboursList.size(); k++) {
                    jumpables[i][j][0][k] = distantNeighboursList.get(k).x;
                    jumpables[i][j][1][k] = distantNeighboursList.get(k).y;
                }
            }
        }
    }

    private static class Cell {
        final int x, y;

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Cell cell = (Cell) o;
            return x == cell.x && y == cell.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }

        private Cell(final int x, final int y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Board board = (Board) o;
        return hashCode[0] == board.hashCode[0] && hashCode[1] == board.hashCode[1] && hashCode[2] == board.hashCode[2];
    }

    @Override
    public int hashCode() {
        return 961 * hashCode[0] + 31 * hashCode[1] + hashCode[2];
    }

    public Board getCopy() {
        return new Board(board, places, options, moves, hashCode, stable);
    }

    @Override
    public String toString() {
        return "Board{" +
                "board=" + Arrays.deepToString(board) +
                ", hashCode=" + Arrays.toString(hashCode) +
                '}';
    }
}