#Requires -Version 5.1
<#
    담소 집 PC 운영 스크립트의 공용 함수. 직접 실행하지 않는다 —
    run-damso.ps1 / backup.ps1 / update.ps1 이 앞머리에서 dot-source 한다.

    설치 위치: C:\damso\bin\damso-common.ps1
#>

Set-StrictMode -Version Latest

# 모든 경로의 기준. 다른 드라이브에 두려면 DAMSO_HOME 환경변수로 바꾼다.
$script:DamsoHome = if ($env:DAMSO_HOME) { $env:DAMSO_HOME } else { 'C:\damso' }

function Get-DamsoHome { $script:DamsoHome }
function Get-DamsoEnvFile { Join-Path (Get-DamsoHome) 'damso.env' }
function Get-DamsoPidFile { Join-Path (Get-DamsoHome) 'damso.pid' }

function Write-DamsoLog {
    param([Parameter(Mandatory)][string]$Message, [string]$Level = 'INFO')

    $line = '[{0}] [{1}] {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
    Write-Host $line
    # 작업 스케줄러가 돌릴 때는 화면이 없다. 파일에도 같이 남긴다.
    $logDir = Join-Path (Get-DamsoHome) 'logs'
    if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
    Add-Content -Path (Join-Path $logDir 'ops.log') -Value $line -Encoding UTF8
}

<#
    damso.env 를 읽어 현재 프로세스의 환경변수로 넣는다.

    systemd EnvironmentFile 과 같은 형식을 쓴다 (서버 쪽 damso.env 와 호환).
    따옴표 없는 값에 공백이 있어도 되므로 JAVA_OPTS=-Xms256m -Xmx1024m 이 그대로 통한다.
    빈 값(KEY=)은 넣지 않는다 — 넣으면 application.yml 의 기본값이 빈 문자열로 덮인다.
#>
function Import-DamsoEnv {
    param([string]$Path = (Get-DamsoEnvFile))

    if (-not (Test-Path $Path)) {
        throw "설정 파일이 없습니다: $Path  (deploy\windows\damso.env.example 를 복사하세요)"
    }

    $loaded = @{}
    foreach ($line in Get-Content -Path $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed -eq '' -or $trimmed.StartsWith('#')) { continue }
        if ($trimmed -notmatch '^([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { continue }

        $key = $Matches[1]
        $val = $Matches[2].Trim()
        if ($val.Length -ge 2 -and $val.StartsWith('"') -and $val.EndsWith('"')) {
            $val = $val.Substring(1, $val.Length - 2)
        }
        if ($val -eq '') { continue }

        Set-Item -Path "Env:$key" -Value $val
        $loaded[$key] = $val
    }
    return $loaded
}

<#
    자바를 찾는다. JAVA_HOME 이 있으면 그것을, 없으면 PATH 의 java 를 쓴다.
    버전까지 확인하는 이유: JDK 17 로 돌리면 "UnsupportedClassVersionError" 라는
    낯선 이름으로만 실패해서 원인을 찾기 어렵다.
#>
function Get-DamsoJava {
    $exe = $null
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        $exe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    } else {
        $cmd = Get-Command java.exe -ErrorAction SilentlyContinue
        if ($cmd) { $exe = $cmd.Source }
    }
    if (-not $exe) {
        throw 'java 를 찾을 수 없습니다. JDK 21 을 설치하세요:  winget install --id EclipseAdoptium.Temurin.21.JDK'
    }

    $verText = & $exe -version 2>&1 | Select-Object -First 1
    if ($verText -match '"(\d+)') {
        $major = [int]$Matches[1]
        if ($major -lt 21) {
            throw "JDK 21 이상이 필요합니다 (현재 $major). winget install --id EclipseAdoptium.Temurin.21.JDK"
        }
    }
    return $exe
}

<# 헬스체크. DB 까지 닿는지 보므로 200 이면 실제로 서비스 가능한 상태다. #>
function Test-DamsoHealth {
    $port = if ($env:SERVER_PORT) { $env:SERVER_PORT } else { '8080' }
    try {
        $res = Invoke-WebRequest -Uri "http://127.0.0.1:$port/api/health" `
                                 -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        return $res.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Wait-DamsoHealth {
    param([int]$TimeoutSeconds = 120)

    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        if (Test-DamsoHealth) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

<# 실행 중인 담소 자바 프로세스. pid 파일을 믿되, 죽은 pid 는 걸러낸다. #>
function Get-DamsoProcess {
    $pidFile = Get-DamsoPidFile
    if (Test-Path $pidFile) {
        $raw = (Get-Content $pidFile -Raw).Trim()
        if ($raw -match '^\d+$') {
            $proc = Get-Process -Id ([int]$raw) -ErrorAction SilentlyContinue
            # pid 가 재사용됐을 수 있으므로 자바인지까지 확인한다.
            if ($proc -and $proc.ProcessName -like 'java*') { return $proc }
        }
        # 죽은 pid 가 적힌 파일은 지운다. 남겨 두면 다음 실행이 헷갈린다.
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
    }
    return $null
}

<#
    담소를 멈춘다.

    바로 강제 종료하지 않는다. H2 파일 DB 는 강제로 끊기면 열려 있던 쓰기가 중간에 끊겨
    다음 기동 때 복구 절차를 타게 된다 — 대개 회복되지만 백업 직전에 감수할 위험이 아니다.
    taskkill 을 /F 없이 먼저 보내면 자바가 종료 훅을 실행해 H2 를 정상으로 닫는다.
#>
function Stop-DamsoApp {
    param([int]$TimeoutSeconds = 45)

    $proc = Get-DamsoProcess
    if (-not $proc) {
        Write-DamsoLog '담소가 실행 중이 아닙니다.'
        return $true
    }

    Write-DamsoLog "담소를 멈춥니다 (PID $($proc.Id)) — 정상 종료 요청"
    & taskkill.exe /PID $proc.Id /T 2>&1 | Out-Null

    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        if ($proc.HasExited) { break }
        Start-Sleep -Seconds 1
        $proc.Refresh()
    }

    if (-not $proc.HasExited) {
        Write-DamsoLog "정상 종료가 ${TimeoutSeconds}초 안에 끝나지 않아 강제 종료합니다 (PID $($proc.Id))" 'WARN'
        & taskkill.exe /PID $proc.Id /T /F 2>&1 | Out-Null
        Start-Sleep -Seconds 3
        $proc.Refresh()
    }

    Remove-Item (Get-DamsoPidFile) -Force -ErrorAction SilentlyContinue

    if ($proc.HasExited) {
        Write-DamsoLog '담소가 멈췄습니다.'
        return $true
    }
    Write-DamsoLog '담소를 멈추지 못했습니다.' 'ERROR'
    return $false
}

<# 작업 스케줄러에 등록된 damso 작업을 시작한다 (백업·업데이트 뒤 되살릴 때 쓴다). #>
function Start-DamsoApp {
    $task = Get-ScheduledTask -TaskName 'damso' -ErrorAction SilentlyContinue
    if (-not $task) {
        throw "작업 스케줄러에 damso 작업이 없습니다. install-task.ps1 을 먼저 실행하세요."
    }

    # 앱을 멈춘 직후에는 작업 인스턴스(run-damso.ps1)가 아직 정리되는 중일 수 있다.
    # 작업은 MultipleInstances=IgnoreNew 로 등록돼 있어서, 그 상태로 시작을 걸면
    # 조용히 무시되고 서비스가 돌아오지 않는다. 끝나기를 기다린 뒤 시작한다.
    for ($i = 0; $i -lt 30; $i++) {
        $state = (Get-ScheduledTask -TaskName 'damso').State
        if ($state -ne 'Running') { break }
        Start-Sleep -Seconds 1
    }

    Write-DamsoLog '담소를 시작합니다 (작업 스케줄러)'
    Start-ScheduledTask -TaskName 'damso'
}
