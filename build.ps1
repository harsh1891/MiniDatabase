# Mini Database System Build and Run Script

# Ensure Java bin is in path (robust check)
if (!(Get-Command javac -ErrorAction SilentlyContinue)) {
    $env:Path += ";C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot\bin"
}

# Create output folder
if (!(Test-Path bin)) {
    New-Item -ItemType Directory -Force -Path bin | Out-Null
}

Write-Host "Compiling Mini Database System..." -ForegroundColor Cyan

# Gather Java files and compile
$javaFiles = Get-ChildItem -Path src -Filter *.java -Recurse | Resolve-Path | ForEach-Object { $_.ProviderPath }
if ($javaFiles.Count -eq 0) {
    Write-Host "No Java files found to compile!" -ForegroundColor Red
    Exit 1
}

# Compile all files into bin/
javac -d bin -encoding UTF-8 $javaFiles
if ($LASTEXITCODE -ne 0) {
    Write-Host "Compilation Failed!" -ForegroundColor Red
    Exit 1
}
Write-Host "Compilation Succeeded!" -ForegroundColor Green

# Handle command line args
$genIdx = [array]::IndexOf($args, "-generate")
if ($genIdx -ne -1) {
    $count = 10000
    if ($args.Count -gt ($genIdx + 1)) {
        $count = $args[$genIdx + 1]
    }
    Write-Host "`nGenerating random mock data with $count users..." -ForegroundColor Cyan
    java -cp bin db.DataGenerator $count
    Exit $LASTEXITCODE
}

if ($args.Contains("-benchmark")) {
    Write-Host "`nRunning Query Engine Performance Benchmarks..." -ForegroundColor Cyan
    java -cp bin db.Benchmark
    Exit $LASTEXITCODE
}

if ($args.Contains("-test")) {
    Write-Host "`nRunning Unit & Integration Tests..." -ForegroundColor Cyan
    java -cp bin db.TestRunner
    Exit $LASTEXITCODE
}

if ($args.Contains("-console")) {
    Write-Host "`nLaunching Mini Database Interactive Console..." -ForegroundColor Cyan
    java -cp bin db.Console
    Exit $LASTEXITCODE
}

Write-Host "`nBuild complete. Usage:" -ForegroundColor Yellow
Write-Host "  To generate mock data: ./build.ps1 -generate <user_count>"
Write-Host "  To run benchmark:      ./build.ps1 -benchmark"
Write-Host "  To run tests:          ./build.ps1 -test"
Write-Host "  To run console:        ./build.ps1 -console"
