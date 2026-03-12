param(
    [string]$ContainerName = "waadapp-db",
    [string]$DatabaseName = "tba_waad_system",
    [string]$DbUser = "postgres",
    [string]$OutputDir = "d:\tba_waad_system-main\tba_waad_system-main\backups"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# Validate Docker daemon/container first so we fail fast with a clear message.
$dockerInfo = docker ps --format "{{.Names}}" 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not running. Start Docker Desktop, then run this script again."
}

if (-not ($dockerInfo -split "`n" | Where-Object { $_ -eq $ContainerName })) {
    throw "Container '$ContainerName' is not running. Start it first (docker compose up -d)."
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outFile = Join-Path $OutputDir "seed_reference_data_${timestamp}.sql"

$tables = @(
    "system_settings",
    "feature_flags",
    "module_access",
    "pdf_company_settings",
    "medical_categories",
    "medical_category_roots",
    "medical_specialties",
    "medical_services",
    "medical_service_categories",
    "ent_service_aliases",
    "benefit_policies",
    "benefit_policy_rules"
)

$header = @(
    "-- ============================================================",
    "-- Reference seed export",
    "-- Generated at: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")",
    "-- Source DB: $DatabaseName",
    "-- ============================================================",
    ""
)
$header | Set-Content -Path $outFile -Encoding UTF8

foreach ($table in $tables) {
    Add-Content -Path $outFile -Value "-- ------------------------------"
    Add-Content -Path $outFile -Value "-- TABLE: $table"
    Add-Content -Path $outFile -Value "-- ------------------------------"

    docker exec $ContainerName pg_dump --username $DbUser --no-password --data-only --inserts --column-inserts --table $table $DatabaseName | Add-Content -Path $outFile

    if ($LASTEXITCODE -ne 0) {
        throw "Failed exporting table: $table"
    }

    Add-Content -Path $outFile -Value ""
}

Write-Host "[OK] Seed export completed: $outFile"
