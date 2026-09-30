#!/usr/bin/env bash
#
# 배포용 단일 jar 빌드 (프론트 포함)
#
#   ./scripts/build.sh
#
# 순서가 중요하다. Vite 가 frontend/dist/ 를 만든 다음에야 Maven 이 그걸 jar 안 static/ 으로 넣는다.
# 결과물: backend/target/match-0.0.1-SNAPSHOT.jar — 이 파일 하나가 프론트와 API 를 모두 서빙한다.
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

# ── Java 21 확인 ──────────────────────────────────────────
find_java_home() {
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then echo "$JAVA_HOME"; return; fi
  if [[ -x /usr/libexec/java_home ]]; then
    local jh; jh="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"
    [[ -n "$jh" ]] && { echo "$jh"; return; }
  fi
  for c in /opt/homebrew/opt/openjdk@21 /usr/local/opt/openjdk@21 /usr/lib/jvm/java-21-openjdk-amd64; do
    [[ -x "$c/bin/java" ]] && { echo "$c"; return; }
  done
}

JH="$(find_java_home)"
if [[ -z "$JH" ]]; then
  echo "[ERROR] JDK 21 을 찾을 수 없습니다." >&2
  echo "        macOS:  brew install openjdk@21" >&2
  echo "        Ubuntu: sudo apt install openjdk-21-jdk" >&2
  exit 1
fi
export JAVA_HOME="$JH"
echo "[CHECK] JAVA_HOME=$JAVA_HOME"
"$JAVA_HOME/bin/java" -version

# ── 프론트 ────────────────────────────────────────────────
echo "[BUILD] 프론트엔드"
cd "$PROJECT_ROOT/frontend"
if [[ -f package-lock.json ]]; then npm ci; else npm install; fi
npm run build
cd "$PROJECT_ROOT"

# dist/ 가 없으면 Maven 은 조용히 건너뛰고 프론트 없는 jar 이 나온다. 여기서 막는다.
[[ -f frontend/dist/index.html ]] || { echo "[ERROR] frontend/dist/index.html 이 없습니다 — 프론트 빌드 실패." >&2; exit 1; }

# ── 서버 ──────────────────────────────────────────────────
echo "[BUILD] 서버"
(cd "$PROJECT_ROOT/backend" && mvn -B clean package -DskipTests)

JAR="$(ls -1 backend/target/match-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
[[ -f "$JAR" ]] || { echo "[ERROR] jar 을 찾을 수 없습니다." >&2; exit 1; }

# 프론트가 실제로 jar 안에 들어갔는지 확인한다. 이게 빠지면 배포 후 흰 화면이 뜬다.
if ! "$JAVA_HOME/bin/jar" tf "$JAR" | grep -q '^BOOT-INF/classes/static/index.html$'; then
  echo "[ERROR] jar 안에 프론트(static/index.html)가 없습니다." >&2
  exit 1
fi

echo
echo "[DONE] $JAR"
echo "       로컬 확인:  java -jar $JAR --spring.profiles.active=dev  → http://localhost:8080"