param(
    [string]$Profile = 'keepit-dev'
)

$ErrorActionPreference = 'Stop'
$infraRoot = Split-Path $PSScriptRoot -Parent
$environmentPath = Join-Path $infraRoot 'environments/dev'
$originalPath = $env:Path
$env:Path = [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';' + [Environment]::GetEnvironmentVariable('Path', 'User')
$credentialVariables = @('AWS_ACCESS_KEY_ID', 'AWS_SECRET_ACCESS_KEY', 'AWS_SESSION_TOKEN')
$previousCredentials = @{}
foreach ($variable in $credentialVariables) {
    $previousCredentials[$variable] = [Environment]::GetEnvironmentVariable($variable, 'Process')
}

function Invoke-Terraform {
    param([string[]]$TerraformArguments)
    & terraform "-chdir=$environmentPath" @TerraformArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Terraform command failed: $($TerraformArguments[0])"
    }
}

try {
    # Export into process memory only. Do not print or persist the returned credentials.
    $sessionCredentials = aws configure export-credentials --profile $Profile --format process | ConvertFrom-Json
    if ($LASTEXITCODE -ne 0 -or -not $sessionCredentials.AccessKeyId) {
        throw "No valid credentials for profile $Profile. Complete aws login for that profile first."
    }
    $env:AWS_ACCESS_KEY_ID = $sessionCredentials.AccessKeyId
    $env:AWS_SECRET_ACCESS_KEY = $sessionCredentials.SecretAccessKey
    $env:AWS_SESSION_TOKEN = $sessionCredentials.SessionToken

    Invoke-Terraform -TerraformArguments @('init', '-input=false', '-lockfile=readonly', '-no-color')
    Invoke-Terraform -TerraformArguments @('fmt', '-check', '-recursive')
    Invoke-Terraform -TerraformArguments @('validate', '-no-color')
    Invoke-Terraform -TerraformArguments @('plan', '-input=false', '-no-color', '-out=review.tfplan')
} finally {
    foreach ($variable in $credentialVariables) {
        [Environment]::SetEnvironmentVariable($variable, $previousCredentials[$variable], 'Process')
    }
    $env:Path = $originalPath
    $sessionCredentials = $null
}
