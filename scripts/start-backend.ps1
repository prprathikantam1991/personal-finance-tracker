param(
    [ValidateSet('lm-studio', 'bedrock-mantle', 'bedrock')]
    [string]$Provider = 'bedrock-mantle',
    [ValidateSet('custom', 'spring-ai-lm-studio', 'spring-ai-bedrock')]
    [string]$Runtime = 'custom',
    [string]$Model = '',
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
if ($Runtime -eq 'spring-ai-lm-studio' -and $Provider -ne 'lm-studio') {
    throw 'The Spring AI V6 runtime currently supports LM Studio only. Use -Provider lm-studio.'
}
if ($Runtime -eq 'spring-ai-bedrock' -and $Provider -ne 'bedrock') {
    throw 'The Spring AI Bedrock runtime uses the AWS Converse API. Use -Provider bedrock.'
}
if ([string]::IsNullOrWhiteSpace($Model)) {
    $Model = if ($Runtime -eq 'spring-ai-bedrock') {
        'global.anthropic.claude-haiku-4-5-20251001-v1:0'
    } else {
        'google.gemma-4-e2b'
    }
}

$env:FINANCE_ASSISTANT_PROVIDER = $Provider
$env:FINANCE_ASSISTANT_RUNTIME = $Runtime
$env:FINANCE_ASSISTANT_MODEL = $Model
$env:FINANCE_ASSISTANT_FINAL_ANSWER_MAX_TOKENS = $FinalAnswerMaxTokens
$env:FINANCE_ASSISTANT_TOOL_SELECTION_MAX_TOKENS = $ToolSelectionMaxTokens
$env:FINANCE_SPRING_AI_ENABLED = if ($Runtime -eq 'spring-ai-lm-studio') { 'true' } else { 'false' }
if ($Runtime -eq 'spring-ai-lm-studio') {
    $env:FINANCE_SPRING_AI_LM_STUDIO_BASE_URL = 'http://localhost:1234'
    $env:FINANCE_SPRING_AI_CHAT_MODEL = 'openai'
} elseif ($Runtime -eq 'spring-ai-bedrock') {
    $env:FINANCE_SPRING_AI_CHAT_MODEL = 'bedrock-converse'
} else {
    $env:FINANCE_SPRING_AI_CHAT_MODEL = 'none'
}

if ($Provider -eq 'bedrock-mantle') {
    $env:BEDROCK_REASONING_EFFORT = $ReasoningEffort
}

Write-Host "Starting Finance Tracker backend"
Write-Host "  Provider: $Provider"
Write-Host "  Runtime: $Runtime"
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
