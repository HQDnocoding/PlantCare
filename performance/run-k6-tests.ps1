#!/usr/bin/env pwsh
<#
.SYNOPSIS
K6 Performance Testing Script
.DESCRIPTION
Easy-to-use script to run K6 tests with different configurations on Plant App Backend
.PARAMETER TestType
Type of test: 'smoke', 'stress', 'load', 'spike', or 'endurance'
.PARAMETER VUs
Number of Virtual Users
.PARAMETER Duration
Test duration (e.g., '5m', '30s')
.PARAMETER BaseURL
Base URL for the API (default: http://localhost:8080)
.EXAMPLE
./run-k6-tests.ps1 -TestType stress -VUs 100
./run-k6-tests.ps1 -TestType smoke -BaseURL "http://api.plantapp.com"
#>

param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('smoke', 'stress', 'load', 'spike', 'endurance')]
    [string]$TestType = 'smoke',
    
    [int]$VUs = 10,
    [string]$Duration = '5m',
    [string]$BaseURL = 'http://localhost:8080',
    [string]$OutputDir = './k6-results'
)

# Ensure output directory exists
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    Write-Host "✅ Created output directory: $OutputDir"
}

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outputFile = "$OutputDir/k6-$TestType-$timestamp.json"
$summaryFile = "$OutputDir/summary-$TestType-$timestamp.txt"

Write-Host "🚀 Starting K6 Performance Test" -ForegroundColor Green
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
Write-Host "Test Type:    $TestType"
Write-Host "Virtual Users: $VUs"
Write-Host "Duration:     $Duration"
Write-Host "Base URL:     $BaseURL"
Write-Host "Output:       $outputFile"
Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# Choose test configuration based on type
$testConfig = @{
    'smoke'     = @{
        script      = 'smoke-test.js'
        stages      = @('{ duration: "10s", target: 1 }')
        description = "Quick endpoint verification"
    }
    'stress'    = @{
        script      = 'stress-test.js'
        stages      = @(
            '{ duration: "1m", target: 10 }',
            '{ duration: "2m", target: 50 }',
            '{ duration: "2m", target: 100 }',
            '{ duration: "1m", target: 200 }',
            '{ duration: "1m", target: 0 }'
        )
        description = "Gradually increase load to find breaking point"
    }
    'load'      = @{
        script      = 'stress-test.js'
        stages      = @(
            '{ duration: "2m", target: 50 }',
            '{ duration: "5m", target: 50 }',
            '{ duration: "2m", target: 0 }'
        )
        description = "Steady load test"
    }
    'spike'     = @{
        script      = 'stress-test.js'
        stages      = @(
            '{ duration: "10s", target: 10 }',
            '{ duration: "1m", target: 500 }',
            '{ duration: "10s", target: 0 }'
        )
        description = "Sudden traffic spike"
    }
    'endurance' = @{
        script      = 'stress-test.js'
        stages      = @(
            '{ duration: "1m", target: 50 }',
            '{ duration: "10m", target: 50 }',
            '{ duration: "1m", target: 0 }'
        )
        description = "Sustained load over extended period"
    }
}

$config = $testConfig[$TestType]
Write-Host "`n📝 Test Description: $($config.description)" -ForegroundColor Cyan

# Build k6 command
$scriptPath = Join-Path $PSScriptRoot $config.script

if (-not (Test-Path $scriptPath)) {
    Write-Host "❌ Script not found: $scriptPath" -ForegroundColor Red
    exit 1
}

$env:BASE_URL = $BaseURL

# Run K6 test
Write-Host "`n⏱️  Running test..." -ForegroundColor Yellow
$startTime = Get-Date

try {
    k6 run `
        --vus $VUs `
        --duration $Duration `
        --out json=$outputFile `
        $scriptPath | Tee-Object -FilePath $summaryFile

    $endTime = Get-Date
    $duration = $endTime - $startTime

    Write-Host "`n✅ Test completed!" -ForegroundColor Green
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    Write-Host "Duration:   $($duration.TotalMinutes.ToString('0.00')) minutes"
    Write-Host "Results:    $outputFile"
    Write-Host "Summary:    $summaryFile"
    Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

    # Parse and display key metrics from results
    Write-Host "`n📊 Key Metrics:" -ForegroundColor Cyan
    $results = Get-Content $outputFile | ConvertFrom-Json -Depth 10
    
    if ($results.metrics) {
        $httpDuration = $results.metrics.http_req_duration
        if ($httpDuration) {
            Write-Host "  Response Time:"
            Write-Host "    Min:  $($httpDuration.values.min)ms"
            Write-Host "    Max:  $($httpDuration.values.max)ms"
            Write-Host "    Avg:  $($httpDuration.values.value)ms"
        }
    }

    Write-Host "`n✨ Open results file to analyze: $outputFile"
    
}
catch {
    Write-Host "`n❌ Test failed with error:" -ForegroundColor Red
    Write-Host $_.Exception.Message
    exit 1
}

# Cleanup
$env:BASE_URL = $null
