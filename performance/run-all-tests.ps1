#!/usr/bin/env pwsh
<#
.SYNOPSIS
Comprehensive Performance Test Suite Runner
.DESCRIPTION
Runs all performance test types sequentially and generates a summary report
.EXAMPLE
./run-all-tests.ps1
./run-all-tests.ps1 -OutputDir "./detailed-results" -BaseURL "http://api.example.com"
#>

param(
    [string]$OutputDir = './k6-results',
    [string]$BaseURL = 'http://localhost:8080',
    [string]$SearchServiceURL = 'http://localhost:8085',
    [bool]$SkipSmoke = $false
)

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$reportDir = "$OutputDir/report-$timestamp"
$testResults = @()

function Invoke-Test {
    param(
        [string]$TestType,
        [int]$VUs,
        [string]$Duration
    )

    Write-Host "`n🚀 Running $TestType test..." -ForegroundColor Cyan
    
    $startTime = Get-Date
    
    try {
        & ./run-k6-tests.ps1 -TestType $TestType -VUs $VUs -Duration $Duration -BaseURL $BaseURL -SearchServiceURL $SearchServiceURL -OutputDir $OutputDir
        
        $endTime = Get-Date
        $elapsed = ($endTime - $startTime).TotalMinutes.ToString('0.00')
        
        $result = @{
            TestType  = $TestType
            Status    = "✅ Passed"
            Duration  = $elapsed
            Timestamp = $startTime
        }
        
        Write-Host "✅ $TestType completed in $elapsed minutes" -ForegroundColor Green
    }
    catch {
        Write-Host "❌ $TestType failed: $_" -ForegroundColor Red
        
        $result = @{
            TestType  = $TestType
            Status    = "❌ Failed"
            Error     = $_.Message
            Timestamp = Get-Date
        }
    }
    
    return $result
}

Write-Host "╔════════════════════════════════════════════════╗" -ForegroundColor Yellow
Write-Host "║   Plant App Backend - Full Performance Suite   ║" -ForegroundColor Yellow
Write-Host "╚════════════════════════════════════════════════╝" -ForegroundColor Yellow

Write-Host "`n📋 Test Plan:" -ForegroundColor Cyan
Write-Host "  1. Smoke Test (Quick validation)"
Write-Host "  2. Search Service Test (Elasticsearch)"
Write-Host "  3. Kafka Message Queue Test"
Write-Host "  4. Resilience & Circuit Breaker Test"
Write-Host "  5. Advanced Scenarios Test (Auth, Uploads, DB)"
Write-Host "  6. Stress Test (Main workload)"
Write-Host "  7. Full System Test (Comprehensive)"

Write-Host "`n⏱️ Estimated Duration: 45-60 minutes" -ForegroundColor Yellow
Write-Host "📊 Results will be saved to: $OutputDir`n" -ForegroundColor Yellow

# Create report directory
if (-not (Test-Path $reportDir)) {
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
}

# Test 1: Smoke
if (-not $SkipSmoke) {
    $testResults += Invoke-Test -TestType "smoke" -VUs 1 -Duration "10s"
    Start-Sleep -Seconds 2
}

# Test 2: Search Service
$testResults += Invoke-Test -TestType "search" -VUs 20 -Duration "3m"
Start-Sleep -Seconds 5

# Test 3: Kafka
$testResults += Invoke-Test -TestType "kafka" -VUs 30 -Duration "4m"
Start-Sleep -Seconds 5

# Test 4: Resilience
$testResults += Invoke-Test -TestType "resilience" -VUs 15 -Duration "5m"
Start-Sleep -Seconds 5

# Test 5: Advanced
$testResults += Invoke-Test -TestType "advanced" -VUs 25 -Duration "5m"
Start-Sleep -Seconds 5

# Test 6: Stress
$testResults += Invoke-Test -TestType "stress" -VUs 50 -Duration "10m"
Start-Sleep -Seconds 5

# Test 7: Full
$testResults += Invoke-Test -TestType "full" -VUs 30 -Duration "10m"

# Generate Summary Report
Write-Host "`n`n╔════════════════════════════════════════════════╗" -ForegroundColor Yellow
Write-Host "║            TEST EXECUTION SUMMARY              ║" -ForegroundColor Yellow
Write-Host "╚════════════════════════════════════════════════╝" -ForegroundColor Yellow

$summaryContent = @"
# Performance Test Execution Report
Generated: $(Get-Date -Format "yyyy-MM-dd HH:mm:ss")

## Summary

"@

foreach ($result in $testResults) {
    $summaryContent += "`n### $($result.TestType.ToUpper())`n"
    $summaryContent += "- Status: $($result.Status)`n"
    
    if ($result.Duration) {
        $summaryContent += "- Duration: $($result.Duration) minutes`n"
    }
    
    if ($result.Error) {
        $summaryContent += "- Error: $($result.Error)`n"
    }
    
    Write-Host "`n$($result.TestType): $($result.Status)"
    if ($result.Duration) {
        Write-Host "  Duration: $($result.Duration) min"
    }
}

$summaryContent += @"

## Test Environment
- Base URL: $BaseURL
- Search Service URL: $SearchServiceURL
- Results Directory: $OutputDir

## Result Files

All detailed results are in the k6-results directory:
- Smoke test: k6-results/k6-smoke-*.json
- Search test: k6-results/search-results.json
- Kafka test: k6-results/kafka-results.json
- Resilience test: k6-results/resilience-results.json
- Advanced test: k6-results/advanced-results.json
- Stress test: k6-results/k6-stress-*.json
- Full test: k6-results/full-test-results.json

## Next Steps

1. Review JSON result files for detailed metrics
2. Check for any threshold violations
3. Analyze P95/P99 latency trends
4. Monitor resource utilization during tests
5. Address any performance bottlenecks found
"@

$reportFile = "$reportDir/summary.md"
$summaryContent | Out-File -FilePath $reportFile -Encoding UTF8

Write-Host "`n📄 Full report saved to: $reportFile" -ForegroundColor Green

# Display final statistics
$totalTests = $testResults.Count
$passedTests = ($testResults | Where-Object { $_.Status -like "✅*" }).Count
$failedTests = $totalTests - $passedTests

Write-Host "`n📊 Final Statistics:" -ForegroundColor Cyan
Write-Host "  Total Tests: $totalTests"
Write-Host "  Passed: $passedTests" -ForegroundColor Green
Write-Host "  Failed: $failedTests" -ForegroundColor $(if ($failedTests -gt 0) { "Red" } else { "Green" })

if ($failedTests -eq 0) {
    Write-Host "`n✅ All tests passed successfully!" -ForegroundColor Green
}
else {
    Write-Host "`n⚠️ Some tests failed. Review the results above." -ForegroundColor Yellow
}

Write-Host "`n🎉 Test suite execution completed!" -ForegroundColor Yellow
