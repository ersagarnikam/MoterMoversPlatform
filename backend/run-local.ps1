# Starts the API on http://localhost:8080 with demo data, using settings from backend/.env (git ignores it),
# such as JAVA_HOME and the Zoom keys. Run from anywhere:  powershell -File backend\run-local.ps1
$envFile = Join-Path $PSScriptRoot '.env'
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        $line = $line.Trim()
        if (-not $line -or $line.StartsWith('#') -or -not $line.Contains('=')) { continue }
        $name, $value = $line.Split('=', 2)
        if ($value.Trim()) { Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim() }
    }
} else {
    Write-Host 'No backend/.env found: starting without local settings.'
}

if ($env:JAVA_HOME) { $env:PATH = "$env:JAVA_HOME\bin;$env:PATH" }

# Say which Zoom settings are present, never their values.
$zoom = 'ZOOM_ACCOUNT_ID', 'ZOOM_CLIENT_ID', 'ZOOM_CLIENT_SECRET', 'ZOOM_HOST_EMAIL'
$missing = $zoom | Where-Object { -not (Get-Item -Path "Env:$_" -ErrorAction SilentlyContinue) }
$embedded = $env:ZOOM_SDK_KEY -and $env:ZOOM_SDK_SECRET -and ($env:ZOOM_SDK_KEY -ne $env:ZOOM_CLIENT_ID)
if ($missing) {
    Write-Host "Zoom: not connected (missing $($missing -join ', '))"
} elseif ($embedded) {
    Write-Host 'Zoom: connected; meetings show inside the platform (Meeting SDK app keys set)'
} else {
    Write-Host 'Zoom: connected; meetings open in Zoom (no separate Meeting SDK app)'
}

Set-Location $PSScriptRoot
.\mvnw.cmd -q spring-boot:run
