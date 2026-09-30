#!/usr/bin/env bash
#
# 담소 매칭 시스템 종료 스크립트 (macOS / Linux)
# start.sh 가 남긴 PID 파일과 포트(8080, 5173)를 기준으로 프로세스를 정리한다.
#
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$PROJECT_ROOT/logs"

kill_pidfile() {
  local name="$1" file="$2"
  if [[ -f "$file" ]]; then
    local pid; pid="$(cat "$file")"
    if kill -0 "$pid" 2>/dev/null; then
      echo "[STOP] $name (pid $pid) 종료"
      kill "$pid" 2>/dev/null || true
    fi
    rm -f "$file"
  fi
}

kill_port() {
  local port="$1"
  local pids; pids="$(lsof -ti tcp:"$port" 2>/dev/null || true)"
  if [[ -n "$pids" ]]; then
    echo "[STOP] 포트 $port 사용 프로세스 종료: $pids"
    kill $pids 2>/dev/null || true
  fi
}

kill_pidfile "백엔드" "$LOG_DIR/server.pid"
kill_pidfile "프론트엔드" "$LOG_DIR/client.pid"
kill_port 8080
kill_port 5173

echo "[DONE] 종료 완료"