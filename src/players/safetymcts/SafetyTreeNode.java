package players.safetymcts;

import core.GameState;
import players.heuristics.AdvancedHeuristic;
import players.heuristics.CustomHeuristic;
import players.heuristics.StateHeuristic;
import utils.ElapsedCpuTimer;
import utils.Types;
import utils.Utils;
import utils.Vector2d;

import java.util.ArrayList;
import java.util.Random;

public class SafetyTreeNode {

    public SafetyMCTSParams params;

    private SafetyTreeNode parent;
    private SafetyTreeNode[] children;

    private double totValue;
    private int nVisits;

    private Random m_rnd;
    private int m_depth;

    private double[] bounds = new double[]{
            Double.MAX_VALUE,
            -Double.MAX_VALUE
    };

    private int childIdx;
    private int fmCallsCount;

    private int num_actions;
    private Types.ACTIONS[] actions;

    private GameState rootState;
    private StateHeuristic rootStateHeuristic;

    SafetyTreeNode(
            SafetyMCTSParams p,
            Random rnd,
            int num_actions,
            Types.ACTIONS[] actions
    ) {
        this(
                p,
                null,
                -1,
                rnd,
                num_actions,
                actions,
                0,
                null
        );
    }

    private SafetyTreeNode(
            SafetyMCTSParams p,
            SafetyTreeNode parent,
            int childIdx,
            Random rnd,
            int num_actions,
            Types.ACTIONS[] actions,
            int fmCallsCount,
            StateHeuristic sh
    ) {
        this.params = p;
        this.fmCallsCount = fmCallsCount;
        this.parent = parent;
        this.m_rnd = rnd;
        this.num_actions = num_actions;
        this.actions = actions;

        children = new SafetyTreeNode[num_actions];
        totValue = 0.0;

        this.childIdx = childIdx;

        if (parent != null) {
            m_depth = parent.m_depth + 1;
            this.rootStateHeuristic = sh;
        } else {
            m_depth = 0;
        }
    }

    void setRootGameState(GameState gs) {
        this.rootState = gs;

        if (params.heuristic_method
                == params.CUSTOM_HEURISTIC) {

            this.rootStateHeuristic =
                    new CustomHeuristic(gs);

        } else if (params.heuristic_method
                == params.ADVANCED_HEURISTIC) {

            this.rootStateHeuristic =
                    new AdvancedHeuristic(gs, m_rnd);

        } else if (params.heuristic_method
                == params.SAFETY_HEURISTIC) {

            this.rootStateHeuristic =
                    new SafetyHeuristic(gs, params);

        } else {
            throw new IllegalArgumentException(
                    "Unknown heuristic method: "
                            + params.heuristic_method
            );
        }
    }

    void mctsSearch(ElapsedCpuTimer elapsedTimer) {
        double averageTimeTaken;
        double accumulatedTimeTaken = 0.0;

        long remaining;
        int numberOfIterations = 0;

        int remainingLimit = 5;
        boolean stop = false;

        while (!stop) {
            GameState state = rootState.copy();

            ElapsedCpuTimer iterationTimer =
                    new ElapsedCpuTimer();

            SafetyTreeNode selected =
                    treePolicy(state);

            double result =
                    selected.rollOut(state);

            backUp(selected, result);

            if (params.stop_type
                    == params.STOP_TIME) {

                numberOfIterations++;

                accumulatedTimeTaken +=
                        iterationTimer.elapsedMillis();

                averageTimeTaken =
                        accumulatedTimeTaken
                                / numberOfIterations;

                remaining =
                        elapsedTimer.remainingTimeMillis();

                stop =
                        remaining <= 2 * averageTimeTaken
                                || remaining
                                <= remainingLimit;

            } else if (params.stop_type
                    == params.STOP_ITERATIONS) {

                numberOfIterations++;

                stop =
                        numberOfIterations
                                >= params.num_iterations;

            } else if (params.stop_type
                    == params.STOP_FMCALLS) {

                fmCallsCount += params.rollout_depth;

                stop =
                        fmCallsCount
                                + params.rollout_depth
                                > params.num_fmcalls;
            }
        }
    }

    private SafetyTreeNode treePolicy(
            GameState state
    ) {
        SafetyTreeNode current = this;

        while (!state.isTerminal()
                && current.m_depth
                < params.rollout_depth) {

            if (current.notFullyExpanded()) {
                return current.expand(state);
            }

            current = current.uct(state);
        }

        return current;
    }

    /**
     * Expands one previously unvisited action.
     *
     * When safe expansion is enabled, unvisited actions with an
     * immediately safe destination are preferred. If none appear safe,
     * expansion falls back to any unvisited action.
     */
    private SafetyTreeNode expand(
            GameState state
    ) {
        int selectedAction =
                selectExpansionAction(state);

        roll(
                state,
                actions[selectedAction]
        );

        SafetyTreeNode child =
                new SafetyTreeNode(
                        params,
                        this,
                        selectedAction,
                        m_rnd,
                        num_actions,
                        actions,
                        fmCallsCount,
                        rootStateHeuristic
                );

        children[selectedAction] = child;

        return child;
    }

    /**
     * Selects an unexpanded action for a new tree node.
     */
    private int selectExpansionAction(
            GameState state
    ) {
        ArrayList<Integer> allUnexpandedActions =
                new ArrayList<>();

        ArrayList<Integer> safeUnexpandedActions =
                new ArrayList<>();

        Vector2d currentPosition =
                state.getPosition();

        DangerMap dangerMap = null;

        if (params.use_safe_expansion
                && currentPosition != null) {
            dangerMap = new DangerMap(state);
        }

        for (int actionIndex = 0;
             actionIndex < children.length;
             actionIndex++) {

            if (children[actionIndex] != null) {
                continue;
            }

            allUnexpandedActions.add(actionIndex);

            if (params.use_safe_expansion
                    && currentPosition != null
                    && isSafeDestination(
                    state,
                    dangerMap,
                    currentPosition,
                    actions[actionIndex]
            )) {

                safeUnexpandedActions.add(
                        actionIndex
                );
            }
        }

        if (!safeUnexpandedActions.isEmpty()) {
            return chooseRandomIndex(
                    safeUnexpandedActions
            );
        }

        if (!allUnexpandedActions.isEmpty()) {
            return chooseRandomIndex(
                    allUnexpandedActions
            );
        }

        throw new IllegalStateException(
                "expand() called on a fully expanded node."
        );
    }

    /**
     * Randomly selects one original action index from a non-empty list.
     */
    private int chooseRandomIndex(
            ArrayList<Integer> actionIndexes
    ) {
        if (actionIndexes == null
                || actionIndexes.isEmpty()) {

            throw new IllegalArgumentException(
                    "Action index list cannot be empty."
            );
        }

        int randomListIndex =
                m_rnd.nextInt(
                        actionIndexes.size()
                );

        return actionIndexes.get(
                randomListIndex
        );
    }

    /**
     * Advances a simulated state using the controlled action and random
     * actions for the other three players.
     */
    private void roll(
            GameState gameState,
            Types.ACTIONS controlledAction
    ) {
        int numberOfPlayers = 4;

        Types.ACTIONS[] allActions =
                new Types.ACTIONS[numberOfPlayers];

        int controlledPlayerIndex =
                gameState.getPlayerId()
                        - Types.TILETYPE.AGENT0.getKey();

        for (int playerIndex = 0;
             playerIndex < numberOfPlayers;
             playerIndex++) {

            if (controlledPlayerIndex
                    == playerIndex) {

                allActions[playerIndex] =
                        controlledAction;

            } else {
                int actionIndex =
                        m_rnd.nextInt(
                                gameState.nActions()
                        );

                allActions[playerIndex] =
                        Types.ACTIONS.all()
                                .get(actionIndex);
            }
        }

        gameState.next(allActions);
    }

    private SafetyTreeNode uct(
            GameState state
    ) {
        SafetyTreeNode selected = null;
        double bestValue =
                -Double.MAX_VALUE;

        for (SafetyTreeNode child
                : children) {

            double heuristicValue =
                    child.totValue;

            double childValue =
                    heuristicValue
                            / (
                            child.nVisits
                                    + params.epsilon
                    );

            childValue =
                    Utils.normalise(
                            childValue,
                            bounds[0],
                            bounds[1]
                    );

            double uctValue =
                    childValue
                            + params.K
                            * Math.sqrt(
                            Math.log(
                                    nVisits + 1
                            )
                                    / (
                                    child.nVisits
                                            + params.epsilon
                            )
                    );

            uctValue =
                    Utils.noise(
                            uctValue,
                            params.epsilon,
                            m_rnd.nextDouble()
                    );

            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }

        if (selected == null) {
            throw new RuntimeException(
                    "Warning! returning null: "
                            + bestValue
                            + " : "
                            + children.length
                            + " "
                            + bounds[0]
                            + " "
                            + bounds[1]
            );
        }

        roll(
                state,
                actions[selected.childIdx]
        );

        return selected;
    }

    private double rollOut(
            GameState state
    ) {
        int currentDepth = m_depth;

        while (!finishRollout(
                state,
                currentDepth
        )) {
            int actionIndex =
                    selectRolloutAction(state);

            roll(
                    state,
                    actions[actionIndex]
            );

            currentDepth++;
        }

        return rootStateHeuristic
                .evaluateState(state);
    }

    /**
     * Selects an action during an MCTS rollout.
     *
     * When danger-aware rollouts are enabled, the method samples from
     * actions whose immediate destination is not blocked, burning or
     * threatened within the configured danger horizon.
     *
     * When disabled, the framework-style rollout policy is used.
     */
    private int selectRolloutAction(
            GameState state
    ) {
        if (!params.use_safe_rollouts) {
            return selectOriginalRolloutAction(
                    state
            );
        }

        Vector2d currentPosition =
                state.getPosition();

        if (currentPosition == null) {
            return m_rnd.nextInt(
                    num_actions
            );
        }

        DangerMap dangerMap =
                new DangerMap(state);

        ArrayList<Integer> safeActionIndexes =
                new ArrayList<>();

        for (int actionIndex = 0;
             actionIndex < num_actions;
             actionIndex++) {

            if (isSafeDestination(
                    state,
                    dangerMap,
                    currentPosition,
                    actions[actionIndex]
            )) {
                safeActionIndexes.add(
                        actionIndex
                );
            }
        }

        if (safeActionIndexes.isEmpty()) {
            return selectOriginalRolloutAction(
                    state
            );
        }

        return chooseRandomIndex(
                safeActionIndexes
        );
    }

    /**
     * Implements the framework-style rollout policy while preserving
     * original action indices after candidates are removed.
     *
     * The policy rejects only destinations containing active flames.
     */
    private int selectOriginalRolloutAction(
            GameState state
    ) {
        Types.TILETYPE[][] board =
                state.getBoard();

        Vector2d position =
                state.getPosition();

        if (position == null) {
            return m_rnd.nextInt(
                    num_actions
            );
        }

        ArrayList<Integer> remainingActions =
                new ArrayList<>();

        for (int actionIndex = 0;
             actionIndex < num_actions;
             actionIndex++) {

            remainingActions.add(
                    actionIndex
            );
        }

        while (!remainingActions.isEmpty()) {
            int listIndex =
                    m_rnd.nextInt(
                            remainingActions.size()
                    );

            int actionIndex =
                    remainingActions.remove(
                            listIndex
                    );

            Types.ACTIONS action =
                    actions[actionIndex];

            Vector2d direction =
                    action.getDirection().toVec();

            int destinationX =
                    position.x + direction.x;

            int destinationY =
                    position.y + direction.y;

            if (destinationY >= 0
                    && destinationY < board.length
                    && destinationX >= 0
                    && destinationX
                    < board[0].length
                    && board[destinationY][destinationX]
                    != Types.TILETYPE.FLAMES) {

                return actionIndex;
            }
        }

        return m_rnd.nextInt(
                num_actions
        );
    }

    /**
     * Checks whether an action has an immediately acceptable destination.
     *
     * This is a short-horizon safety filter rather than a complete proof
     * that the action survives every possible future sequence.
     */
    private boolean isSafeDestination(
            GameState state,
            DangerMap dangerMap,
            Vector2d currentPosition,
            Types.ACTIONS action
    ) {
        if (dangerMap == null
                || currentPosition == null
                || action == null) {
            return false;
        }

        if (action
                == Types.ACTIONS.ACTION_BOMB) {

            if (state.getAmmo() <= 0) {
                return false;
            }

            if (params.use_escape_check
                    && !EscapeRouteChecker
                    .hasEscapeRoute(state)) {

                return false;
            }

            /*
             * Bomb placement does not move the agent immediately.
             */
            return !dangerMap
                    .isDangerousWithin(
                            currentPosition.x,
                            currentPosition.y,
                            params.danger_horizon
                    );
        }

        Vector2d direction =
                action.getDirection().toVec();

        int destinationX =
                currentPosition.x
                        + direction.x;

        int destinationY =
                currentPosition.y
                        + direction.y;

        if (!dangerMap.isInsideBoard(
                destinationX,
                destinationY
        )) {
            return false;
        }

        Types.TILETYPE[][] board =
                state.getBoard();

        Types.TILETYPE destinationTile =
                board[destinationY][destinationX];

        if (destinationTile
                == Types.TILETYPE.RIGID
                || destinationTile
                == Types.TILETYPE.WOOD
                || destinationTile
                == Types.TILETYPE.BOMB
                || destinationTile
                == Types.TILETYPE.FLAMES
                || destinationTile
                == Types.TILETYPE.FOG) {

            return false;
        }

        return !dangerMap.isDangerousWithin(
                destinationX,
                destinationY,
                params.danger_horizon
        );
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean finishRollout(
            GameState rolloutState,
            int depth
    ) {
        if (depth >= params.rollout_depth) {
            return true;
        }

        if (rolloutState.isTerminal()) {
            return true;
        }

        return false;
    }

    private void backUp(
            SafetyTreeNode node,
            double result
    ) {
        SafetyTreeNode current = node;

        while (current != null) {
            current.nVisits++;
            current.totValue += result;

            if (result < current.bounds[0]) {
                current.bounds[0] = result;
            }

            if (result > current.bounds[1]) {
                current.bounds[1] = result;
            }

            current = current.parent;
        }
    }

    int mostVisitedAction() {
        int selected = -1;

        double bestValue =
                -Double.MAX_VALUE;

        boolean allEqual = true;
        double firstVisitCount = -1;

        for (int actionIndex = 0;
             actionIndex < children.length;
             actionIndex++) {

            SafetyTreeNode child =
                    children[actionIndex];

            if (child == null) {
                continue;
            }

            if (firstVisitCount == -1) {
                firstVisitCount =
                        child.nVisits;

            } else if (firstVisitCount
                    != child.nVisits) {

                allEqual = false;
            }

            double childValue =
                    child.nVisits;

            childValue =
                    Utils.noise(
                            childValue,
                            params.epsilon,
                            m_rnd.nextDouble()
                    );

            if (childValue > bestValue) {
                bestValue = childValue;
                selected = actionIndex;
            }
        }

        if (selected == -1) {
            selected = 0;

        } else if (allEqual) {
            selected = bestAction();
        }

        return selected;
    }

    private int bestAction() {
        int selected = -1;

        double bestValue =
                -Double.MAX_VALUE;

        for (int actionIndex = 0;
             actionIndex < children.length;
             actionIndex++) {

            SafetyTreeNode child =
                    children[actionIndex];

            if (child == null) {
                continue;
            }

            double childValue =
                    child.totValue
                            / (
                            child.nVisits
                                    + params.epsilon
                    );

            childValue =
                    Utils.noise(
                            childValue,
                            params.epsilon,
                            m_rnd.nextDouble()
                    );

            if (childValue > bestValue) {
                bestValue = childValue;
                selected = actionIndex;
            }
        }

        if (selected == -1) {
            System.out.println(
                    "Unexpected selection!"
            );

            selected = 0;
        }

        return selected;
    }

    private boolean notFullyExpanded() {
        for (SafetyTreeNode child
                : children) {

            if (child == null) {
                return true;
            }
        }

        return false;
    }
}