#!/usr/bin/env bash
#
# 운영 서버(AWS EC2) 안에서 실행되는 내보내기 스크립트. 직접 실행하지 않는다 —
# 로컬에서 deploy/export-from-aws.sh 가 SSM 터널로 이 파일을 흘려보내 root 로 돌린다.
#
#   sudo bash aws-export-bundle.sh <STAMP> [--stop]
#
# 만드는 것: /tmp/damso-export-<STAMP>.tgz — 아래 셋이 함께 들어 있다.
#   db/*.csv        public 스키마의 테이블별 데이터 (스키마는 옮기지 않는다)
#   damso.key       개인정보 암호화 키 — 이게 없으면 이름·전화번호를 복호화할 수 없다
#   uploads/        소개팅 사진
#
# 세 개는 반드시 한 묶음이어야 한다. DB 만 옮기면 사진 없는 프로필이 되고,
# 키를 빼먹으면 행은 있는데 개인정보 칸이 전부 못 읽는 암호문으로 남는다.
set -euo pipefail

STAMP="${1:?STAMP 인자가 필요합니다}"
STOP_SERVICE="no"
[[ "${2:-}" == "--stop" ]] && STOP_SERVICE="yes"

ENV_FILE=/etc/damso/damso.env
UPLOADS_DIR=/var/lib/damso/uploads
DATA_KEY=/var/lib/damso/damso.key
WORK="/tmp/damso-export-$STAMP"
BUNDLE="/tmp/damso-export-$STAMP.tgz"

log() { echo "[서버] $*"; }

[[ -f "$ENV_FILE" ]] || { echo "[ERROR] $ENV_FILE 이 없습니다." >&2; exit 1; }

# systemd EnvironmentFile 형식을 그대로 읽는다 — backup.sh 와 같은 방식.
# source 로 읽으면 JAVA_OPTS=-Xms256m -Xmx768m 같은 줄에서 -Xmx768m 을 실행해 버린다.
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

: "${DB_USER:?damso.env 에 DB_USER 가 없습니다}"
: "${DB_PASSWORD:?damso.env 에 DB_PASSWORD 가 없습니다}"

# ── 서비스 정지 (최종 이전일 때만) ────────────────────────
# CSV 내보내기 자체는 한 트랜잭션에서 읽으므로 켜진 채로도 일관적이다. 다만 사진은 그렇지 않다 —
# 뽑는 중에 업로드가 들어오면 DB 행은 있는데 파일이 빠진 번들이 된다.
# 실제 이전(cutover)에는 멈추고 뽑고, 그대로 멈춘 채 둔다. 손님이 옛 서버에 계속 글을 쓰면
# 그 데이터는 로컬로 넘어오지 않는다.
if [[ "$STOP_SERVICE" == "yes" ]]; then
  log "damso 서비스를 멈춥니다 (이전이 끝날 때까지 옛 서버는 응답하지 않습니다)"
  systemctl stop damso
  sleep 2
fi

rm -rf "$WORK" "$BUNDLE"
mkdir -p "$WORK/db"

# ── DB → CSV ──────────────────────────────────────────────
export PGPASSWORD="$DB_PASSWORD"
PSQL=(psql -h "${DB_HOST:-127.0.0.1}" -p "${DB_PORT:-5432}" -U "$DB_USER" -d "${DB_NAME:-damso}"
      -v ON_ERROR_STOP=1 --no-psqlrc)

# flyway_schema_history 는 옮기지 않는다. 받는 쪽 DB 는 자기 이력을 따로 갖고 있다.
mapfile -t TABLES < <("${PSQL[@]}" -At -c \
  "SELECT table_name FROM information_schema.tables
   WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
     AND table_name <> 'flyway_schema_history' ORDER BY table_name")

if [[ ${#TABLES[@]} -eq 0 ]]; then
  echo "[ERROR] public 스키마에 테이블이 없습니다. DB 이름을 확인하세요." >&2
  exit 1
fi

: > "$WORK/db/counts.txt"
echo "# PostgreSQL 원본 행 수 (이전 후 대조용)" >> "$WORK/db/counts.txt"

TOTAL=0
for t in "${TABLES[@]}"; do
  # NULL '' 은 NULL 을 따옴표 없는 빈 칸으로, 빈 문자열을 "" 로 구분해 쓴다.
  # H2Import 가 이 둘을 정확히 되살린다.
  "${PSQL[@]}" -c "\\copy \"$t\" TO '$WORK/db/$t.csv' WITH (FORMAT csv, HEADER true, NULL '')" >/dev/null
  n=$("${PSQL[@]}" -At -c "SELECT COUNT(*) FROM \"$t\"")
  printf '%-24s %6d\n' "$t" "$n" >> "$WORK/db/counts.txt"
  printf '  %-24s %6d 행\n' "$t" "$n"
  TOTAL=$((TOTAL + n))
done
unset PGPASSWORD
log "테이블 ${#TABLES[@]}개 / 총 ${TOTAL}행"

# ── 암호화 키 ─────────────────────────────────────────────
# DAMSO_DATA_KEY 를 환경변수로 직접 넣어 쓰는 구성이면 키 파일이 없다. 그 값을 파일로 내보낸다.
if [[ -f "$DATA_KEY" ]]; then
  install -m 600 "$DATA_KEY" "$WORK/damso.key"
  log "암호화 키: 파일에서 가져왔습니다 ($DATA_KEY)"
elif [[ -n "${DAMSO_DATA_KEY:-}" ]]; then
  printf '%s' "$DAMSO_DATA_KEY" > "$WORK/damso.key"
  chmod 600 "$WORK/damso.key"
  log "암호화 키: damso.env 의 DAMSO_DATA_KEY 를 파일로 내보냈습니다"
else
  echo "[ERROR] 암호화 키를 찾을 수 없습니다 ($DATA_KEY 도, DAMSO_DATA_KEY 도 없음)." >&2
  echo "        이 키 없이 옮기면 저장된 이름·전화번호를 영구히 읽을 수 없습니다." >&2
  exit 1
fi

# ── 업로드 사진 ───────────────────────────────────────────
if [[ -d "$UPLOADS_DIR" ]]; then
  cp -a "$UPLOADS_DIR" "$WORK/uploads"
  log "사진 $(find "$WORK/uploads" -type f | wc -l | tr -d ' ')장"
else
  mkdir -p "$WORK/uploads"
  log "[경고] $UPLOADS_DIR 이 없습니다 — 사진 없이 묶습니다"
fi

# ── 묶기 ──────────────────────────────────────────────────
tar -czf "$BUNDLE" -C "$WORK" db damso.key uploads
chmod 600 "$BUNDLE"
# scp 로 가져가는 계정(ubuntu)이 읽을 수 있어야 한다. 키가 들어 있으므로 다른 사용자에게는 열지 않는다.
chown "${SUDO_USER:-ubuntu}" "$BUNDLE"
rm -rf "$WORK"

log "번들 준비 완료: $BUNDLE ($(du -h "$BUNDLE" | cut -f1))"
echo "BUNDLE=$BUNDLE"
