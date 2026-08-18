# 프론트엔드 연동 가이드

이 서비스의 API를 브라우저에서 호출해 화면을 만들 때 필요한 내용입니다.
전체 엔드포인트 명세는 Swagger에 있으니, 이 문서는 **명세만 봐서는 알기 어려운 것들**을 다룹니다.

- **Swagger UI**: `https://handover-card.o-r.kr/swagger-ui/index.html`
- **OpenAPI 스펙(JSON)**: `https://handover-card.o-r.kr/v3/api-docs` — 타입이나 클라이언트 코드 생성에 쓸 수 있습니다
- **API 기준 주소**: 없음 — **상대 경로로 호출하세요** (아래)

## 1. 주소와 CORS

**프론트와 API가 한 도메인에서 서비스됩니다.** 정적 파일과 API가 같은 서버의 nginx를 거치며,
경로로 나뉩니다.

```
https://handover-card.o-r.kr/            → 프론트 (여러분이 빌드한 정적 파일)
https://handover-card.o-r.kr/api/**      → API
https://handover-card.o-r.kr/oauth2/**   → 소셜 로그인 흐름
```

그래서 **API 기준 주소를 빈 값으로 두고 상대 경로로 호출하시면 됩니다.**

```
VITE_API_BASE_URL=
```

```js
fetch("/api/handover-cards", { headers: { Authorization: `Bearer ${token}` } })
```

### CORS는 신경 쓰지 않으셔도 됩니다

같은 출처라 브라우저가 교차 출처 요청으로 보지 않고, 서버도 CORS 헤더를 붙이지 않습니다.
사전 요청(preflight)도 발생하지 않습니다. 배포 주소가 바뀔 때마다 백엔드에 허용 목록 추가를
요청하던 절차가 없어집니다.

**로컬 개발에서는 Vite 프록시를 쓰세요.** 코드를 그대로 두고 개발 서버가 API를 대신 호출해 주므로,
로컬과 운영에서 같은 상대 경로가 동작합니다.

```js
// vite.config.js
server: {
  proxy: {
    "/api": { target: "https://handover-card.o-r.kr", changeOrigin: true },
  },
}
```

로컬에서 프록시 없이 운영 API를 직접 부르면 그때는 교차 출처가 되어 막힙니다. 꼭 그렇게 하셔야
하면 개발 서버 주소를 알려주세요 — 서버 허용 목록에 그 주소만 추가합니다.

## 2. 인증

### Bearer 토큰을 쓰세요

이 서버는 **`Authorization` 헤더와 쿠키를 둘 다** 받습니다. 프론트에서는 헤더를 쓰시면 됩니다.

같은 도메인이 되었으므로 쿠키 인증도 기술적으로는 가능해졌지만, 지금은 전환하지 않습니다.
서버 이전과 인증 방식 변경을 동시에 하면 문제가 생겼을 때 원인을 가리기 어렵기 때문입니다.
토큰은 메모리나 스토리지에 직접 보관하세요.

### 흐름

```
POST /api/auth/signup   { email, password, name }        → 201
POST /api/auth/login    { email, password }              → { accessToken, refreshToken, tokenType, expiresInSeconds }
POST /api/auth/refresh  { refreshToken }                 → 새 토큰 쌍
POST /api/auth/logout   { refreshToken }                 → 204
```

이후 모든 요청에 헤더를 붙입니다.

```
Authorization: Bearer <accessToken>
```

- 액세스 토큰 **30분**, 리프레시 토큰 **14일**
- **재발급하면 기존 리프레시 토큰은 즉시 폐기됩니다(회전).** 새로 받은 값으로 교체하세요.
  이미 쓴 리프레시 토큰을 다시 보내면 거부됩니다
- 401을 받으면 refresh를 한 번 시도하고, 그것도 실패하면 로그인 화면으로 보내는 흐름을 권합니다

### 소셜 로그인 (Google / GitHub)

```
GET  /api/auth/oauth2/providers          → 이 서버에 설정된 공급자 목록
                                           (provider, authorizationUri, clientId, scopes)
POST /api/auth/oauth2/{provider}         { code, redirectUri } → 로그인과 같은 토큰 쌍
```

프론트가 공급자로 보내는 인가 요청을 직접 만들고, 돌아온 `code`를 서버에 넘기는 방식입니다.
`redirectUri`는 **인가 코드를 받을 때 쓴 값과 완전히 같아야** 합니다. `state` 검증은 인가 요청을
만든 프론트가 직접 해야 합니다 — 서버는 그 코드가 어느 요청에서 왔는지 알 수 없습니다.

사용할 리다이렉트 URI가 정해지면 백엔드 담당자에게 알려주세요. 공급자 콘솔에 등록이 필요합니다.

## 3. 인계 카드

### 생성은 비동기입니다 — 폴링이 필요합니다

가장 중요한 부분입니다. 업로드 응답은 **처리 완료가 아니라 접수**를 뜻합니다.

```
POST /api/handover-cards        (multipart/form-data)  → 202 Accepted { id, status }
GET  /api/handover-cards/{id}                          → 카드 상세 (status 확인)
```

`status`가 아래 순서로 바뀝니다. `COMPLETED` 또는 `FAILED`가 될 때까지 폴링하세요.

```
RECEIVED → TRANSCRIBING → TRANSCRIBED → SUMMARIZING → COMPLETED
                                                    ↘ FAILED
```

- 음성 길이에 따라 수십 초 이상 걸립니다. 2~3초 간격 폴링을 권합니다
- `FAILED`면 `errorMessage`에 이유가 담깁니다. `POST /api/handover-cards/{id}/reprocess`로 재처리할 수 있습니다
- 완료되면 `transcript`(원문), `translatedText`(번역), `summary`(요약)가 채워집니다

### 업로드 요청 필드

`multipart/form-data`로 보냅니다.

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `audio` | ✅ | 음성 파일. **최대 25MB** |
| `senderName` | ✅ | |
| `receiverName` | ✅ | |
| `sourceLanguage` | ✅ | 아래 언어 코드 |
| `targetLanguage` | ✅ | 아래 언어 코드 |
| `receiverEmail` | | 가입된 회원 이메일이면 그 회원도 카드를 조회할 수 있게 됩니다. 미가입 이메일이면 조용히 무시되고 생성은 성공합니다 |

25MB를 넘기면 413이 옵니다. 브라우저 녹음을 쓰신다면 길이 제한을 두는 편이 좋습니다.

### 언어 코드

`en`(영어) · `ko`(한국어) · `ja`(일본어) · `zh`(중국어) · `es`(스페인어) · `vi`(베트남어)

API 자체는 임의의 문자열을 받지만, 실제로 검증된 조합은 위 목록입니다.

### 조회 · 삭제

```
GET    /api/handover-cards?page=0&size=20   → 내가 owner이거나 receiver인 카드 (최신순, 페이지네이션)
GET    /api/handover-cards/{id}             → 상세
DELETE /api/handover-cards/{id}             → 삭제
```

한 페이지 최대 100건입니다.

## 4. 브라우저 녹음 시 주의

마이크 접근(`getUserMedia`)은 **보안 컨텍스트에서만** 허용됩니다.

- `http://localhost` — 예외로 허용됩니다. 로컬 개발은 그대로 됩니다
- 배포된 프론트는 **반드시 HTTPS**여야 합니다. HTTP로 서비스하면 녹음이 동작하지 않습니다

## 5. 오류 응답 형식

모든 오류가 같은 형태로 옵니다.

```json
{
  "timestamp": "2026-08-11T05:06:27.578Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Failed to store audio file",
  "details": []
}
```

`details`는 입력 검증에 실패했을 때 필드별 메시지가 담깁니다. 화면에 그대로 노출하기보다
`status`로 분기하고 `message`는 참고용으로 쓰시길 권합니다.

| 상태 | 의미 |
| --- | --- |
| 400 | 입력 검증 실패, 저장소 오류 |
| 401 | 토큰 없음/만료/무효 |
| 403 | 권한 없음 (남의 카드 등) |
| 404 | 없는 리소스 |
| 413 | 업로드 용량 초과 |
| 500 | 서버 오류 — 이건 백엔드에 알려주세요 |

## 6. 그 밖의 API

팀(`/api/teams`)과 회원 조회(`/api/members`) 엔드포인트도 있습니다. 명세는 Swagger를 참고하세요.

## 막히면

- CORS 오류 → 상대 경로 대신 절대 주소로 부르고 있지 않은지 확인하세요. 같은 도메인이면 CORS가
  뜰 이유가 없습니다. 로컬에서 운영 API를 직접 부르는 경우라면 Vite 프록시를 쓰거나 개발 서버
  주소를 백엔드에 알려주세요
- 새로고침하면 404 → SPA 라우팅 경로입니다. 서버 설정 문제이니 백엔드에 알려주세요
- 401이 계속 → 토큰 만료 여부, `Bearer ` 접두사 확인
- 500 → 서버 로그를 봐야 하니 요청 시각과 내용을 백엔드에 전달
