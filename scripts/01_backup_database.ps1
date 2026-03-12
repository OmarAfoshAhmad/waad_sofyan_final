#!/usr/bin/env pwsh
# =============================================================================
# Script: 01_backup_database.ps1
# Purpose: Create a full PostgreSQL backup before any database cleanup.
#          Backup is saved as a plain SQL file that can be restored with psql.
# Usage:   .\scripts\01_backup_database.ps1
# =============================================================================

param(
    [string]$Container = "waadapp-db",
    [string]$DbName    = "tba_waad_system",
    [string]$DbUser    = "postgres",
    [string]$OutputDir = "$PSScriptRoot\..\backups"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# -------------------------------------------------
# Ensure output directory exists
# -------------------------------------------------
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
    Write-Host "[INFO] Created backups directory: $OutputDir"
}

# -------------------------------------------------
# Timestamp for unique filename
# -------------------------------------------------
$timestamp  = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFile = Join-Path $OutputDir "backup_${DbName}_${timestamp}.sql"

# -------------------------------------------------
# Check Docker container is running
# -------------------------------------------------
$running = docker inspect --format "{{.State.Running}}" $Container 2>$null
if ($running -ne "true") {
    Write-Error "Container '$Container' is not running. Start Docker first."
    exit 1
}

# -------------------------------------------------
# Run pg_dump inside the container (data-only snapshot)
# -------------------------------------------------
Write-Host "[INFO] Starting data-only pg_dump of database '$DbName'..."
Write-Host "[INFO] Output file: $backupFile"

docker exec $Container pg_dump `
    --username $DbUser `
    --no-password `
    --format plain `
    --encoding UTF8 `
    --data-only `
    --inserts `
    $DbName | Out-File -FilePath $backupFile -Encoding UTF8

if ($LASTEXITCODE -ne 0) {
    Write-Error "pg_dump failed with exit code $LASTEXITCODE"
    exit 1
}

# -------------------------------------------------
# Also create a full schema+data backup
# -------------------------------------------------
$fullFile = Join-Path $OutputDir "backup_full_${DbName}_${timestamp}.sql"
Write-Host "[INFO] Creating full backup (schema + data): $fullFile"

docker exec $Container pg_dump `
    --username $DbUser `
    --no-password `
    --format plain `
    --encoding UTF8 `
    $DbName | Out-File -FilePath $fullFile -Encoding UTF8

if ($LASTEXITCODE -ne 0) {
    Write-Warning "Full backup failed (partial file may exist). Data-only backup is still available."
} else {
    $size = (Get-Item $fullFile).Length
    Write-Host "[OK] Full backup saved: $fullFile ($([Math]::Round($size/1KB, 1)) KB)"
}

$dataSize = (Get-Item $backupFile).Length
Write-Host "[OK] Data-only backup saved: $backupFile ($([Math]::Round($dataSize/1KB, 1)) KB)"
Write-Host ""
Write-Host "To restore this backup on a fresh database:"
Write-Host "  docker exec -i $Container psql -U $DbUser -d $DbName < '$fullFile'"
