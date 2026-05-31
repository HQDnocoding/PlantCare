$services = @(
    "auth",
    "user-service",
    "community-service",
    # "chat-service",
    # "message-service",
    "scan-service",
    "search-service",
    # "media-service",
    "notification-service",
    "api-gateway"
)

foreach ($service in $services) {
    $path = Join-Path $PSScriptRoot $service

    wt -w 0 new-tab `
        --title $service `
        cmd /k "cd /d $path && set SPRING_PROFILES_ACTIVE=local && mvn spring-boot:run"

    Start-Sleep -Seconds 2
}