#!/usr/bin/env bash
#
# 담소 매칭 시스템 — 일일 백업 (cron: 매일 04:10, damso 계정)
#
# DB 덤프와 업로드 사진을 함께 남긴다. 둘 중 하나만 있으면 복구가 되지 않는다
# (프로필 행은 있는데 사진이 없거나, 그 반대).
#
# 원격 보관: /etc/damso/damso.env 에 BACKUP_REMOTE 를 넣으면 그쪽으로도 복사한다.
#   BACKUP_REMOTE=user@backup-host:/path/damso
# 같은 서버에만 있는 백업은 서버가 죽는 순간 같이 사라진다. 원격 설정을 권한다.
set -euo pipefail

ENV_FILE=/etc/damso/damso.env
BACKUP_DIR=/var/backups/damso
UPLOADS_DIR=/var/lib/damso/uploads
DATA_KEY=/var/lib/damso/damso.key   # 개인정보 암호화 키 — 이게 없으면 DB 덤프를 복원해도 번호를 못 읽는다
RETENTION_DAYS=14

# systemd EnvironmentFile 형식을 그대로 읽는다 — 따옴표 없는 값에 공백이 있어도 된다.
# (source 로 읽으면 JAVA_OPTS=-Xms256m -Xmx768m 같은 줄에서 -Xmx768m 을 명령으로 실행해 버린다)
load_env() {
  local line key val
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]] || continue
    key="${line%%=*}"; val="${line#*=}"
    val="${val#\"}"; val="${val%\"}"
    export "$key=$val"
  done < "$1"
}

load_env "$ENV_FILE"

STAMP="$(date +%Y%m%d-%H%M)"
DB_FILE="$BACKUP_DIR/db-$STAMP.dump"
UP_FILE="$BACKUP_DIR/uploads-$STAMP.tar.gz"

log() { echo "[$(date '+%F %T')] $*"; }

log "백업 시작"

# ── DB ────────────────────────────────────────────────────
# -Fc(커스텀 포맷)은 압축되고 pg_restore 로 선택 복구가 된다.
PGPASSWORD="$DB_PASSWORD" pg_dump \
  -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" \
  -U "$DB_USER" -d "${DB_NAME:-damso}" \
  -Fc -f "$DB_FILE"

# 크기 0 인 덤프가 조용히 쌓이는 것이 가장 흔한 백업 실패다. 여기서 잡는다.
if [[ ! -s "$DB_FILE" ]]; then
  log "[ERROR] DB 덤프가 비어 있습니다: $DB_FILE"
  exit 1
fi
log "DB 덤프 완료: $(du -h "$DB_FILE" | cut -f1)"

# ── 업로드 사진 ───────────────────────────────────────────
tar -czf "$UP_FILE" -C "$(dirname "$UPLOADS_DIR")" "$(basename "$UPLOADS_DIR")"
# 키 파일은 덤프와 짝을 이룬다. 있을 때만 옆에 둔다(600).
if [[ -f "$DATA_KEY" ]]; then
  install -m 600 "$DATA_KEY" "$BACKUP_DIR/damso-$STAMP.key"
fi
log "사진 백업 완료: $(du -h "$UP_FILE" | cut -f1)"

# ── 원격 복사 ─────────────────────────────────────────────
if [[ -n "${BACKUP_REMOTE:-}" ]]; then
  log "원격 복사: $BACKUP_REMOTE"
  rsync -az "$DB_FILE" "$UP_FILE" "$BACKUP_REMOTE/"
fi

# ── 보관 기간 지난 것 삭제 ────────────────────────────────
find "$BACKUP_DIR" -maxdepth 1 -name 'db-*.dump'       -mtime "+$RETENTION_DAYS" -delete
find "$BACKUP_DIR" -maxdepth 1 -name 'uploads-*.tar.gz' -mtime "+$RETENTION_DAYS" -delete

log "백업 완료 (보관 ${RETENTION_DAYS}일)"