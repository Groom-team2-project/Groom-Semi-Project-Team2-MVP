param(
    [int]$Vus = 100,
    [int]$Iterations = 500,
    [int]$Runs = 3,
    [int]$GatewayPort = 18080,
    [int]$App1Port = 18081,
    [int]$App2Port = 18082,
    [string[]]$StrategyOrder = @("pessimistic", "distributed"),
    [switch]$KeepRunning
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$baseCompose = Join-Path $projectRoot "docker-compose.yml"
$multiCompose = Join-Path $PSScriptRoot "docker-compose.multi-instance.yml"
$k6Compose = Join-Path $PSScriptRoot "docker-compose.k6.yml"
$resultsDir = Join-Path $PSScriptRoot "results"
$reportFile = Join-Path $projectRoot "docs\event-lock-multi-instance-performance-report.md"

function Invoke-MultiCompose {
    param([string[]]$Arguments)

    & docker compose -f $baseCompose -f $multiCompose @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose failed: $($Arguments -join ' ')"
    }
}

function Wait-ForEndpoint {
    param(
        [string]$Name,
        [int]$Port
    )

    $deadline = (Get-Date).AddSeconds(120)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest `
                -Uri "http://localhost:$Port/v3/api-docs" `
                -UseBasicParsing `
                -TimeoutSec 2 | Out-Null
            return
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }

    throw "$Name did not become ready on port $Port within 120 seconds."
}

function Start-Cluster {
    param([string]$Strategy)

    Invoke-MultiCompose @("stop", "performance-gateway", "app-1", "app-2")

    $env:EVENT_LOCK_STRATEGY = $Strategy
    $env:PERFORMANCE_GATEWAY_PORT = [string]$GatewayPort
    $env:PERFORMANCE_APP1_PORT = [string]$App1Port
    $env:PERFORMANCE_APP2_PORT = [string]$App2Port

    Invoke-MultiCompose @("up", "-d", "mysql", "redis")
    Invoke-MultiCompose @("up", "-d", "--force-recreate", "app-1")
    Wait-ForEndpoint -Name "app-1" -Port $App1Port

    Invoke-MultiCompose @("up", "-d", "--force-recreate", "app-2")
    Wait-ForEndpoint -Name "app-2" -Port $App2Port

    Invoke-MultiCompose @("up", "-d", "--force-recreate", "performance-gateway")
    Wait-ForEndpoint -Name "performance-gateway" -Port $GatewayPort
}

function Stop-Cluster {
    Invoke-MultiCompose @("stop", "performance-gateway", "app-1", "app-2")
}

function New-PerformanceEvent {
    param([int]$Limit)

    $body = @{ limitCount = $Limit } | ConvertTo-Json
    return Invoke-RestMethod `
        -Method Post `
        -Uri "http://localhost:$GatewayPort/api/performance/events" `
        -ContentType "application/json" `
        -Body $body
}

function Invoke-K6Run {
    param(
        [string]$Strategy,
        [long]$EventId,
        [int]$RunNumber,
        [int]$VirtualUsers,
        [int]$RequestCount,
        [bool]$Warmup
    )

    $resultName = if ($Warmup) {
        "multi-$Strategy-warmup.json"
    } else {
        "multi-$Strategy-run-$RunNumber.json"
    }
    $memberBase = if ($Warmup) {
        700000000
    } else {
        300000000 + ($RunNumber * 1000000)
    }

    & docker compose `
        -f $k6Compose `
        run --rm `
        -e "BASE_URL=http://host.docker.internal:$GatewayPort" `
        -e "EVENT_ID=$EventId" `
        -e "LOCK_STRATEGY=multi-$Strategy" `
        -e "VUS=$VirtualUsers" `
        -e "ITERATIONS=$RequestCount" `
        -e "MEMBER_ID_BASE=$memberBase" `
        -e "RESULTS_FILE=/results/$resultName" `
        k6 run /scripts/event-lock-comparison.js

    if ($LASTEXITCODE -ne 0) {
        throw "k6 exited with code $LASTEXITCODE for strategy $Strategy."
    }
}

function Get-MetricValue {
    param(
        $Summary,
        [string]$Metric,
        [string]$Value
    )

    $metricObject = $Summary.metrics.$Metric
    if ($null -eq $metricObject) {
        return 0
    }
    return [double]$metricObject.values.$Value
}

function Get-Median {
    param([double[]]$Values)

    $sorted = @($Values | Sort-Object)
    if ($sorted.Count -eq 0) {
        return 0
    }
    if ($sorted.Count % 2 -eq 1) {
        return $sorted[[int][Math]::Floor($sorted.Count / 2)]
    }
    return ($sorted[$sorted.Count / 2 - 1] + $sorted[$sorted.Count / 2]) / 2
}

function Get-UpstreamHitCounts {
    $app1Ip = (& docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' groom-mvp-perf-app-1).Trim()
    $app2Ip = (& docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' groom-mvp-perf-app-2).Trim()
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $logs = (& docker logs groom-mvp-perf-gateway 2>&1) -join "`n"
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    return [PSCustomObject]@{
        App1 = ([regex]::Matches($logs, [regex]::Escape("$app1Ip`:8080"))).Count
        App2 = ([regex]::Matches($logs, [regex]::Escape("$app2Ip`:8080"))).Count
    }
}

function Write-Report {
    param(
        [object[]]$Rows,
        [int]$VirtualUsers,
        [int]$RequestCount,
        [int]$RunCount
    )

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# Multi-Instance Event Lock Performance Comparison")
    $lines.Add("")
    $lines.Add("- Measured at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')")
    $lines.Add("- Topology: k6 -> Nginx -> two Spring Boot instances -> shared MySQL and Redis")
    $lines.Add("- Load: $VirtualUsers VUs, $RequestCount requests per run, $RunCount runs per strategy")
    $lines.Add("- Every request uses a distinct member ID; event capacity equals request count")
    $lines.Add("")
    $lines.Add("## Results by Run")
    $lines.Add("")
    $lines.Add("| Strategy | Run | Throughput(req/s) | Avg(ms) | p95(ms) | p99(ms) | Success | Lock timeout | Other failure | Event count | Participant rows | Consistency |")
    $lines.Add("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|")

    foreach ($row in $Rows) {
        $consistent = if (
            $row.ParticipatedCount -eq $row.ParticipantRows -and
            $row.ParticipatedCount -le $RequestCount
        ) { "PASS" } else { "FAIL" }
        $lines.Add(
            "| $($row.Strategy) | $($row.Run) | $($row.Throughput.ToString('F2')) | " +
            "$($row.Average.ToString('F2')) | $($row.P95.ToString('F2')) | " +
            "$($row.P99.ToString('F2')) | $($row.Success) | $($row.LockTimeouts) | " +
            "$($row.UnexpectedFailures) | $($row.ParticipatedCount) | " +
            "$($row.ParticipantRows) | $consistent |"
        )
    }

    $lines.Add("")
    $lines.Add("## Median Comparison")
    $lines.Add("")
    $lines.Add("| Strategy | Throughput(req/s) | Avg(ms) | p95(ms) | p99(ms) |")
    $lines.Add("|---|---:|---:|---:|---:|")

    foreach ($strategy in @("pessimistic", "distributed")) {
        $strategyRows = @($Rows | Where-Object Strategy -eq $strategy)
        $lines.Add(
            "| $strategy | $((Get-Median @($strategyRows.Throughput)).ToString('F2')) | " +
            "$((Get-Median @($strategyRows.Average)).ToString('F2')) | " +
            "$((Get-Median @($strategyRows.P95)).ToString('F2')) | " +
            "$((Get-Median @($strategyRows.P99)).ToString('F2')) |"
        )
    }

    $lines.Add("")
    $lines.Add("## Load Distribution")
    $lines.Add("")
    foreach ($strategy in @("pessimistic", "distributed")) {
        $strategyRows = @($Rows | Where-Object Strategy -eq $strategy)
        if ($strategyRows.Count -gt 0) {
            $last = $strategyRows[-1]
            $lines.Add("- ${strategy}: app-1=$($last.App1Hits), app-2=$($last.App2Hits) Nginx upstream requests")
        }
    }
    $lines.Add("")
    $lines.Add("## Limitations")
    $lines.Add("")
    $lines.Add("- This is a local single-host comparison, not absolute production capacity.")
    $lines.Add("- Both application instances, MySQL, Redis, Nginx, and k6 share host resources.")
    $lines.Add("- Results include HTTP, load balancing, JPA, and persistence costs.")

    [System.IO.File]::WriteAllLines($reportFile, $lines)
}

New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null
$originalStrategy = $env:EVENT_LOCK_STRATEGY
$originalGatewayPort = $env:PERFORMANCE_GATEWAY_PORT
$originalApp1Port = $env:PERFORMANCE_APP1_PORT
$originalApp2Port = $env:PERFORMANCE_APP2_PORT

try {
    & docker version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop is not running."
    }

    & (Join-Path $projectRoot "gradlew.bat") bootJar
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle bootJar failed."
    }

    Invoke-MultiCompose @("build", "app-1", "app-2")

    $rows = [System.Collections.Generic.List[object]]::new()
    foreach ($strategy in $StrategyOrder) {
        Start-Cluster -Strategy $strategy

        $warmupIterations = [Math]::Min(100, $Iterations)
        $warmupVus = [Math]::Min(20, $Vus)
        $warmupEvent = New-PerformanceEvent -Limit $warmupIterations
        Invoke-K6Run `
            -Strategy $strategy `
            -EventId $warmupEvent.eventId `
            -RunNumber 0 `
            -VirtualUsers $warmupVus `
            -RequestCount $warmupIterations `
            -Warmup $true

        for ($run = 1; $run -le $Runs; $run++) {
            $event = New-PerformanceEvent -Limit $Iterations
            Invoke-K6Run `
                -Strategy $strategy `
                -EventId $event.eventId `
                -RunNumber $run `
                -VirtualUsers $Vus `
                -RequestCount $Iterations `
                -Warmup $false

            $state = Invoke-RestMethod `
                -Method Get `
                -Uri "http://localhost:$GatewayPort/api/performance/events/$($event.eventId)"
            $summary = Get-Content `
                -Raw `
                (Join-Path $resultsDir "multi-$strategy-run-$run.json") |
                ConvertFrom-Json
            $hits = Get-UpstreamHitCounts

            $rows.Add([PSCustomObject]@{
                Strategy = $strategy
                Run = $run
                Throughput = Get-MetricValue $summary "http_reqs" "rate"
                Average = Get-MetricValue $summary "http_req_duration" "avg"
                P95 = Get-MetricValue $summary "http_req_duration" "p(95)"
                P99 = Get-MetricValue $summary "http_req_duration" "p(99)"
                Success = Get-MetricValue $summary "successful_requests" "count"
                LockTimeouts = Get-MetricValue $summary "lock_timeouts" "count"
                UnexpectedFailures = Get-MetricValue $summary "unexpected_failures" "count"
                ParticipatedCount = $state.participatedCount
                ParticipantRows = $state.participantRows
                App1Hits = $hits.App1
                App2Hits = $hits.App2
            })
        }
    }

    Write-Report `
        -Rows $rows `
        -VirtualUsers $Vus `
        -RequestCount $Iterations `
        -RunCount $Runs

    Write-Host ""
    Write-Host "Multi-instance comparison completed."
    Write-Host "Report: $reportFile"
} finally {
    if (-not $KeepRunning) {
        try {
            Stop-Cluster
        } catch {
            Write-Warning "Failed to stop the performance cluster: $_"
        }
    }

    $env:EVENT_LOCK_STRATEGY = $originalStrategy
    $env:PERFORMANCE_GATEWAY_PORT = $originalGatewayPort
    $env:PERFORMANCE_APP1_PORT = $originalApp1Port
    $env:PERFORMANCE_APP2_PORT = $originalApp2Port
}
