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
    [ValidateSet('smoke', 'stress', 'load', 'spike', 'endurance', 'search', 'resilience', 'advanced', 'full')]
    [string]$TestType = 'smoke',

    [int]$VUs = 10,
    [string]$Duration = '5m',
    [string]$BaseURL = 'http://localhost:8080',
    [string]$OutputDir = './k6-results'
)

if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
    Write-Host "Created output directory: $OutputDir"
}

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outputFile = "$OutputDir/k6-$TestType-$timestamp.json"
$summaryFile = "$OutputDir/summary-$TestType-$timestamp.txt"

Write-Host "Starting K6 Performance Test" -ForegroundColor Green
Write-Host "----------------------------------------"
Write-Host "Test Type:     $TestType"
Write-Host "Virtual Users: $VUs"
Write-Host "Duration:      $Duration"
Write-Host "Base URL:      $BaseURL"
Write-Host "Output:        $outputFile"
Write-Host "----------------------------------------"

$testConfig = @{
    'smoke'      = @{
        script      = 'smoke-test.js'
        description = 'Quick endpoint verification'
    }
    'stress'     = @{
        script      = 'stress-test.js'
        description = 'Gradually increase load to find breaking point'
    }
    'load'       = @{
        script      = 'stress-test.js'
        description = 'Steady load test'
    }
    'spike'      = @{
        script      = 'stress-test.js'
        description = 'Sudden traffic spike'
    }
    'endurance'  = @{
        script      = 'stress-test.js'
        description = 'Sustained load over extended period'
    }
    'search'     = @{
        script      = 'search-test.js'
        description = 'Elasticsearch search service performance'
    }
    'kafka'      = @{
        script      = 'kafka-test.js'
        description = 'Message queue throughput and consumer lag testing'
    }
    'resilience' = @{
        script      = 'resilience-test.js'
        description = 'Circuit breaker, timeout, and recovery scenarios'
    }
    'advanced'   = @{
        script      = 'advanced-test.js'
        description = 'JWT auth, file uploads, DB connection pool, rate limiting'
    }
    'full'       = @{
        script      = 'full-test.js'
        description = 'Complete system test combining all scenarios'
    }
}

$config = $testConfig[$TestType]
$scriptPath = Join-Path $PSScriptRoot $config.script

Write-Host "`nTest Description: $($config.description)" -ForegroundColor Cyan

if (-not (Test-Path $scriptPath)) {
    Write-Host "Script not found: $scriptPath" -ForegroundColor Red
    exit 1
}

$env:BASE_URL = $BaseURL
$env:AUTH_BASE_URL = $BaseURL
$env:COMMUNITY_BASE_URL = $BaseURL
$env:USER_BASE_URL = $BaseURL
$env:KAFKA_PRODUCER_URL = $BaseURL
$env:KAFKA_CONSUMER_URL = $BaseURL

Write-Host "`nRunning test..." -ForegroundColor Yellow
$startTime = Get-Date

try {
    k6 run `
        --vus $VUs `
        --duration $Duration `
        --out json=$outputFile `
        $scriptPath | Tee-Object -FilePath $summaryFile

    $endTime = Get-Date
    $totalMinutes = ($endTime - $startTime).TotalMinutes.ToString('0.00')

    Write-Host "`nTest completed!" -ForegroundColor Green
    Write-Host "----------------------------------------"
    Write-Host "Duration: $totalMinutes minutes"
    Write-Host "Results:  $outputFile"
    Write-Host "Summary:  $summaryFile"
    Write-Host "----------------------------------------"

    Write-Host "`nKey Metrics:" -ForegroundColor Cyan
    $results = Get-Content $outputFile | ConvertFrom-Json 

    if ($results.metrics) {
        $httpDuration = $results.metrics.http_req_duration
        if ($httpDuration) {
            Write-Host "  Response Time:"
            Write-Host "    Min: $($httpDuration.values.min)ms"
            Write-Host "    Max: $($httpDuration.values.max)ms"
            Write-Host "    Avg: $($httpDuration.values.value)ms"
        }
    }
}
catch {
    Write-Host "`nTest failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Cleanup
$env:BASE_URL = $null
