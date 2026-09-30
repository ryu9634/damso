# 배포 가이드

VPS 1대 + PostgreSQL + nginx 구성. `main` 에 push 하면 GitHub Actions 가 빌드해서 배포한다.

> 집 윈도우 PC + H2 + Cloudflare Tunnel 로 옮기는 절차는 [LOCAL_SERVER.md](LOCAL_SERVER.md) 에 있다.
> 이 문서는 그대로 남겨 둔다 — 집 PC 구성이 안 맞을 때 되돌아갈 곳이다.

```
git push (main)
  → GitHub Actions: frontend 에서 npm run build → 그 dist/ 를 jar 안 static/ 으로
  → backend 에서 mvn package
  → OIDC 로 AWS 로그인 → jar 을 S3 에 업로드
  → SSM 으로 서버에 배포 명령 전송 → 서버가 S3 에서 jar 을 내려받음
  → current 심볼릭 링크 교체 → systemctl restart damso
  → 헬스체크(/api/health) → 실패하면 이전 릴리스로 롤백
```

**SSH 를 쓰지 않는다.** 서버는 80/443 만 열려 있고 22번은 닫혀 있다. SSM 은 서버가 밖으로 거는
연결만 쓰므로 인바운드를 열 필요가 없고, GitHub 에 만료 없는 자격증명을 두지 않아도 된다.

배포 단위는 **jar 파일 하나**다. 프론트(React)가 그 안에 들어 있어 nginx 는 프록시만 한다.

## 서버 디렉터리

| 경로 | 내용 | 배포 시 |
|---|---|---|
| `/opt/damso/releases/<릴리스>/match.jar` | 릴리스별 jar | 새로 추가 (최근 5개 유지) |
| `/opt/damso/current` | 현재 릴리스 심볼릭 링크 | 교체됨 |
| `/opt/damso/bin/` | `deploy.sh`, `backup.sh` | 그대로 |
| `/var/lib/damso/uploads` | 업로드된 사진 | **유지** |
| `/etc/damso/damso.env` | DB 비밀번호·API 키 (600) | **유지** |
| `/var/lib/damso/damso.key` | 개인정보 암호화 키 (600). 잃으면 DB 의 번호·이름을 못 읽는다 | **유지 · 백업 필수** |
| `/var/backups/damso` | 일일 백업 | 유지 |

사진과 자격증명이 `/opt/damso` 바깥에 있는 것이 핵심이다. 릴리스 디렉터리는 언제 지워져도 상관없어야 한다.

---

## 최초 1회 설정

### 1. 서버 구성

Ubuntu 22.04 또는 24.04, 2vCPU / 2~4GB RAM. 도메인 A 레코드를 서버 IP로 미리 걸어 둔다.

```bash
git clone https://github.com/ryu9634/damso.git && cd damso
sudo ./deploy/provision.sh damso.example.com
```

패키지 설치, `damso`/`deploy` 계정, 디렉터리, PostgreSQL DB, systemd 유닛, nginx, 백업 cron, 방화벽까지 한 번에 처리한다. 여러 번 실행해도 안전하다.

### 1-a. AWS EC2 로 서버를 만드는 경우

로컬에서 한 번 실행하면 키페어·보안그룹·인스턴스·탄력적 IP 까지 만들어진다. 멱등이라 다시 돌려도 안전하다.

```bash
aws configure                    # 최초 1회, 자격증명 등록
./deploy/aws-ec2-create.sh       # 기본값: 서울(ap-northeast-2), t4g.small, Ubuntu 24.04, gp3 30GB
```

| 환경변수 | 기본값 | 비고 |
|---|---|---|
| `AWS_REGION` | `ap-northeast-2` | 서울 |
| `INSTANCE_TYPE` | `t4g.small` | 2 vCPU / 2GB. x86 이 필요하면 `INSTANCE_TYPE=t3.small ARCH=amd64` |
| `VOLUME_SIZE` | `30` | GB, gp3 암호화 |
| `AWS_PROFILE` | (없음) | 프로필을 나눠 쓸 때 |

**보안그룹은 22번을 스크립트 실행 시점의 회선 IP 에만 연다.** 카페나 다른 망에서 접속하려면 스크립트를 다시 실행하면 규칙이 추가된다. 80/443 은 전체 공개다.

**퍼블릭 IPv4 는 탄력적 IP 든 자동 할당이든 똑같이 과금된다.** 고정한다고 요금이 늘지 않으므로 처음부터 붙여 둔다 — 나중에 IP 가 바뀌면 DNS 와 카카오/네이버 콜백 URI 를 전부 다시 손봐야 한다.

인스턴스가 뜨면 서버 안에서:

```bash
ssh -i ~/.ssh/damso-ec2.pem ubuntu@<퍼블릭IP>
sudo apt-get update && sudo apt-get install -y git
git clone https://github.com/ryu9634/damso.git && cd damso
sudo ./deploy/provision.sh <퍼블릭IP>      # 도메인이 생기면 도메인으로 다시 실행
```

DB 는 EC2 안의 PostgreSQL 을 쓴다(`provision.sh` 가 구성). RDS 로 분리하지 않았으므로 **인스턴스를 종료(terminate)하면 데이터도 같이 사라진다.** 백업의 원격 보관(`BACKUP_REMOTE`)이 그만큼 중요하다 — S3 버킷 하나면 충분하다.

도메인 없이 IP 로 먼저 띄우는 경우, `/etc/damso/damso.env` 의 카카오/네이버 `REDIRECT_URI` 에는 IP 가 박힌다. 이 값들은 키가 비어 있는 동안 쓰이지 않지만, 도메인을 붙일 때 **`provision.sh` 는 이미 있는 환경 파일을 건드리지 않으므로 직접 고쳐야 한다.**

### 2. 배포 권한 (GitHub → AWS)

`aws-ec2-create.sh` 가 만든 것 외에 세 가지가 더 필요하다. 계정당 한 번만 하면 된다.

1. **S3 버킷** — 빌드된 jar 이 잠깐 머무는 곳. 30일 뒤 자동 삭제되게 수명주기를 걸어 둔다.
2. **GitHub OIDC 공급자** — `token.actions.githubusercontent.com`.
3. **`damso-github-actions` 역할** — 신뢰 정책을 `repo:ryu9634/damso:ref:refs/heads/main` 로 **좁혀 둔다.**
   이 조건이 없으면 누구의 어느 레포든 이 역할을 가져갈 수 있다. 권한은 세 가지뿐이다:
   버킷의 `releases/*` 에 `s3:PutObject`, 그 인스턴스 하나에 `ssm:SendCommand`, 결과 조회.

인스턴스 역할(`damso-ssm`)에는 같은 경로에 대한 `s3:GetObject` 를 준다.

GitHub → Settings → Secrets and variables → Actions 에 등록:

| Secret | 값 |
|---|---|
| `AWS_ROLE_ARN` | `arn:aws:iam::<계정>:role/damso-github-actions` |
| `AWS_S3_BUCKET` | 산출물 버킷 이름 |
| `AWS_INSTANCE_ID` | `i-...` |

셋 다 비밀이랄 것은 없지만, 공개 레포에 계정 번호와 인스턴스 ID 를 박아 둘 이유도 없다.

DB 비밀번호와 카카오/네이버 키는 **여기에 넣지 않는다.** 서버의 `/etc/damso/damso.env` 에만 둔다.
배포 경로가 뚫려도 자격증명은 새지 않고, 키를 바꿔도 재배포가 필요 없다.

### 3. TLS

```bash
sudo certbot --nginx -d damso.example.com
```

간편로그인의 redirect-uri 는 HTTPS 여야 등록된다. 인증서를 먼저 받고 카카오/네이버 콘솔에 URI를 등록한 뒤 `/etc/damso/damso.env` 를 채우고 `sudo systemctl restart damso`.

### 4. 기존 데이터 옮기기 (H2 → PostgreSQL)

앱을 완전히 끄고 로컬에서:

```bash
./deploy/migrate-h2-to-postgres.sh ./data/damso /tmp/damso-migrate
```

스키마는 옮기지 않는다. PostgreSQL 테이블은 앱을 prod 로 한 번 띄워 Flyway(V1)가 만들게 하고, 데이터만 CSV로 넣는다. H2 스키마를 그대로 옮기면 이미 제거된 레거시 컬럼(`provider`, `height` 등)이 따라와 스키마 보정 코드와 충돌한다.

스크립트가 마지막에 서버에서 실행할 명령을 출력한다. **적재 후 출력되는 행 수를 `counts.txt` 와 한 줄씩 대조할 것.** 사진 파일(`uploads/`)도 함께 옮겨야 한다 — DB만 옮기면 프로필에 사진이 안 나온다.

### 5. 관리자 비밀번호 변경

`admin` / `admin` 으로 열려 있다. 첫 배포 직후 `https://도메인/admin/login` 에서 바꾼다. prod 프로파일에서는 손님 테스트 계정(`test`/`test1234`)이 생성되지 않는다.

---

## 평소 배포

```bash
git push origin main
```

Actions 탭에서 진행 상황을 본다. 실패하면 **서비스는 이전 버전으로 살아 있다** — `deploy.sh` 가 헬스체크 실패 시 롤백까지 마치고 종료하기 때문이다.

### 수동 배포

```bash
./scripts/build.sh
REL=manual-$(date +%Y%m%d%H%M)
aws s3 cp target/match-0.0.1-SNAPSHOT.jar s3://<버킷>/releases/$REL.jar
aws ssm send-command --instance-ids <인스턴스ID> --document-name AWS-RunShellScript \
  --parameters commands="aws s3 cp s3://<버킷>/releases/$REL.jar /tmp/$REL.jar --region ap-northeast-2,sudo -u deploy /opt/damso/bin/deploy.sh /tmp/$REL.jar $REL"
```

### 서버에 직접 들어가기

22번이 닫혀 있으므로 Session Manager 로 들어간다. 회선이 22번을 막는 곳에서도 된다.

```bash
brew install --cask session-manager-plugin     # 최초 1회
aws ssm start-session --target <인스턴스ID> --region ap-northeast-2
sudo su - ubuntu
```

### 롤백

```bash
ssh deploy@서버
ls -lt /opt/damso/releases/          # 되돌릴 릴리스 확인
sudo ln -sfn /opt/damso/releases/<릴리스> /opt/damso/current
sudo systemctl restart damso
```

---

## 운영

```bash
sudo systemctl status damso           # 상태
sudo journalctl -u damso -f           # 실시간 로그
sudo journalctl -u damso --since "1 hour ago"
curl -s localhost:8080/api/health     # 헬스체크 (외부에는 열려 있지 않다)
```

### 백업

매일 04:10 에 `/opt/damso/bin/backup.sh` 가 DB 덤프와 사진을 `/var/backups/damso` 에 남기고 14일치를 보관한다. 로그는 `/var/log/damso-backup.log`.

**같은 서버에만 있는 백업은 서버가 죽으면 같이 사라진다.** `/etc/damso/damso.env` 에 `BACKUP_REMOTE=user@host:/path` 를 넣어 원격 보관을 켜 두는 것을 권한다.

복구:

```bash
sudo systemctl stop damso
sudo -u postgres pg_restore -d damso --clean --if-exists /var/backups/damso/db-YYYYmmdd-HHMM.dump
sudo tar -xzf /var/backups/damso/uploads-YYYYmmdd-HHMM.tar.gz -C /var/lib/damso/
sudo chown -R damso:damso /var/lib/damso/uploads
sudo systemctl start damso
```

복구는 실제로 한 번 해 봐야 안다. 첫 배포 후 리허설을 권한다.

---

## 환경별 설정

| | dev (기본) | prod |
|---|---|---|
| DB | H2 파일 (`./data/damso`) | PostgreSQL |
| H2 콘솔 | 켜짐 | **꺼짐** |
| CORS | 전체 허용 (Vite 5173 + LAN) | 없음 (동일 오리진) |
| 로그 | DEBUG | INFO |
| 테스트 계정 | 생성 | 생성 안 함 |
| 프론트 | Vite 개발 서버 (5173) | jar 안 static |

프로파일을 지정하지 않으면 dev 다. 로컬 실행 방법(`./scripts/start.sh`)은 예전과 같다.

---

## 알아 둘 것

**스키마는 Flyway 가 관리한다.** `backend/src/main/resources/db/migration/V*.sql` 을 기동 때 순서대로 적용하고, Hibernate 는 `ddl-auto: validate` 로 엔티티와 테이블이 맞는지만 본다.

- 엔티티에 컬럼을 추가·변경하면 `V{다음번호}__설명.sql` 을 함께 만든다. 빠뜨리면 validate 가 실패해 앱이 뜨지 않는다(배포 스크립트의 헬스체크가 롤백한다).
- SQL 은 H2(MODE=PostgreSQL) 와 PostgreSQL 양쪽에서 돌아야 한다. 집 PC 는 H2, AWS 는 PostgreSQL 이다.
- 이미 적용된 V 파일은 고치지 않는다. 고치면 체크섬이 달라져 기동이 실패한다.
- Flyway 도입(2026-09-30) 전부터 있던 DB 는 첫 기동 때 `baseline-on-migrate` 로 V1 을 건너뛰고 "V1 적용됨"으로 표시된다. V1 은 빈 DB 용이다.
- 기존 DB 에는 Hibernate 가 만들어 둔 enum CHECK 제약이 남아 있다(V1 로 만든 새 DB 에는 없다). enum 값을 늘릴 때는 `SchemaOps.dropEnumCheck` 를 쓰는 보정 코드가 필요하다.

**스키마 보정 코드 두 개(`DatingSchemaMigration`, `AppUserSchemaMigration`)는 H2 시절 레거시용이다.** PostgreSQL 신규 DB 에서는 아무 일도 하지 않는다. 이전이 끝나고 한동안 문제가 없으면 삭제해도 된다.

**무중단 배포가 아니다.** 재시작에 3~5초 걸린다. 매장 영업 중에 배포하지 않는 것으로 충분하다.