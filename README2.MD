# SafetyMCTS for Java-Pommerman

**Author:** Rohan Burman
**Student number:** 220260916
**Module:** ECS7032P – Statistical Planning and Reinforcement Learning

## Overview

SafetyMCTS is a Monte Carlo Tree Search agent for the Java-Pommerman framework. It extends the supplied MCTS implementation with Pommerman-specific safety information.

The agent introduces:

* a bomb danger map that records the earliest visible threat to each board cell;
* danger-aware rollout action selection;
* safety-biased tree expansion;
* breadth-first escape-route checking before bomb placement; and
* a safety-aware state heuristic based on immediate danger and local mobility.

Unsafe actions are generally treated as lower-priority alternatives rather than being permanently removed from the search.

## Required Files

Place the following directory inside the Java-Pommerman `src/players` directory:

```text
players/safetymcts/
├── DangerMap.java
├── EscapeRouteChecker.java
├── SafetyHeuristic.java
├── SafetyMCTSParams.java
├── SafetyMCTSPlayer.java
└── SafetyTreeNode.java
```

The optional experimental runner should be placed directly inside `src`:

```text
src/SafetyMCTSExperiment.java
```

## Agent Construction

The agent can be constructed using its default parameters:

```java
Player player = new SafetyMCTSPlayer(seed, playerId);
```

A configurable agent can be constructed using `SafetyMCTSParams`:

```java
SafetyMCTSParams params = new SafetyMCTSParams();

params.stop_type = params.STOP_ITERATIONS;
params.num_iterations = 100;
params.rollout_depth = 12;

params.use_safe_rollouts = true;
params.use_safe_expansion = true;
params.use_escape_check = true;
params.heuristic_method = params.SAFETY_HEURISTIC;

Player player = new SafetyMCTSPlayer(
        seed,
        playerId,
        params
);
```

`seed` is the random seed used by the agent. `playerId` must correspond to the appropriate Java-Pommerman agent tile identifier.

## Main Parameters

| Parameter            | Description                                                           |
| -------------------- | --------------------------------------------------------------------- |
| `num_iterations`     | Number of MCTS iterations performed per decision                      |
| `rollout_depth`      | Maximum simulated depth of each rollout                               |
| `use_safe_rollouts`  | Enables danger-aware action selection during rollouts                 |
| `use_safe_expansion` | Prioritises safer unvisited actions during expansion                  |
| `use_escape_check`   | Rejects bomb placement when no escape route is found                  |
| `danger_horizon`     | Defines how soon a visible threat must occur to be treated as urgent  |
| `heuristic_method`   | Selects the framework or safety-aware state heuristic                 |
| `danger_weight`      | Controls the contribution of immediate danger to the safety heuristic |
| `mobility_weight`    | Controls the contribution of safe-action availability                 |

## SafetyMCTS Configurations

The submitted code supports the following configurations:

| Configuration         | Safe rollout | Safe expansion | Escape check | Safety heuristic |
| --------------------- | -----------: | -------------: | -----------: | ---------------: |
| Baseline copy         |           No |             No |           No |               No |
| Safe rollout          |          Yes |             No |           No |               No |
| Safe expansion        |           No |            Yes |           No |               No |
| Rollout and expansion |          Yes |            Yes |           No |               No |
| Escape-aware          |          Yes |            Yes |          Yes |               No |
| Safety heuristic      |           No |             No |           No |              Yes |
| Full SafetyMCTS       |          Yes |            Yes |          Yes |              Yes |

The main submitted agent is the **Full SafetyMCTS** configuration.

## Running in IntelliJ IDEA

1. Clone or open the Java-Pommerman framework.
2. Copy `players/safetymcts` into `src/players`.
3. Copy `SafetyMCTSExperiment.java` into `src` when running the supplied experiments.
4. Mark `src` as the project source directory if IntelliJ has not detected it automatically.
5. Build the project using **Build → Rebuild Project**.
6. Instantiate `SafetyMCTSPlayer` in a launcher using one of the constructors shown above.

In the author’s modified `Run.java`, the following command-line configuration runs Full SafetyMCTS against the original MCTS, RHEA and rule-based agents:

```text
0 1 1 -1 6 5 4 3
```

The numeric identifier is specific to the modified launcher and is not required when constructing the agent directly.

## Running the Experiment

Run:

```text
SafetyMCTSExperiment
```

with the number of repetitions as its program argument.

For the final reported experiment:

```text
7
```

The final experiment used:

* free-for-all game mode;
* full observability;
* 20 fixed map seeds;
* 7 repetitions;
* rotation through all 4 player positions;
* 100 MCTS iterations per decision;
* rollout depth 12;
* 560 games per reported configuration.

Results are written to:

```text
experiment-output/
```

## Analysing Results

The optional Python script `analyse_experiment.py` generates summary tables, Wilson confidence intervals and figures.

Install its dependencies:

```powershell
python -m pip install pandas matplotlib openpyxl
```

Run:

```powershell
python .\analyse_experiment.py `
    ".\experiment-output\safety-mcts-results-TIMESTAMP.csv"
```

Generated analysis files are written to:

```text
analysis-output/
```

## Tested Environment

* Windows 11
* IntelliJ IDEA
* OpenJDK 26.0.2
* Java-Pommerman framework
* Python 3 with pandas, matplotlib and openpyxl for result analysis

## Notes and Limitations

The danger map predicts threats from the currently visible bomb configuration. It does not fully model every chain reaction, future opponent bomb placement or moving bombs.

The escape-route checker evaluates routes using the current board geometry. Opponents may subsequently block a route or create additional hazards.

These limitations are discussed in the accompanying report.
