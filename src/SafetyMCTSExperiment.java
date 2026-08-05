import core.Game;
import players.Player;
import players.SimplePlayer;
import players.mcts.MCTSParams;
import players.mcts.MCTSPlayer;
import players.rhea.RHEAPlayer;
import players.rhea.utils.Constants;
import players.rhea.utils.RHEAParams;
import players.safetymcts.SafetyMCTSParams;
import players.safetymcts.SafetyMCTSPlayer;
import utils.Types;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reproducible experiment runner for the SafetyMCTS assignment.
 *
 * Each SafetyMCTS variant is evaluated with the same fixed map seeds,
 * search budget and opponent set. The tested agent is rotated through
 * all four player positions to reduce position bias.
 */
public final class SafetyMCTSExperiment {

    private static final long[] FIXED_MAP_SEEDS = {
            93988, 19067, 64416, 83884, 55636,
            27599, 44350, 87872, 40815, 11772,
            58367, 17546, 75375, 75772, 58237,
            30464, 27180, 23643, 67054, 19508
    };

    private static final int BOARD_SIZE = Types.BOARD_SIZE;
    private static final Types.GAME_MODE GAME_MODE =
            Types.GAME_MODE.FFA;

    private static final int ORIGINAL_MCTS = 5;
    private static final int RHEA = 4;
    private static final int RULE_BASED = 3;

    /**
     * IDs correspond to the ablation configurations registered in Run.
     */
    private static final LinkedHashMap<Integer, String> VARIANTS =
            new LinkedHashMap<>();

    static {
        VARIANTS.put(6, "full");
        VARIANTS.put(7, "baseline_copy");
        VARIANTS.put(8, "safe_rollout");
        VARIANTS.put(9, "safe_expansion");
        VARIANTS.put(10, "rollout_expansion");
        VARIANTS.put(11, "escape_aware");
        VARIANTS.put(12, "safety_heuristic");
    }

    private SafetyMCTSExperiment() {
        // Utility entry-point class.
    }

    public static void main(String[] args) {
        /*
         * Validation:
         *   java SafetyMCTSExperiment 1
         *
         * Final experiment:
         *   java SafetyMCTSExperiment 7
         *
         * 20 seeds × 7 repetitions × 4 positions
         * = 560 games per variant.
         */
        int repetitionsPerSeedPerPosition = 1;

        if (args.length >= 1) {
            repetitionsPerSeedPerPosition =
                    Integer.parseInt(args[0]);
        }

        if (repetitionsPerSeedPerPosition <= 0) {
            throw new IllegalArgumentException(
                    "Repetitions must be greater than zero."
            );
        }

        Types.DEFAULT_VISION_RANGE = -1;
        Types.VISUALS = false;

        Path outputFile = createOutputPath();

        try {
            runAllExperiments(
                    repetitionsPerSeedPerPosition,
                    outputFile
            );
        } catch (IOException exception) {
            System.err.println(
                    "Experiment failed while writing CSV."
            );
            exception.printStackTrace();
            System.exit(1);
        }

        System.out.println();
        System.out.println("Experiment complete.");
        System.out.println(
                "CSV written to: "
                        + outputFile.toAbsolutePath()
        );
    }

    private static void runAllExperiments(
            int repetitions,
            Path outputFile
    ) throws IOException {

        Files.createDirectories(outputFile.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(
                outputFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        )) {
            writeHeader(writer);

            for (Map.Entry<Integer, String> variant
                    : VARIANTS.entrySet()) {

                runVariant(
                        variant.getKey(),
                        variant.getValue(),
                        repetitions,
                        writer
                );
            }
        }
    }

    private static void runVariant(
            int variantId,
            String variantName,
            int repetitions,
            BufferedWriter writer
    ) throws IOException {

        int gamesPerVariant =
                FIXED_MAP_SEEDS.length
                        * repetitions
                        * Types.NUM_PLAYERS;

        int completedGames = 0;
        int wins = 0;
        int ties = 0;
        int losses = 0;

        System.out.println();
        System.out.println(
                "============================================"
        );
        System.out.println(
                "Running variant: " + variantName
        );
        System.out.println(
                "Games: " + gamesPerVariant
        );
        System.out.println(
                "============================================"
        );

        for (int testedPosition = 0;
             testedPosition < Types.NUM_PLAYERS;
             testedPosition++) {

            for (long mapSeed : FIXED_MAP_SEEDS) {

                for (int repetition = 0;
                     repetition < repetitions;
                     repetition++) {

                    long playerSeed =
                            deterministicPlayerSeed(
                                    mapSeed,
                                    repetition,
                                    testedPosition,
                                    variantId
                            );

                    String[] agentNames = createAgentNames(
                            variantName,
                            testedPosition
                    );

                    ArrayList<Player> players =
                            createPlayers(
                                    variantId,
                                    testedPosition,
                                    playerSeed
                            );

                    String gameId = createGameId(
                            variantName,
                            mapSeed,
                            repetition,
                            testedPosition
                    );

                    Game game = new Game(
                            mapSeed,
                            BOARD_SIZE,
                            GAME_MODE,
                            gameId
                    );

                    game.setPlayers(players);

                    Types.RESULT[] results =
                            game.run(false);

                    int[] overtimes =
                            game.getPlayerOvertimes();

                    Types.RESULT testedResult =
                            results[testedPosition];

                    switch (testedResult) {
                        case WIN:
                            wins++;
                            break;
                        case TIE:
                            ties++;
                            break;
                        case LOSS:
                            losses++;
                            break;
                        default:
                            throw new IllegalStateException(
                                    "Unexpected result: "
                                            + testedResult
                            );
                    }

                    int winnerPosition =
                            findWinnerPosition(results);

                    writeGameRow(
                            writer,
                            variantId,
                            variantName,
                            mapSeed,
                            repetition,
                            playerSeed,
                            testedPosition,
                            agentNames,
                            results,
                            testedResult,
                            winnerPosition,
                            overtimes
                    );

                    completedGames++;

                    if (completedGames % 20 == 0
                            || completedGames
                            == gamesPerVariant) {
                        System.out.printf(
                                "%s: %d/%d games%n",
                                variantName,
                                completedGames,
                                gamesPerVariant
                        );
                    }
                }
            }
        }

        double winRate =
                wins * 100.0 / gamesPerVariant;

        double tieRate =
                ties * 100.0 / gamesPerVariant;

        double lossRate =
                losses * 100.0 / gamesPerVariant;

        System.out.printf(
                "%s summary: wins=%d (%.2f%%), "
                        + "ties=%d (%.2f%%), "
                        + "losses=%d (%.2f%%)%n",
                variantName,
                wins,
                winRate,
                ties,
                tieRate,
                losses,
                lossRate
        );
    }

    /**
     * Builds four agents, placing the tested SafetyMCTS variant in the
     * requested player position. The remaining positions use original
     * MCTS, RHEA and RuleBased in a stable order.
     */
    private static ArrayList<Player> createPlayers(
            int variantId,
            int testedPosition,
            long playerSeed
    ) {
        int[] opponentIds = {
                ORIGINAL_MCTS,
                RHEA,
                RULE_BASED
        };

        ArrayList<Player> players = new ArrayList<>();
        int opponentIndex = 0;

        for (int position = 0;
             position < Types.NUM_PLAYERS;
             position++) {

            int agentId;

            if (position == testedPosition) {
                agentId = variantId;
            } else {
                agentId = opponentIds[opponentIndex++];
            }

            int playerId =
                    Types.TILETYPE.AGENT0.getKey()
                            + position;

            /*
             * Each player receives a deterministic but distinct seed.
             */
            long individualSeed =
                    playerSeed + position * 10_007L;

            players.add(
                    createPlayer(
                            agentId,
                            individualSeed,
                            playerId
                    )
            );
        }

        return players;
    }

    private static String[] createAgentNames(
            String variantName,
            int testedPosition
    ) {
        String[] names = new String[Types.NUM_PLAYERS];
        String[] opponentNames = {
                "MCTS",
                "RHEA",
                "RuleBased"
        };

        int opponentIndex = 0;

        for (int position = 0;
             position < Types.NUM_PLAYERS;
             position++) {

            if (position == testedPosition) {
                names[position] =
                        "SafetyMCTS-" + variantName;
            } else {
                names[position] =
                        opponentNames[opponentIndex++];
            }
        }

        return names;
    }

    private static Player createPlayer(
            int agentId,
            long seed,
            int playerId
    ) {
        switch (agentId) {
            case 3:
                return new SimplePlayer(seed, playerId);

            case 4:
                RHEAParams rheaParams = new RHEAParams();
                rheaParams.budget_type =
                        Constants.ITERATION_BUDGET;
                rheaParams.iteration_budget = 200;
                rheaParams.individual_length = 12;
                rheaParams.heurisic_type =
                        Constants.CUSTOM_HEURISTIC;
                rheaParams.mutation_rate = 0.5;

                return new RHEAPlayer(
                        seed,
                        playerId,
                        rheaParams
                );

            case 5:
                MCTSParams mctsParams = new MCTSParams();
                mctsParams.stop_type =
                        mctsParams.STOP_ITERATIONS;
                mctsParams.num_iterations = 200;
                mctsParams.rollout_depth = 12;
                mctsParams.heuristic_method =
                        mctsParams.CUSTOM_HEURISTIC;

                return new MCTSPlayer(
                        seed,
                        playerId,
                        mctsParams
                );

            case 6:
                return createSafetyPlayer(
                        seed,
                        playerId,
                        true,
                        true,
                        true,
                        true
                );

            case 7:
                return createSafetyPlayer(
                        seed,
                        playerId,
                        false,
                        false,
                        false,
                        false
                );

            case 8:
                return createSafetyPlayer(
                        seed,
                        playerId,
                        true,
                        false,
                        false,
                        false
                );

            case 9:
                return createSafetyPlayer(
                        seed,
                        playerId,
                        false,
                        true,
                        false,
                        false
                );

            case 10:
                return createSafetyPlayer(
                        seed,
                        playerId,
                        true,
                        true,
                        false,
                        false
                );

            case 11:
                return createSafetyPlayer(
                        seed,
                        playerId,
                        true,
                        true,
                        true,
                        false
                );

            case 12:
                return createSafetyPlayer(
                        seed,
                        playerId,
                        false,
                        false,
                        false,
                        true
                );

            default:
                throw new IllegalArgumentException(
                        "Unsupported agent ID: " + agentId
                );
        }
    }

    private static SafetyMCTSPlayer createSafetyPlayer(
            long seed,
            int playerId,
            boolean safeRollouts,
            boolean safeExpansion,
            boolean escapeCheck,
            boolean safetyHeuristic
    ) {
        SafetyMCTSParams params =
                new SafetyMCTSParams();

        params.stop_type = params.STOP_ITERATIONS;
        params.num_iterations = 200;
        params.rollout_depth = 12;

        params.use_safe_rollouts = safeRollouts;
        params.use_safe_expansion = safeExpansion;
        params.use_escape_check = escapeCheck;

        params.heuristic_method =
                safetyHeuristic
                        ? params.SAFETY_HEURISTIC
                        : params.CUSTOM_HEURISTIC;

        return new SafetyMCTSPlayer(
                seed,
                playerId,
                params
        );
    }

    private static long deterministicPlayerSeed(
            long mapSeed,
            int repetition,
            int testedPosition,
            int variantId
    ) {
        long seed = mapSeed * 1_000_003L;
        seed += repetition * 10_007L;
        seed += testedPosition * 101L;
        seed += variantId;

        return seed;
    }

    private static int findWinnerPosition(
            Types.RESULT[] results
    ) {
        int winner = -1;

        for (int position = 0;
             position < results.length;
             position++) {

            if (results[position]
                    == Types.RESULT.WIN) {

                /*
                 * FFA normally has one winner. Return -1 if the result
                 * does not contain exactly one winner.
                 */
                if (winner != -1) {
                    return -1;
                }

                winner = position;
            }
        }

        return winner;
    }

    private static String createGameId(
            String variantName,
            long mapSeed,
            int repetition,
            int testedPosition
    ) {
        return variantName
                + "-"
                + mapSeed
                + "-"
                + repetition
                + "-"
                + testedPosition;
    }

    private static void writeHeader(
            BufferedWriter writer
    ) throws IOException {
        writer.write(
                "variant_id,variant,map_seed,repetition,"
                        + "player_seed,tested_position,"
                        + "player_0,player_1,player_2,player_3,"
                        + "result_0,result_1,result_2,result_3,"
                        + "tested_result,winner_position,"
                        + "overtime_0,overtime_1,"
                        + "overtime_2,overtime_3"
        );

        writer.newLine();
    }

    private static void writeGameRow(
            BufferedWriter writer,
            int variantId,
            String variantName,
            long mapSeed,
            int repetition,
            long playerSeed,
            int testedPosition,
            String[] agentNames,
            Types.RESULT[] results,
            Types.RESULT testedResult,
            int winnerPosition,
            int[] overtimes
    ) throws IOException {

        StringBuilder row = new StringBuilder();

        appendCsv(row, variantId);
        appendCsv(row, variantName);
        appendCsv(row, mapSeed);
        appendCsv(row, repetition);
        appendCsv(row, playerSeed);
        appendCsv(row, testedPosition);

        for (String agentName : agentNames) {
            appendCsv(row, agentName);
        }

        for (Types.RESULT result : results) {
            appendCsv(row, result);
        }

        appendCsv(row, testedResult);
        appendCsv(row, winnerPosition);

        for (int overtime : overtimes) {
            appendCsv(row, overtime);
        }

        /*
         * Remove the final comma added by appendCsv.
         */
        row.setLength(row.length() - 1);

        writer.write(row.toString());
        writer.newLine();
        writer.flush();
    }

    private static void appendCsv(
            StringBuilder row,
            Object value
    ) {
        String text = String.valueOf(value);

        /*
         * Defensive CSV escaping.
         */
        if (text.contains(",")
                || text.contains("\"")
                || text.contains("\n")) {
            text = "\""
                    + text.replace("\"", "\"\"")
                    + "\"";
        }

        row.append(text).append(',');
    }

    private static Path createOutputPath() {
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern(
                        "yyyyMMdd-HHmmss"
                );

        String timestamp =
                LocalDateTime.now().format(formatter);

        return Paths.get(
                "experiment-output",
                "safety-mcts-results-"
                        + timestamp
                        + ".csv"
        );
    }
}