$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME -and -not (Get-Command java -ErrorAction SilentlyContinue)) {
    $localJdk = Get-ChildItem -LiteralPath (Join-Path $env:USERPROFILE '.jdks') -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName 'bin/java.exe') } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($localJdk) { $env:JAVA_HOME = $localJdk.FullName }
}
Write-Host 'Starting Still. Open http://localhost:8080 once the server has started.'
Write-Host 'Keep this window open. Press Ctrl+C to stop the app.'
& .\mvnw.cmd spring-boot:run
if ($LASTEXITCODE -ne 0) { throw 'The app could not start. Check that JDK 21 or newer is installed and port 8080 is available.' }
