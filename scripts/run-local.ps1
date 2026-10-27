$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envFile)) {
    throw 'Crie o .env a partir do .env.example antes de executar.'
}
$settings = @{}
Get-Content -LiteralPath $envFile -Encoding utf8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -and -not $line.StartsWith('#')) {
        $parts = $line.Split('=', 2)
        if ($parts.Count -eq 2) { $settings[$parts[0].Trim()] = $parts[1].Trim() }
    }
}
$databaseName = if ($settings.ContainsKey('POSTGRES_DB')) { $settings['POSTGRES_DB'] } else { 'orderflow' }
$databasePort = if ($settings.ContainsKey('DB_PORT')) { $settings['DB_PORT'] } else { '5432' }
$env:DB_URL = 'jdbc:postgresql://localhost:' + $databasePort + '/' + $databaseName
$env:DB_USERNAME = $settings['POSTGRES_USER']
$env:DB_PASSWORD = $settings['POSTGRES_PASSWORD']
$env:ORDERFLOW_JWT_SECRET = $settings['ORDERFLOW_JWT_SECRET']
$env:ORDERFLOW_BOOTSTRAP_USERNAME = $settings['ORDERFLOW_BOOTSTRAP_USERNAME']
$env:ORDERFLOW_BOOTSTRAP_PASSWORD = $settings['ORDERFLOW_BOOTSTRAP_PASSWORD']
$env:PORT = if ($settings.ContainsKey('APP_PORT')) { $settings['APP_PORT'] } else { '8080' }
Push-Location -LiteralPath $projectRoot
try {
    & (Join-Path $projectRoot 'mvnw.cmd') spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw 'A execução da aplicação falhou.' }
} finally {
    Pop-Location
}
