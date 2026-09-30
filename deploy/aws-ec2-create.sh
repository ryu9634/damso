#!/usr/bin/env bash
#
# 담소 매칭 시스템 — AWS EC2 인스턴스 생성 (로컬 맥에서 실행)
#
#   ./deploy/aws-ec2-create.sh
#
# 하는 일: 기본 VPC 확인 → SSH 키페어 → 보안그룹 → Ubuntu AMI 조회 → 인스턴스 기동 → 탄력적 IP 연결.
# 여러 번 실행해도 안전하다(멱등). 이미 만들어진 것은 그대로 두고 다음 단계로 넘어간다.
#
# 이 스크립트는 서버 안을 구성하지 않는다. 인스턴스가 뜬 뒤 deploy/provision.sh 를 서버에서 실행한다.
set -euo pipefail

# ── 설정 ──────────────────────────────────────────────────
REGION="${AWS_REGION:-ap-northeast-2}"        # 서울
NAME="${NAME:-damso}"                          # 태그·키페어·보안그룹 이름의 접두사
INSTANCE_TYPE="${INSTANCE_TYPE:-t4g.small}"    # 2 vCPU / 2GB, ARM(Graviton). x86 을 쓰려면 t3.small + ARCH=amd64
ARCH="${ARCH:-arm64}"                          # arm64 | amd64 — INSTANCE_TYPE 과 반드시 맞아야 한다
VOLUME_SIZE="${VOLUME_SIZE:-30}"               # GB, gp3
KEY_NAME="${KEY_NAME:-$NAME-key}"                # 기존 키페어를 재사용하려면 이름을 넘긴다
KEY_FILE="${KEY_FILE:-$HOME/.ssh/${KEY_NAME}.pem}"
PROFILE_NAME="${PROFILE_NAME:-$NAME-ssm}"      # Session Manager 용 IAM 인스턴스 프로필
# 프로필은 AWS_PROFILE 환경변수를 CLI 가 직접 읽는다. --profile 을 따로 넘기지 않는다.
# (macOS 기본 bash 3.2 에서는 빈 배열 확장이 set -u 에 걸려 터진다)
aws_() { aws --region "$REGION" "$@"; }
log()  { echo -e "\n[$(date +%H:%M:%S)] $*"; }

# ── 0. 자격증명 확인 ──────────────────────────────────────
log "AWS 자격증명 확인"
if ! ACCOUNT=$(aws_ sts get-caller-identity --query Account --output text 2>/dev/null); then
  cat >&2 <<'MSG'
AWS 자격증명이 없다. 먼저 아래 중 하나를 실행할 것:

  aws configure            # 액세스 키 입력 (IAM 사용자)
  aws configure sso        # IAM Identity Center 를 쓴다면

프로필을 나눠 쓴다면 AWS_PROFILE=이름 을 앞에 붙여 이 스크립트를 실행한다.
MSG
  exit 1
fi
echo "  계정 $ACCOUNT / 리전 $REGION"

# ── 1. 기본 VPC ───────────────────────────────────────────
log "기본 VPC 확인"
VPC_ID=$(aws_ ec2 describe-vpcs --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text)
if [[ "$VPC_ID" == "None" || -z "$VPC_ID" ]]; then
  echo "  기본 VPC 가 없다. 만들고 다시 실행할 것:  aws ec2 create-default-vpc --region $REGION" >&2
  exit 1
fi
SUBNET_ID=$(aws_ ec2 describe-subnets --filters Name=vpc-id,Values="$VPC_ID" Name=default-for-az,Values=true \
  --query 'Subnets[0].SubnetId' --output text)
echo "  VPC $VPC_ID / 서브넷 $SUBNET_ID"

# ── 2. SSH 키페어 ─────────────────────────────────────────
# 개인키는 생성 시점에 단 한 번만 받을 수 있다. 로컬에 파일이 없는데 AWS 쪽에만 있으면 접속할 방법이 없으므로,
# 그 경우 이름을 바꿔 다시 만들도록 안내한다.
log "SSH 키페어 준비: $KEY_NAME"
KEY_EXISTS=$(aws_ ec2 describe-key-pairs --key-names "$KEY_NAME" --query 'KeyPairs[0].KeyName' --output text 2>/dev/null || echo "")
if [[ -z "$KEY_EXISTS" || "$KEY_EXISTS" == "None" ]]; then
  mkdir -p "$(dirname "$KEY_FILE")"
  aws_ ec2 create-key-pair --key-name "$KEY_NAME" --key-type ed25519 \
    --query 'KeyMaterial' --output text > "$KEY_FILE"
  chmod 400 "$KEY_FILE"
  echo "  키페어 생성 → $KEY_FILE (이 파일을 잃으면 서버에 못 들어간다. 백업할 것)"
elif [[ ! -f "$KEY_FILE" ]]; then
  echo "  [중단] AWS 에 키페어 '$KEY_NAME' 는 있는데 로컬에 $KEY_FILE 이 없다." >&2
  echo "         개인키가 다른 곳에 있으면 KEY_FILE=경로 로 알려주고, 잃었으면 KEY_NAME=새이름 으로 다시 실행한다." >&2
  exit 1
else
  echo "  이미 있음 — 그대로 사용"
fi

# ── 3. 보안그룹 ───────────────────────────────────────────
# 22 는 지금 이 회선에서만, 80/443 은 전체 공개. SSH 를 전체 공개하면 즉시 무차별 대입 시도가 붙는다.
log "보안그룹 준비: $NAME-sg"
SG_ID=$(aws_ ec2 describe-security-groups --filters Name=group-name,Values="$NAME-sg" Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || echo "None")
if [[ "$SG_ID" == "None" || -z "$SG_ID" ]]; then
  SG_ID=$(aws_ ec2 create-security-group --group-name "$NAME-sg" --vpc-id "$VPC_ID" \
    --description "damso web server" --query 'GroupId' --output text)
  echo "  생성 $SG_ID"
else
  echo "  이미 있음 $SG_ID"
fi

MY_IP=$(curl -s --max-time 10 https://checkip.amazonaws.com || echo "")
[[ -z "$MY_IP" ]] && { echo "  현재 공인 IP 를 확인하지 못했다." >&2; exit 1; }
echo "  현재 회선 IP: $MY_IP"

allow() { # 포트, CIDR, 설명
  aws_ ec2 authorize-security-group-ingress --group-id "$SG_ID" \
    --ip-permissions "IpProtocol=tcp,FromPort=$1,ToPort=$1,IpRanges=[{CidrIp=$2,Description=$3}]" \
    >/dev/null 2>&1 && echo "  허용 $1 ← $2" || echo "  이미 허용됨 $1 ← $2"
}
# 배포는 SSM 으로 하므로 22번은 없어도 된다. 필요하면 OPEN_SSH=yes 로 켠다.
if [[ "${OPEN_SSH:-no}" == "yes" ]]; then allow 22 "$MY_IP/32" ssh-from-here; fi
allow 80  0.0.0.0/0   http
allow 443 0.0.0.0/0   https

# ── 3-a. Session Manager 용 IAM 인스턴스 프로필 ───────────
# 22번이 막힌 회선(사무실 방화벽 등)에서도 셸을 받으려면 필요하다. SSM 은 443 으로 나가는
# 아웃바운드 연결만 쓰므로 인바운드를 하나도 열지 않고도 접속된다.
#   aws ssm start-session --target <인스턴스ID>
log "IAM 인스턴스 프로필 준비: $PROFILE_NAME"
if ! aws iam get-instance-profile --instance-profile-name "$PROFILE_NAME" >/dev/null 2>&1; then
  aws iam create-role --role-name "$PROFILE_NAME" \
    --assume-role-policy-document '{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"Service":"ec2.amazonaws.com"},"Action":"sts:AssumeRole"}]}' \
    >/dev/null
  aws iam attach-role-policy --role-name "$PROFILE_NAME" \
    --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
  aws iam create-instance-profile --instance-profile-name "$PROFILE_NAME" >/dev/null
  aws iam add-role-to-instance-profile \
    --instance-profile-name "$PROFILE_NAME" --role-name "$PROFILE_NAME"
  echo "  생성 — IAM 전파에 몇 초 걸린다"
else
  echo "  이미 있음 — 그대로 사용"
fi

# ── 4. AMI ────────────────────────────────────────────────
# 하드코딩하면 금방 낡는다. Canonical 이 관리하는 SSM 파라미터에서 최신 Ubuntu 24.04 를 받아온다.
log "Ubuntu 24.04 AMI 조회 ($ARCH)"
AMI_ID=$(aws_ ssm get-parameters \
  --names "/aws/service/canonical/ubuntu/server/24.04/stable/current/$ARCH/hvm/ebs-gp3/ami-id" \
  --query 'Parameters[0].Value' --output text)
[[ "$AMI_ID" == ami-* ]] || { echo "  AMI 조회 실패: $AMI_ID" >&2; exit 1; }
echo "  $AMI_ID"

# ── 5. 인스턴스 ───────────────────────────────────────────
log "인스턴스 확인/생성: $NAME"
INSTANCE_ID=$(aws_ ec2 describe-instances \
  --filters Name=tag:Name,Values="$NAME" "Name=instance-state-name,Values=pending,running,stopped" \
  --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || echo "None")

if [[ "$INSTANCE_ID" == "None" || -z "$INSTANCE_ID" ]]; then
  # 방금 만든 인스턴스 프로필은 잠깐 동안 "존재하지 않는다"는 응답이 온다(IAM 은 최종적 일관성).
  # 여기서 실패하면 프로필 없이 뜬 인스턴스를 나중에 손보게 되므로, 붙을 때까지 몇 번 다시 시도한다.
  for attempt in 1 2 3 4 5 6; do
    if INSTANCE_ID=$(aws_ ec2 run-instances \
    --image-id "$AMI_ID" \
    --instance-type "$INSTANCE_TYPE" \
    --key-name "$KEY_NAME" \
    --subnet-id "$SUBNET_ID" \
    --security-group-ids "$SG_ID" \
    --block-device-mappings "DeviceName=/dev/sda1,Ebs={VolumeSize=$VOLUME_SIZE,VolumeType=gp3,Encrypted=true,DeleteOnTermination=true}" \
    --metadata-options "HttpTokens=required,HttpEndpoint=enabled" \
    --iam-instance-profile "Name=$PROFILE_NAME" \
    --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=$NAME},{Key=Project,Value=damso}]" \
    --query 'Instances[0].InstanceId' --output text 2>/dev/null); then
      break
    fi
    echo "  IAM 프로필 전파 대기 ($attempt/6)"
    sleep 10
  done
  [[ -n "${INSTANCE_ID:-}" && "$INSTANCE_ID" != "None" ]] || { echo "  인스턴스 생성 실패" >&2; exit 1; }
  echo "  생성 $INSTANCE_ID — 기동 대기"
  aws_ ec2 wait instance-running --instance-ids "$INSTANCE_ID"
else
  echo "  이미 있음 $INSTANCE_ID"
  STATE=$(aws_ ec2 describe-instances --instance-ids "$INSTANCE_ID" \
    --query 'Reservations[0].Instances[0].State.Name' --output text)
  if [[ "$STATE" == "stopped" ]]; then
    echo "  중지 상태 — 다시 시작한다"
    aws_ ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
    aws_ ec2 wait instance-running --instance-ids "$INSTANCE_ID"
  fi
fi

# ── 6. 탄력적 IP ──────────────────────────────────────────
# 재부팅·중지 후에도 IP 가 바뀌지 않게 고정한다. DNS 와 카카오/네이버 콜백 URI 를 다시 손대지 않으려면 필요하다.
# (퍼블릭 IPv4 는 탄력적 IP 든 자동 할당이든 동일하게 과금된다 — 고정한다고 비용이 늘지 않는다)
log "탄력적 IP 연결"
EIP_ALLOC=$(aws_ ec2 describe-addresses --filters Name=tag:Name,Values="$NAME" \
  --query 'Addresses[0].AllocationId' --output text 2>/dev/null || echo "None")
if [[ "$EIP_ALLOC" == "None" || -z "$EIP_ALLOC" ]]; then
  EIP_ALLOC=$(aws_ ec2 allocate-address --domain vpc \
    --tag-specifications "ResourceType=elastic-ip,Tags=[{Key=Name,Value=$NAME}]" \
    --query 'AllocationId' --output text)
  echo "  할당 $EIP_ALLOC"
fi
aws_ ec2 associate-address --instance-id "$INSTANCE_ID" --allocation-id "$EIP_ALLOC" >/dev/null
PUBLIC_IP=$(aws_ ec2 describe-addresses --allocation-ids "$EIP_ALLOC" --query 'Addresses[0].PublicIp' --output text)

# ── 안내 ──────────────────────────────────────────────────
cat <<EOF

========================================================
 EC2 준비 완료
========================================================
 인스턴스   $INSTANCE_ID ($INSTANCE_TYPE, $ARCH)
 퍼블릭 IP  $PUBLIC_IP

 접속 방법 두 가지 — 어느 쪽이든 같은 서버다.
   SSH        ssh -i $KEY_FILE ubuntu@$PUBLIC_IP
   SSM(443)   aws ssm start-session --target $INSTANCE_ID --region $REGION
              회선이 22번을 막고 있을 때 쓴다. 로컬에 플러그인이 필요하다:
              brew install --cask session-manager-plugin
              접속하면 ssm-user 로 들어간다. sudo su - ubuntu 로 옮겨 가면 아래 명령을 그대로 쓸 수 있다.

 남은 순서

 1) 서버 구성 (부팅 후 접속 가능해질 때까지 1~2분 걸릴 수 있다)
      sudo apt-get update && sudo apt-get install -y git
      git clone https://github.com/ryu9634/damso.git && cd damso
      sudo ./deploy/provision.sh $PUBLIC_IP

    도메인을 붙인 뒤에는 도메인을 인자로 넣어 다시 실행한다(멱등).

 2) 배포용 키 등록 → GitHub Secrets (docs/DEPLOY.md 2번 항목)
      SSH_HOST=$PUBLIC_IP  SSH_USER=deploy  SSH_KEY=개인키 전문
    GitHub Actions 는 GitHub 쪽 회선에서 접속하므로 로컬의 22번 차단과 무관하다.

 3) 첫 배포 후 http://$PUBLIC_IP 확인, /admin/login 비밀번호 변경

 정지(과금 중단)      aws ec2 stop-instances --instance-ids $INSTANCE_ID --region $REGION
 회선 IP 가 바뀌었을 때 이 스크립트를 다시 실행하면 SSH 규칙이 추가된다.
========================================================
EOF
