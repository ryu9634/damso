#Requires -Version 5.1
<#
    담소를 윈도우 부팅 때 자동으로 띄우도록 작업 스케줄러에 등록하고, 절전을 끈다.
    관리자 PowerShell 에서 한 번만 실행한다.

        powershell -ExecutionPolicy Bypass -File C:\damso\bin\install-task.ps1

    되돌리려면:
        powershell -ExecutionPolicy Bypass -File C:\damso\bin\install-task.ps1 -Remove

    윈도우 서비스가 아니라 작업 스케줄러를 쓰는 이유: 자바 jar 을 서비스로 만들려면
    래퍼(NSSM 등)를 따로 깔아야 한다. 부팅 시작·죽으면 재시작·로그인 없이 실행은
    작업 스케줄러만으로 다 되므로, 설치할 것을 하나 줄였다.
#>

param([switch]$Remove)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'damso-common.ps1')

$isAdmin = ([Security.Principal.WindowsPrincipal] `
    [Security.Principal.WindowsIdentity]::GetCurrent()
    ).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) {
    throw '관리자 권한이 필요합니다. PowerShell 을 "관리자로 실행"한 뒤 다시 실행하세요.'
}

$home_ = Get-DamsoHome
$bin = Join-Path $home_ 'bin'

# ── 제거 ──────────────────────────────────────────────────
if ($Remove) {
    foreach ($name in 'damso', 'damso-backup') {
        if (Get-ScheduledTask -TaskName $name -ErrorAction SilentlyContinue) {
            Unregister-ScheduledTask -TaskName $name -Confirm:$false
            Write-DamsoLog "작업 제거: $name"
        }
    }
    Write-DamsoLog '제거 완료. 절전 설정은 그대로 두었습니다 (필요하면 직접 되돌리세요).'
    exit 0
}

foreach ($f in 'run-damso.ps1', 'backup.ps1', 'damso-common.ps1') {
    if (-not (Test-Path (Join-Path $bin $f))) {
        throw "$bin 에 $f 가 없습니다. deploy\windows\ 의 파일을 모두 복사하세요."
    }
}
if (-not (Test-Path (Get-DamsoEnvFile))) {
    throw "$(Get-DamsoEnvFile) 가 없습니다. damso.env.example 를 복사해서 채우세요."
}

# ── 담소 본체 ─────────────────────────────────────────────
$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$bin\run-damso.ps1`"" `
    -WorkingDirectory $home_

# SYSTEM 으로 돌린다 — 로그인하지 않아도, 화면이 잠겨 있어도 돈다.
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest

$settings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) `
    -ExecutionTimeLimit ([TimeSpan]::Zero)

# 부팅 직후 + 매 10분 점검. 두 번째 트리거가 실질적인 감시자 역할을 한다 —
# 앱이 죽어 작업이 끝났으면 다시 띄우고, 이미 돌고 있으면 run-damso.ps1 이 즉시 빠져나온다.
$triggers = @(
    (New-ScheduledTaskTrigger -AtStartup),
    # RepetitionDuration 을 생략하면 윈도우 버전에 따라 반복이 한 번만 걸린다. 명시한다.
    (New-ScheduledTaskTrigger -Once -At (Get-Date).Date.AddMinutes(1) `
        -RepetitionInterval (New-TimeSpan -Minutes 10) `
        -RepetitionDuration (New-TimeSpan -Days 3650))
)

if (Get-ScheduledTask -TaskName 'damso' -ErrorAction SilentlyContinue) {
    Unregister-ScheduledTask -TaskName 'damso' -Confirm:$false
}
Register-ScheduledTask -TaskName 'damso' -Action $action -Principal $principal `
    -Settings $settings -Trigger $triggers `
    -Description '담소 매칭 앱 — 부팅 시 시작, 10분마다 살아 있는지 점검' | Out-Null
Write-DamsoLog '작업 등록: damso (부팅 시 + 10분마다 점검)'

# ── 일일 백업 ─────────────────────────────────────────────
$backupAction = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$bin\backup.ps1`"" `
    -WorkingDirectory $home_

# 새벽 4시 10분 — 손님이 없는 시간. 백업 동안 몇십 초 응답이 끊긴다.
$backupSettings = New-ScheduledTaskSettingsSet `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 1)

if (Get-ScheduledTask -TaskName 'damso-backup' -ErrorAction SilentlyContinue) {
    Unregister-ScheduledTask -TaskName 'damso-backup' -Confirm:$false
}
Register-ScheduledTask -TaskName 'damso-backup' -Action $backupAction -Principal $principal `
    -Settings $backupSettings -Trigger (New-ScheduledTaskTrigger -Daily -At '04:10') `
    -Description '담소 일일 백업 — DB·암호화 키·사진' | Out-Null
Write-DamsoLog '작업 등록: damso-backup (매일 04:10)'

# ── 절전 끄기 ─────────────────────────────────────────────
# 서버가 잠들면 도메인은 그대로인데 접속만 안 된다. cloudflared 도 함께 끊긴다.
# 모니터는 꺼져도 되므로 화면만 남겨 둔다.
Write-DamsoLog '절전 설정을 끕니다 (대기 모드·최대 절전)'
& powercfg.exe /change standby-timeout-ac 0
& powercfg.exe /change hibernate-timeout-ac 0
& powercfg.exe /change disk-timeout-ac 0
& powercfg.exe /change monitor-timeout-ac 10

# ── cloudflared 확인 ──────────────────────────────────────
$cf = Get-Service -Name 'cloudflared' -ErrorAction SilentlyContinue
if ($cf) {
    Write-DamsoLog "cloudflared 서비스: $($cf.Status)"
    if ($cf.Status -ne 'Running') {
        Write-DamsoLog 'cloudflared 가 멈춰 있습니다. Start-Service cloudflared 로 켜세요.' 'WARN'
    }
} else {
    Write-DamsoLog 'cloudflared 서비스가 없습니다. docs/LOCAL_SERVER.md 의 Cloudflare Tunnel 절차를 따라가세요.' 'WARN'
}

Write-Host ''
Write-Host '────────────────────────────────────────────────'
Write-Host ' 등록 완료'
Write-Host '────────────────────────────────────────────────'
Write-Host ' 지금 바로 띄우기 :  Start-ScheduledTask -TaskName damso'
Write-Host ' 상태 보기        :  Get-ScheduledTask damso, damso-backup | Get-ScheduledTaskInfo'
Write-Host ' 앱 로그          :  Get-Content C:\damso\logs\damso.log -Tail 50 -Wait'
Write-Host ' 운영 로그        :  Get-Content C:\damso\logs\ops.log -Tail 50'
Write-Host ''
Write-Host ' 남은 것: BIOS 에서 "정전 복구 후 자동 부팅"(Restore on AC Power Loss)을 켜 두세요.'
Write-Host '          윈도우 업데이트 재시작 뒤에는 부팅 트리거가 알아서 다시 띄웁니다.'
