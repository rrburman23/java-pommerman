package players.safetymcts;

import core.GameState;
import utils.Types;
import utils.Vector2d;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Uses breadth-first search to determine whether the controlled agent
 * can escape the blast of a newly placed bomb before it explodes.
 */
public final class EscapeRouteChecker {

    private static final int[][] DIRECTIONS = {
            {0, -1},
            {0, 1},
            {-1, 0},
            {1, 0}
    };

    private EscapeRouteChecker() {
        // Utility class.
    }

    /**
     * Checks whether placing a bomb at the player's current position
     * leaves at least one reachable position outside that bomb's blast.
     */
    public static boolean hasEscapeRoute(GameState state) {
        if (state == null || state.getPosition() == null) {
            return false;
        }

        if (state.getAmmo() <= 0) {
            return false;
        }

        Vector2d start = state.getPosition();
        Types.TILETYPE[][] board = state.getBoard();

        int height = board.length;
        int width = board[0].length;

        int bombLife = Types.BOMB_LIFE;
        int blastStrength = state.getBlastStrength();

        boolean[][] visited = new boolean[height][width];
        Queue<SearchNode> queue = new ArrayDeque<>();

        queue.add(new SearchNode(start.x, start.y, 0));
        visited[start.y][start.x] = true;

        while (!queue.isEmpty()) {
            SearchNode current = queue.remove();

            /*
             * The agent must reach safety before the bomb explodes.
             */
            if (current.steps >= bombLife) {
                continue;
            }

            if (current.steps > 0
                    && !isInsideProposedBlast(
                    board,
                    start.x,
                    start.y,
                    current.x,
                    current.y,
                    blastStrength
            )) {
                return true;
            }

            for (int[] direction : DIRECTIONS) {
                int nextX = current.x + direction[0];
                int nextY = current.y + direction[1];

                if (!isInsideBoard(nextX, nextY, width, height)) {
                    continue;
                }

                if (visited[nextY][nextX]) {
                    continue;
                }

                if (!isWalkable(
                        board,
                        nextX,
                        nextY,
                        start.x,
                        start.y
                )) {
                    continue;
                }

                visited[nextY][nextX] = true;

                queue.add(new SearchNode(
                        nextX,
                        nextY,
                        current.steps + 1
                ));
            }
        }

        return false;
    }

    /**
     * Checks whether a cell can be traversed during escape planning.
     */
    private static boolean isWalkable(
            Types.TILETYPE[][] board,
            int x,
            int y,
            int bombX,
            int bombY
    ) {
        /*
         * The newly placed bomb blocks its origin once the agent leaves.
         */
        if (x == bombX && y == bombY) {
            return false;
        }

        Types.TILETYPE tile = board[y][x];

        return tile != Types.TILETYPE.RIGID
                && tile != Types.TILETYPE.WOOD
                && tile != Types.TILETYPE.BOMB
                && tile != Types.TILETYPE.FLAMES
                && tile != Types.TILETYPE.FOG;
    }

    /**
     * Checks whether a position lies in the proposed bomb's blast.
     */
    private static boolean isInsideProposedBlast(
            Types.TILETYPE[][] board,
            int bombX,
            int bombY,
            int targetX,
            int targetY,
            int blastStrength
    ) {
        if (targetX == bombX && targetY == bombY) {
            return true;
        }

        if (targetX != bombX && targetY != bombY) {
            return false;
        }

        int deltaX = Integer.compare(targetX, bombX);
        int deltaY = Integer.compare(targetY, bombY);

        int distance = Math.abs(targetX - bombX)
                + Math.abs(targetY - bombY);

        /*
         * Blast strength includes the bomb's own cell.
         */
        if (distance > blastStrength - 1) {
            return false;
        }

        for (int step = 1; step <= distance; step++) {
            int x = bombX + deltaX * step;
            int y = bombY + deltaY * step;

            Types.TILETYPE tile = board[y][x];

            if (tile == Types.TILETYPE.RIGID) {
                return false;
            }

            if (tile == Types.TILETYPE.WOOD) {
                return x == targetX && y == targetY;
            }
        }

        return true;
    }

    private static boolean isInsideBoard(
            int x,
            int y,
            int width,
            int height
    ) {
        return x >= 0
                && y >= 0
                && x < width
                && y < height;
    }

    /**
     * One BFS state.
     */
    private static final class SearchNode {
        private final int x;
        private final int y;
        private final int steps;

        private SearchNode(int x, int y, int steps) {
            this.x = x;
            this.y = y;
            this.steps = steps;
        }
    }
}