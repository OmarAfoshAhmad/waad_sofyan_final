<#
.SYNOPSIS
    Apply the per-table schema files to a PostgreSQL database inside Docker.

.DESCRIPTION
    Runs all SQL files under schema/ in numeric order:
      1. schema/00_sequences.sql
      2. schema/tables/01_employers.sql  ..  52_account_transactions.sql

    Requires Docker Desktop to be running and the container to be up.

.PARAMETER ContainerName
    Docker container name (default: waadapp-db)

.PARAMETER DatabaseName
    PostgreSQL database name (default: tba_waad_system)

.PARAMETER DbUser
    PostgreSQL user (default: postgres)

.EXAMPLE
    .\scripts\03_apply_schema.ps1
    .\scripts\03_apply_schema.ps1 -ContainerName my-db -DatabaseName mydb
#>
param(
    [string]$ContainerName = "waadapp-db",
    [string]$DatabaseName  = "tba_waad_system",
    [string]$DbUser        = "postgres"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$WorkspaceRoot = Split-Path -Parent $PSScriptRoot
$SchemaDir     = Join-Path $WorkspaceRoot "schema"
$TablesDir     = Join-Path $SchemaDir "tables"

# ── Validation ──────────────────────────────────────────────
Write-Host "`n[INFO] Validating environment..." -ForegroundColor Cyan

$dockerCmd = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCmd) {
    Write-Error "Docker CLI not found. Install Docker Desktop and ensure it is in PATH."
    exit 1
}

$containerRunning = docker ps --filter "name=^${ContainerName}$" --format "{{.Names}}" 2>$null
if ($containerRunning -ne $ContainerName) {
    Write-Error "Container '$ContainerName' is not running. Start Docker Desktop and run: docker-compose up -d"
    exit 1
}

Write-Host "[OK] Container '$ContainerName' is running." -ForegroundColor Green

# ── Helper: run one SQL file ────────────────────────────────
function Invoke-SqlFile {
    param([string]$HostPath, [string]$Label)

    # Convert Windows path to Docker-compatible format
    $dockerPath = "/tmp/schema_apply/" + (Split-Path $HostPath -Leaf)

    # Copy file into container
    docker cp $HostPath "${ContainerName}:${dockerPath}" 2>&1 | Out-Null

    # Execute
    $result = docker exec $ContainerName `
        psql -U $DbUser -d $DatabaseName -v ON_ERROR_STOP=1 -f $dockerPath 2>&1

    if ($LASTEXITCODE -ne 0) {
        Write-Host "[FAIL] $Label" -ForegroundColor Red
        Write-Host $result -ForegroundColor DarkRed
        exit 1
    }

    Write-Host "[OK]   $Label" -ForegroundColor Green
}

# ── Prepare tmp dir inside container ────────────────────────
docker exec $ContainerName mkdir -p /tmp/schema_apply 2>&1 | Out-Null

# ── Apply sequences ─────────────────────────────────────────
Write-Host "`n[STEP 1] Applying sequences..." -ForegroundColor Cyan
Invoke-SqlFile -HostPath (Join-Path $SchemaDir "00_sequences.sql") -Label "00_sequences.sql"

# ── Apply tables in order ───────────────────────────────────
Write-Host "`n[STEP 2] Applying tables in order..." -ForegroundColor Cyan

$tableFiles = Get-ChildItem -Path $TablesDir -Filter "*.sql" | Sort-Object Name

foreach ($file in $tableFiles) {
    Invoke-SqlFile -HostPath $file.FullName -Label $file.Name
}

# ── Cleanup ─────────────────────────────────────────────────
docker exec $ContainerName rm -rf /tmp/schema_apply 2>&1 | Out-Null

Write-Host "`n[DONE] All $($tableFiles.Count + 1) schema files applied successfully." -ForegroundColor Green
Write-Host "       Database : $DatabaseName" -ForegroundColor Gray
Write-Host "       Container: $ContainerName`n" -ForegroundColor Gray
