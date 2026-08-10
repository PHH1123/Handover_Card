# Handover_Card
비동기 음성 인계 서비스

## 요구 사항
- JDK 25 (Gradle wrapper 9.6.1 기준, 빌드 툴체인이 25로 고정되어 있어 Gradle 실행용 JDK와 별개로 25가 설치되어 있어야 함)
- Docker (MySQL을 `docker-compose`로 실행)
- OpenAI API Key

## 아키텍처
- 음성 파일 업로드 → 전사/번역(`transcription` 패키지, OpenAI `gpt-4o-mini-transcribe`(STT) + `gpt-4o-mini`(번역) 실연동, `transcription.provider=openai`) → 요약(`summarization` 패키지, `gpt-4o-mini`, `summarization.provider=openai`) → 인계 카드 저장
- 전체 파이프라인은 비동기(`pipeline.HandoverProcessingPipeline`)로 처리되며, 카드의 `status`를 폴링해서 진행 상황을 확인
- `transcription.TranscriptionService`/`summarization.SummarizationService` 둘 다 `mock`/`openai` provider 스위치로 추상화됨. 기본값(코드 레벨)은 둘 다 `mock`이며, 로컬 실행용 `application.yml`은 `transcription.provider=mock`(테스트 환경 보호) / `summarization.provider=openai`(현행 실동작 유지)로 서로 다르게 설정되어 있음. 테스트 프로필(`application-test.yml`)은 둘 다 `mock`으로 강제해 네트워크 호출 없이 동작. 추후 실시간 Agora RTC 채널 기반 전사 구현체로도 교체 가능 (`transcription.provider=agora`)
- 회원가입/로그인은 Spring Security + JWT(Access/Refresh) 기반(`member`, `auth`, `security` 패키지). `/api/handover-cards/**`를 포함한 모든 API는 `/api/auth/**`를 제외하고 인증 필요. Refresh Token은 DB(`refresh_tokens`)에 저장되어 재발급 시 회전(rotation)되고, 로그아웃/재사용 시 폐기됨. Google/GitHub 소셜 로그인(`auth.oauth2` 패키지)도 지원하며, 공급자 인증이 끝나면 같은 JWT를 발급해 이후 흐름은 로그인 방식을 구분하지 않음 ([소셜 로그인](#소셜-로그인-oauth-20))
- 카드는 생성자(owner)만 접근 가능한 게 기본이지만, 업로드 시 `receiverEmail`이 가입된 회원 이메일과 일치하면 그 회원도 조회 가능 (미가입 이메일이면 조용히 무시되고 카드 생성은 그대로 성공). `GET /api/handover-cards`로 본인이 owner이거나 receiver인 카드 전체 목록 조회 가능

## 실행 방법

```bash
# DB와 오디오 저장소 준비 (docker-compose.yml 참고)
docker compose up -d db storage storage-init

export DB_USERNAME=handover
export DB_PASSWORD=handover
export OPENAI_API_KEY=sk-...
export TRANSCRIPTION_PROVIDER=openai  # 실제 STT/번역 사용 시 (기본값은 mock)
export SUMMARIZATION_PROVIDER=mock  # 요약도 mock으로 돌리고 싶을 때 (기본값은 openai)
export JWT_SECRET=...  # 프로덕션에서는 필수 (미설정 시 개발용 기본값 사용, 32바이트 이상)

./gradlew bootRun
```

DB 계정/데이터베이스는 컨테이너 최초 기동 시 `docker-compose.yml`의 환경변수(`DB_USERNAME`/`DB_PASSWORD`, 기본값 `handover`/`handover`)로 자동 생성됩니다. `.env` 파일을 사용하면 `set -a && source .env && set +a`로 환경변수를 한 번에 불러올 수 있습니다.

DB를 내리려면 `docker compose down` (데이터까지 삭제하려면 `docker compose down -v`).

## 오디오 저장소

업로드된 음성은 `AudioStorageService` 뒤에 감춰져 있고 `handover.storage.provider`로 구현체를 고릅니다.

| provider | 저장 위치 | 용도 |
| --- | --- | --- |
| `s3` (기본값) | S3 호환 저장소 | 개발은 docker-compose의 MinIO, 운영은 AWS S3 |
| `local` | `./data/audio` | 테스트, 그리고 저장소를 띄우기 번거로울 때 |

개발 환경에서 굳이 MinIO를 쓰는 이유는, 로컬 디스크로 개발하면 **S3 경로가 배포 전까지 한 번도 실행되지 않아**
권한·키 네이밍·네트워크 오류 같은 문제가 전부 운영에서 처음 터지기 때문입니다. 엔드포인트만 다르고
애플리케이션이 타는 코드는 개발과 운영이 같습니다.

- MinIO 웹 콘솔: http://localhost:9001 (기본 계정 `handover` / `handover-secret`)
- 저장소 없이 띄우려면 `STORAGE_PROVIDER=local`

운영(AWS S3)에서는 `S3_ENDPOINT`를 비우면 실제 S3로 붙고, `S3_ACCESS_KEY`/`S3_SECRET_KEY`를 비우면
인스턴스 역할 등 AWS SDK 기본 자격증명 체인을 사용합니다. `S3_PATH_STYLE=false`로 두는 것을 권장합니다.

카드에 저장되는 값은 두 구현체 모두 같은 형식의 문자열(로컬 파일명 · S3 객체 키)이라, provider를 바꿔도
이미 저장된 값의 의미가 달라지지 않습니다. 다만 **기존 파일이 자동으로 옮겨지지는 않습니다.**

## 확인용 웹 화면 (Thymeleaf SSR)

API를 직접 호출하지 않고 브라우저에서 전체 기능을 확인할 수 있는 서버 사이드 렌더링 화면이 있습니다.

- http://localhost:8080 (미로그인 시 로그인 화면으로 이동)

| 경로 | 기능 |
| --- | --- |
| `/` | `/web/cards`로 리다이렉트 |
| `/web/signup` | 회원가입 |
| `/web/login` | 로그인 (액세스/리프레시 토큰을 HttpOnly 쿠키로 발급). 설정된 경우 Google/GitHub 로그인 버튼도 표시 |
| `/web/cards` | 음성 업로드 폼 + 내가 접근 가능한 카드 목록(페이지네이션) |
| `/web/cards/{id}` | 카드 상세 (전사/번역/요약, 삭제, FAILED일 때 재처리) |

업로드 폼의 원본/번역 언어는 `web.SupportedLanguage`에 정의된 목록(영어/한국어/일본어/중국어/스페인어/베트남어)에서
선택하며 기본값은 영어 → 한국어입니다. API는 기존대로 임의의 언어 코드 문자열을 받습니다.

음성은 **파일 업로드**와 **브라우저 직접 녹음** 두 방식을 지원합니다. 녹음은 별도 라이브러리 없이 브라우저의
`MediaRecorder`를 사용하며, 녹음본을 파일 input에 주입하기 때문에 서버로는 두 방식 모두 동일한 multipart 업로드로
전송됩니다. 녹음 후 제출 전에 미리듣기로 확인하고 다시 녹음할 수 있습니다.

> 마이크 접근(`getUserMedia`)은 보안 컨텍스트에서만 허용됩니다. `localhost`는 예외라 로컬 개발은 그대로 되지만,
> **배포 시 HTTP로 서비스하면 녹음 기능이 동작하지 않으므로 HTTPS가 필요합니다.** 지원하지 않는 환경에서는
> 녹음 탭에 안내 문구가 표시되고 파일 업로드로 대체할 수 있습니다.

녹음 결과 포맷은 브라우저마다 달라(Chrome·Firefox `webm`, Safari `mp4`) 저장소 허용 확장자에 둘 다 포함되어
있습니다(`LocalFileSystemAudioStorageService`). OpenAI 전사 API가 두 포맷을 모두 지원하므로 서버 측 변환은 없습니다.

REST API와 동일한 서비스 계층·JWT를 그대로 사용하며, 브라우저가 `Authorization` 헤더를 붙일 수 없으므로
토큰만 쿠키로 주고받습니다(`JwtAuthenticationFilter`가 헤더 → 쿠키 순으로 토큰을 찾음). 카드 상태가 처리 중이면
상세 화면이 3초마다 자동 새로고침되어 비동기 파이프라인의 진행 상황을 볼 수 있습니다.

로컬에서 OpenAI 호출 없이 화면만 확인하려면 `TRANSCRIPTION_PROVIDER=mock SUMMARIZATION_PROVIDER=mock`으로 실행하세요.

## 소셜 로그인 (OAuth 2.0)

Google·GitHub 계정으로 로그인할 수 있습니다. 인증이 끝나면 **이메일/비밀번호 로그인과 똑같은 JWT
Access/Refresh Token**을 발급하므로, 그 뒤의 화면과 API는 로그인 방식을 구분하지 않습니다.

| 흐름 | 시작점 | 설명 |
| --- | --- | --- |
| 브라우저 | `GET /oauth2/authorization/{google\|github}` | 로그인 화면의 버튼. 공급자에 다녀온 뒤 토큰 쿠키를 심고 `/web/cards`로 이동 |
| API 클라이언트 | `POST /api/auth/oauth2/{google\|github}` | 클라이언트가 받아 온 인가 코드를 넘기면 토큰을 JSON으로 반환 |

두 흐름 모두 클라이언트 시크릿은 서버 밖으로 나가지 않는 Authorization Code 방식이며, 토큰 교환과
회원 연동 코드는 한 곳(`auth.oauth2`)을 공유합니다.

### 계정 연동 규칙

1. 이미 연결된 소셜 계정이면 그 회원으로 로그인합니다. 공급자가 알려주는 이메일이 나중에 바뀌어도
   불변 식별자(Google `sub`, GitHub `id`)로 찾으므로 계정이 갈라지지 않습니다.
2. 처음 보는 소셜 계정인데 같은 이메일의 회원이 있으면 그 회원에 연결합니다.
3. 회원도 없으면 비밀번호 없는 회원을 새로 만듭니다.

2·3번은 **공급자가 소유를 확인해 준 이메일일 때만** 합니다(Google `email_verified`, GitHub의 주 이메일 +
`verified`). 확인되지 않은 이메일을 믿으면 남의 주소를 적어 둔 소셜 계정으로 기존 회원에 올라탈 수 있기
때문입니다. GitHub은 `/user` 응답의 이메일이 비공개이거나 확인 여부를 알려주지 않아 `user:email` 스코프로
`/user/emails`를 한 번 더 호출합니다.

연결 정보는 `social_accounts` 테이블에 쌓이며 한 회원이 Google과 GitHub을 모두 연결할 수 있습니다.
소셜로만 가입한 회원은 `members.password`가 비어 있어 이메일/비밀번호 로그인은 할 수 없습니다.

### 설정

클라이언트 ID/시크릿은 각자 발급받는 값이라 저장소에 넣지 않았습니다. **환경 변수를 넣지 않아도
애플리케이션은 그대로 뜨고, 설정하지 않은 공급자는 로그인 화면에 버튼이 나오지 않습니다.**

```bash
export GOOGLE_CLIENT_ID=...
export GOOGLE_CLIENT_SECRET=...
export GITHUB_CLIENT_ID=...
export GITHUB_CLIENT_SECRET=...
```

> 쓰지 않을 공급자는 **변수 자체를 정의하지 마세요.** `GOOGLE_CLIENT_ID=`처럼 빈 값으로 두면
> Spring이 `Client id must not be empty`로 기동에 실패합니다(`.env`에서는 줄째로 주석 처리).

발급받는 곳과 등록할 리다이렉트 URI(브라우저 흐름 기준)는 다음과 같습니다.

| 공급자 | 발급 | 리다이렉트 URI |
| --- | --- | --- |
| Google | [Google Cloud Console](https://console.cloud.google.com/apis/credentials) → 사용자 인증 정보 → OAuth 클라이언트 ID(웹 애플리케이션) | `http://localhost:8080/login/oauth2/code/google` |
| GitHub | [GitHub Settings](https://github.com/settings/developers) → Developer settings → OAuth Apps | `http://localhost:8080/login/oauth2/code/github` |

배포 시에는 같은 경로의 실제 도메인(HTTPS)을 리다이렉트 URI로 추가 등록해야 합니다.

> **기존 DB가 있다면 한 번 실행해야 하는 마이그레이션이 있습니다.** 소셜 전용 회원은 비밀번호가 없어
> `members.password`가 NULL을 허용해야 하는데, `ddl-auto=update`는 이미 만들어진 열의 NOT NULL 제약을
> 풀어 주지 않습니다. 새로 만든 DB는 해당 없습니다.
>
> ```sql
> ALTER TABLE members MODIFY password VARCHAR(255) NULL;
> ```

Google은 `openid`를 뺀 `profile`, `email` 스코프만 요청합니다. OIDC 대신 일반 OAuth 2.0 흐름이 되어
"액세스 토큰으로 사용자 정보 엔드포인트 호출"이라는 한 갈래로 GitHub과 같은 코드를 태울 수 있기 때문입니다.

## API

애플리케이션 실행 후 아래에서 전체 API 문서(Swagger UI)를 확인할 수 있습니다. 우측 상단 Authorize 버튼에
로그인으로 발급받은 accessToken을 입력하면 인증이 필요한 API도 바로 호출해볼 수 있습니다.

- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI 스펙(JSON): http://localhost:8080/v3/api-docs

### 인증 (`/api/auth/**`, 인증 불필요)

- `POST /api/auth/signup` (JSON: `email`, `password`, `name`) → 201 Created
- `POST /api/auth/login` (JSON: `email`, `password`) → `{ accessToken, refreshToken, tokenType, expiresInSeconds }`
- `POST /api/auth/refresh` (JSON: `refreshToken`) → 새 Access/Refresh Token 쌍 (기존 refresh token은 즉시 폐기됨)
- `POST /api/auth/logout` (JSON: `refreshToken`) → 204 No Content, 해당 refresh token 폐기
- `GET /api/auth/oauth2/providers` → 이 서버에 설정된 소셜 로그인 공급자 목록 (`provider`, `authorizationUri`, `clientId`, `scopes`)
- `POST /api/auth/oauth2/{provider}` (JSON: `code`, `redirectUri`) → 로그인과 같은 형태의 Access/Refresh Token

```bash
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com","password":"password123","name":"Alex"}'

curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com","password":"password123"}'
```

소셜 로그인은 클라이언트가 공급자에게 받은 인가 코드를 넘기면 서버가 토큰 교환까지 마치고 우리 토큰을
돌려주는 방식입니다. `redirectUri`는 인가 코드를 받을 때 쓴 값과 **똑같아야** 하며, `state` 검증은 인가
요청을 만든 클라이언트가 직접 해야 합니다(서버는 그 코드가 어느 요청에서 왔는지 알 수 없습니다).

```bash
curl -s -X POST http://localhost:8080/api/auth/oauth2/google \
  -H "Content-Type: application/json" \
  -d '{"code":"4/0Ab_5qlk...","redirectUri":"https://app.example.com/oauth2/callback"}'
```

### 인계 카드 (`/api/handover-cards/**`, `Authorization: Bearer <accessToken>` 필요)

- `POST /api/handover-cards` (multipart: `audio`, `senderName`, `receiverName`, `sourceLanguage`, `targetLanguage`, 선택: `receiverEmail`) → 202 Accepted
- `GET /api/handover-cards/{id}` → 카드 상세 및 진행 상태 조회 (owner 또는 연결된 receiver만)
- `GET /api/handover-cards` → 본인이 owner이거나 receiver인 카드 전체 목록 (최신순)

```bash
curl -i -X POST http://localhost:8080/api/handover-cards \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "audio=@sample.mp3;type=audio/mpeg" \
  -F "senderName=Alex" -F "receiverName=Minji" \
  -F "sourceLanguage=en" -F "targetLanguage=ko" \
  -F "receiverEmail=minji@example.com"

curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/handover-cards/1
curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/handover-cards
```
