#Requires -Version 5.1
<#
    새 버전을 받아 바꿔 끼운다. 헬스체크가 실패하면 이전 jar 로 되돌린다.

    설치 위치: C:\damso\bin\update.ps1

        powershell -ExecutionPolicy Bypass -File C:\damso\bin\update.ps1
        powershell -ExecutionPolicy Bypass -File C:\damso\bin\update.ps1 -Jar D:\받은\match.jar
        powershell -ExecutionPolicy Bypass -File C:\damso\bin\update.ps1 -Rollback

    인자 없이 실행하면 GitHub 의 latest 릴리스에서 jar 을 내려받는다 (GITHUB_REPO 설정).
    푸시하면 GitHub Actions 가 jar 을 빌드해 그 릴리스에 올려 둔다 — 이 PC 에는
    JDK 만 있으면 되고 Node·Maven 은 필요 없다.

    이전 버전은 app\match-previous.jar 로 남는다. 되돌리기는 -Rollback 한 번이다.
#>

param(
    [string]$Jar,
    [switch]$Rollback
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'damso-common.ps1')

$home_ = Get-DamsoHome
Import-DamsoEnv | Out-Null

$appDir = Join-Path $home_ 'app'
$current = Join-Path $appDir 'match.jar'
$previous = Join-Path $appDir 'match-previous.jar'
if (-not (Test-Path $appDir)) { New-Item -ItemType Directory -Path $appDir -Force | Out-Null }

# PS 5.1 은 기본 TLS 설정으로 GitHub API 에 붙지 못하는 경우가 있다.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

<# jar 을 끼우고 기동을 확인한다. 실패하면 $false. #>
function Install-DamsoJar {
    param([Parameter(Mandatory)][string]$Source)

    Stop-DamsoApp | Out-Null

    if (Test-Path $current) {
        Copy-Item -Path $current -Destination $previous -Force
    }
    Copy-Item -Path $Source -Destination $current -Force
    Write-DamsoLog "jar 교체: $Source → $current"

    Start-DamsoApp
    if (Wait-DamsoHealth -TimeoutSeconds 180) {
        Write-DamsoLog '새 버전 기동 확인 (헬스체크 통과)'
        return $true
    }
    Write-DamsoLog '새 버전이 헬스체크를 통과하지 못했습니다.' 'ERROR'
    return $false
}

# ── 되돌리기 ──────────────────────────────────────────────
if ($Rollback) {
    if (-not (Test-Path $previous)) { throw "되돌릴 이전 jar 이 없습니다: $previous" }
    Write-DamsoLog '이전 버전으로 되돌립니다'
    Stop-DamsoApp | Out-Null
    Copy-Item -Path $previous -Destination $current -Force
    Start-DamsoApp
    if (Wait-DamsoHealth -TimeoutSeconds 180) {
        Write-DamsoLog '되돌리기 완료'
        exit 0
    }
    Write-DamsoLog '되돌린 뒤에도 기동에 실패했습니다. C:\damso\logs\damso.log 를 확인하세요.' 'ERROR'
    exit 1
}

# ── jar 확보 ──────────────────────────────────────────────
$source = $Jar
if (-not $source) {
    $repo = $env:GITHUB_REPO
    if (-not $repo) { throw 'damso.env 에 GITHUB_REPO 가 없습니다. (예: ryu9634/damso)' }

    $headers = @{ 'User-Agent' = 'damso-update'; 'Accept' = 'application/vnd.github+json' }
    if ($env:GITHUB_TOKEN) { $headers['Authorization'] = "Bearer $($env:GITHUB_TOKEN)" }

    Write-DamsoLog "GitHub 릴리스 확인: $repo"
    $release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/latest" `
                                 -Headers $headers -TimeoutSec 30

    $asset = $release.assets | Where-Object { $_.name -like '*.jar' } | Select-Object -First 1
    if (-not $asset) { throw "릴리스 $($release.tag_name) 에 jar 첨부파일이 없습니다." }

    Write-DamsoLog "내려받기: $($asset.name) ($($release.tag_name), $([math]::Round($asset.size/1MB,1)) MB)"
    $source = Join-Path $appDir $asset.name

    # 첨부파일 다운로드는 Accept 헤더가 달라야 한다. JSON 을 받으면 jar 이 아니라 메타데이터가 온다.
    $dlHeaders = $headers.Clone()
    $dlHeaders['Accept'] = 'application/octet-stream'
    Invoke-WebRequest -Uri $asset.url -Headers $dlHeaders -OutFile $source -TimeoutSec 600 -UseBasicParsing

    if ((Get-Item $source).Length -lt 1MB) {
        throw "내려받은 파일이 너무 작습니다 ($source). 다운로드가 실패한 것으로 봅니다."
    }
}

if (-not (Test-Path $source)) { throw "jar 을 찾을 수 없습니다: $source" }

# 같은 파일을 가리키면 할 일이 없다 (업데이트를 두 번 돌렸을 때).
$sourcePath = (Resolve-Path $source).Path
if (Test-Path $current) {
    if ($sourcePath -eq (Resolve-Path $current).Path) {
        Write-DamsoLog '이미 이 jar 이 실행 중입니다. 할 일이 없습니다.'
        exit 0
    }
}

# ── 교체 ──────────────────────────────────────────────────
if (Install-DamsoJar -Source $source) {
    Write-DamsoLog '업데이트 완료'
    exit 0
}

Write-DamsoLog '이전 버전으로 되돌립니다' 'WARN'
if (Test-Path $previous) {
    Stop-DamsoApp | Out-Null
    Copy-Item -Path $previous -Destination $current -Force
    Start-DamsoApp
    if (Wait-DamsoHealth -TimeoutSeconds 180) {
        Write-DamsoLog '되돌리기 완료 — 서비스는 이전 버전으로 살아 있습니다.' 'WARN'
        exit 1
    }
}
Write-DamsoLog '되돌리기까지 실패했습니다. 직접 확인이 필요합니다.' 'ERROR'
exit 1
