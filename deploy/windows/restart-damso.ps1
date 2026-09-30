#Requires -Version 5.1
<#
    담소를 정상 종료하고 다시 띄운다. damso.env 를 고친 뒤 적용할 때 쓴다.

    설치 위치: C:\damso\bin\restart-damso.ps1
        powershell -ExecutionPolicy Bypass -File C:\damso\bin\restart-damso.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'damso-common.ps1')

Import-DamsoEnv | Out-Null

Stop-DamsoApp | Out-Null
Start-DamsoApp

if (Wait-DamsoHealth -TimeoutSeconds 180) {
    Write-DamsoLog '재시작 완료 (헬스체크 통과)'
    exit 0
}
Write-DamsoLog '재시작 후 헬스체크가 응답하지 않습니다. C:\damso\logs\damso.log 를 확인하세요.' 'ERROR'
exit 1
