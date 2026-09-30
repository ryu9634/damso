# 집 PC 로 옮기기 — 윈도우 + H2 + Cloudflare Tunnel

AWS EC2 + PostgreSQL 로 돌던 담소를 **집 윈도우 PC + H2 파일 DB** 로 옮기고,
**Cloudflare Tunnel** 로 도메인을 그 PC 에 연결하는 절차. 도메인 비용과 전기료만 든다.

운영 문서는 둘이 공존한다. AWS 구성은 [DEPLOY.md](DEPLOY.md) 에 그대로 남겨 둔다 —
집 PC 가 안 맞으면 되돌아갈 곳이 있어야 한다.

---

## 무엇이 바뀌는가

| | 지금 (AWS) | 옮긴 뒤 (집 PC) |
|---|---|---|
| 서버 | EC2 (Ubuntu) | 윈도우 PC, 작업 스케줄러 |
| DB | PostgreSQL | H2 파일 DB (`damso.mv.db` 한 개) |
| 앞단 | nginx + certbot | Cloudflare Tunnel (`cloudflared`) |
| HTTPS | Let's Encrypt 직접 갱신 | Cloudflare 가 자동 |
| 공개 경로 | 공인 IP + 80/443 인바운드 | 인바운드 없음 — PC 가 밖으로 거는 연결만 |
| 배포 | main 푸시 → S3 → SSM | main 푸시 → GitHub 릴리스 → `update.ps1` |
| 프로파일 | `prod` | `local` |
| 월 비용 | EC2 + EBS + S3 | 도메인 + 전기료 |

**포트포워딩과 DDNS 는 하지 않는다.** Cloudflare Tunnel 은 PC 가 Cloudflare 로 바깥 방향
연결을 걸어 두고 그 위로 요청을 받는 방식이다. 그래서

- 공유기 설정을 건드릴 필요가 없고, 집 공인 IP 가 바뀌어도 상관없다 (DDNS 불필요),
- 집 IP 가 밖에 노출되지 않고,
- 80·443 을 막는 인터넷 회선에서도 된다.

### 비용을 미리 계산해 볼 것

전기료는 PC 에 따라 크게 갈린다. 상시 가동 소비전력이 곧 월 비용이다.

| 상시 소비전력 | 월 사용량 | 월 전기료(주택용 누진 상단 기준, 대략) |
|---|---|---|
| 15W (미니PC·노트북) | 약 11 kWh | 2,000 ~ 4,000원 |
| 60W (일반 데스크톱 유휴) | 약 43 kWh | 9,000 ~ 14,000원 |
| 120W (외장 GPU 있는 데스크톱) | 약 86 kWh | 18,000 ~ 28,000원 |

지금 AWS 비용이 월 2~3만원 수준이라면, **미니PC 는 확실히 이득이지만 게이밍 데스크톱을
24시간 켜 두는 건 절약 폭이 얼마 안 된다.** 옮기기 전에 쓸 PC 의 유휴 소비전력을 한 번
확인해 보기를 권한다.

### 감수하는 것

정직하게 적어 둔다. 셋 다 AWS 에서는 없던 문제다.

- **정전·인터넷 장애 = 서비스 정지.** UPS 가 없으면 정전이 곧 다운이다. BIOS 에서
  "정전 복구 후 자동 부팅"(Restore on AC Power Loss)을 켜 두면 전기가 돌아올 때 알아서 뜬다.
- **백업 중 20~60초 정지.** H2 파일 DB 는 켜진 채로 복사하면 쓰기 도중의 어긋난 상태가
  찍힐 수 있어, 새벽 4시 10분에 잠깐 멈추고 복사한다.
- **가정용 인터넷 약관.** 통신사 약관에 상용 서버 운영을 제한하는 조항이 있을 수 있다.
  포트를 열지 않으므로 기술적으로는 걸릴 게 없지만, 약관은 별개 문제다.

H2 로 바꾸는 것 자체는 이 규모에서 문제가 아니다. 한 프로세스만 DB 파일을 열 수 있으니
서버를 여러 대로 늘릴 수 없지만, 행사 한 건에 수십 명 규모라면 충분하다.

---

## 준비물

**맥(지금 이 저장소가 있는 기기)** — 데이터 이전 작업만 여기서 한다.

- JDK 21, Maven
- AWS CLI + 자격증명 (`aws sts get-caller-identity` 가 성공해야 한다)
- Session Manager 플러그인 — `brew install --cask session-manager-plugin`
- EC2 키페어 `.pem` (`~/.ssh/damso*.pem` — 스크립트가 알아서 찾는다)

**윈도우 PC** — 실제로 서비스가 도는 곳.

- JDK 21 — `winget install --id EclipseAdoptium.Temurin.21.JDK`
- cloudflared — `winget install --id Cloudflare.cloudflared`
- 관리자 권한

**Cloudflare 계정** (무료) + 지금 쓰는 도메인.

---

## 전체 순서

한 번에 넘기지 않는다. **연습 → 확인 → 실전 전환** 으로 두 번 돌린다.
연습에서는 AWS 를 계속 켜 둔 채 데이터 사본만 가져와, 집 PC 에서 정상 동작하는지 끝까지 본다.

```
 1. Cloudflare 에 도메인 올리기        (AWS 계속 운영)
 2. 윈도우 PC 준비                     (AWS 계속 운영)
 3. 데이터 연습 이전 → 집 PC 에서 확인  (AWS 계속 운영)
 4. Cloudflare Tunnel 연결 → 임시 주소로 확인
 ─────────── 여기까지 서비스 영향 없음 ───────────
 5. 실전 전환: AWS 정지 → 최종 데이터 이전 → 도메인 전환
 6. 며칠 지켜본 뒤 AWS 정리
```

---

## 1. Cloudflare 에 도메인 올리기

Cloudflare Tunnel 은 도메인의 DNS 를 Cloudflare 가 맡아야 쓸 수 있다.

1. [dash.cloudflare.com](https://dash.cloudflare.com) 에서 **Add a site** → 도메인 입력 → **Free** 플랜.
2. Cloudflare 가 기존 DNS 레코드를 읽어 온다. **지금 AWS 를 가리키는 A 레코드가 그대로
   들어왔는지 확인한다.** 이게 있어야 네임서버를 옮겨도 서비스가 안 끊긴다.
3. Cloudflare 가 알려주는 네임서버 두 개를 도메인 등록업체(가비아·후이즈 등)에 등록한다.
4. 반영까지 보통 몇 분 ~ 몇 시간. Cloudflare 대시보드가 **Active** 로 바뀌면 끝.

이 단계에서 서비스는 계속 AWS 로 간다. 바뀌는 건 DNS 를 누가 관리하는지뿐이다.

> 무료 플랜 요청 본문 한도는 100MB 다. 앱 한도가 10MB 이므로 여유가 있다.

---

## 2. 윈도우 PC 준비

### 폴더 만들기

```
C:\damso\
  damso.env        설정·비밀 (600 에 해당하는 권한으로 관리)
  app\             match.jar (업데이트가 이 폴더만 바꾼다)
  data\            damso.mv.db, damso.key
  uploads\         소개팅 사진
  logs\            damso.log, ops.log, stdout.log, cloudflared.log
  backups\         일일 백업
  bin\             운영 스크립트
```

관리자 PowerShell 에서:

```powershell
mkdir C:\damso\app, C:\damso\data, C:\damso\uploads, C:\damso\logs, C:\damso\backups, C:\damso\bin
```

### 스크립트와 설정 복사

저장소의 `deploy\windows\` 에 있는 파일을 옮긴다.

```powershell
# 저장소를 이 PC 에 클론했다면 (아니면 맥에서 복사해 온다)
copy deploy\windows\*.ps1 C:\damso\bin\
copy deploy\windows\damso.env.example C:\damso\damso.env
notepad C:\damso\damso.env
```

`damso.env` 에서 최소한 이 둘은 확인한다.

- `GITHUB_REPO` — 업데이트를 받아올 저장소 (`ryu9634/damso`)
- `BACKUP_MIRROR` — 백업 두 번째 사본을 둘 곳. **비워 두면 이 PC 가 죽을 때 백업도 같이 죽는다.**
  외장 하드, NAS, OneDrive/구글드라이브 동기화 폴더 중 무엇이든 적는다.

간편로그인(`KAKAO_*` / `NAVER_*`) 값은 지금 AWS 의 `/etc/damso/damso.env` 에 있는 것을 그대로 옮긴다.
`REDIRECT_URI` 는 비워 두는 편이 좋다 — 서버가 요청이 들어온 도메인 기준으로 콜백 주소를 만든다.

### jar 받기

```powershell
powershell -ExecutionPolicy Bypass -File C:\damso\bin\update.ps1
```

GitHub 릴리스 `latest` 에서 jar 을 받아 `C:\damso\app\match.jar` 로 놓는다.
아직 그 릴리스가 없으면 main 에 한 번 푸시하거나, 맥에서 빌드한 jar 을 직접 준다:

```powershell
powershell -File C:\damso\bin\update.ps1 -Jar D:\받은\match.jar
```

---

## 3. 데이터 연습 이전

**맥에서** 한다. AWS 는 계속 돌아간다.

### 3-1. 가져오기

```bash
INSTANCE_ID=i-0123456789abcdef0 ./deploy/export-from-aws.sh
```

`INSTANCE_ID` 는 GitHub 의 `AWS_INSTANCE_ID` 시크릿과 같은 값이다.
SSM 터널 위로 SSH 를 얹어 붙으므로 보안그룹을 열 필요가 없다.

이 서버는 지금까지 SSM 으로만 운영해 왔으므로 `.pem` 이 `authorized_keys` 에 없을 수 있다.
그 경우 스크립트가 **물어본 뒤 SSM 으로 공개키를 등록하고 다시 붙는다.** 22번은 여전히 닫혀 있고,
등록에 쓰는 권한도 이미 배포에 쓰고 있는 SSM 권한뿐이다.

가져오는 것은 셋이고, **반드시 함께여야 한다.**

| | 없으면 |
|---|---|
| `db/*.csv` | 데이터가 없다 |
| `damso.key` | 행은 있는데 이름·전화번호가 전부 못 읽는 암호문으로 남는다 (**복구 불가**) |
| `uploads/` | 프로필은 있는데 사진이 없다 |

`~/damso-migrate/<시각>/` 에 펼쳐지고 테이블별 행 수를 출력한다.

### 3-2. H2 로 옮기기

```bash
./deploy/migrate-postgres-to-h2.sh ~/damso-migrate/20260923-140000
```

하는 일:

1. H2 스키마가 없으면 앱을 `local` 프로파일로 잠깐 띄워 Flyway 가 테이블을 만들게 한다.
   **스키마는 옮기지 않는다** — 그래야 엔티티와 정확히 맞고, 운영 PostgreSQL 에서 보정한
   컬럼 타입이 따라오지 않는다.
2. CSV 를 H2 에 적재한다 (`deploy/H2Import.java`). PostgreSQL 의 `t`/`f` boolean,
   마이크로초 타임스탬프, 값 안의 줄바꿈, NULL 과 빈 문자열의 구분을 그대로 되살린다.
3. IDENTITY 시퀀스를 `MAX(id)` 다음으로 맞춘다. 이걸 빼먹으면 이전 직후 첫 신청이
   중복 키로 실패한다 — 가장 흔한 실수다.
4. 암호화 키와 사진을 제자리에 놓는다.
5. **테이블별 행 수를 PostgreSQL 원본과 한 줄씩 대조한다.** 하나라도 다르면 실패로 끝난다.

결과는 `<번들>/out/` 에 윈도우로 그대로 복사할 모양으로 놓인다.

### 3-3. 윈도우 PC 로 복사

```
out/data/damso.mv.db  →  C:\damso\data\damso.mv.db
out/data/damso.key    →  C:\damso\data\damso.key     ← 유출되면 개인정보가 풀린다
out/uploads/          →  C:\damso\uploads\
```

### 3-4. 자동 시작 등록하고 띄우기

관리자 PowerShell 에서:

```powershell
powershell -ExecutionPolicy Bypass -File C:\damso\bin\install-task.ps1
Start-ScheduledTask -TaskName damso
```

`install-task.ps1` 이 하는 일:

- `damso` 작업 — 부팅 때 시작 + 10분마다 살아 있는지 점검 (죽었으면 다시 띄운다)
- `damso-backup` 작업 — 매일 04:10
- 절전·최대절전 끄기 (서버가 잠들면 도메인은 그대로인데 접속만 안 된다)

### 3-5. 확인

```powershell
Invoke-WebRequest http://127.0.0.1:8080/api/health -UseBasicParsing
Get-Content C:\damso\logs\damso.log -Tail 50
```

브라우저로 `http://127.0.0.1:8080` 을 열고 **AWS 에서 보던 것과 같은지** 본다.
특히 이 둘을 꼭 본다.

- 관리자 화면에서 **회원 이름과 전화번호가 제대로 보이는지** — 암호화 키가 맞는지 확인하는 방법이다.
  깨져 보이거나 `enc1:...` 같은 문자열이 보이면 키가 잘못됐다. **그 상태로 운영하지 말고**
  `C:\damso\data\damso.key` 를 다시 확인한다.
- 소개팅 프로필의 **사진이 보이는지** — `uploads/` 가 제대로 왔는지 확인하는 방법이다.

> 앱은 `local` 프로파일로 돈다. `dev` 로 띄우면 H2 콘솔이 열리고 테스트 계정(test/test1234)이
> 생기고 CORS 가 풀린다 — 손님을 받는 서버에서는 절대 쓰지 않는다.

---

## 4. Cloudflare Tunnel 연결

대시보드에서 만드는 방법을 권한다. 공개 호스트명 설정이 Cloudflare 쪽에 저장되므로
PC 에 설정 파일이 없고, 나중에 도메인을 바꿀 때도 PC 를 건드리지 않는다.

1. [Zero Trust](https://one.dash.cloudflare.com) → **Networks → Tunnels → Create a tunnel**
2. **Cloudflared** 선택 → 이름 `damso` → 저장
3. **Windows** 탭에 나오는 설치 명령을 관리자 PowerShell 에 그대로 붙인다.
   토큰이 박힌 `cloudflared service install <토큰>` 형태다.
4. **Public Hostname** 탭에서 라우트를 하나 추가한다.

   | 항목 | 값 |
   |---|---|
   | Subdomain | (비움 — 루트 도메인을 쓸 때) |
   | Domain | 쓰는 도메인 |
   | Type | `HTTP` |
   | URL | `127.0.0.1:8080` |

   `https` 가 아니라 `http` 다. 같은 PC 안이라 TLS 가 필요 없고,
   Cloudflare ↔ cloudflared 구간은 이미 암호화된다.

5. 확인:

   ```powershell
   Get-Service cloudflared
   Get-Content C:\damso\logs\cloudflared.log -Tail 30
   ```

CLI 로 하고 싶으면 [deploy/windows/cloudflared-config.yml](../deploy/windows/cloudflared-config.yml)
의 주석에 절차가 있다.

### 실전 전환 전에 임시 주소로 먼저 확인하기

도메인을 바로 옮기지 않고, 4-4 에서 서브도메인 하나(`test.도메인`)로 라우트를 만들어
그 주소로 먼저 전부 확인한다. 이러면 AWS 가 계속 서비스하는 동안 집 PC 를 실제 HTTPS 로 시험할 수 있다.

확인할 것:

- 손님 화면 · 관리자 로그인
- 사진 업로드 (10MB 가까운 파일로)
- 간편로그인 — 카카오·네이버 콘솔의 콜백 주소에 `https://test.도메인/oauth/kakao` 를 임시로 추가해야 된다
- 푸시 알림 — VAPID 키는 `app_settings` 테이블에 있어서 함께 옮겨졌다.
  기존 구독이 그대로 살아 있어야 정상이다

---

## 5. 실전 전환

손님이 없는 시간에 한다. 순서가 중요하다.

```
1) AWS 담소 정지 + 최종 데이터 뽑기   ./deploy/export-from-aws.sh --cutover
2) H2 로 이전                          ./deploy/migrate-postgres-to-h2.sh <번들>
3) 윈도우 PC 에 복사 (담소를 멈추고)
4) 담소 시작 + 127.0.0.1 로 확인
5) Cloudflare 라우트를 실제 도메인으로 변경
6) 실제 도메인으로 최종 확인
```

`--cutover` 는 AWS 의 담소를 멈춘 뒤 데이터를 뽑고, **멈춘 채로 둔다.** 이 시점 이후
옛 서버로 들어온 신청·지목은 넘어오지 않는다. 그래서 정지와 도메인 전환 사이를 짧게 가져간다.

3) 에서 파일을 덮어쓸 때는 담소를 먼저 멈춘다. H2 는 켜진 채로 `.mv.db` 를 바꿀 수 없다.

```powershell
Stop-ScheduledTask -TaskName damso   # 또는 아래 스크립트가 정상 종료까지 해 준다
powershell -File C:\damso\bin\restart-damso.ps1
```

5) 에서 4-4 의 라우트를 `test.도메인` → 실제 도메인으로 바꾸고, 임시 라우트는 지운다.
카카오·네이버 콜백에 임시 주소를 추가했다면 그것도 지운다.

### 되돌리기

전환 후 문제가 생기면 AWS 를 다시 켜면 된다. **AWS 를 정리하기 전까지는** 이게 가능하다.

```bash
aws ssm send-command --instance-ids i-... --document-name AWS-RunShellScript \
  --parameters 'commands=["systemctl start damso"]'
```

그리고 Cloudflare DNS 에서 도메인을 다시 EC2 공인 IP 로 돌린다.
단, 집 PC 에서 그동안 쌓인 데이터는 AWS 에 없다. 되돌리기는 전환 직후에만 깔끔하다.

---

## 6. AWS 정리

**며칠 지켜본 뒤에** 한다. 최소 한 주는 되돌아갈 수 있게 남겨 두기를 권한다.
정리하기 전에 마지막 백업을 하나 받아 따로 보관한다.

지우는 순서 (앞에서부터):

1. **GitHub 시크릿 `AWS_ROLE_ARN` 삭제** — 이것만 지우면 main 푸시가 AWS 배포를 건너뛴다
   (빌드·테스트·릴리스 게시는 계속 된다). 워크플로 파일은 그대로 두면 되돌리기 쉽다.
2. EC2 인스턴스 종료(terminate)
3. EBS 볼륨 — 인스턴스와 함께 지워졌는지 확인. 남으면 계속 과금된다
4. Elastic IP — **할당만 되어 있고 붙은 인스턴스가 없으면 오히려 요금이 붙는다.** 꼭 릴리스
5. S3 버킷 (`releases/` 의 옛 jar) — 비우고 삭제
6. IAM 역할·인스턴스 프로파일, OIDC 공급자
7. Route 53 호스팅 존 — 쓰고 있었다면. DNS 는 이제 Cloudflare 가 맡는다

마지막으로 **Billing → Cost Explorer** 에서 다음 달 청구가 0 에 가까운지 확인한다.
남은 리소스는 대개 여기서 발견된다.

---

## 운영

### 업데이트

main 에 푸시하면 GitHub Actions 가 테스트하고 jar 을 빌드해 `latest` 릴리스에 올린다.
윈도우 PC 에서:

```powershell
powershell -File C:\damso\bin\update.ps1
```

받아서 바꿔 끼우고 헬스체크까지 한다. **실패하면 이전 jar 로 자동 복귀한다.**
직접 되돌리려면 `update.ps1 -Rollback`.

### 백업

매일 04:10 에 `damso-backup` 작업이 돈다. `C:\damso\backups\` 에 세 개가 한 묶음으로 남는다.

```
db-20260923-0410.mv.db      DB
damso-20260923-0410.key     암호화 키   ← DB 만으로는 개인정보를 못 읽는다
uploads-20260923-0410.zip   사진
```

기본 14일 보관. `BACKUP_MIRROR` 를 채워 두면 두 번째 사본도 만든다.

손으로 돌려보기:

```powershell
powershell -File C:\damso\bin\backup.ps1
Get-Content C:\damso\logs\ops.log -Tail 30
```

> 예전 AWS 에서 백업 cron 이 한동안 돌지 않은 적이 있다. 등록해 두고 믿지 말고,
> 며칠 뒤 `C:\damso\backups\` 에 파일이 실제로 쌓였는지 눈으로 확인한다.

### 복구

```powershell
powershell -File C:\damso\bin\restart-damso.ps1   # 먼저 멈추기 위해
Stop-ScheduledTask -TaskName damso

copy C:\damso\backups\db-20260923-0410.mv.db    C:\damso\data\damso.mv.db
copy C:\damso\backups\damso-20260923-0410.key   C:\damso\data\damso.key
Expand-Archive C:\damso\backups\uploads-20260923-0410.zip -DestinationPath C:\damso\uploads -Force

Start-ScheduledTask -TaskName damso
```

**세 개를 같은 시각 것으로 함께 되돌린다.** DB 만 되돌리면 그 뒤에 올라온 사진과 어긋나고,
키를 빼먹으면 이름·전화번호를 못 읽는다.

### 상태 보기

```powershell
Get-ScheduledTask damso, damso-backup | Get-ScheduledTaskInfo   # 마지막 실행·결과
Invoke-WebRequest http://127.0.0.1:8080/api/health -UseBasicParsing
Get-Content C:\damso\logs\damso.log -Tail 50 -Wait               # 앱 로그
Get-Content C:\damso\logs\ops.log -Tail 50                       # 시작·백업·업데이트 기록
Get-Content C:\damso\logs\cloudflared.log -Tail 30               # 터널
Get-Service cloudflared
```

### 자주 겪는 것

| 증상 | 볼 곳 |
|---|---|
| 도메인이 502 | 앱이 죽었다. `ops.log` → `damso.log` |
| 도메인이 안 열리는데 `127.0.0.1:8080` 은 됨 | cloudflared. `Get-Service cloudflared`, `cloudflared.log` |
| 이름·전화번호가 깨져 보임 | 암호화 키가 다르다. 백업의 `damso-*.key` 로 되돌린다 |
| 사진만 안 보임 | `DAMSO_UPLOADS` 경로와 `C:\damso\uploads\` 내용 |
| 기동 실패, DB 잠금 오류 | 자바가 두 개 떠 있다. `Get-Process java`, `damso.pid` 확인 |
| 신청하면 중복 키 오류 | IDENTITY 시퀀스가 안 맞는다. 이전 로그의 "시퀀스 재설정" 줄 확인 |
| 업데이트 후 안 뜸 | `update.ps1 -Rollback` |

### 절대 하지 말 것

- **`C:\damso\data\damso.key` 를 잃어버리기.** 이 키 없이는 저장된 이름·전화번호를
  되돌릴 방법이 없다. 백업 두 번째 사본을 반드시 다른 장치에 둔다.
- **`dev` 프로파일로 손님 받기.** H2 콘솔이 인증 없이 DB 전체를 노출한다.
- **담소가 켜진 상태에서 `damso.mv.db` 복사·교체.**
- **`damso.env` 를 git 에 커밋하기.**

---

## 참고

| 파일 | 하는 일 |
|---|---|
| [backend/src/main/resources/application-local.yml](../backend/src/main/resources/application-local.yml) | 집 PC 운영 프로파일 |
| [deploy/export-from-aws.sh](../deploy/export-from-aws.sh) | AWS → 로컬 번들 (맥에서 실행) |
| [deploy/aws-export-bundle.sh](../deploy/aws-export-bundle.sh) | 위 스크립트가 서버에서 돌리는 부분 |
| [deploy/migrate-postgres-to-h2.sh](../deploy/migrate-postgres-to-h2.sh) | 번들 → H2 (맥에서 실행) |
| [deploy/H2Import.java](../deploy/H2Import.java) | CSV → H2 적재 도구 |
| [deploy/windows/damso.env.example](../deploy/windows/damso.env.example) | 윈도우 설정 서식 |
| [deploy/windows/run-damso.ps1](../deploy/windows/run-damso.ps1) | 앱 실행 (작업 스케줄러가 부른다) |
| [deploy/windows/install-task.ps1](../deploy/windows/install-task.ps1) | 자동 시작·백업 등록, 절전 끄기 |
| [deploy/windows/backup.ps1](../deploy/windows/backup.ps1) | 일일 백업 |
| [deploy/windows/update.ps1](../deploy/windows/update.ps1) | 새 jar 받아 교체, 실패 시 복귀 |
| [deploy/windows/restart-damso.ps1](../deploy/windows/restart-damso.ps1) | 정상 종료 후 재시작 |
| [DEPLOY.md](DEPLOY.md) | AWS 구성 (되돌아갈 곳) |
