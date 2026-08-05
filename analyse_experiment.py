from __future__ import annotations

import argparse
import math
from pathlib import Path

import matplotlib.pyplot as plt
import pandas as pd


REQUIRED_COLUMNS = {
    "variant",
    "map_seed",
    "repetition",
    "tested_position",
    "tested_result",
    "winner_position",
}


DISPLAY_NAMES = {
    "baseline_copy": "Baseline copy",
    "escape_aware": "Escape-aware",
    "full": "Full SafetyMCTS",
}


def wilson_interval(
    successes: int,
    trials: int,
    z: float = 1.959963984540054,
) -> tuple[float, float]:
    """
    Calculate a two-sided Wilson score confidence interval.

    Returns lower and upper bounds as proportions between 0 and 1.
    """
    if trials <= 0:
        return float("nan"), float("nan")

    proportion = successes / trials
    z_squared = z**2

    denominator = 1 + z_squared / trials

    centre = (proportion + z_squared / (2 * trials)) / denominator

    margin = (
        z
        * math.sqrt(
            proportion * (1 - proportion) / trials + z_squared / (4 * trials**2)
        )
        / denominator
    )

    return centre - margin, centre + margin


def load_results(csv_path: Path) -> pd.DataFrame:
    dataframe = pd.read_csv(csv_path)

    missing_columns = REQUIRED_COLUMNS - set(dataframe.columns)

    if missing_columns:
        missing = ", ".join(sorted(missing_columns))
        raise ValueError(f"CSV is missing required columns: {missing}")

    dataframe["variant"] = dataframe["variant"].astype(str)
    dataframe["tested_result"] = (
        dataframe["tested_result"].astype(str).str.upper().str.strip()
    )

    valid_results = {"WIN", "TIE", "LOSS"}
    observed_results = set(dataframe["tested_result"].unique())

    invalid_results = observed_results - valid_results

    if invalid_results:
        raise ValueError(
            "Unexpected tested_result values: " + ", ".join(sorted(invalid_results))
        )

    return dataframe


def validate_experiment(dataframe: pd.DataFrame) -> None:
    print("\nEXPERIMENT VALIDATION")
    print("=" * 72)

    print(f"Total rows: {len(dataframe):,}")
    print(f"Unique variants: {dataframe['variant'].nunique()}")
    print(f"Unique map seeds: {dataframe['map_seed'].nunique()}")
    print(f"Unique tested positions: {sorted(dataframe['tested_position'].unique())}")

    print("\nRows per variant:")
    print(dataframe.groupby("variant").size().rename("games").to_string())

    print("\nRows per variant and position:")
    print(
        dataframe.groupby(["variant", "tested_position"])
        .size()
        .rename("games")
        .to_string()
    )

    duplicate_columns = [
        "variant",
        "map_seed",
        "repetition",
        "tested_position",
    ]

    duplicates = dataframe.duplicated(subset=duplicate_columns).sum()

    print(f"\nDuplicate experimental conditions: {duplicates}")

    overtime_columns = [
        column for column in dataframe.columns if column.startswith("overtime_")
    ]

    if overtime_columns:
        total_overtimes = dataframe[overtime_columns].fillna(0).sum().sum()
        print(f"Total overtime events: {int(total_overtimes)}")

    expected_positions = {0, 1, 2, 3}
    actual_positions = set(dataframe["tested_position"].unique())

    if actual_positions != expected_positions:
        raise ValueError(
            "Expected tested positions 0, 1, 2 and 3, "
            f"but found {sorted(actual_positions)}."
        )

    if duplicates:
        raise ValueError("Duplicate experiment rows were detected.")


def build_summary(dataframe: pd.DataFrame) -> pd.DataFrame:
    rows: list[dict[str, float | int | str]] = []

    for variant, group in dataframe.groupby(
        "variant",
        sort=False,
    ):
        games = len(group)
        wins = int((group["tested_result"] == "WIN").sum())
        ties = int((group["tested_result"] == "TIE").sum())
        losses = int((group["tested_result"] == "LOSS").sum())

        lower, upper = wilson_interval(wins, games)

        rows.append(
            {
                "variant": variant,
                "configuration": DISPLAY_NAMES.get(
                    variant,
                    variant.replace("_", " ").title(),
                ),
                "games": games,
                "wins": wins,
                "draws": ties,
                "losses": losses,
                "win_rate": wins / games,
                "ci_lower": lower,
                "ci_upper": upper,
                "win_rate_percent": 100 * wins / games,
                "ci_lower_percent": 100 * lower,
                "ci_upper_percent": 100 * upper,
            }
        )

    summary = pd.DataFrame(rows)

    preferred_order = [
        "baseline_copy",
        "escape_aware",
        "full",
    ]

    order_map = {variant: index for index, variant in enumerate(preferred_order)}

    summary["_order"] = summary["variant"].map(order_map).fillna(len(preferred_order))

    return summary.sort_values("_order").drop(columns="_order").reset_index(drop=True)


def build_position_summary(
    dataframe: pd.DataFrame,
) -> pd.DataFrame:
    grouped = (
        dataframe.assign(is_win=dataframe["tested_result"].eq("WIN"))
        .groupby(
            ["variant", "tested_position"],
            as_index=False,
        )
        .agg(
            games=("tested_result", "size"),
            wins=("is_win", "sum"),
        )
    )

    grouped["win_rate_percent"] = 100 * grouped["wins"] / grouped["games"]

    grouped["configuration"] = (
        grouped["variant"]
        .map(DISPLAY_NAMES)
        .fillna(grouped["variant"].str.replace("_", " ").str.title())
    )

    return grouped


def add_comparisons(
    summary: pd.DataFrame,
) -> pd.DataFrame:
    indexed = summary.set_index("variant")

    comparisons: list[dict[str, float | str]] = []

    baseline_variant = "baseline_copy"

    if baseline_variant not in indexed.index:
        return pd.DataFrame()

    baseline_rate = indexed.loc[
        baseline_variant,
        "win_rate_percent",
    ]

    for variant in indexed.index:
        if variant == baseline_variant:
            continue

        rate = indexed.loc[
            variant,
            "win_rate_percent",
        ]

        comparisons.append(
            {
                "comparison": (
                    f"{DISPLAY_NAMES.get(variant, variant)} vs Baseline copy"
                ),
                "baseline_win_rate_percent": baseline_rate,
                "variant_win_rate_percent": rate,
                "difference_percentage_points": (rate - baseline_rate),
            }
        )

    return pd.DataFrame(comparisons)


def save_report_table(
    summary: pd.DataFrame,
    output_directory: Path,
) -> None:
    report_table = summary[
        [
            "configuration",
            "games",
            "wins",
            "draws",
            "losses",
            "win_rate_percent",
            "ci_lower_percent",
            "ci_upper_percent",
        ]
    ].copy()

    report_table["win_rate_percent"] = report_table["win_rate_percent"].map(
        lambda value: f"{value:.2f}%"
    )

    report_table["95% CI"] = report_table.apply(
        lambda row: f"[{row['ci_lower_percent']:.2f}%, {row['ci_upper_percent']:.2f}%]",
        axis=1,
    )

    report_table = report_table.drop(
        columns=[
            "ci_lower_percent",
            "ci_upper_percent",
        ]
    )

    report_table = report_table.rename(
        columns={
            "configuration": "Configuration",
            "games": "Games",
            "wins": "Wins",
            "draws": "Draws",
            "losses": "Losses",
            "win_rate_percent": "Win rate",
        }
    )

    report_table.to_csv(
        output_directory / "report_summary_table.csv",
        index=False,
    )

    report_table.to_excel(
        output_directory / "report_summary_table.xlsx",
        index=False,
    )


def plot_win_rates(
    summary: pd.DataFrame,
    output_directory: Path,
) -> None:
    labels = summary["configuration"]
    rates = summary["win_rate_percent"]

    lower_errors = rates - summary["ci_lower_percent"]
    upper_errors = summary["ci_upper_percent"] - rates

    figure, axis = plt.subplots(figsize=(7.2, 4.2))

    axis.bar(
        labels,
        rates,
        yerr=[lower_errors, upper_errors],
        capsize=5,
    )

    axis.set_ylabel("Win rate (%)")
    axis.set_xlabel("Configuration")
    axis.set_title("SafetyMCTS ablation performance")
    axis.set_ylim(
        0,
        max(60, summary["ci_upper_percent"].max() + 8),
    )
    axis.grid(
        axis="y",
        alpha=0.25,
    )

    for index, value in enumerate(rates):
        axis.text(
            index,
            value + upper_errors.iloc[index] + 1.2,
            f"{value:.1f}%",
            ha="center",
            va="bottom",
            fontsize=9,
        )

    figure.tight_layout()

    figure.savefig(
        output_directory / "win_rate_with_95ci.png",
        dpi=300,
        bbox_inches="tight",
    )

    figure.savefig(
        output_directory / "win_rate_with_95ci.pdf",
        bbox_inches="tight",
    )

    plt.close(figure)


def plot_position_rates(
    position_summary: pd.DataFrame,
    output_directory: Path,
) -> None:
    pivot = position_summary.pivot(
        index="tested_position",
        columns="configuration",
        values="win_rate_percent",
    )

    axis = pivot.plot(
        kind="bar",
        figsize=(7.4, 4.4),
    )

    axis.set_xlabel("Tested player position")
    axis.set_ylabel("Win rate (%)")
    axis.set_title("Win rate by player position")
    axis.set_xticklabels(
        [str(value) for value in pivot.index],
        rotation=0,
    )
    axis.legend(
        title="Configuration",
        fontsize=8,
    )
    axis.grid(
        axis="y",
        alpha=0.25,
    )

    figure = axis.get_figure()
    figure.tight_layout()

    figure.savefig(
        output_directory / "win_rate_by_position.png",
        dpi=300,
        bbox_inches="tight",
    )

    plt.close(figure)


def main() -> None:
    parser = argparse.ArgumentParser(
        description=("Analyse SafetyMCTS experiment CSV results.")
    )

    parser.add_argument(
        "csv",
        type=Path,
        help="Path to the completed experiment CSV.",
    )

    parser.add_argument(
        "--output",
        type=Path,
        default=Path("analysis-output"),
        help="Directory for generated tables and figures.",
    )

    arguments = parser.parse_args()

    csv_path = arguments.csv.resolve()
    output_directory = arguments.output.resolve()

    if not csv_path.exists():
        raise FileNotFoundError(f"CSV not found: {csv_path}")

    output_directory.mkdir(
        parents=True,
        exist_ok=True,
    )

    dataframe = load_results(csv_path)
    validate_experiment(dataframe)

    summary = build_summary(dataframe)
    position_summary = build_position_summary(dataframe)
    comparisons = add_comparisons(summary)

    summary.to_csv(
        output_directory / "variant_summary.csv",
        index=False,
    )

    position_summary.to_csv(
        output_directory / "position_summary.csv",
        index=False,
    )

    comparisons.to_csv(
        output_directory / "comparisons.csv",
        index=False,
    )

    save_report_table(
        summary,
        output_directory,
    )

    plot_win_rates(
        summary,
        output_directory,
    )

    plot_position_rates(
        position_summary,
        output_directory,
    )

    print("\nVARIANT SUMMARY")
    print("=" * 72)

    print(
        summary[
            [
                "configuration",
                "games",
                "wins",
                "draws",
                "losses",
                "win_rate_percent",
                "ci_lower_percent",
                "ci_upper_percent",
            ]
        ].to_string(
            index=False,
            formatters={
                "win_rate_percent": (lambda value: f"{value:.2f}%"),
                "ci_lower_percent": (lambda value: f"{value:.2f}%"),
                "ci_upper_percent": (lambda value: f"{value:.2f}%"),
            },
        )
    )

    if not comparisons.empty:
        print("\nCOMPARISONS WITH BASELINE")
        print("=" * 72)
        print(
            comparisons.to_string(
                index=False,
                formatters={
                    "baseline_win_rate_percent": (lambda value: f"{value:.2f}%"),
                    "variant_win_rate_percent": (lambda value: f"{value:.2f}%"),
                    "difference_percentage_points": (lambda value: f"{value:+.2f} pp"),
                },
            )
        )

    print(f"\nAnalysis files written to: {output_directory}")


if __name__ == "__main__":
    main()
