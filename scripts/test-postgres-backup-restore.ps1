<#
.SYNOPSIS
  Creates a PostgreSQL backup, restores it into a safe test database, and compares key table counts.

.DESCRIPTION
  This script is intended for production-readiness validation. It never restores into the source
  database. By default, it restores into "<SourceDatabase>_restore_test".

  Safety rules:
  - The restore database name must end with "_restore_test" unless -AllowCustomRestoreDatabase is passed.
  - Dropping/recreating the restore database requires -ResetRestoreDatabase.
  - The source database is never dropped or modified.

.EXAMPLE
  .\scripts\test-postgres-backup-restore.ps1

.EXAMPLE
  .\scripts\test-postgres-backup-restore.ps1 -HostName localhost -Port 5432 -User postgres -SourceDatabase tba_waad_system -ResetRestoreDatabase

.EXAMPLE
  $env:PGPASSWORD = "your_password"
  .\scripts\test-postgres-backup-restore.ps1 -ResetRestoreDatabase
#>

[CmdletBinding(SupportsShouldProcess = $true)]
param(
  [string]$HostName = "localhost",
  [int]$Port = 5432,
  [string]$User = "postgres",
  [string]$SourceDatabase,
  [string]$RestoreDatabase,
  [string]$BackupDirectory = "backups",
  [switch]$ResetRestoreDatabase,
  [switch]$AllowCustomRestoreDatabase,
  [string[]]$TablesToCompare = @(
    "users",
    "members",
    "claims",
    "providers",
    "provider_contracts",
    "benefit_policies",
    "benefit_rules",
    "medical_categories",
    "medical_dictionary_entries",
    "price_list_sessions",
    "pre_authorizations",
    "flyway_schema_history"
  )
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Read-DotEnv {
  param([string]$Path)

  $values = @{}
  if (-not (Test-Path -LiteralPath $Path)) {
    return $values
  }

  Get-Content -LiteralPath $Path | ForEach-Object {
    $line = $_.Trim()
    if (-not $line -or $line.StartsWith("#") -or -not $line.Contains("=")) {
      return
    }

    $parts = $line.Split("=", 2)
    $key = $parts[0].Trim()
    $value = $parts[1].Trim().Trim('"').Trim("'")
    if ($key) {
      $values[$key] = $value
    }
  }

  return $values
}

function Require-Command {
  param([string]$Name)

  $cmd = Get-Command $Name -ErrorAction SilentlyContinue
  if (-not $cmd) {
    throw "الأداة المطلوبة غير موجودة في PATH: $Name. تأكد من تثبيت PostgreSQL client tools وإضافتها إلى PATH."
  }
}

function Invoke-Checked {
  param(
    [string]$FilePath,
    [string[]]$Arguments,
    [string]$StepName
  )

  Write-Host "[$StepName] $FilePath $($Arguments -join ' ')" -ForegroundColor DarkCyan
  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "فشلت خطوة: $StepName. ExitCode=$LASTEXITCODE"
  }
}

function Invoke-ScalarSql {
  param(
    [string]$Database,
    [string]$Sql
  )

  $args = @(
    "-h", $HostName,
    "-p", [string]$Port,
    "-U", $User,
    "-d", $Database,
    "-t",
    "-A",
    "-c", $Sql
  )

  $output = & psql @args
  if ($LASTEXITCODE -ne 0) {
    throw "فشل تنفيذ SQL على قاعدة $Database: $Sql"
  }

  return (($output | Where-Object { $_ -and $_.Trim() } | Select-Object -First 1) -as [string]).Trim()
}

function Test-DatabaseExists {
  param([string]$Database)

  $escaped = $Database.Replace("'", "''")
  $result = Invoke-ScalarSql -Database "postgres" -Sql "SELECT 1 FROM pg_database WHERE datname = '$escaped';"
  return $result -eq "1"
}

function Test-TableExists {
  param(
    [string]$Database,
    [string]$TableName
  )

  $escaped = $TableName.Replace("'", "''")
  $sql = "SELECT to_regclass('public.$escaped') IS NOT NULL;"
  $result = Invoke-ScalarSql -Database $Database -Sql $sql
  return $result -eq "t" -or $result -eq "true"
}

function Get-TableCount {
  param(
    [string]$Database,
    [string]$TableName
  )

  if (-not (Test-TableExists -Database $Database -TableName $TableName)) {
    return $null
  }

  $quoted = '"' + $TableName.Replace('"', '""') + '"'
  $count = Invoke-ScalarSql -Database $Database -Sql "SELECT COUNT(*) FROM public.$quoted;"
  return [int64]$count
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$envValues = Read-DotEnv -Path (Join-Path $repoRoot ".env")

if (-not $SourceDatabase) {
  $SourceDatabase = if ($envValues.ContainsKey("POSTGRES_DB")) { $envValues["POSTGRES_DB"] } else { "tba_waad_system" }
}
if (-not $RestoreDatabase) {
  $RestoreDatabase = "${SourceDatabase}_restore_test"
}
if ($envValues.ContainsKey("POSTGRES_USER") -and $User -eq "postgres") {
  $User = $envValues["POSTGRES_USER"]
}
if (-not $env:PGPASSWORD -and $envValues.ContainsKey("DB_PASSWORD")) {
  $env:PGPASSWORD = $envValues["DB_PASSWORD"]
}

if ($RestoreDatabase -eq $SourceDatabase) {
  throw "اسم قاعدة الاسترجاع يساوي قاعدة العمل. هذا ممنوع لحماية البيانات: $RestoreDatabase"
}
if (-not $AllowCustomRestoreDatabase -and -not $RestoreDatabase.EndsWith("_restore_test")) {
  throw "اسم قاعدة الاسترجاع يجب أن ينتهي بـ _restore_test للحماية. الاسم الحالي: $RestoreDatabase"
}

Require-Command "pg_dump"
Require-Command "pg_restore"
Require-Command "psql"
Require-Command "createdb"
Require-Command "dropdb"

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resolvedBackupDirectory = if ([System.IO.Path]::IsPathRooted($BackupDirectory)) {
  $BackupDirectory
} else {
  Join-Path $repoRoot $BackupDirectory
}
New-Item -ItemType Directory -Force -Path $resolvedBackupDirectory | Out-Null

$backupFile = Join-Path $resolvedBackupDirectory "${SourceDatabase}_${timestamp}.dump"
$reportFile = Join-Path $resolvedBackupDirectory "restore_validation_${SourceDatabase}_${timestamp}.md"

Write-Host "بدء اختبار النسخ الاحتياطي والاسترجاع" -ForegroundColor Green
Write-Host "قاعدة المصدر: $SourceDatabase" -ForegroundColor Gray
Write-Host "قاعدة الاسترجاع: $RestoreDatabase" -ForegroundColor Gray
Write-Host "ملف النسخة: $backupFile" -ForegroundColor Gray

if (-not (Test-DatabaseExists -Database $SourceDatabase)) {
  throw "قاعدة المصدر غير موجودة: $SourceDatabase"
}

Invoke-Checked -FilePath "pg_dump" -StepName "إنشاء النسخة الاحتياطية" -Arguments @(
  "-h", $HostName,
  "-p", [string]$Port,
  "-U", $User,
  "-Fc",
  "-f", $backupFile,
  $SourceDatabase
)

$backupInfo = Get-Item -LiteralPath $backupFile
if ($backupInfo.Length -le 0) {
  throw "تم إنشاء ملف backup فارغ. هذا فشل حرج."
}

$restoreExists = Test-DatabaseExists -Database $RestoreDatabase
if ($restoreExists -and -not $ResetRestoreDatabase) {
  throw "قاعدة الاسترجاع موجودة مسبقاً: $RestoreDatabase. شغّل السكربت مع -ResetRestoreDatabase لإعادة إنشائها بأمان."
}

if ($restoreExists -and $ResetRestoreDatabase) {
  if ($PSCmdlet.ShouldProcess($RestoreDatabase, "Drop restore test database")) {
    Invoke-Checked -FilePath "dropdb" -StepName "حذف قاعدة اختبار الاسترجاع القديمة" -Arguments @(
      "-h", $HostName,
      "-p", [string]$Port,
      "-U", $User,
      "--if-exists",
      $RestoreDatabase
    )
  }
}

if (-not (Test-DatabaseExists -Database $RestoreDatabase)) {
  Invoke-Checked -FilePath "createdb" -StepName "إنشاء قاعدة اختبار الاسترجاع" -Arguments @(
    "-h", $HostName,
    "-p", [string]$Port,
    "-U", $User,
    $RestoreDatabase
  )
}

Invoke-Checked -FilePath "pg_restore" -StepName "استرجاع النسخة إلى قاعدة الاختبار" -Arguments @(
  "-h", $HostName,
  "-p", [string]$Port,
  "-U", $User,
  "-d", $RestoreDatabase,
  "--clean",
  "--if-exists",
  $backupFile
)

$comparisonRows = @()
$failedComparisons = 0

foreach ($table in $TablesToCompare) {
  $sourceCount = Get-TableCount -Database $SourceDatabase -TableName $table
  $restoreCount = Get-TableCount -Database $RestoreDatabase -TableName $table
  $status = if ($null -eq $sourceCount -and $null -eq $restoreCount) {
    "غير موجود في القاعدتين"
  } elseif ($sourceCount -eq $restoreCount) {
    "مطابق"
  } else {
    $failedComparisons++
    "غير مطابق"
  }

  $comparisonRows += [PSCustomObject]@{
    Table = $table
    SourceCount = if ($null -eq $sourceCount) { "-" } else { $sourceCount }
    RestoreCount = if ($null -eq $restoreCount) { "-" } else { $restoreCount }
    Status = $status
  }
}

$flywayCount = Get-TableCount -Database $RestoreDatabase -TableName "flyway_schema_history"
if ($null -eq $flywayCount -or $flywayCount -le 0) {
  $failedComparisons++
}

$summaryStatus = if ($failedComparisons -eq 0) { "ناجح" } else { "يحتاج مراجعة" }

$reportLines = @()
$reportLines += "# تقرير اختبار Backup / Restore"
$reportLines += ""
$reportLines += "- التاريخ: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
$reportLines += "- قاعدة المصدر: ``$SourceDatabase``"
$reportLines += "- قاعدة الاسترجاع: ``$RestoreDatabase``"
$reportLines += "- ملف النسخة: ``$backupFile``"
$reportLines += "- حجم النسخة: $([Math]::Round($backupInfo.Length / 1MB, 2)) MB"
$reportLines += "- النتيجة: **$summaryStatus**"
$reportLines += ""
$reportLines += "## مقارنة الجداول"
$reportLines += ""
$reportLines += "| الجدول | الأصل | الاسترجاع | الحالة |"
$reportLines += "|---|---:|---:|---|"
foreach ($row in $comparisonRows) {
  $reportLines += "| $($row.Table) | $($row.SourceCount) | $($row.RestoreCount) | $($row.Status) |"
}
$reportLines += ""
$reportLines += "## ملاحظات التشغيل"
$reportLines += ""
$reportLines += "- لم يتم تعديل قاعدة المصدر."
$reportLines += "- الاسترجاع تم فقط على قاعدة اختبار."
$reportLines += "- إذا كانت النتيجة ناجحة، اختبر تشغيل backend مؤقتاً على قاعدة الاسترجاع للتأكد من إقلاع التطبيق ونجاح تسجيل الدخول."

Set-Content -LiteralPath $reportFile -Value $reportLines -Encoding UTF8

Write-Host ""
Write-Host "انتهى اختبار النسخ والاسترجاع: $summaryStatus" -ForegroundColor $(if ($failedComparisons -eq 0) { "Green" } else { "Yellow" })
Write-Host "ملف التقرير: $reportFile" -ForegroundColor Cyan

$comparisonRows | Format-Table -AutoSize

if ($failedComparisons -ne 0) {
  exit 2
}
