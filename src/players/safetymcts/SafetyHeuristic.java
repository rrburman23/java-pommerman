package players.safetymcts;

import core.GameState;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.Types;
import utils.Vector2d;

/**
 * Extends the framework's strategic heuristic with Pommerman-specific
 * safety and mobility information.
 */
public final class SafetyHeuristic extends StateHeuristic {

    private final CustomHeuristic strategicHeuristic;
    private final SafetyMCTSParams params;

    /**
     * Creates a heuristic relative to the root state of an MCTS search.
     *
     * @param rootState root state for the current MCTS decision
     * @param params SafetyMCTS configuration
     */
    public SafetyHeuristic(
            GameState rootState,
            SafetyMCTSParams params
    ) {
        if (rootState == null) {
            throw new IllegalArgumentException(
                    "Root state cannot be null."
            );
        }

        if (params == null) {
            throw new IllegalArgumentException(
                    "SafetyMCTS parameters cannot be null."
            );
        }

        this.strategicHeuristic =
                new CustomHeuristic(rootState);

        this.params = params;
    }

    /**
     * Evaluates a simulated state.
     *
     * Terminal wins and losses retain the framework values of 1 and -1.
     * Non-terminal states combine the existing strategic heuristic with
     * a danger penalty and a small safe-mobility reward.
     */
    @Override
    public double evaluateState(GameState state) {
        if (state == null) {
            return -1.0;
        }

        if (state.isTerminal()) {
            if (state.winner() == Types.RESULT.WIN) {
                return 1.0;
            }

            if (state.winner() == Types.RESULT.LOSS) {
                return -1.0;
            }
        }

        double strategicScore =
                strategicHeuristic.evaluateState(state);

        Vector2d position = state.getPosition();

        if (position == null) {
            return -1.0;
        }

        DangerMap dangerMap = new DangerMap(state);

        double dangerPenalty = calculateDangerPenalty(
                dangerMap,
                position
        );

        double mobilityReward = calculateMobilityReward(
                state,
                dangerMap,
                position
        );

        double score = strategicScore
                - params.danger_weight * dangerPenalty
                + params.mobility_weight * mobilityReward;

        return clamp(score, -1.0, 1.0);
    }

    /**
     * Produces a danger value between 0 and 1.
     */
    private double calculateDangerPenalty(
            DangerMap dangerMap,
            Vector2d position
    ) {
        int earliestDanger = dangerMap.getEarliestDanger(
                position.x,
                position.y
        );

        if (earliestDanger == DangerMap.SAFE) {
            return 0.0;
        }

        if (earliestDanger <= 0) {
            return 1.0;
        }

        /*
         * Danger becomes less severe as the remaining bomb life grows.
         * A threat at one tick receives a larger penalty than a threat
         * several ticks away.
         */
        return 1.0 / earliestDanger;
    }

    /**
     * Measures the proportion of immediate actions that lead to a
     * traversable cell outside urgent danger.
     */
    private double calculateMobilityReward(
            GameState state,
            DangerMap dangerMap,
            Vector2d position
    ) {
        Types.TILETYPE[][] board = state.getBoard();

        int safeDestinations = 0;
        int movementActions = 0;

        for (Types.ACTIONS action : Types.ACTIONS.all()) {
            if (action == Types.ACTIONS.ACTION_BOMB) {
                continue;
            }

            Vector2d direction =
                    action.getDirection().toVec();

            int x = position.x + direction.x;
            int y = position.y + direction.y;

            movementActions++;

            if (!dangerMap.isInsideBoard(x, y)) {
                continue;
            }

            Types.TILETYPE tile = board[y][x];

            if (!isWalkable(tile)) {
                continue;
            }

            if (dangerMap.isDangerousWithin(
                    x,
                    y,
                    params.danger_horizon
            )) {
                continue;
            }

            safeDestinations++;
        }

        if (movementActions == 0) {
            return 0.0;
        }

        return safeDestinations
                / (double) movementActions;
    }

    private boolean isWalkable(Types.TILETYPE tile) {
        return tile != Types.TILETYPE.RIGID
                && tile != Types.TILETYPE.WOOD
                && tile != Types.TILETYPE.BOMB
                && tile != Types.TILETYPE.FLAMES
                && tile != Types.TILETYPE.FOG;
    }

    private double clamp(
            double value,
            double minimum,
            double maximum
    ) {
        return Math.max(
                minimum,
                Math.min(maximum, value)
        );
    }
}