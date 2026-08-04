$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
Set-Location $projectRoot

$java = "$env:USERPROFILE\.jdks\openjdk-26.0.2\bin\java.exe"
$classPath = "out\production\java-pommerman;lib\*"

$configurations = @(
    @{
        Name = "Full SafetyMCTS"
        Args = "0 1 1 -1 6 5 4 3"
    },
    @{
        Name = "Baseline Copy"
        Args = "0 1 1 -1 7 5 4 3"
    },
    @{
        Name = "Safe Rollout Only"
        Args = "0 1 1 -1 8 5 4 3"
    },
    @{
        Name = "Safe Expansion Only"
        Args = "0 1 1 -1 9 5 4 3"
    },
    @{
        Name = "Rollout and Expansion"
        Args = "0 1 1 -1 10 5 4 3"
    },
    @{
        Name = "Escape Aware"
        Args = "0 1 1 -1 11 5 4 3"
    },
    @{
        Name = "Safety Heuristic Only"
        Args = "0 1 1 -1 12 5 4 3"
    }
)

$failedTests = @()

foreach ($configuration in $configurations) {
    Write-Host ""
    Write-Host "===================================================="
    Write-Host "Running: $($configuration.Name)"
    Write-Host "Arguments: $($configuration.Args)"
    Write-Host "===================================================="

    $arguments = $configuration.Args -split " "

    & $java -cp $classPath Run @arguments

    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAILED: $($configuration.Name)"
        $failedTests += $configuration.Name
    }
    else {
        Write-Host "PASSED: $($configuration.Name)"
    }
}

Write-Host ""
Write-Host "===================================================="
Write-Host "SMOKE TEST SUMMARY"
Write-Host "===================================================="

if ($failedTests.Count -eq 0) {
    Write-Host "All configurations completed successfully."
    exit 0
}

Write-Host "Failed configurations:"

foreach ($failedTest in $failedTests) {
    Write-Host " - $failedTest"
}

exit 1