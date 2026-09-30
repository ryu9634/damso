#!/usr/bin/env bash
#
# 담소 매칭 시스템 — 릴리스 교체 (서버에서 실행. /opt/damso/bin/deploy.sh 로 설치된다)
#
#   deploy.sh <새 jar 경로> <릴리스 이름>
#   예) deploy.sh /tmp/match.jar 1a2b3c4
#
# 흐름: 새 릴리스 배치 → current 링크 교체 → 재시작 → 헬스체크 → 실패하면 이전 릴리스로 되돌린다.
# 되돌리는 것까지가 배포다. 헬스체크가 실패했는데 그대로 두면 장애가 그대로 남는다.
set -euo pipefail

JAR_SRC="${1:-}"
RELEASE="${2:-}"
if [[ -z "$JAR_SRC" || -z "$RELEASE" ]]; then
  echo "사용법: $0 <jar 경로> <릴리스 이름>" >&2
  exit 1
fi
[[ -f "$JAR_SRC" ]] || { echo "[ERROR] jar 을 찾을 수 없습니다: $JAR_SRC" >&2; exit 1; }

APP_HOME=/opt/damso
RELEASES="$APP_HOME/releases"
CURRENT="$APP_HOME/current"
HEALTH_URL="http://127.0.0.1:8080/api/health"
HEALTH_TIMEOUT=90     # 초. JVM 기동 + 스키마 보정까지 여유 있게.
KEEP_RELEASES=5

log() { echo "[$(date '+%F %T')] $*"; }

# 롤백 대상을 미리 기억해 둔다. 첫 배포면 비어 있다.
PREVIOUS=""
if [[ -L "$CURRENT" ]]; then
  PREVIOUS="$(readlink -f "$CURRENT")"
  log "현재 릴리스: $(basename "$PREVIOUS")"
fi

# ── 새 릴리스 배치 ────────────────────────────────────────
TARGET="$RELEASES/$RELEASE"
log "새 릴리스 배치: $TARGET"
mkdir -p "$TARGET"
install -m 644 "$JAR_SRC" "$TARGET/match.jar"

# ── 링크 교체 ─────────────────────────────────────────────
# 원자적으로 바꾸기 위해 임시 링크를 만든 뒤 mv -T 로 덮어쓴다.
ln -sfn "$TARGET" "$CURRENT.tmp"
mv -Tf "$CURRENT.tmp" "$CURRENT"
log "current → $RELEASE"

# ── 재시작 ────────────────────────────────────────────────
log "서비스 재시작"
sudo systemctl restart damso

# ── 헬스체크 ──────────────────────────────────────────────
log "헬스체크 대기 (최대 ${HEALTH_TIMEOUT}초)"
healthy=0
for ((i = 0; i < HEALTH_TIMEOUT; i++)); do
  if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null 2>&1; then
    healthy=1
    log "헬스체크 성공 (${i}초)"
    break
  fi
  # 서비스가 아예 죽어 재시작 루프에 빠졌다면 더 기다릴 이유가 없다.
  if ! systemctl is-active --quiet damso; then
    sleep 3
    if ! systemctl is-active --quiet damso; then
      log "서비스가 기동하지 못했습니다."
      break
    fi
  fi
  sleep 1
done

# ── 실패 시 롤백 ──────────────────────────────────────────
if [[ "$healthy" != "1" ]]; then
  log "[ERROR] 헬스체크 실패 — 최근 로그:"
  journalctl -u damso -n 40 --no-pager || true

  if [[ -n "$PREVIOUS" && -d "$PREVIOUS" ]]; then
    log "이전 릴리스로 롤백: $(basename "$PREVIOUS")"
    ln -sfn "$PREVIOUS" "$CURRENT.tmp"
    mv -Tf "$CURRENT.tmp" "$CURRENT"
    sudo systemctl restart damso
    sleep 5
    if curl -fsS --max-time 3 "$HEALTH_URL" >/dev/null 2>&1; then
      log "롤백 완료 — 서비스는 이전 버전으로 정상 동작합니다."
    else
      log "[CRITICAL] 롤백 후에도 헬스체크가 실패합니다. 수동 확인이 필요합니다."
    fi
  else
    log "[CRITICAL] 롤백할 이전 릴리스가 없습니다 (첫 배포). 수동 확인이 필요합니다."
  fi

  # 실패한 릴리스는 남겨 둔다. 원인 파악에 필요하다.
  exit 1
fi

# ── 오래된 릴리스 정리 ────────────────────────────────────
# 현재 가리키고 있는 것은 절대 지우지 않는다.
CURRENT_REAL="$(readlink -f "$CURRENT")"
mapfile -t old < <(ls -1dt "$RELEASES"/*/ 2>/dev/null | tail -n "+$((KEEP_RELEASES + 1))")
for dir in "${old[@]:-}"; do
  [[ -z "$dir" ]] && continue
  [[ "$(readlink -f "$dir")" == "$CURRENT_REAL" ]] && continue
  log "오래된 릴리스 삭제: $(basename "$dir")"
  rm -rf "$dir"
done

log "배포 완료 — $RELEASE"