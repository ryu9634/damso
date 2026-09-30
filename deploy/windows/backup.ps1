#Requires -Version 5.1
<#
    담소 일일 백업. 작업 스케줄러의 damso-backup 작업이 매일 04:10 에 부른다.

    설치 위치: C:\damso\bin\backup.ps1
    손으로 돌릴 때:  powershell -ExecutionPolicy Bypass -File C:\damso\bin\backup.ps1

    DB·암호화 키·사진을 한 묶음으로 남긴다. 셋 중 하나만 있으면 복구가 되지 않는다
    (프로필 행은 있는데 사진이 없거나, 행은 있는데 이름·번호가 못 읽는 암호문으로만 남거나).

    백업 동안 앱을 잠깐 멈춘다. H2 파일 DB 는 켜진 채로 파일을 복사하면 쓰기 도중의
    어긋난 상태가 찍힐 수 있고, 그걸 알아채는 건 정작 복구하려는 순간이다.
    새벽 몇십 초의 정지를 그 위험과 바꾼다.
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'damso-common.ps1')

$home_ = Get-DamsoHome
Import-DamsoEnv | Out-Null

$stamp = Get-Date -Format 'yyyyMMdd-HHmm'
$backupDir = if ($env:BACKUP_DIR) { $env:BACKUP_DIR -replace '/', '\' } else { Join-Path $home_ 'backups' }
$retention = if ($env:BACKUP_RETENTION_DAYS) { [int]$env:BACKUP_RETENTION_DAYS } else { 14 }
$mirror = if ($env:BACKUP_MIRROR) { $env:BACKUP_MIRROR -replace '/', '\' } else { '' }

$dbPrefix = if ($env:DAMSO_DB) { $env:DAMSO_DB -replace '/', '\' } else { Join-Path $home_ 'data\damso' }
$dbFile = "$dbPrefix.mv.db"
$keyFile = if ($env:DAMSO_DATA_KEY_FILE) { $env:DAMSO_DATA_KEY_FILE -replace '/', '\' } else { Join-Path $home_ 'data\damso.key' }
$uploads = if ($env:DAMSO_UPLOADS) { $env:DAMSO_UPLOADS -replace '/', '\' } else { Join-Path $home_ 'uploads' }

if (-not (Test-Path $backupDir)) { New-Item -ItemType Directory -Path $backupDir -Force | Out-Null }

$dbOut = Join-Path $backupDir "db-$stamp.mv.db"
$keyOut = Join-Path $backupDir "damso-$stamp.key"
$upOut = Join-Path $backupDir "uploads-$stamp.zip"

Write-DamsoLog '백업 시작'

$wasRunning = [bool](Get-DamsoProcess)

try {
    # ── 정지 ──────────────────────────────────────────────
    if ($wasRunning) {
        if (-not (Stop-DamsoApp)) {
            # 멈추지 못한 상태에서 복사하면 깨진 백업이 조용히 쌓인다. 차라리 안 남긴다.
            throw '앱을 멈추지 못해 백업을 건너뜁니다. 깨진 백업을 남기지 않기 위해 중단합니다.'
        }
    } else {
        Write-DamsoLog '앱이 실행 중이 아닙니다 — 그대로 복사합니다.'
    }

    # ── DB ────────────────────────────────────────────────
    if (-not (Test-Path $dbFile)) { throw "DB 파일이 없습니다: $dbFile" }
    Copy-Item -Path $dbFile -Destination $dbOut -Force

    # 크기 0 인 덤프가 조용히 쌓이는 것이 가장 흔한 백업 실패다. 여기서 잡는다.
    if ((Get-Item $dbOut).Length -le 0) { throw "DB 백업이 비어 있습니다: $dbOut" }
    Write-DamsoLog ('DB 백업 완료: {0:N1} MB' -f ((Get-Item $dbOut).Length / 1MB))

    # ── 암호화 키 ─────────────────────────────────────────
    # DB 덤프와 짝을 이룬다. 매일 같은 내용이지만, 어느 백업을 꺼내도 짝이 맞도록 함께 둔다.
    if (Test-Path $keyFile) {
        Copy-Item -Path $keyFile -Destination $keyOut -Force
        Write-DamsoLog '암호화 키 백업 완료'
    } elseif ($env:DAMSO_DATA_KEY) {
        Set-Content -Path $keyOut -Value $env:DAMSO_DATA_KEY -NoNewline -Encoding ASCII
        Write-DamsoLog '암호화 키 백업 완료 (damso.env 의 값에서)'
    } else {
        Write-DamsoLog '암호화 키를 찾을 수 없습니다. 이 백업만으로는 개인정보를 복구할 수 없습니다.' 'WARN'
    }

    # ── 사진 ──────────────────────────────────────────────
    if ((Test-Path $uploads) -and (Get-ChildItem $uploads -File -ErrorAction SilentlyContinue)) {
        Compress-Archive -Path (Join-Path $uploads '*') -DestinationPath $upOut -Force
        Write-DamsoLog ('사진 백업 완료: {0:N1} MB' -f ((Get-Item $upOut).Length / 1MB))
    } else {
        Write-DamsoLog '사진이 없어 건너뜁니다.'
    }
}
finally {
    # 백업이 실패했더라도 서비스는 되살린다. 백업 실패보다 서비스 정지가 더 나쁘다.
    if ($wasRunning) {
        Start-DamsoApp
        if (Wait-DamsoHealth -TimeoutSeconds 180) {
            Write-DamsoLog '서비스 복귀 확인 (헬스체크 통과)'
        } else {
            Write-DamsoLog '백업 후 서비스가 돌아오지 않았습니다! C:\damso\logs\damso.log 를 확인하세요.' 'ERROR'
        }
    }
}

# ── 두 번째 사본 ──────────────────────────────────────────
# 같은 디스크에만 있는 백업은 그 디스크가 죽는 순간 같이 사라진다.
if ($mirror) {
    try {
        if (-not (Test-Path $mirror)) { New-Item -ItemType Directory -Path $mirror -Force | Out-Null }
        foreach ($f in @($dbOut, $keyOut, $upOut)) {
            if (Test-Path $f) { Copy-Item -Path $f -Destination $mirror -Force }
        }
        Write-DamsoLog "두 번째 사본 복사 완료: $mirror"
    } catch {
        Write-DamsoLog "두 번째 사본 복사 실패: $($_.Exception.Message)" 'WARN'
    }
} else {
    Write-DamsoLog 'BACKUP_MIRROR 가 비어 있습니다 — 백업이 이 PC 안에만 있습니다.' 'WARN'
}

# ── 보관 기간 지난 것 삭제 ────────────────────────────────
$cutoff = (Get-Date).AddDays(-$retention)
$removed = 0
foreach ($pattern in 'db-*.mv.db', 'uploads-*.zip', 'damso-*.key') {
    Get-ChildItem -Path $backupDir -Filter $pattern -File -ErrorAction SilentlyContinue |
        Where-Object { $_.LastWriteTime -lt $cutoff } |
        ForEach-Object { Remove-Item $_.FullName -Force; $removed++ }
}

# 가장 최근 백업이 남아 있는지 확인한다. 정리 규칙이 잘못돼 전부 지워지는 사고를 막는다.
$newest = Get-ChildItem -Path $backupDir -Filter 'db-*.mv.db' -File -ErrorAction SilentlyContinue |
          Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $newest) {
    Write-DamsoLog '백업 폴더에 DB 백업이 하나도 없습니다!' 'ERROR'
} else {
    Write-DamsoLog "백업 완료 (보관 $retention 일, 정리 $removed 개, 최신 $($newest.Name))"
}
