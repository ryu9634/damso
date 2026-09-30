#!/usr/bin/env bash
#
# 담소 매칭 시스템 실행 스크립트 (macOS / Linux)
#
#   ./scripts/start.sh              기존 빌드된 jar 로 실행 (없으면 자동 빌드)
#   ./scripts/start.sh --rebuild    서버를 다시 빌드한 뒤 실행
#   ./scripts/start.sh -r           동일 (짧은 옵션)
#
# 백엔드(Spring Boot, 8080) + 프론트엔드(Vite, 5173) 를 함께 띄우고,
# Ctrl+C 를 누르면 둘 다 정리하고 종료한다.
#
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

JAR="backend/target/match-0.0.1-SNAPSHOT.jar"
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR" "$PROJECT_ROOT/data"

REBUILD=0
[[ "${1:-}" == "--rebuild" || "${1:-}" == "-r" ]] && REBUILD=1

# ─── Java 자동 탐지 (JDK 21+) ────────────────────────────────
find_java() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    echo "$JAVA_HOME/bin/java"; return
  fi
  if [[ -x /usr/libexec/java_home ]]; then
    local jh; jh="$(/usr/libexec/java_home 2>/dev/null || true)"
    [[ -n "$jh" && -x "$jh/bin/java" ]] && { echo "$jh/bin/java"; return; }
  fi
  for c in /opt/homebrew/opt/openjdk/bin/java /usr/local/opt/openjdk/bin/java; do
    [[ -x "$c" ]] && { echo "$c"; return; }
  done
  command -v java 2>/dev/null || true
}

JAVA_BIN="$(find_java)"
if [[ -z "$JAVA_BIN" ]]; then
  echo "[ERROR] Java 를 찾을 수 없습니다. JDK 21 이상을 설치하세요 (예: brew install openjdk@21)." >&2
  exit 1
fi
export JAVA_HOME="$(cd "$(dirname "$JAVA_BIN")/.." && pwd)"
echo "[CHECK] Java: $JAVA_BIN"
"$JAVA_BIN" -version

# ─── 서버 빌드 ───────────────────────────────────────────────
if [[ ! -f "$JAR" ]]; then
  echo "[INFO] 빌드된 jar 가 없어 빌드를 진행합니다."
  REBUILD=1
fi
if [[ "$REBUILD" == "1" ]]; then
  echo "[BUILD] 서버 빌드 중..."
  (cd "$PROJECT_ROOT/backend" && mvn clean package -DskipTests)
  echo "[DONE] 서버 빌드 완료"
else
  echo "[SKIP] 서버 빌드 생략 (--rebuild 로 재빌드)"
fi

# ─── 프론트 의존성 ───────────────────────────────────────────
if [[ ! -d frontend/node_modules ]]; then
  echo "[CHECK] 프론트 의존성 설치 중..."
  (cd "$PROJECT_ROOT/frontend" && npm install)
fi

# ─── 정리 핸들러 ─────────────────────────────────────────────
SERVER_PID=""
CLIENT_PID=""
cleanup() {
  echo
  echo "[STOP] 종료 중..."
  [[ -n "$CLIENT_PID" ]] && kill "$CLIENT_PID" 2>/dev/null || true
  [[ -n "$SERVER_PID" ]] && kill "$SERVER_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  echo "[DONE] 모두 종료되었습니다."
}
trap cleanup INT TERM EXIT

# ─── 백엔드 실행 ─────────────────────────────────────────────
echo "[START] 백엔드 서버 실행 (port 8080)..."
"$JAVA_BIN" -jar "$JAR" > "$LOG_DIR/server.log" 2>&1 &
SERVER_PID=$!
echo "$SERVER_PID" > "$LOG_DIR/server.pid"

# ─── 프론트엔드 실행 ─────────────────────────────────────────
echo "[START] 프론트엔드 서버 실행 (port 5173)..."
(cd "$PROJECT_ROOT/frontend" && exec npm run dev) > "$LOG_DIR/client.log" 2>&1 &
CLIENT_PID=$!
echo "$CLIENT_PID" > "$LOG_DIR/client.pid"

sleep 2
cat <<EOF

========================================
 담소 매칭 시스템 실행 완료
========================================
  관리자     http://localhost:5173/admin
  손님       http://localhost:5173/
  소개팅     http://localhost:5173/dating

  로그       $LOG_DIR/server.log
             $LOG_DIR/client.log

  종료: Ctrl+C  (또는 ./scripts/stop.sh)
========================================

로그를 표시합니다 (Ctrl+C 로 종료)...
EOF

# 두 프로세스 로그를 따라가며 대기. 하나라도 죽으면 종료.
tail -f "$LOG_DIR/server.log" "$LOG_DIR/client.log" &
TAIL_PID=$!

# 서버/클라이언트 중 하나라도 종료되면 빠져나온다.
while kill -0 "$SERVER_PID" 2>/dev/null && kill -0 "$CLIENT_PID" 2>/dev/null; do
  sleep 1
done

kill "$TAIL_PID" 2>/dev/null || true
echo "[WARN] 프로세스 중 하나가 종료되었습니다. 로그를 확인하세요."