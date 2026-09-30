#!/usr/bin/env bash
#
# H2 파일 DB → PostgreSQL 데이터 이전 (1회성)
#
#   ./deploy/migrate-h2-to-postgres.sh [H2 파일 경로 접두사] [출력 디렉터리]
#   예) ./deploy/migrate-h2-to-postgres.sh ./data/damso /tmp/damso-migrate
#
# "파일 경로 접두사"는 .mv.db 를 뺀 이름이다. data/damso.mv.db → ./data/damso
#
# 이 스크립트는 데이터를 CSV 로 뽑고 적재용 load.sql 을 만드는 데까지만 한다.
# 실제 적재는 서버에서 하며, 마지막에 그 명령을 그대로 출력해 준다.
#
# 전제:
#   - 담소 앱이 꺼져 있을 것. 켜진 채로 뽑으면 도중에 바뀐 데이터가 섞인다.
#   - PostgreSQL 쪽에는 이미 테이블이 만들어져 있을 것.
#     (prod 프로파일로 앱을 한 번 띄우면 Flyway 가 만든다. 스키마는 옮기지 않는다)
set -euo pipefail

H2_PREFIX="${1:-./data/damso}"
OUT_DIR="${2:-/tmp/damso-migrate}"
H2_VERSION="2.2.224"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

[[ -f "${H2_PREFIX}.mv.db" ]] || {
  echo "[ERROR] H2 파일이 없습니다: ${H2_PREFIX}.mv.db" >&2
  echo "        .mv.db 를 뺀 경로를 넘겨야 합니다. 예) ./data/damso" >&2
  exit 1
}

if [[ -f "${H2_PREFIX}.lock.db" ]]; then
  echo "[경고] ${H2_PREFIX}.lock.db 가 있습니다 — 앱이 켜져 있을 수 있습니다."
  echo "       ./scripts/stop.sh 로 완전히 끈 뒤 다시 실행하세요."
  read -rp "       그래도 계속할까요? [y/N] " ans
  [[ "$ans" == "y" || "$ans" == "Y" ]] || exit 1
fi

# ── H2 드라이버 확보 ──────────────────────────────────────
find_h2_jar() {
  local cached
  cached="$(find "$HOME/.m2/repository/com/h2database/h2" -name 'h2-*.jar' 2>/dev/null | sort | tail -1)"
  if [[ -n "$cached" ]]; then echo "$cached"; return; fi

  local dl="/tmp/h2-$H2_VERSION.jar"
  if [[ ! -f "$dl" ]]; then
    echo "[INFO] H2 드라이버 내려받는 중 ($H2_VERSION)..." >&2
    curl -fsSL -o "$dl" \
      "https://repo1.maven.org/maven2/com/h2database/h2/$H2_VERSION/h2-$H2_VERSION.jar"
  fi
  echo "$dl"
}
H2_JAR="$(find_h2_jar)"
echo "[INFO] H2 드라이버: $H2_JAR"

command -v java >/dev/null || { echo "[ERROR] java 가 필요합니다 (JDK 21 권장)." >&2; exit 1; }

# ── 내보내기 ──────────────────────────────────────────────
# 앱과 같은 URL 옵션을 써야 식별자 대소문자 처리가 동일하다.
JDBC_URL="jdbc:h2:file:$(cd "$(dirname "$H2_PREFIX")" && pwd)/$(basename "$H2_PREFIX");MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;IFEXISTS=TRUE"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

echo "[INFO] 데이터 추출 중..."
java -cp "$H2_JAR" "$SCRIPT_DIR/H2Export.java" "$JDBC_URL" "$OUT_DIR"

# postgres 계정이 psql \copy 로 읽어야 하므로 읽기 권한을 열어 둔다.
chmod 755 "$OUT_DIR"
chmod 644 "$OUT_DIR"/*

cat <<EOF

========================================================
 추출 완료 → $OUT_DIR
========================================================
 다음 순서로 서버에 적재합니다.

 1) 서버로 복사
      rsync -av $OUT_DIR/ deploy@서버주소:/tmp/damso-migrate/

 2) (서버에서) 앱을 prod 로 한 번 띄워 테이블을 만든 뒤 멈춘다
      sudo systemctl start damso && sleep 20 && sudo systemctl stop damso

 3) (서버에서) 적재 — \copy 가 상대경로를 쓰므로 반드시 그 디렉터리에서 실행한다
      cd /tmp/damso-migrate
      sudo -u postgres psql -d damso -v ON_ERROR_STOP=1 -f load.sql

 4) 마지막에 출력되는 행 수를 counts.txt 와 대조한다. 한 줄이라도 다르면 멈추고 원인을 확인할 것.

 5) 사진 파일도 함께 옮긴다 (DB 만 옮기면 프로필에 사진이 안 나온다)
      rsync -av ./uploads/ deploy@서버주소:/tmp/uploads/
      ssh deploy@서버주소 'sudo rsync -a /tmp/uploads/ /var/lib/damso/uploads/ && sudo chown -R damso:damso /var/lib/damso/uploads'

 6) 앱 기동
      sudo systemctl start damso
========================================================
EOF