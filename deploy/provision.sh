#!/usr/bin/env bash
#
# 담소 매칭 시스템 — 서버 최초 구성 (Ubuntu 22.04 / 24.04, root 권한 필요)
#
#   sudo ./provision.sh damso.example.com
#
# 하는 일: 패키지 설치 → 계정/디렉터리 → PostgreSQL DB 생성 → 환경파일 → systemd → nginx → 방화벽.
# 여러 번 실행해도 안전하다(멱등). 이미 만들어진 것은 건드리지 않는다.
#
# 이 스크립트는 TLS 인증서까지는 발급하지 않는다. 도메인의 A 레코드가 이 서버를 가리키는 것을
# 확인한 뒤 마지막 안내대로 certbot 을 실행하면 된다. (DNS 전파 전에 실행하면 실패한다)
set -euo pipefail

DOMAIN="${1:-}"
if [[ -z "$DOMAIN" ]]; then
  echo "사용법: sudo $0 <도메인>   예) sudo $0 damso.example.com" >&2
  exit 1
fi
[[ $EUID -eq 0 ]] || { echo "root 로 실행하세요: sudo $0 $DOMAIN" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_USER=damso            # 애플리케이션 실행 계정 (로그인 불가)
DEPLOY_USER=deploy        # GitHub Actions 가 SSH 로 접속할 계정
APP_HOME=/opt/damso       # 릴리스
DATA_HOME=/var/lib/damso  # 업로드 사진 — 배포와 무관하게 살아남아야 한다
BACKUP_HOME=/var/backups/damso

log() { echo -e "\n[$(date +%H:%M:%S)] $*"; }

# ── 1. 패키지 ─────────────────────────────────────────────
log "패키지 설치"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq \
  openjdk-21-jre-headless postgresql postgresql-client \
  nginx certbot python3-certbot-nginx rsync ufw curl unzip

# AWS CLI — 배포 산출물(jar)을 S3 에서 내려받는 데 쓴다. 우분투 저장소에는 arm64 패키지가 없어
# AWS 가 배포하는 zip 을 직접 설치한다. 이미 있으면 건너뛴다.
if ! command -v aws >/dev/null 2>&1; then
  log "AWS CLI 설치"
  case "$(uname -m)" in
    aarch64) AWSCLI_URL=https://awscli.amazonaws.com/awscli-exe-linux-aarch64.zip ;;
    x86_64)  AWSCLI_URL=https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip ;;
    *) echo "  지원하지 않는 아키텍처: $(uname -m)" >&2; exit 1 ;;
  esac
  tmp=$(mktemp -d)
  curl -fsSL "$AWSCLI_URL" -o "$tmp/awscliv2.zip"
  unzip -q "$tmp/awscliv2.zip" -d "$tmp"
  "$tmp/aws/install" >/dev/null
  rm -rf "$tmp"
fi

# ── 2. 계정과 디렉터리 ────────────────────────────────────
log "계정 · 디렉터리 준비"
id -u "$APP_USER"    &>/dev/null || useradd --system --no-create-home --shell /usr/sbin/nologin "$APP_USER"
id -u "$DEPLOY_USER" &>/dev/null || useradd --create-home --shell /bin/bash "$DEPLOY_USER"
# 배포 실패 시 deploy.sh 가 journalctl 로 원인을 찍는다. 그룹이 없으면 아무것도 안 보인다.
usermod -aG systemd-journal "$DEPLOY_USER"

install -d -o "$DEPLOY_USER" -g "$APP_USER" -m 755 "$APP_HOME" "$APP_HOME/releases" "$APP_HOME/bin"
install -d -o "$APP_USER"    -g "$APP_USER" -m 755 "$DATA_HOME" "$DATA_HOME/uploads"
install -d -o root           -g root        -m 750 /etc/damso
install -d -o "$APP_USER"    -g "$APP_USER" -m 750 "$BACKUP_HOME"

install -d -o "$DEPLOY_USER" -g "$DEPLOY_USER" -m 700 "/home/$DEPLOY_USER/.ssh"
touch "/home/$DEPLOY_USER/.ssh/authorized_keys"
chown "$DEPLOY_USER:$DEPLOY_USER" "/home/$DEPLOY_USER/.ssh/authorized_keys"
chmod 600 "/home/$DEPLOY_USER/.ssh/authorized_keys"

# ── 3. PostgreSQL ─────────────────────────────────────────
# 외부에 포트를 열지 않는다. 앱과 DB 가 같은 서버에 있으므로 127.0.0.1 로만 접속한다.
log "PostgreSQL 준비"
systemctl enable --now postgresql

DB_NAME=damso
DB_USER=damso
DB_PASSWORD=""

role_exists=$(sudo -u postgres psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='$DB_USER'")
if [[ "$role_exists" != "1" ]]; then
  DB_PASSWORD="$(openssl rand -base64 32 | tr -d '/+=' | head -c 32)"
  sudo -u postgres psql -qc "CREATE ROLE $DB_USER LOGIN PASSWORD '$DB_PASSWORD'"
  echo "  DB 역할 $DB_USER 생성 (비밀번호 자동 생성)"
else
  echo "  DB 역할 $DB_USER 이미 있음 — 비밀번호는 그대로 둔다"
fi

db_exists=$(sudo -u postgres psql -tAc "SELECT 1 FROM pg_database WHERE datname='$DB_NAME'")
if [[ "$db_exists" != "1" ]]; then
  sudo -u postgres createdb -O "$DB_USER" -E UTF8 "$DB_NAME"
  echo "  DB $DB_NAME 생성"
else
  echo "  DB $DB_NAME 이미 있음"
fi

# ── 4. 환경 파일 ──────────────────────────────────────────
ENV_FILE=/etc/damso/damso.env
if [[ ! -f "$ENV_FILE" ]]; then
  log "환경 파일 생성: $ENV_FILE"
  install -m 640 -o root -g "$APP_USER" "$SCRIPT_DIR/damso.env.example" "$ENV_FILE"
  [[ -n "$DB_PASSWORD" ]] && sed -i "s|^DB_PASSWORD=.*|DB_PASSWORD=$DB_PASSWORD|" "$ENV_FILE"
  sed -i "s|https://example.com|https://$DOMAIN|g" "$ENV_FILE"
else
  log "환경 파일 이미 있음 — 건드리지 않음: $ENV_FILE"
  if [[ -n "$DB_PASSWORD" ]]; then
    echo "  [주의] DB 비밀번호를 새로 만들었는데 환경 파일은 그대로다. 아래 값을 직접 넣어야 한다:"
    echo "         DB_PASSWORD=$DB_PASSWORD"
  fi
fi

# ── 5. systemd ────────────────────────────────────────────
log "systemd 유닛 설치"
install -m 644 "$SCRIPT_DIR/damso.service" /etc/systemd/system/damso.service
install -m 755 -o "$DEPLOY_USER" -g "$DEPLOY_USER" "$SCRIPT_DIR/deploy.sh" "$APP_HOME/bin/deploy.sh"
install -m 755 "$SCRIPT_DIR/backup.sh" "$APP_HOME/bin/backup.sh"
systemctl daemon-reload
systemctl enable damso   # 아직 start 하지 않는다. jar 이 배포된 뒤 deploy.sh 가 띄운다.

# 배포 계정이 이 서비스만 비밀번호 없이 제어할 수 있게 한다. 그 외 sudo 권한은 주지 않는다.
# 경로는 sudo 가 실제로 실행하는 것과 문자 그대로 일치해야 한다. 배포판마다 /usr/bin 과 /bin 이
# 갈리므로 둘 다 적는다. (Ubuntu 는 /bin 이 /usr/bin 심볼릭 링크지만 sudoers 는 경로 문자열로 비교한다)
{
  printf '%s ALL=(root) NOPASSWD:' "$DEPLOY_USER"
  sep=""
  for base in /usr/bin/systemctl /bin/systemctl; do
    for cmd in "restart damso" "start damso" "stop damso" "is-active damso" "status damso"; do
      printf '%s %s %s' "$sep" "$base" "$cmd"
      sep=","
    done
  done
  printf '\n'
} > /etc/sudoers.d/damso-deploy
chmod 440 /etc/sudoers.d/damso-deploy
visudo -cf /etc/sudoers.d/damso-deploy >/dev/null

# ── 6. nginx ──────────────────────────────────────────────
log "nginx 설정"
sed "s/DOMAIN_PLACEHOLDER/$DOMAIN/" "$SCRIPT_DIR/nginx-damso.conf" > /etc/nginx/sites-available/damso
ln -sfn /etc/nginx/sites-available/damso /etc/nginx/sites-enabled/damso
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

# ── 7. 백업 cron ──────────────────────────────────────────
log "백업 스케줄 등록 (매일 04:10)"
cat > /etc/cron.d/damso-backup <<EOF
10 4 * * * $APP_USER /opt/damso/bin/backup.sh >> /var/log/damso-backup.log 2>&1
EOF
chmod 644 /etc/cron.d/damso-backup

# ── 8. 방화벽 ─────────────────────────────────────────────
# SSH 를 먼저 허용하고 나서 켠다. 순서가 바뀌면 접속이 끊긴다.
log "방화벽 설정"
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw --force enable
ufw status verbose

cat <<EOF

========================================================
 서버 구성 완료
========================================================
 남은 순서

 1) 배포용 SSH 공개키 등록
    로컬에서:  ssh-keygen -t ed25519 -f ~/.ssh/damso_deploy -C "github-actions"
    공개키(~/.ssh/damso_deploy.pub) 내용을 서버의
      /home/$DEPLOY_USER/.ssh/authorized_keys
    에 추가한다. 개인키는 GitHub Secrets 의 SSH_KEY 에 넣는다.

 2) $DOMAIN 의 A 레코드가 이 서버를 가리키는지 확인한 뒤 TLS 발급
      sudo certbot --nginx -d $DOMAIN

 3) 간편로그인을 쓴다면 $ENV_FILE 에 카카오/네이버 키를 채우고
      sudo systemctl restart damso

 4) 첫 배포 (로컬 저장소에서)
      ./scripts/build.sh
      scp target/match-0.0.1-SNAPSHOT.jar $DEPLOY_USER@$DOMAIN:/tmp/match.jar
      ssh $DEPLOY_USER@$DOMAIN '/opt/damso/bin/deploy.sh /tmp/match.jar manual-\$(date +%Y%m%d%H%M)'

 5) 관리자 비밀번호 변경 (기본값 admin/admin 으로 열려 있다)
      https://$DOMAIN/admin/login
========================================================
EOF