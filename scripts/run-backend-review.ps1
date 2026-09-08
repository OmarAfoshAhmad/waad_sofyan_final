param(
  [int]$Port = 8080,
  [string]$DatabaseName = "waad_review1_local_context_20260905_host",
  [string]$DatabaseHost = "localhost",
  [int]$DatabasePort = 5432,
  [string]$DatabaseUser = "postgres",
  [string]$DatabasePassword = "postgres"
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$backendDir = Join-Path $repoRoot "backend"

if (-not (Test-Path -LiteralPath $backendDir)) {
  throw "لم أجد مجلد backend داخل: $repoRoot"
}

Write-Host "إيقاف أي عملية تستخدم المنفذ $Port ..." -ForegroundColor Cyan
$listeners = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
$processIds = @($listeners | Select-Object -ExpandProperty OwningProcess -Unique)

foreach ($processId in $processIds) {
  if ($processId -and $processId -ne $PID) {
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($process) {
      Write-Host "إيقاف PID $processId ($($process.ProcessName))" -ForegroundColor Yellow
      Stop-Process -Id $processId -Force
    }
  }
}

$env:SPRING_PROFILES_ACTIVE = "dev"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://${DatabaseHost}:${DatabasePort}/${DatabaseName}"
$env:SPRING_DATASOURCE_USERNAME = $DatabaseUser
$env:SPRING_DATASOURCE_PASSWORD = $DatabasePassword
$env:SESSION_TIMEOUT = "24h"
$env:ADMIN_DEFAULT_PASSWORD = "Admin@123"
$env:JWT_SECRET = "local-review-jwt-secret-please-do-not-use-in-production-2026-claim-tests"
$env:APP_SECRET_ENCRYPTION_KEY = "local-review-encryption-key-32chars"
$env:SERVER_PORT = "$Port"

Write-Host "تشغيل backend على http://localhost:$Port باستخدام قاعدة: $DatabaseName" -ForegroundColor Green
Set-Location -LiteralPath $backendDir
$mavenWrapper = Join-Path $backendDir "mvnw.cmd"
if (Test-Path -LiteralPath $mavenWrapper) {
  & $mavenWrapper spring-boot:run
} else {
  mvn.cmd spring-boot:run
}
