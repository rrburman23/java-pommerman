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

public class SafetyTreeNode
{
    public SafetyMCTSParams params;

    private SafetyTreeNode parent;
    private SafetyTreeNode[] children;
    private double totValue;
    private int nVisits;
    private Random m_rnd;
    private int m_depth;
    private double[] bounds = new double[]{Double.MAX_VALUE, -Double.MAX_VALUE};
    private int childIdx;
    private int fmCallsCount;

    private int num_actions;
    private Types.ACTIONS[] actions;

    private GameState rootState;
    private StateHeuristic rootStateHeuristic;

    SafetyTreeNode(SafetyMCTSParams p, Random rnd, int num_actions, Types.ACTIONS[] actions) {
        this(p, null, -1, rnd, num_actions, actions, 0, null);
    }

    private SafetyTreeNode(SafetyMCTSParams p, SafetyTreeNode parent, int childIdx, Random rnd, int num_actions,
                           Types.ACTIONS[] actions, int fmCallsCount, StateHeuristic sh) {
        this.params = p;
        this.fmCallsCount = fmCallsCount;
        this.parent = parent;
        this.m_rnd = rnd;
        this.num_actions = num_actions;
        this.actions = actions;
        children = new SafetyTreeNode[num_actions];
        totValue = 0.0;
        this.childIdx = childIdx;
        if(parent != null) {
            m_depth = parent.m_depth + 1;
            this.rootStateHeuristic = sh;
        }
        else
            m_depth = 0;
    }

    void setRootGameState(GameState gs)
    {
        this.rootState = gs;
        if (params.heuristic_method == params.CUSTOM_HEURISTIC)
            this.rootStateHeuristic = new CustomHeuristic(gs);
        else if (params.heuristic_method == params.ADVANCED_HEURISTIC) // New method: combined heuristics
            this.rootStateHeuristic = new AdvancedHeuristic(gs, m_rnd);
    }


    void mctsSearch(ElapsedCpuTimer elapsedTimer) {

        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int numIters = 0;

        int remainingLimit = 5;
        boolean stop = false;

        while(!stop){

            GameState state = rootState.copy();
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SafetyTreeNode selected = treePolicy(state);
            double delta = selected.rollOut(state);
            backUp(selected, delta);

            //Stopping condition
            if(params.stop_type == params.STOP_TIME) {
                numIters++;
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis()) ;
                avgTimeTaken  = acumTimeTaken/numIters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            }else if(params.stop_type == params.STOP_ITERATIONS) {
                numIters++;
                stop = numIters >= params.num_iterations;
            }else if(params.stop_type == params.STOP_FMCALLS)
            {
                fmCallsCount+=params.rollout_depth;
                stop = (fmCallsCount + params.rollout_depth) > params.num_fmcalls;
            }
        }
        //System.out.println(" ITERS " + numIters);
    }

    private SafetyTreeNode treePolicy(GameState state) {

        SafetyTreeNode cur = this;

        while (!state.isTerminal() && cur.m_depth < params.rollout_depth)
        {
            if (cur.notFullyExpanded()) {
                return cur.expand(state);

            } else {
                cur = cur.uct(state);
            }
        }

        return cur;
    }


    /**
     * Expands one previously unvisited action.
     *
     * When safe expansion is enabled, unvisited actions with an immediately
     * safe destination are preferred. If none appear safe, expansion falls
     * back to any unvisited action so the tree search can continue.
     */
    private SafetyTreeNode expand(GameState state)
    {
        int selectedAction = selectExpansionAction(state);

        roll(state, actions[selectedAction]);

        SafetyTreeNode child = new SafetyTreeNode(
                params,
                this,
                selectedAction,
                this.m_rnd,
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
    private int selectExpansionAction(GameState state)
    {
        ArrayList<Integer> allUnexpandedActions =
                new ArrayList<>();

        ArrayList<Integer> safeUnexpandedActions =
                new ArrayList<>();

        Vector2d currentPosition = state.getPosition();
        DangerMap dangerMap = null;

        if (params.use_safe_expansion
                && currentPosition != null) {
            dangerMap = new DangerMap(state);
        }

        for (int actionIndex = 0;
             actionIndex < children.length;
             actionIndex++)
        {
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
                safeUnexpandedActions.add(actionIndex);
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
     * Randomly selects one action index from a non-empty list.
     */
    private int chooseRandomIndex(
            ArrayList<Integer> actionIndexes
    )
    {
        if (actionIndexes == null
                || actionIndexes.isEmpty()) {
            throw new IllegalArgumentException(
                    "Action index list cannot be empty."
            );
        }

        int randomListIndex =
                m_rnd.nextInt(actionIndexes.size());

        return actionIndexes.get(randomListIndex);
    }

    private void roll(GameState gs, Types.ACTIONS act)
    {
        //Simple, all random first, then my position.
        int nPlayers = 4;
        Types.ACTIONS[] actionsAll = new Types.ACTIONS[4];
        int playerId = gs.getPlayerId() - Types.TILETYPE.AGENT0.getKey();

        for(int i = 0; i < nPlayers; ++i)
        {
            if(playerId == i)
            {
                actionsAll[i] = act;
            }else {
                int actionIdx = m_rnd.nextInt(gs.nActions());
                actionsAll[i] = Types.ACTIONS.all().get(actionIdx);
            }
        }

        gs.next(actionsAll);

    }

    private SafetyTreeNode uct(GameState state) {
        SafetyTreeNode selected = null;
        double bestValue = -Double.MAX_VALUE;
        for (SafetyTreeNode child : this.children)
        {
            double hvVal = child.totValue;
            double childValue =  hvVal / (child.nVisits + params.epsilon);

            childValue = Utils.normalise(childValue, bounds[0], bounds[1]);

            double uctValue = childValue +
                    params.K * Math.sqrt(Math.log(this.nVisits + 1) / (child.nVisits + params.epsilon));

            uctValue = Utils.noise(uctValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly

            // small sampleRandom numbers: break ties in unexpanded nodes
            if (uctValue > bestValue) {
                selected = child;
                bestValue = uctValue;
            }
        }
        if (selected == null)
        {
            throw new RuntimeException("Warning! returning null: " + bestValue + " : " + this.children.length + " " +
                    + bounds[0] + " " + bounds[1]);
        }

        //Roll the state:
        roll(state, actions[selected.childIdx]);

        return selected;
    }

    private double rollOut(GameState state)
    {
        int thisDepth = this.m_depth;

        while (!finishRollout(state, thisDepth)) {
            int action = selectRolloutAction(state);
            roll(state, actions[action]);
            thisDepth++;
        }

        return rootStateHeuristic.evaluateState(state);
    }



    @SuppressWarnings("RedundantIfStatement")
    private boolean finishRollout(GameState rollerState, int depth)
    {
        if (depth >= params.rollout_depth)      //rollout end condition.
            return true;

        if (rollerState.isTerminal())               //end of game
            return true;

        return false;
    }

    private void backUp(SafetyTreeNode node, double result)
    {
        SafetyTreeNode n = node;
        while(n != null)
        {
            n.nVisits++;
            n.totValue += result;
            if (result < n.bounds[0]) {
                n.bounds[0] = result;
            }
            if (result > n.bounds[1]) {
                n.bounds[1] = result;
            }
            n = n.parent;
        }
    }


    int mostVisitedAction() {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;
        boolean allEqual = true;
        double first = -1;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null)
            {
                if(first == -1)
                    first = children[i].nVisits;
                else if(first != children[i].nVisits)
                {
                    allEqual = false;
                }

                double childValue = children[i].nVisits;
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            selected = 0;
        }else if(allEqual)
        {
            //If all are equal, we opt to choose for the one with the best Q.
            selected = bestAction();
        }

        return selected;
    }

    private int bestAction()
    {
        int selected = -1;
        double bestValue = -Double.MAX_VALUE;

        for (int i=0; i<children.length; i++) {

            if(children[i] != null) {
                double childValue = children[i].totValue / (children[i].nVisits + params.epsilon);
                childValue = Utils.noise(childValue, params.epsilon, this.m_rnd.nextDouble());     //break ties randomly
                if (childValue > bestValue) {
                    bestValue = childValue;
                    selected = i;
                }
            }
        }

        if (selected == -1)
        {
            System.out.println("Unexpected selection!");
            selected = 0;
        }

        return selected;
    }


    private boolean notFullyExpanded() {
        for (SafetyTreeNode tn : children) {
            if (tn == null) {
                return true;
            }
        }

        return false;
    }

    /**
     * Selects an action during an MCTS rollout.
     *
     * When danger-aware rollouts are enabled, the method chooses randomly
     * between actions whose immediate destination is not blocked, burning,
     * or threatened by a bomb within the configured danger horizon.
     */
    private int selectRolloutAction(GameState state)
    {
        if (!params.use_safe_rollouts) {
            return m_rnd.nextInt(num_actions);
        }

        Vector2d currentPosition = state.getPosition();

        if (currentPosition == null) {
            return m_rnd.nextInt(num_actions);
        }

        DangerMap dangerMap = new DangerMap(state);
        ArrayList<Integer> safeActionIndexes = new ArrayList<>();

        for (int actionIndex = 0;
             actionIndex < num_actions;
             actionIndex++)
        {
            Types.ACTIONS action = actions[actionIndex];

            if (isSafeDestination(
                    state,
                    dangerMap,
                    currentPosition,
                    action
            )) {
                safeActionIndexes.add(actionIndex);
            }
        }

        /*
         * When every action appears dangerous, retain exploration by
         * returning a random action instead of stopping the rollout.
         */
        if (safeActionIndexes.isEmpty()) {
            return m_rnd.nextInt(num_actions);
        }

        return chooseRandomIndex(safeActionIndexes);
    }

    /**
     * Checks the immediate destination produced by an action.
     *
     * This is a lightweight safety filter rather than a complete survival
     * proof. Longer-term escape-route analysis will be added separately.
     */
    private boolean isSafeDestination(
            GameState state,
            DangerMap dangerMap,
            Vector2d currentPosition,
            Types.ACTIONS action
    )
    {
        Vector2d direction = action.getDirection().toVec();

        int destinationX =
                currentPosition.x + direction.x;

        int destinationY =
                currentPosition.y + direction.y;

        if (!dangerMap.isInsideBoard(
                destinationX,
                destinationY
        )) {
            return false;
        }

        Types.TILETYPE[][] board = state.getBoard();
        Types.TILETYPE destinationTile =
                board[destinationY][destinationX];

        if (destinationTile == Types.TILETYPE.RIGID
                || destinationTile == Types.TILETYPE.WOOD
                || destinationTile == Types.TILETYPE.BOMB
                || destinationTile == Types.TILETYPE.FLAMES) {
            return false;
        }

        return !dangerMap.isDangerousWithin(
                destinationX,
                destinationY,
                params.danger_horizon
        );
    }
}
