# 담소혼술바 매칭 시스템

매장 내에서 진행하는 소규모(최대 18명, 9남 9여) 호감도 기반 매칭 이벤트 운영 도구.
관리자가 PC로 단계를 진행하고, 손님은 휴대폰으로 자기 자리·호감도 지목·메시지 전송을 진행한다.

기획·백엔드·프론트·DB·배포 전 영역 단독 개발.

---

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| 백엔드 | Spring Boot 3.3.5, Java 21, JPA(Hibernate), H2 (파일 임베디드, AUTO_SERVER) |
| 프론트 | React 18, Vite, TypeScript, Tailwind CSS |
| 빌드 | Maven (`backend/`), npm (`frontend/`) — 산출물은 jar 하나로 합친다 |
| 배포 | GitHub Actions, AWS EC2(systemd·nginx) 또는 집 PC(Cloudflare Tunnel), 데이터 이전 스크립트 |

---

## 사전 준비

- macOS / Linux
- **Java 21+** (`brew install openjdk@21` 또는 `openjdk@25`)
- **Maven** (`brew install maven`)
- **Node 18+** + npm

설치 확인:
```bash
java -version
mvn -version
node -version
```

> 개발은 macOS / Linux 기준이다. 윈도우 PC 에서 **운영**하는 구성은
> [LOCAL_SERVER.md](docs/LOCAL_SERVER.md) 에 따로 있다.

---

## 실행

```bash
# 최초 실행 / 코드 변경 시 빌드 포함
./scripts/start.sh --rebuild

# 평소 (빌드 캐시 사용)
./scripts/start.sh

# 종료
./scripts/stop.sh
```

`scripts/start.sh`는 백엔드(8080)와 프론트(5173)를 함께 띄운 뒤,
두 프로세스의 로그를 `tail -f`로 이어서 출력한다. `Ctrl+C`로 둘 다 정리된다.

로그 파일은 아래 경로에 쌓인다.

```bash
logs/server.log   # 백엔드
logs/client.log   # 프론트
```

기동 후 콘솔에 표시되는 주소로 접속한다.

| 용도 | 주소 |
|---|---|
| 관리자 (PC) | http://localhost:5173/admin |
| 손님 (같은 매장 휴대폰) | http://&lt;매장 LAN IP&gt;:5173/ |
| 소개팅 관리 | http://localhost:5173/dating |

손님 휴대폰은 **PC와 같은 와이파이**에 연결되어야 한다.
같은 LAN IP는 관리자 페이지의 "손님 입장 QR" 화면에 자동 표시되며,
손님이 QR로 스캔하면 바로 입장 페이지로 이동한다.

---

## 데이터 저장 위치

- H2 파일 DB: `./data/damso.mv.db`
- 프로젝트 폴더를 통째로 압축해서 옮기면 데이터가 함께 따라간다.
- 완전 초기화: 서버 종료 후 `rm data/damso.*` → 재시작

---

## 운영 흐름

이벤트는 다음 7단계를 순차적으로 진행한다. 관리자가 단계 버튼을 눌러 진행.

| 단계 | 상태값 | 관리자 화면 | 손님 화면 |
|---|---|---|---|
| 1 | `REGISTRATION` | 손님 등록 / 수정 | 정보 입력 폼 |
| 2 | `ROUND_1` | 1차 자리 배치 | 본인 자리 + 동석자 |
| 3 | `ROUND_2` | 2차 자리 배치 | 본인 자리 + 동석자 |
| 4 | `PREFERENCE` | 호감도 지목 현황 | 1·2순위 지목 |
| 5 | `ROUND_3` | 3차 자리 배치 | 본인 자리 + 동석자 |
| 6 | `MESSAGE` | 메시지 모니터링 | 메시지 작성/수신 |
| 7 | `CLOSED` | 이벤트 종료 | 종료 안내 |

### 자리 레이아웃 (ㄷ자 9석 × 슬롯 2)

```
[6][6][5][5][4][4][3]
[7] ┌───────────────┐ [3]
[7] │               │ [2]
[8] │     주인장     │ [2]
[8] │   (와인잔)    │ [1]
[9] │               │ [1]
[9] └───────────────┘
```

- 9개 자리 × 2 슬롯 = 최대 18명
- 자동 배치 시 슬롯 기본 규칙:
  - **자리 1~6**: 위 = 여, 밑 = 남
  - **자리 7~9**: 위 = 남, 밑 = 여
- 자동 배치 후 운영자가 수동으로 셀 클릭하여 자유롭게 보정 가능

---

## 관리자 기능

### 이벤트
- 현재 진행 중인 이벤트 1개 + 과거 종료 이벤트 목록
- 새 이벤트 시작 (이전 이벤트는 자동 종료)
- 단계 진행 / 되돌리기

### 손님 등록 (관리자가 대리 입력 가능)
- 닉네임, 본명, 연락처, 성별, MBTI, 가치관 16문항, 자리 번호
- 등록 후 상태 변경 가능 (ACTIVE / LEFT — 노쇼 처리)

### 자리 배치
3개 라운드 각각 자동 배치 버튼 + 수동 보정 지원.

| 라운드 | 자동 배치 기준 |
|---|---|
| 1차 | 손님이 입력한 자리 번호 그대로 배치 |
| 2차 | MBTI 궁합 + 가치관 일치도 점수 최대화 페어링 (동점 랜덤) |
| 3차 | 쌍방 1순위 → 1·2순위 교차/쌍방 2순위 → 단방 지목 → 미지목 순 |

- 자동 배치 후 빈 셀 클릭 → 손님 선택 모달
- 배정된 셀 우클릭 → 해제
- 라운드 전체 초기화 버튼

### 호감도 지목 현황 (`PREFERENCE` 단계)
- 손님별 1·2순위 제출 내역
- **💕 쌍방 1순위 / 💗 2순위 포함 쌍방** 분리 표시
- 미제출자 명단 (실시간 3초 갱신)

### MBTI 매칭 점수표
- 16×16 MBTI 매트릭스 (편집 가능)
- 실시간 점수 계산: MBTI 매트릭스 + 가치관 16문항 일치 개수

### 메시지 모니터링
- 손님 → 손님 직접 전달 메시지 모두 확인
- 발신자 연락처 포함

### 가치관 설문 문항 관리
- 16문항 라벨/옵션 A·B 편집
- 순서 변경, 기본값 리셋

---

## 손님 (모바일) 기능

### 입장
- QR 스캔 또는 관리자가 안내한 URL 접속
- `REGISTRATION` 단계에서 정보 입력 후 등록
- 등록 후 단계가 진행되면 자동으로 다음 화면 전환

### 세션 끊김 → 재로그인
브라우저 캐시 삭제 / 휴대폰 재부팅 등으로 세션이 끊기면
**본명 + 연락처** 로 재로그인 가능 (등록 시 입력값과 일치해야 함).

### 본인 자리 보기 (`ROUND_1/2/3`)
- ㄷ자 전체 자리 배치도 표시
- 본인 자리는 ★ 마크 + 황색 하이라이트
- 다른 손님은 닉네임만 노출 (본명/연락처 비공개)
- 4초마다 자동 갱신

### 호감도 지목 (`PREFERENCE`)
- 이성 후보 목록에서 1·2순위 지목 (둘 다 필수)
- 제출 후 다시 선택 가능

### 메시지 (`MESSAGE`)
- 이성 1명에게 메시지 + 본인 연락처 전송 (1회 한정)
- 받은 메시지 실시간 확인

---

## 프로젝트 구조

**저장소가 백엔드와 프론트로 나뉘어 있다.** `backend/` 에서 Maven, `frontend/` 에서
npm 을 돌린다. 루트에는 저장소 수준 파일만 둔다.

배포 단위는 여전히 **jar 파일 하나**다. Maven 이 `frontend/dist/` 를 jar 안
`static/` 으로 넣기 때문에, 그 jar 하나가 프론트와 API 를 같은 오리진에서 서빙한다.

```
.
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/damso/match/
│       │   ├── DamsoMatchApplication.java   # 컴포넌트 스캔 기준 — 제자리
│       │   │
│       │   ├── api/              # 프레젠테이션 — 누가 호출하나로 나눈다
│       │   │   ├── admin/        #   /api/admin/** — 인터셉터가 통째로 잠근다
│       │   │   │   ├── event/  dating/  venue/  game/
│       │   │   │   └── setting/  member/  auth/
│       │   │   ├── app/          #   손님 PWA
│       │   │   │   └── home/  auth/  dating/  game/  notification/
│       │   │   └── guest/        #   현장 손님 (/api/guest)
│       │   │
│       │   ├── domain/           # 비즈니스 — 누가 부르든 같은 코드
│       │   │   ├── event/        #   행사·손님·좌석·메시지·통계
│       │   │   ├── dating/       #   담소 셀렉팅 (프로필·지목·성사)
│       │   │   ├── game/         #   게임방
│       │   │   ├── member/       #   앱 회원·소셜 로그인·세션
│       │   │   ├── venue/        #   술 메뉴·영업 캘린더·방명록
│       │   │   ├── notification/ #   알림·웹푸시
│       │   │   ├── setting/      #   앱 설정
│       │   │   └── admin/        #   관리자 계정
│       │   │
│       │   └── global/           # 횡단 관심사
│       │       ├── config/       #   WebConfig · OAuth 설정 · 초기 데이터
│       │       ├── error/        #   전역 예외 처리
│       │       ├── security/     #   개인정보 암호화 · 관리자 인터셉터
│       │       ├── schema/       #   스키마 보정 (Flyway 이전부터 있던 DB 용)
│       │       └── common/       #   파일 저장 · 헬스체크 · 공용 enum
│       │
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml     # 로컬 개발 (H2 콘솔·테스트 계정)
│           ├── application-local.yml   # 집 PC 운영 (H2 파일 DB)
│           ├── application-prod.yml    # AWS 운영 (PostgreSQL)
│           └── db/migration/           # Flyway 스키마 (V1 = 기준 스키마, 이후 V2, V3 …)
│
├── frontend/
│   ├── package.json  vite.config.ts  tsconfig.json  tailwind.config.js
│   ├── index.html              # Vite 엔트리
│   ├── public/                 # 파비콘 · OG 이미지 · 서비스워커
│   └── src/
│       ├── main.tsx
│       ├── App.tsx             # 어느 경로를 어느 앱이 맡는지만
│       ├── index.css
│       │
│       ├── admin/              # ── 관리자 콘솔 ──
│       │   ├── routes.tsx
│       │   ├── api/            #   관리자 API 클라이언트 (damso.admin.token)
│       │   ├── layout/         #   AdminLayout · 공용 UI · 행사 선택
│       │   ├── hooks/          #   행사 컨텍스트
│       │   ├── pages/          #   로그인 · 계정
│       │   └── features/       #   event · dating · venue · game · setting · member
│       │
│       ├── user/               # ── 손님 앱 (PWA) ──
│       │   ├── routes.tsx
│       │   ├── api/            #   손님 앱 API 클라이언트 (damso.app.token)
│       │   ├── layout/         #   AppShell · 기능 게이트 · 로그인 게이트 · UI
│       │   └── features/       #   home · auth · dating · matching
│       │                       #   game · guestbook · notification
│       │
│       └── shared/             # 두 앱이 함께 쓰는 것만
│           ├── types/
│           └── utils/          #   csv · 이미지 · 닉네임 · 전화번호 · 개인정보 마스킹
│
├── deploy/                     # 운영 배포 자산 (AWS · 집 PC · 데이터 이전)
├── scripts/                    # start.sh, stop.sh, build.sh, seed_dummy.py
├── docs/                       # 배포·운영·개발 기록 문서
├── .github/workflows/          # CI/CD
│
├── data/                       # H2 DB 파일 · 암호화 키 (gitignore)
├── uploads/                    # 업로드된 사진 (gitignore)
└── logs/                       # 백/프론트 로그 (gitignore)
```

### 어디를 열어야 하나

**기능을 고칠 때는 두 곳을 본다.** 화면이 부르는 엔드포인트가 `api/` 에 있고,
그 뒤의 로직이 `domain/` 에 있다.

| 하고 싶은 일 | 여는 곳 |
|---|---|
| 관리자 손님 목록 화면 | `frontend/src/admin/features/event/CustomersPage.tsx` |
| 그 화면이 부르는 API | `backend/.../api/admin/event/AdminCustomerController.java` |
| 손님 저장 규칙 | `backend/.../domain/event/service/CustomerService.java` |
| 손님 앱 셀렉팅 화면 | `frontend/src/user/features/dating/DatingPage.tsx` |
| 현장 소개팅 흐름 | `frontend/src/user/features/matching/` |
| 개인정보 암호화 | `backend/.../global/security/DataCrypto.java` |

**`api/` 의 폴더 경계가 인증 경계와 같다.** `api/admin/` 아래는 전부
`AdminAuthInterceptor` 가 잠그고, `api/app/` 은 손님 로그인 토큰을 보고,
`api/guest/` 는 현장 세션을 쓴다. 컨트롤러가 잠겨 있는지 경로만 보고 알 수 있다.

**DTO 는 쓰는 대상 쪽 `api/` 에 둔다.** 다만 두 대상이 함께 쓰는 것은
`domain/*/dto` 에 남겼다 — 한쪽으로 밀면 중복이 생긴다.

---

## 배포

`deploy/` 아래에 운영 배포에 필요한 자산이 들어 있다.

| 파일 | 용도 |
|---|---|
| `provision.sh` | 서버 초기 프로비저닝 |
| `aws-ec2-create.sh` | AWS EC2 인스턴스 생성 |
| `deploy.sh` | 배포 실행 |
| `damso.service` | systemd 서비스 유닛 |
| `nginx-damso.conf` | nginx 리버스 프록시 설정 |
| `damso.env.example` | 환경 변수 템플릿 |
| `backup.sh` | 데이터 백업 |
| `migrate-h2-to-postgres.sh` | H2 → PostgreSQL 마이그레이션 |
| `H2Export.java` | H2 데이터 내보내기 유틸 |
| `export-from-aws.sh` · `aws-export-bundle.sh` | AWS 운영 데이터 내려받기 (집 PC 이전) |
| `migrate-postgres-to-h2.sh` · `H2Import.java` | PostgreSQL → H2 마이그레이션 (집 PC 이전) |
| `windows/` | 집 윈도우 PC 운영 스크립트 (실행·백업·업데이트) |

`.github/workflows/deploy.yml`에 자동 배포 워크플로가 정의되어 있다.
main 에 push 하면 테스트·빌드 후 jar 을 GitHub 릴리스(`latest`)에 올리고,
AWS 시크릿이 설정돼 있으면 이어서 EC2 로 배포한다.

상세 절차는 [DEPLOY.md](docs/DEPLOY.md)(AWS), [LOCAL_SERVER.md](docs/LOCAL_SERVER.md)(집 PC) 참고.

---

## 문서

| 문서 | 내용 |
|---|---|
| [DEPLOY.md](docs/DEPLOY.md) | 배포 절차 — AWS EC2 + PostgreSQL |
| [LOCAL_SERVER.md](docs/LOCAL_SERVER.md) | 집 윈도우 PC 운영 — H2 + Cloudflare Tunnel |
| [PORTFOLIO.md](docs/PORTFOLIO.md) | 기술 소개 — 설계 의사결정과 트러블슈팅 |
| [DEVLOG.md](docs/DEVLOG.md) | 개발 기록 — 무엇을 왜 만들었나 (시간순) |

---

## API 엔드포인트 요약

### 관리자 (`/api/admin/*`)
- `GET/POST /events`, `PATCH /events/{id}/status`, `POST /events/{id}/clear`, `POST /events/start-new`
- `GET/POST/PUT/DELETE /customers`, `PATCH /customers/{id}/status`
- `GET/POST/PUT/DELETE /seats` + `/seats/round{1,2,3}/auto`, `/seats/manual`
- `GET /dashboard`, `/preferences`, `/messages`, `/match-scores`, `/host-info`
- `GET/POST/PUT/DELETE /questions`, `POST /questions/{id}/move`

### 손님 (`/api/guest/*`, 인증 없음)
- `GET /event`, `/questions`
- `POST /customers` (등록), `GET /me`, `POST /login`
- `GET /seats`, `/me/seat`
- `GET /me/candidates`, `POST/GET /preference`
- `GET /me/messages-sent`, `/me/messages-received`, `POST /message`

---

## 자주 쓰는 명령

```bash
# 데이터 완전 초기화
./scripts/stop.sh && rm data/damso.* && ./scripts/start.sh

# 코드 변경 후 재시작
./scripts/stop.sh && ./scripts/start.sh --rebuild

# 백엔드만 다시 빌드
(cd backend && mvn -DskipTests package)

# 프론트 프로덕션 빌드
(cd frontend && npm run build)

# 배포용 jar 하나로 (프론트 빌드 → jar 안 static/ 까지)
./scripts/build.sh
```

---

## 라이선스 / 사용 안내

사내/매장 운영용. 외부 인증·결제 모듈 없음.

손님 개인정보(본명·휴대폰번호·연락처)는 **AES-256-GCM 으로 암호화해 저장한다.**
DB 파일이나 덤프가 새어 나가도 값이 바로 읽히지 않는다
([DataCrypto.java](backend/src/main/java/com/damso/match/global/security/DataCrypto.java)).
다만 **키를 잃으면 되돌릴 수 없다** — 키 파일은 DB 와 함께 백업해야 한다.

암호화가 보관 기간을 대신하지는 않는다. 이벤트 종료 후 데이터 삭제 정책은
별도로 정해서 운영할 것.
