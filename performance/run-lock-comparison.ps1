param(
    [int]$Vus = 100,
    [int]$Iterations = 500,
    [int]$Runs = 3,
    [int]$Port = 8080,
    [string]$JavaExecutable = "",
    [string[]]$StrategyOrder = @("distributed", "pessimistic")
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $PSScriptRoot "docker-compose.k6.yml"
$resultsDir = Join-Path $PSScriptRoot "results"
$reportFile = Join-Path $projectRoot "docs\event-lock-performance-report.md"
$appProcess = $null

function Resolve-JavaExecutable {
    param([string]$ExplicitPath)

    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($ExplicitPath)) {
        $candidates.Add($ExplicitPath)
    }
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add((Join-Path $env:JAVA_HOME "bin\java.exe"))
    }

    $userJdks = Join-Path $env:USERPROFILE ".jdks"
    if (Test-Path $userJdks) {
        Get-ChildItem $userJdks -Directory |
            ForEach-Object { $candidates.Add((Join-Path $_.FullName "bin\java.exe")) }
    }

    $programJdks = "C:\Program Files\Java"
    if (Test-Path $programJdks) {
        Get-ChildItem $programJdks -Directory -Filter "jdk-*" |
            ForEach-Object { $candidates.Add((Join-Path $_.FullName "bin\java.exe")) }
    }

    $pathJava = (Get-Command java -ErrorAction SilentlyContinue).Source
    if (-not [string]::IsNullOrWhiteSpace($pathJava)) {
        $candidates.Add($pathJava)
    }

    foreach ($candidate in $candidates) {
        if (-not (Test-Path $candidate)) {
            continue
        }

        $versionProcessInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $versionProcessInfo.FileName = $candidate
        $versionProcessInfo.Arguments = "-version"
        $versionProcessInfo.UseShellExecute = $false
        $versionProcessInfo.RedirectStandardError = $true
        $versionProcessInfo.CreateNoWindow = $true
        $versionProcess = [System.Diagnostics.Process]::Start($versionProcessInfo)
        $versionLine = $versionProcess.StandardError.ReadLine()
        $versionProcess.WaitForExit()
        if ($versionLine -match 'version "(\d+)') {
            $majorVersion = [int]$Matches[1]
            if ($majorVersion -ge 21) {
                return $candidate
            }
        }
    }

    throw "Java 21 or newer was not found. Pass -JavaExecutable with a compatible java.exe path."
}

function Wait-ForApplication {
    param([int]$TargetPort)

    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        try {
            Invoke-WebRequest `
                -Uri "http://localhost:$TargetPort/v3/api-docs" `
                -UseBasicParsing `
                -TimeoutSec 2 | Out-Null
            return
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }

    throw "Application did not become ready within 90 seconds."
}

function Start-PerformanceApplication {
    param(
        [string]$Strategy,
        [string]$JarPath,
        [int]$TargetPort,
        [string]$JavaPath
    )

    $stdout = Join-Path $resultsDir "$Strategy-app.log"
    $stderr = Join-Path $resultsDir "$Strategy-app-error.log"
    $arguments = @(
        "-jar",
        $JarPath,
        "--spring.profiles.active=local,performance",
        "--event.lock.strategy=$Strategy",
        "--server.port=$TargetPort"
    )

    $process = Start-Process `
        -FilePath $JavaPath `
        -ArgumentList $arguments `
        -WorkingDirectory $projectRoot `
        -RedirectStandardOutput $stdout `
        -RedirectStandardError $stderr `
        -WindowStyle Hidden `
        -PassThru

    Wait-ForApplication -TargetPort $TargetPort
    return $process
}

function Stop-PerformanceApplication {
    param($Process)

    if ($null -ne $Process -and -not $Process.HasExited) {
        Stop-Process -Id $Process.Id -Force
        $Process.WaitForExit()
    }
}

function New-PerformanceEvent {
    param(
        [int]$TargetPort,
        [int]$Limit
    )

    $body = @{ limitCount = $Limit } | ConvertTo-Json
    return Invoke-RestMethod `
        -Method Post `
        -Uri "http://localhost:$TargetPort/api/performance/events" `
        -ContentType "application/json" `
        -Body $body
}

function Invoke-K6Run {
    param(
        [string]$Strategy,
        [long]$EventId,
        [int]$RunNumber,
        [int]$TargetPort,
        [int]$VirtualUsers,
        [int]$RequestCount,
        [bool]$Warmup
    )

    $resultName = if ($Warmup) {
        "$Strategy-warmup.json"
    } else {
        "$Strategy-run-$RunNumber.json"
    }
    $memberBase = if ($Warmup) {
        900000000
    } else {
        100000000 + ($RunNumber * 1000000)
    }

    & docker compose `
        -f $composeFile `
        run --rm `
        -e "BASE_URL=http://host.docker.internal:$TargetPort" `
        -e "EVENT_ID=$EventId" `
        -e "LOCK_STRATEGY=$Strategy" `
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
    $count = $sorted.Count
    if ($count -eq 0) {
        return 0
    }
    if ($count % 2 -eq 1) {
        return $sorted[[int][Math]::Floor($count / 2)]
    }
    return ($sorted[$count / 2 - 1] + $sorted[$count / 2]) / 2
}

function Write-ComparisonReport {
    param(
        [object[]]$Rows,
        [int]$VirtualUsers,
        [int]$RequestCount,
        [int]$RunCount
    )

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add("# First-Come Event Lock Performance Comparison")
    $lines.Add("")
    $lines.Add("- Measured at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss K')")
    $lines.Add("- Environment: one application instance, local Docker MySQL/Redis, k6 in Docker")
    $lines.Add("- Load: $VirtualUsers VUs, $RequestCount requests per run, $RunCount measured runs per strategy")
    $lines.Add("- Compared: Redisson distributed lock and MySQL pessimistic write lock on the same event flow")
    $lines.Add("- Event capacity equals request count; every request uses a distinct member ID")
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
    $lines.Add("| Strategy | Median throughput(req/s) | Median avg(ms) | Median p95(ms) | Median p99(ms) |")
    $lines.Add("|---|---:|---:|---:|---:|")

    $medians = @{}
    foreach ($strategy in $StrategyOrder) {
        $strategyRows = @($Rows | Where-Object Strategy -eq $strategy)
        $median = [PSCustomObject]@{
            Throughput = Get-Median @($strategyRows.Throughput)
            Average = Get-Median @($strategyRows.Average)
            P95 = Get-Median @($strategyRows.P95)
            P99 = Get-Median @($strategyRows.P99)
        }
        $medians[$strategy] = $median
        $lines.Add(
            "| $strategy | $($median.Throughput.ToString('F2')) | " +
            "$($median.Average.ToString('F2')) | $($median.P95.ToString('F2')) | " +
            "$($median.P99.ToString('F2')) |"
        )
    }

    $distributed = $medians["distributed"]
    $pessimistic = $medians["pessimistic"]
    $throughputDelta = if ($distributed.Throughput -eq 0) {
        0
    } else {
        (($pessimistic.Throughput - $distributed.Throughput) / $distributed.Throughput) * 100
    }
    $p95Delta = if ($distributed.P95 -eq 0) {
        0
    } else {
        (($pessimistic.P95 - $distributed.P95) / $distributed.P95) * 100
    }

    $lines.Add("")
    $lines.Add("## Interpretation")
    $lines.Add("")
    $lines.Add(
        "- Pessimistic-lock median throughput differed from distributed lock by " +
        "$($throughputDelta.ToString('+0.00;-0.00;0.00'))%."
    )
    $lines.Add(
        "- Pessimistic-lock median p95 latency differed from distributed lock by " +
        "$($p95Delta.ToString('+0.00;-0.00;0.00'))%."
    )
    $lines.Add("- Consistency passes only when the event count and participant-row count match without exceeding capacity.")
    $lines.Add("")
    $lines.Add("## Limitations")
    $lines.Add("")
    $lines.Add("- These local single-host measurements are not absolute production capacity.")
    $lines.Add("- The application, MySQL, Redis, and k6 share host CPU and disk resources.")
    $lines.Add("- This single-instance test does not measure the distributed lock's multi-instance coordination benefit.")
    $lines.Add("- Results include HTTP, JSON, JPA, and DB persistence costs; this is not a lock-command microbenchmark.")

    [System.IO.File]::WriteAllLines($reportFile, $lines)
}

New-Item -ItemType Directory -Force -Path $resultsDir | Out-Null

try {
    $resolvedJava = Resolve-JavaExecutable -ExplicitPath $JavaExecutable
    Write-Host "Using Java: $resolvedJava"

    & docker version | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "Docker Desktop is not running."
    }

    & docker compose -f (Join-Path $projectRoot "docker-compose.yml") up -d mysql redis
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start MySQL and Redis."
    }

    & (Join-Path $projectRoot "gradlew.bat") bootJar
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle bootJar failed."
    }

    $jar = Get-ChildItem (Join-Path $projectRoot "build\libs\*.jar") |
        Where-Object Name -NotLike "*-plain.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $jar) {
        throw "Boot jar was not found."
    }

    $rows = [System.Collections.Generic.List[object]]::new()
    foreach ($strategy in $StrategyOrder) {
        try {
            $appProcess = Start-PerformanceApplication `
                -Strategy $strategy `
                -JarPath $jar.FullName `
                -TargetPort $Port `
                -JavaPath $resolvedJava

            $warmupIterations = [Math]::Min(100, $Iterations)
            $warmupVus = [Math]::Min(20, $Vus)
            $warmupEvent = New-PerformanceEvent -TargetPort $Port -Limit $warmupIterations
            Invoke-K6Run `
                -Strategy $strategy `
                -EventId $warmupEvent.eventId `
                -RunNumber 0 `
                -TargetPort $Port `
                -VirtualUsers $warmupVus `
                -RequestCount $warmupIterations `
                -Warmup $true

            for ($run = 1; $run -le $Runs; $run++) {
                $event = New-PerformanceEvent -TargetPort $Port -Limit $Iterations
                Invoke-K6Run `
                    -Strategy $strategy `
                    -EventId $event.eventId `
                    -RunNumber $run `
                    -TargetPort $Port `
                    -VirtualUsers $Vus `
                    -RequestCount $Iterations `
                    -Warmup $false

                $state = Invoke-RestMethod `
                    -Method Get `
                    -Uri "http://localhost:$Port/api/performance/events/$($event.eventId)"
                $summaryPath = Join-Path $resultsDir "$strategy-run-$run.json"
                $summary = Get-Content -Raw $summaryPath | ConvertFrom-Json

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
                })
            }
        } finally {
            Stop-PerformanceApplication -Process $appProcess
            $appProcess = $null
        }
    }

    Write-ComparisonReport `
        -Rows $rows `
        -VirtualUsers $Vus `
        -RequestCount $Iterations `
        -RunCount $Runs

    Write-Host ""
    Write-Host "Comparison completed."
    Write-Host "Report: $reportFile"
} finally {
    Stop-PerformanceApplication -Process $appProcess
}
