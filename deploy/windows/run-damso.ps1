#Requires -Version 5.1
<#
    담소 앱을 실행한다. 작업 스케줄러의 damso 작업이 부팅 때 이 스크립트를 부른다.

    설치 위치: C:\damso\bin\run-damso.ps1
    손으로 확인할 때:  powershell -ExecutionPolicy Bypass -File C:\damso\bin\run-damso.ps1

    자바를 자식 프로세스로 띄우고 끝날 때까지 기다린다. 스크립트가 먼저 끝나 버리면
    작업 스케줄러는 작업이 완료된 줄 알고, 앱이 죽어도 다시 살리지 않는다.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'damso-common.ps1')

$home_ = Get-DamsoHome
Import-DamsoEnv | Out-Null

# ── 이미 돌고 있으면 아무것도 하지 않는다 ────────────────
# H2 파일 DB 는 한 프로세스만 열 수 있다. 두 번째 프로세스는 잠금 오류로 죽는데,
# 그 사이 헬스체크가 흔들리고 로그가 둘로 갈려 원인을 찾기 어려워진다.
$existing = Get-DamsoProcess
if ($existing) {
    Write-DamsoLog "이미 실행 중입니다 (PID $($existing.Id)). 그대로 둡니다."
    exit 0
}

# ── 디렉터리 ──────────────────────────────────────────────
foreach ($sub in 'app', 'data', 'uploads', 'logs', 'backups') {
    $p = Join-Path $home_ $sub
    if (-not (Test-Path $p)) { New-Item -ItemType Directory -Path $p -Force | Out-Null }
}

# ── jar ───────────────────────────────────────────────────
$jar = Join-Path $home_ 'app\match.jar'
if (-not (Test-Path $jar)) {
    Write-DamsoLog "실행할 jar 이 없습니다: $jar  (update.ps1 로 받아오거나 직접 복사하세요)" 'ERROR'
    exit 1
}

# ── 암호화 키 안전장치 ────────────────────────────────────
# 키가 없으면 앱은 조용히 새 키를 만들어 정상 기동한다. 그런데 기존 DB 의 이름·전화번호는
# 옛 키로 암호화돼 있어 그때부터 전부 읽히지 않는다 — 앱은 오류를 내지 않으므로
# 손님 화면이 깨지고 나서야 알게 된다. 그 상태를 만들지 않도록 여기서 멈춘다.
$dbPrefix = if ($env:DAMSO_DB) { $env:DAMSO_DB } else { Join-Path $home_ 'data\damso' }
$dbFile = "$dbPrefix.mv.db" -replace '/', '\'
$keyFile = if ($env:DAMSO_DATA_KEY_FILE) { $env:DAMSO_DATA_KEY_FILE -replace '/', '\' } else { '' }
$hasKeyValue = [bool]$env:DAMSO_DATA_KEY
$hasKeyFile = $keyFile -and (Test-Path $keyFile)

if ((Test-Path $dbFile) -and -not $hasKeyValue -and -not $hasKeyFile) {
    Write-DamsoLog "DB 는 있는데 암호화 키가 없습니다: $keyFile" 'ERROR'
    Write-DamsoLog '이대로 띄우면 새 키가 만들어지고, 저장된 이름·전화번호를 영구히 읽을 수 없게 됩니다.' 'ERROR'
    Write-DamsoLog 'AWS 에서 가져온 damso.key 를 위 경로에 놓고 다시 시작하세요. (백업 폴더에도 damso-*.key 로 있습니다)' 'ERROR'
    exit 1
}

# ── 실행 ──────────────────────────────────────────────────
$java = Get-DamsoJava
$javaOpts = if ($env:JAVA_OPTS) { $env:JAVA_OPTS -split '\s+' | Where-Object { $_ } } else { @() }
$args_ = @($javaOpts) + @('-jar', $jar, '--spring.profiles.active=local')

Write-DamsoLog "담소를 시작합니다 — $jar (프로파일 local)"

# 앱 로그는 logback 이 DAMSO_LOG 로 쓴다. 여기로 오는 것은 기동 초기 출력과,
# logback 이 준비되기 전에 터진 예외뿐이다 — 그래서 매번 덮어써도 잃는 게 없다.
$stdout = Join-Path $home_ 'logs\stdout.log'
$stderr = Join-Path $home_ 'logs\stderr.log'

$proc = Start-Process -FilePath $java -ArgumentList $args_ `
    -WorkingDirectory $home_ -NoNewWindow -PassThru `
    -RedirectStandardOutput $stdout -RedirectStandardError $stderr

Set-Content -Path (Get-DamsoPidFile) -Value $proc.Id -Encoding ASCII
Write-DamsoLog "자바 PID $($proc.Id)"

if (Wait-DamsoHealth -TimeoutSeconds 150) {
    Write-DamsoLog '기동 완료 — 헬스체크 통과'
} else {
    Write-DamsoLog "기동 후 헬스체크가 응답하지 않습니다. $stderr 와 $env:DAMSO_LOG 를 확인하세요." 'WARN'
}

# 자바가 끝날 때까지 기다린다. 이 줄이 있어야 작업 스케줄러가 앱을 "실행 중"으로 본다.
Wait-Process -Id $proc.Id
$code = $proc.ExitCode
Remove-Item (Get-DamsoPidFile) -Force -ErrorAction SilentlyContinue
Write-DamsoLog "담소가 종료됐습니다 (종료 코드 $code)" $(if ($code -eq 0) { 'INFO' } else { 'ERROR' })
exit $code
