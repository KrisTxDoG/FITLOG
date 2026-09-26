$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$javaCommand = Get-Command java -ErrorAction SilentlyContinue
$javaPath = if ($javaCommand) { $javaCommand.Source } else { $null }
# This workspace has a portable JDK downloaded for testing. No system settings are changed.
if (-not $javaPath) {
    $workspaceTools = Join-Path $PSScriptRoot '..\..\work\tools\java'
    $portable = Get-ChildItem -Path "$workspaceTools\*\bin\java.exe" -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($portable) { $javaPath = $portable.FullName }
}
if (-not $javaPath) { throw 'Java 17 or 21 is required. Install a JDK, then run this script again.' }
$jarPath = Join-Path $PSScriptRoot 'backend\target\fitlog-1.0.0.jar'
if (-not (Test-Path -LiteralPath $jarPath)) { throw 'Build frontend with npm ci and npm run build, then build backend with mvn package first.' }
Write-Host 'Fitlog: http://127.0.0.1:8080 (Ctrl+C to stop)'
Push-Location (Join-Path $PSScriptRoot 'backend')
try { & $javaPath -jar $jarPath } finally { Pop-Location }
