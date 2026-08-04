$ErrorActionPreference = "Stop"

$projectRoot = $PSScriptRoot
Set-Location $projectRoot

$java = "$env:USERPROFILE\.jdks\openjdk-26.0.2\bin\java.exe"
$classPath = "out\production\java-pommerman;lib\*"
$arguments = @("0", "-1", "1", "-1", "6", "5", "4", "3")

$outputDirectory = Join-Path $projectRoot "experiment-output"
New-Item -ItemType Directory `
    -Path $outputDirectory `
    -Force | Out-Null

$firstOutput = Join-Path $outputDirectory "reproducibility-run-1.txt"
$secondOutput = Join-Path $outputDirectory "reproducibility-run-2.txt"

Write-Host "Running reproducibility test 1..."
& $java -cp $classPath Run @arguments |
    Tee-Object -FilePath $firstOutput

if ($LASTEXITCODE -ne 0) {
    throw "The first reproducibility run failed."
}

Write-Host ""
Write-Host "Running reproducibility test 2..."
& $java -cp $classPath Run @arguments |
    Tee-Object -FilePath $secondOutput

if ($LASTEXITCODE -ne 0) {
    throw "The second reproducibility run failed."
}

Write-Host ""
Write-Host "Outputs saved to:"
Write-Host $firstOutput
Write-Host $secondOutput
Write-Host ""
Write-Host "Both runs completed successfully."