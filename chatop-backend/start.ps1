$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

$config = Join-Path $PSScriptRoot '.env'
if (Test-Path -LiteralPath $config) {
    foreach ($line in Get-Content -LiteralPath $config) {
        $entry = $line.Trim()
        if ($entry -eq '' -or $entry.StartsWith('#')) {
            continue
        }
        if ($entry -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$') {
            throw 'Invalid .env entry. Expected NAME=value without multiline values.'
        }
        $name = $matches[1]
        $value = $matches[2]
        if ($name -notlike 'CHATOP_*') {
            throw 'Only CHATOP_ variables are accepted in .env.'
        }
        if ([string]::IsNullOrEmpty([Environment]::GetEnvironmentVariable($name, 'Process'))) {
            [Environment]::SetEnvironmentVariable($name, $value, 'Process')
        }
    }
}

foreach ($name in @('CHATOP_DB_URL', 'CHATOP_DB_USERNAME', 'CHATOP_DB_PASSWORD', 'CHATOP_JWT_SECRET')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name, 'Process'))) {
        throw "Missing required variable: $name"
    }
}

& (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run
if ($LASTEXITCODE -ne 0) {
    throw 'Backend startup failed. See the Maven output above.'
}
