param(
    [ValidateSet('lm-studio', 'bedrock-mantle', 'bedrock')]
    [string]$Provider = 'bedrock-mantle',
    [string]$Model = 'google.gemma-4-e2b',
    [ValidateSet('', 'low', 'medium', 'high')]
    [string]$ReasoningEffort = 'high',
    [ValidateRange(160, 4000)]
    [int]$ToolSelectionMaxTokens = 600,
    [ValidateRange(500, 4000)]
    [int]$FinalAnswerMaxTokens = 1200,
    [switch]$DryRun
)

$scriptRoot = Split-Path -Parent $PSScriptRoot
$backendPath = Join-Path $scriptRoot 'backend'

if (-not (Test-Path -LiteralPath $backendPath)) {
    throw "Backend directory was not found at $backendPath. Run this script from the project repository."
}

if ($Provider -eq 'bedrock-mantle' -and [string]::IsNullOrWhiteSpace($env:AWS_BEARER_TOKEN_BEDROCK) -and [string]::IsNullOrWhiteSpace($env:BEDROCK_API_KEY)) {
    throw 'Bedrock Mantle needs AWS_BEARER_TOKEN_BEDROCK or BEDROCK_API_KEY in this PowerShell session. The script never stores credentials.'
}

$env:FINANCE_ASSISTANT_PROVIDER = $Provider
$env:FINANCE_ASSISTANT_MODEL = $Model
$env:FINANCE_ASSISTANT_FINAL_ANSWER_MAX_TOKENS = $FinalAnswerMaxTokens
$env:FINANCE_ASSISTANT_TOOL_SELECTION_MAX_TOKENS = $ToolSelectionMaxTokens

if ($Provider -eq 'bedrock-mantle') {
    $env:BEDROCK_REASONING_EFFORT = $ReasoningEffort
}

Write-Host "Starting Finance Tracker backend"
Write-Host "  Provider: $Provider"
Write-Host "  Model: $Model"
Write-Host "  Tool-selection allowance: $ToolSelectionMaxTokens"
Write-Host "  Final-answer allowance: $FinalAnswerMaxTokens"
if ($Provider -eq 'bedrock-mantle') { Write-Host "  Reasoning effort: $ReasoningEffort" }

if ($DryRun) { return }

Push-Location $backendPath
try {
    mvn spring-boot:run
} finally {
    Pop-Location
}
