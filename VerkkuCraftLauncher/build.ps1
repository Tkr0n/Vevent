# VerkkuCraft Launcher Build Script
# Builds a self-contained Windows executable

param(
    [string]$Configuration = "Release",
    [string]$OutputDir = "publish"
)

$ErrorActionPreference = "Stop"

Write-Host "Building VerkkuCraft Launcher..." -ForegroundColor Cyan

# Clean previous build
if (Test-Path $OutputDir) {
    Remove-Item -Recurse -Force $OutputDir
}

# Build self-contained executable
dotnet publish src/VerkkuCraftLauncher/VerkkuCraftLauncher.csproj `
    -c $Configuration `
    -r win-x64 `
    --self-contained true `
    -p:PublishSingleFile=true `
    -p:IncludeNativeLibrariesForSelfExtract=true `
    -p:EnableCompressionInSingleFile=true `
    -o $OutputDir

if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed!" -ForegroundColor Red
    exit 1
}

# Get file size
$exePath = Join-Path $OutputDir "VerkkuCraftLauncher.exe"
if (Test-Path $exePath) {
    $size = (Get-Item $exePath).Length / 1MB
    Write-Host "Build successful!" -ForegroundColor Green
    Write-Host "Output: $exePath" -ForegroundColor Yellow
    Write-Host "Size: $([math]::Round($size, 2)) MB" -ForegroundColor Yellow
} else {
    Write-Host "Executable not found!" -ForegroundColor Red
    exit 1
}
