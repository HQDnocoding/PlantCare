#!/usr/bin/env pwsh
<#
.SYNOPSIS
Download K6 directly (no admin required)
#>

$k6Version = "0.52.0"
$downloadUrl = "https://github.com/grafana/k6/releases/download/v${k6Version}/k6-v${k6Version}-windows-amd64.zip"
$outputDir = "$PSScriptRoot/k6-bin"

Write-Host "📥 Downloading K6 v${k6Version}..." -ForegroundColor Cyan
Write-Host "URL: $downloadUrl"

# Create output directory
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

$zipPath = "$outputDir/k6-${k6Version}.zip"
$extractPath = "$outputDir/extract"

try {
    # Download
    Invoke-WebRequest -Uri $downloadUrl -OutFile $zipPath
    Write-Host "✅ Downloaded to $zipPath"

    # Extract
    Expand-Archive -Path $zipPath -DestinationPath $extractPath -Force
    Write-Host "✅ Extracted in $extractPath"

    # Move k6.exe to bin folder
    $k6Exe = Get-ChildItem -Path $extractPath -Filter "k6.exe" -Recurse
    if ($k6Exe) {
        Copy-Item -Path $k6Exe.FullName -Destination "$outputDir/k6.exe" -Force
        Write-Host "✅ K6 executable ready at: $outputDir/k6.exe"
        
        # Add to PATH (current session)
        $env:PATH = "$outputDir;$env:PATH"
        Write-Host "✅ Added to PATH (current session)"
        
        # Show version
        & "$outputDir/k6.exe" version
        
        Write-Host "`n📝 To use K6 in all terminals, add to PATH:" -ForegroundColor Green
        Write-Host "   $env:PSModulePath += '$outputDir'" 
    }
    else {
        Write-Host "❌ k6.exe not found in downloaded archive"
    }

}
catch {
    Write-Host "❌ Download failed: $_" -ForegroundColor Red
    exit 1
}
finally {
    # Cleanup
    Remove-Item $extractPath -Recurse -Force -ErrorAction SilentlyContinue
}
