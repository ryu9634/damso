#!/usr/bin/env bash
#
# AWS 운영 서버의 데이터를 로컬로 내려받는다. (집 PC 이전 1단계)
#
#   ./deploy/export-from-aws.sh              # 연습: 서비스를 멈추지 않고 뽑는다
#   ./deploy/export-from-aws.sh --cutover    # 실제 이전: 서비스를 멈추고 뽑는다 (멈춘 채로 둔다)
#
# 먼저 --cutover 없이 한 번 돌려서 전 과정을 확인하고, 데이터 대조까지 끝난 뒤에
# 손님이 없는 시간에 --cutover 로 다시 돌린다. --cutover 이후 옛 서버에 들어온 글은 넘어오지 않는다.
#
# 서버에 인바운드 포트를 열지 않는다. SSM 터널 위로 SSH 를 얹는다 —
# 공유기도 보안그룹도 건드리지 않으며, 22번을 열어 둘 필요도 없다.
#
# 필요한 것:
#   · AWS CLI + 자격증명 (ssm:StartSession 권한). aws sts get-caller-identity 로 확인.
#   · Session Manager 플러그인:  brew install --cask session-manager-plugin
#   · EC2 키페어 .pem — ~/.ssh/damso*.pem 를 알아서 찾는다 (KEY_FILE 로 직접 지정도 가능).
#     이 키가 서버에 등록돼 있지 않아도 된다. SSH 가 막히면 물어본 뒤 SSM 으로 공개키를
#     등록하고 다시 붙는다 — 22번을 열지 않고 SSM 권한만으로 된다.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

INSTANCE_ID="${INSTANCE_ID:-}"
AWS_REGION="${AWS_REGION:-ap-northeast-2}"
SSH_USER="${SSH_USER:-ubuntu}"
KEY_FILE="${KEY_FILE:-}"
OUT_ROOT="${OUT_ROOT:-$HOME/damso-migrate}"

CUTOVER="no"
for arg in "$@"; do
  case "$arg" in
    --cutover) CUTOVER="yes" ;;
    -h|--help) sed -n '2,25p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "모르는 옵션: $arg  (--cutover 만 받습니다)" >&2; exit 1 ;;
  esac
done

die() { echo "[ERROR] $*" >&2; exit 1; }
log() { echo "[로컬] $*"; }

# ── 준비물 확인 ───────────────────────────────────────────
command -v aws >/dev/null || die "AWS CLI 가 필요합니다."
command -v session-manager-plugin >/dev/null \
  || die "Session Manager 플러그인이 필요합니다:  brew install --cask session-manager-plugin"
command -v ssh >/dev/null || die "ssh 가 필요합니다."

if [[ -z "$INSTANCE_ID" ]]; then
  echo "INSTANCE_ID 를 알려 주세요. GitHub 의 AWS_INSTANCE_ID 시크릿과 같은 값입니다."
  echo "  예)  INSTANCE_ID=i-0123456789abcdef0 $0 ${1:-}"
  # 계정에 인스턴스가 하나뿐이면 찾아 준다.
  FOUND=$(aws ec2 describe-instances --region "$AWS_REGION" \
            --filters 'Name=instance-state-name,Values=running' \
            --query 'Reservations[].Instances[].InstanceId' --output text 2>/dev/null || true)
  [[ -n "$FOUND" ]] && echo "  현재 실행 중인 인스턴스: $FOUND"
  exit 1
fi

if [[ -z "$KEY_FILE" ]]; then
  KEY_FILE="$(find "$HOME/.ssh" -maxdepth 1 -name 'damso*.pem' 2>/dev/null | sort | head -1)"
fi
[[ -n "$KEY_FILE" && -f "$KEY_FILE" ]] \
  || die "EC2 키 파일(.pem)을 찾을 수 없습니다. KEY_FILE=~/.ssh/damso-xxx.pem 로 지정하세요."
chmod 600 "$KEY_FILE" 2>/dev/null || true

aws sts get-caller-identity >/dev/null 2>&1 \
  || die "AWS 자격증명이 없습니다. aws configure 로 설정하세요."

STATE=$(aws ssm describe-instance-information --region "$AWS_REGION" \
          --filters "Key=InstanceIds,Values=$INSTANCE_ID" \
          --query 'InstanceInformationList[0].PingStatus' --output text 2>/dev/null || echo None)
[[ "$STATE" == "Online" ]] \
  || die "SSM 에서 인스턴스가 보이지 않습니다 (상태: $STATE). 인스턴스가 켜져 있는지 확인하세요."

# ── SSH (SSM 터널 경유) ───────────────────────────────────
PROXY="aws ssm start-session --target %h --document-name AWS-StartSSHSession --parameters portNumber=%p --region $AWS_REGION"
SSH_OPTS=(
  -i "$KEY_FILE"
  -o "ProxyCommand=$PROXY"
  -o StrictHostKeyChecking=accept-new
  -o UserKnownHostsFile="$HOME/.ssh/known_hosts"
  -o ConnectTimeout=30
)

STAMP="$(date +%Y%m%d-%H%M%S)"
OUT_DIR="$OUT_ROOT/$STAMP"
mkdir -p "$OUT_DIR"

# 셸 줄 여러 개를 SSM 이 받는 JSON 파라미터 파일로 만든다.
# 손으로 JSON 을 조립하면 공개키에 든 +, /, = 에서 깨진다. 인용은 python 에 맡긴다.
ssm_params_file() {
  local out="$1"; shift
  # python3 -c 에서는 sys.argv[0] 이 '-c' 다. 경로는 [1], 명령들은 [2:] 다.
  python3 -c 'import json,sys; json.dump({"commands": sys.argv[2:]}, open(sys.argv[1], "w"))' \
    "$out" "$@"
}

# SSM 으로 명령을 보내고 끝날 때까지 기다린다. (deploy.yml 의 대기 로직과 같은 방식)
ssm_run() {
  local params_file="$1" cmd_id status
  cmd_id=$(aws ssm send-command --region "$AWS_REGION" --instance-ids "$INSTANCE_ID" \
             --document-name AWS-RunShellScript --timeout-seconds 300 \
             --parameters "file://$params_file" --query 'Command.CommandId' --output text)
  status=Pending
  for _ in $(seq 1 30); do
    status=$(aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$cmd_id" \
               --instance-id "$INSTANCE_ID" --query Status --output text 2>/dev/null || echo Pending)
    case "$status" in Success|Failed|Cancelled|TimedOut) break ;; esac
    sleep 3
  done
  aws ssm get-command-invocation --region "$AWS_REGION" --command-id "$cmd_id" \
    --instance-id "$INSTANCE_ID" --query StandardOutputContent --output text || true
  [[ "$status" == "Success" ]]
}

# 이 .pem 이 서버의 authorized_keys 에 들어 있지 않을 수 있다 (인바운드 22번을 한 번도
# 열지 않고 SSM 으로만 운영해 왔다면 그렇다). 그럴 때는 SSM 으로 공개키를 넣고 다시 붙는다 —
# SSM 권한만으로 되므로 보안그룹도 키페어도 새로 만들지 않는다.
log "서버에 접속합니다 ($INSTANCE_ID, SSM 터널)"
if ! ssh "${SSH_OPTS[@]}" "$SSH_USER@$INSTANCE_ID" 'hostname' 2>/dev/null; then
  echo
  log "SSH 로 붙지 못했습니다 — 이 키가 서버에 등록되지 않은 것 같습니다."
  echo "    키 파일: $KEY_FILE"
  echo "    SSM 으로 이 키의 공개키를 $SSH_USER 계정에 등록하고 다시 시도할 수 있습니다."
  echo "    (인바운드 포트는 열지 않습니다. SSM 터널 위로만 붙습니다)"
  read -rp "    등록할까요? [y/N] " ans
  [[ "$ans" == "y" || "$ans" == "Y" ]] || die "중단합니다."

  PUBKEY="$(ssh-keygen -y -f "$KEY_FILE")" \
    || die "공개키를 뽑지 못했습니다. $KEY_FILE 이 올바른 비밀키인지 확인하세요."

  log "공개키 등록 (SSM)"
  AK="/home/$SSH_USER/.ssh/authorized_keys"
  PARAMS="$OUT_ROOT/.ssm-installkey.json"
  mkdir -p "$OUT_ROOT"
  # 이미 있으면 덧붙이지 않는다 — 여러 번 실행해도 결과가 같다.
  ssm_params_file "$PARAMS" \
    'set -e' \
    "install -d -m 700 -o $SSH_USER -g $SSH_USER /home/$SSH_USER/.ssh" \
    "touch $AK" \
    "grep -qxF '$PUBKEY' $AK || printf '%s\n' '$PUBKEY' >> $AK" \
    "chmod 600 $AK" \
    "chown $SSH_USER:$SSH_USER $AK" \
    'echo 등록완료'
  ssm_run "$PARAMS" || die "공개키 등록에 실패했습니다. 위 출력을 확인하세요."
  rm -f "$PARAMS"

  log "다시 접속합니다"
  ssh "${SSH_OPTS[@]}" "$SSH_USER@$INSTANCE_ID" 'hostname' \
    || die "그래도 붙지 못했습니다. SSH_USER($SSH_USER) 가 맞는지 확인하세요 (Ubuntu 는 ubuntu, Amazon Linux 는 ec2-user)."
fi
log "접속 확인"

if [[ "$CUTOVER" == "yes" ]]; then
  echo
  echo "  ⚠ --cutover: 옛 서버의 담소 서비스를 멈춘 뒤 데이터를 뽑습니다."
  echo "    이 시점 이후 옛 서버로 들어온 신청·지목은 로컬로 넘어오지 않습니다."
  read -rp "    계속할까요? [y/N] " ans
  [[ "$ans" == "y" || "$ans" == "Y" ]] || exit 1
  STOP_FLAG="--stop"
else
  log "연습 모드 — 서비스는 계속 돌아갑니다 (사진과 DB 가 미세하게 어긋날 수 있습니다)"
  STOP_FLAG=""
fi

log "서버에서 내보내기 실행"
ssh "${SSH_OPTS[@]}" "$SSH_USER@$INSTANCE_ID" \
  "sudo bash -s $STAMP $STOP_FLAG" < "$SCRIPT_DIR/aws-export-bundle.sh"

REMOTE_BUNDLE="/tmp/damso-export-$STAMP.tgz"
log "번들 내려받기"
scp "${SSH_OPTS[@]}" "$SSH_USER@$INSTANCE_ID:$REMOTE_BUNDLE" "$OUT_DIR/bundle.tgz"

log "서버의 임시 번들 삭제 (암호화 키가 들어 있으므로 남겨 두지 않는다)"
ssh "${SSH_OPTS[@]}" "$SSH_USER@$INSTANCE_ID" "rm -f $REMOTE_BUNDLE"

# ── 펼치고 확인 ───────────────────────────────────────────
tar -xzf "$OUT_DIR/bundle.tgz" -C "$OUT_DIR"
rm -f "$OUT_DIR/bundle.tgz"

[[ -s "$OUT_DIR/damso.key" ]] || die "번들에 암호화 키가 없습니다. 중단합니다."
[[ -f "$OUT_DIR/db/counts.txt" ]] || die "번들에 DB CSV 가 없습니다. 중단합니다."
chmod 600 "$OUT_DIR/damso.key"

echo
echo "────────────────────────────────────────────────"
echo " 내려받기 완료: $OUT_DIR"
echo "────────────────────────────────────────────────"
cat "$OUT_DIR/db/counts.txt"
echo
echo " 사진 $(find "$OUT_DIR/uploads" -type f 2>/dev/null | wc -l | tr -d ' ')장"
echo
echo " 다음:  ./deploy/migrate-postgres-to-h2.sh $OUT_DIR"
