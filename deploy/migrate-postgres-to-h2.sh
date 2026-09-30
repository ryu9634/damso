#!/usr/bin/env bash
#
# PostgreSQL 내보내기 번들 → H2 파일 DB. (집 PC 이전 2단계)
#
#   ./deploy/migrate-postgres-to-h2.sh <번들 디렉터리> [출력 디렉터리]
#   예) ./deploy/migrate-postgres-to-h2.sh ~/damso-migrate/20260923-140000
#
# 번들은 deploy/export-from-aws.sh 가 만든 것이다 (db/*.csv · damso.key · uploads/).
# 결과는 출력 디렉터리에 윈도우 PC 로 그대로 복사할 모양으로 놓인다:
#
#   <출력>/data/damso.mv.db   H2 파일 DB      → C:\damso\data\
#   <출력>/data/damso.key     암호화 키       → C:\damso\data\
#   <출력>/uploads/           소개팅 사진     → C:\damso\uploads\
#
# 이 스크립트는 맥/리눅스에서 돌린다. 윈도우 PC 에는 결과만 복사한다.
# H2 스키마는 앱을 local 프로파일로 한 번 띄워 Flyway 가 만들게 한다 (필요하면 자동으로 한다).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

BUNDLE_DIR="${1:?사용법: $0 <번들 디렉터리> [출력 디렉터리]}"
BUNDLE_DIR="$(cd "$BUNDLE_DIR" && pwd)"
OUT_DIR="${2:-$BUNDLE_DIR/out}"

H2_VERSION="2.2.224"
BOOT_PORT="${BOOT_PORT:-18080}"

die() { echo "[ERROR] $*" >&2; exit 1; }
log() { echo "[이전] $*"; }

# ── 번들 확인 ─────────────────────────────────────────────
[[ -d "$BUNDLE_DIR/db" ]]        || die "번들에 db/ 가 없습니다: $BUNDLE_DIR"
[[ -f "$BUNDLE_DIR/damso.key" ]] || die "번들에 damso.key 가 없습니다. 키 없이는 개인정보를 읽을 수 없습니다."
ls "$BUNDLE_DIR"/db/*.csv >/dev/null 2>&1 || die "번들의 db/ 에 CSV 가 없습니다."

command -v java >/dev/null || die "java 가 필요합니다 (JDK 21)."

mkdir -p "$OUT_DIR/data" "$OUT_DIR/uploads"
H2_PREFIX="$OUT_DIR/data/damso"

# ── H2 드라이버 확보 ──────────────────────────────────────
# migrate-h2-to-postgres.sh 와 같은 방식 — 로컬 m2 캐시를 먼저 보고, 없으면 내려받는다.
find_h2_jar() {
  local cached
  cached="$(find "$HOME/.m2/repository/com/h2database/h2" -name 'h2-*.jar' 2>/dev/null | sort | tail -1)"
  if [[ -n "$cached" ]]; then echo "$cached"; return; fi

  local dl="/tmp/h2-$H2_VERSION.jar"
  if [[ ! -f "$dl" ]]; then
    log "H2 드라이버 내려받는 중 ($H2_VERSION)..."
    curl -fsSL -o "$dl" \
      "https://repo1.maven.org/maven2/com/h2database/h2/$H2_VERSION/h2-$H2_VERSION.jar"
  fi
  echo "$dl"
}
H2_JAR="$(find_h2_jar)"
log "H2 드라이버: $H2_JAR"

# 앱과 한 글자도 다르지 않아야 한다 (application-local.yml 과 동일).
# 옵션이 다르면 식별자 대소문자 처리가 달라져 같은 파일을 열어도 테이블을 못 찾는다.
JDBC_URL="jdbc:h2:file:$H2_PREFIX;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER"

# ── 스키마 준비 ───────────────────────────────────────────
# 데이터만 옮긴다. 테이블은 앱이 만든다 — 그래야 엔티티와 정확히 일치하고,
# 운영 PostgreSQL 에서 보정된 컬럼 타입이 따라오지 않는다.
if [[ -f "$H2_PREFIX.mv.db" ]]; then
  log "기존 H2 파일을 그대로 씁니다: $H2_PREFIX.mv.db (데이터는 아래에서 전부 비우고 새로 넣습니다)"
else
  JAR="$(ls "$PROJECT_DIR"/backend/target/match-*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)"
  if [[ -z "$JAR" ]]; then
    command -v mvn >/dev/null || die "jar 이 없고 mvn 도 없습니다. ./scripts/build.sh 로 먼저 빌드하세요."
    log "jar 이 없어 빌드합니다 (테스트는 건너뜁니다)"
    (cd "$PROJECT_DIR/backend" && mvn -q -B clean package -DskipTests)
    JAR="$(ls "$PROJECT_DIR"/backend/target/match-*.jar | grep -v '\.original$' | head -1)"
  fi
  log "빈 H2 에 스키마를 만듭니다 — 앱을 local 프로파일로 잠깐 띄웁니다 ($JAR)"

  BOOT_LOG="$OUT_DIR/schema-boot.log"
  # 이전용 임시 기동이므로 운영 데이터 경로를 건드리지 않게 전부 출력 디렉터리 안으로 돌린다.
  DAMSO_DB="$H2_PREFIX" \
  DAMSO_DATA_KEY_FILE="$OUT_DIR/data/damso.key" \
  DAMSO_UPLOADS="$OUT_DIR/uploads" \
  DAMSO_LOG="$OUT_DIR/schema-boot-app.log" \
  SERVER_PORT="$BOOT_PORT" \
    java -jar "$JAR" --spring.profiles.active=local > "$BOOT_LOG" 2>&1 &
  BOOT_PID=$!

  # 기동을 기다린다. 실패했는데 계속 기다리지 않도록 프로세스 생존도 함께 본다.
  READY="no"
  for _ in $(seq 1 60); do
    if ! kill -0 "$BOOT_PID" 2>/dev/null; then break; fi
    if curl -fsS "http://127.0.0.1:$BOOT_PORT/api/health" >/dev/null 2>&1; then READY="yes"; break; fi
    sleep 1
  done
  kill "$BOOT_PID" 2>/dev/null || true
  wait "$BOOT_PID" 2>/dev/null || true

  if [[ "$READY" != "yes" ]]; then
    echo "--- 기동 로그 마지막 40줄 ---" >&2
    tail -40 "$BOOT_LOG" >&2
    die "스키마 생성용 기동이 실패했습니다. 위 로그를 확인하세요. (포트 $BOOT_PORT 충돌이면 BOOT_PORT 로 바꿔 주세요)"
  fi
  log "스키마 생성 완료"

  # 앱이 첫 기동에 만든 키는 버린다. 아래에서 AWS 에서 가져온 진짜 키로 덮어쓴다 —
  # 이 순서를 어기면 기존 개인정보가 전부 복호화 불가가 된다.
  rm -f "$OUT_DIR/data/damso.key"
fi

# H2 는 비정상 종료 뒤 .lock.db 를 남긴다. 붙어 있는 프로세스가 없으면 무해하다.
rm -f "$H2_PREFIX.lock.db"

# ── 적재 ──────────────────────────────────────────────────
log "CSV → H2 적재"
java -cp "$H2_JAR" "$SCRIPT_DIR/H2Import.java" "$JDBC_URL" "$BUNDLE_DIR/db"

# ── 키와 사진 ─────────────────────────────────────────────
# DB·키·사진은 한 묶음이어야 한다. 하나만 옮기면 복구가 되지 않는다.
install -m 600 "$BUNDLE_DIR/damso.key" "$OUT_DIR/data/damso.key"
log "암호화 키 배치 완료"

if [[ -d "$BUNDLE_DIR/uploads" ]]; then
  # --delete 없이 덮어쓴다. 여러 번 돌려도 사진이 사라지지 않는다.
  rsync -a "$BUNDLE_DIR/uploads/" "$OUT_DIR/uploads/"
  log "사진 $(find "$OUT_DIR/uploads" -type f | wc -l | tr -d ' ')장 배치 완료"
fi

# ── 대조 ──────────────────────────────────────────────────
echo
echo "────────── 행 수 대조 ──────────"
if [[ -f "$BUNDLE_DIR/db/counts.txt" && -f "$BUNDLE_DIR/db/counts-h2.txt" ]]; then
  MISMATCH=0
  while read -r table pg; do
    [[ "$table" == "#"* || -z "$table" ]] && continue
    h2=$(awk -v t="$table" '$1 == t { print $2 }' "$BUNDLE_DIR/db/counts-h2.txt")
    h2="${h2:-0}"
    if [[ "$pg" == "$h2" ]]; then
      printf '  %-24s %6s = %-6s ✓\n' "$table" "$pg" "$h2"
    else
      printf '  %-24s %6s ≠ %-6s ✗\n' "$table" "$pg" "$h2"
      MISMATCH=1
    fi
  done < "$BUNDLE_DIR/db/counts.txt"
  echo
  if [[ "$MISMATCH" == "1" ]]; then
    die "행 수가 맞지 않습니다. 위 ✗ 표시된 테이블과 적재 로그의 경고를 확인하세요."
  fi
  log "모든 테이블의 행 수가 일치합니다"
else
  echo "  (counts 파일이 없어 건너뜁니다)"
fi

echo
echo "────────────────────────────────────────────────"
echo " 이전 완료: $OUT_DIR"
echo "────────────────────────────────────────────────"
echo " 윈도우 PC 로 복사할 것:"
echo "   $OUT_DIR/data/damso.mv.db   →  C:\\damso\\data\\damso.mv.db"
echo "   $OUT_DIR/data/damso.key     →  C:\\damso\\data\\damso.key   (유출되면 개인정보가 풀린다)"
echo "   $OUT_DIR/uploads/           →  C:\\damso\\uploads\\"
echo
echo " 이어서 docs/LOCAL_SERVER.md 의 '윈도우 PC 준비' 부터 따라간다."
