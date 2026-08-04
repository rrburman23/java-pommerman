package players.safetymcts;

import core.GameState;
import utils.Types;
import utils.Vector2d;

/**
 * Analyses a Pommerman GameState and identifies cells that are currently
 * dangerous or will be hit by an existing bomb.
 *
 * Coordinates follow the framework convention:
 * board[y][x]
 */
public final class DangerMap {

    /**
     * A large value representing a cell that is not currently threatened
     * by any visible bomb.
     */
    public static final int SAFE = Integer.MAX_VALUE;

    private final int width;
    private final int height;

    /**
     * Earliest bomb life at which each cell will be hit.
     *
     * For example:
     * 1 means the cell is threatened by a bomb with one tick remaining.
     * 5 means the earliest threatening bomb has five ticks remaining.
     * SAFE means no current bomb threatens the cell.
     */
    private final int[][] earliestDanger;

    /**
     * True for cells containing flames right now.
     */
    private final boolean[][] activeFlames;

    /**
     * Creates a danger map from the supplied game state.
     *
     * @param gameState current observable Pommerman state
     */
    public DangerMap(GameState gameState) {
        if (gameState == null) {
            throw new IllegalArgumentException(
                    "Game state cannot be null."
            );
        }

        Types.TILETYPE[][] board = gameState.getBoard();

        if (board == null || board.length == 0) {
            throw new IllegalArgumentException(
                    "Game board cannot be null or empty."
            );
        }

        height = board.length;
        width = board[0].length;

        earliestDanger = new int[height][width];
        activeFlames = new boolean[height][width];

        initialiseSafeCells();
        analyseState(gameState);
    }

    /**
     * Initially marks every board cell as safe.
     */
    private void initialiseSafeCells() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                earliestDanger[y][x] = SAFE;
            }
        }
    }

    /**
     * Finds existing flames and projects the blast of every visible bomb.
     */
    private void analyseState(GameState gameState) {
        Types.TILETYPE[][] board = gameState.getBoard();
        int[][] bombLife = gameState.getBombLife();
        int[][] bombBlastStrength =
                gameState.getBombBlastStrength();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Types.TILETYPE tile = board[y][x];

                if (tile == Types.TILETYPE.FLAMES) {
                    activeFlames[y][x] = true;
                    earliestDanger[y][x] = 0;
                }

                if (tile == Types.TILETYPE.BOMB) {
                    int life = bombLife[y][x];
                    int blastStrength = bombBlastStrength[y][x];

                    projectBombBlast(
                            board,
                            x,
                            y,
                            blastStrength,
                            life
                    );
                }
            }
        }
    }

    /**
     * Marks the bomb cell and all cells its flames could reach.
     */
    private void projectBombBlast(
            Types.TILETYPE[][] board,
            int bombX,
            int bombY,
            int blastStrength,
            int bombLife
    ) {
        markDanger(bombX, bombY, bombLife);

        /*
         * Framework blast strength includes the bomb cell itself.
         * Therefore a blast strength of 2 travels one cell outward.
         */
        int maximumDistance = blastStrength - 1;

        projectDirection(
                board,
                bombX,
                bombY,
                1,
                0,
                maximumDistance,
                bombLife
        );

        projectDirection(
                board,
                bombX,
                bombY,
                -1,
                0,
                maximumDistance,
                bombLife
        );

        projectDirection(
                board,
                bombX,
                bombY,
                0,
                1,
                maximumDistance,
                bombLife
        );

        projectDirection(
                board,
                bombX,
                bombY,
                0,
                -1,
                maximumDistance,
                bombLife
        );
    }

    /**
     * Projects one arm of a bomb explosion.
     *
     * Rigid walls block flames completely.
     * Wooden walls are hit but stop further propagation.
     */
    private void projectDirection(
            Types.TILETYPE[][] board,
            int startX,
            int startY,
            int deltaX,
            int deltaY,
            int maximumDistance,
            int bombLife
    ) {
        for (
                int distance = 1;
                distance <= maximumDistance;
                distance++
        ) {
            int x = startX + deltaX * distance;
            int y = startY + deltaY * distance;

            if (!isInsideBoard(x, y)) {
                return;
            }

            Types.TILETYPE tile = board[y][x];

            if (tile == Types.TILETYPE.RIGID) {
                return;
            }

            markDanger(x, y, bombLife);

            if (tile == Types.TILETYPE.WOOD) {
                return;
            }
        }
    }

    /**
     * Records the earliest known threat to a cell.
     */
    private void markDanger(int x, int y, int bombLife) {
        earliestDanger[y][x] = Math.min(
                earliestDanger[y][x],
                bombLife
        );
    }

    /**
     * @return true when the coordinates are on the board
     */
    public boolean isInsideBoard(int x, int y) {
        return x >= 0
                && y >= 0
                && x < width
                && y < height;
    }

    /**
     * Checks whether a cell is threatened by a bomb or contains flames.
     */
    public boolean isDangerous(int x, int y) {
        validateCoordinates(x, y);
        return earliestDanger[y][x] != SAFE;
    }

    /**
     * Checks whether a cell will become dangerous within a given number
     * of ticks.
     *
     * @param x board x coordinate
     * @param y board y coordinate
     * @param ticks maximum bomb life considered urgent
     */
    public boolean isDangerousWithin(
            int x,
            int y,
            int ticks
    ) {
        validateCoordinates(x, y);

        if (ticks < 0) {
            throw new IllegalArgumentException(
                    "Ticks cannot be negative."
            );
        }

        return earliestDanger[y][x] <= ticks;
    }

    /**
     * Checks whether the supplied position is dangerous.
     */
    public boolean isDangerous(Vector2d position) {
        if (position == null) {
            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }

        return isDangerous(position.x, position.y);
    }

    /**
     * Checks whether the supplied position becomes dangerous within the
     * given number of ticks.
     */
    public boolean isDangerousWithin(
            Vector2d position,
            int ticks
    ) {
        if (position == null) {
            throw new IllegalArgumentException(
                    "Position cannot be null."
            );
        }

        return isDangerousWithin(
                position.x,
                position.y,
                ticks
        );
    }

    /**
     * Returns the life of the earliest bomb threatening this cell.
     *
     * Zero means active flames.
     * SAFE means no bomb currently threatens the cell.
     */
    public int getEarliestDanger(int x, int y) {
        validateCoordinates(x, y);
        return earliestDanger[y][x];
    }

    /**
     * Checks whether a cell contains an active flame.
     */
    public boolean hasActiveFlame(int x, int y) {
        validateCoordinates(x, y);
        return activeFlames[y][x];
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    private void validateCoordinates(int x, int y) {
        if (!isInsideBoard(x, y)) {
            throw new IndexOutOfBoundsException(
                    "Coordinates outside board: x="
                            + x
                            + ", y="
                            + y
            );
        }
    }
}