# 프론트엔드 연동 가이드

이 서비스의 API를 브라우저에서 호출해 화면을 만들 때 필요한 내용입니다.
전체 엔드포인트 명세는 Swagger에 있으니, 이 문서는 **명세만 봐서는 알기 어려운 것들**을 다룹니다.

- **Swagger UI**: https://api.handover-card.o-r.kr/swagger-ui/index.html
- **OpenAPI 스펙(JSON)**: https://api.handover-card.o-r.kr/v3/api-docs — 타입이나 클라이언트 코드 생성에 쓸 수 있습니다
- **API 기준 주소**: `https://api.handover-card.o-r.kr`

## 1. CORS

서버가 허용한 출처에서만 브라우저가 API 응답을 받을 수 있습니다. 현재 허용 목록은 환경 변수로
관리되며, **개발 서버 주소를 백엔드 담당자에게 알려주시면 추가**합니다.

기본으로 `http://localhost:5173`(Vite 기본 포트)이 열려 있습니다. 다른 포트를 쓰시면 알려주세요.

허용되지 않은 출처에서 호출하면 사전 요청(preflight)이 403으로 막힙니다. 브라우저 콘솔에
`No 'Access-Control-Allow-Origin' header` 가 뜨면 이 경우입니다.

> Vite 프록시(`server.proxy`)를 쓰셔도 됩니다. 그 경우 브라우저는 개발 서버하고만 통신하므로
> CORS 자체가 발생하지 않습니다. 어느 쪽이든 서버는 지원합니다.

## 2. 인증

### 개발 중에는 Bearer 토큰을 쓰세요

이 서버는 **`Authorization` 헤더와 쿠키를 둘 다** 받습니다. 다만 개발 단계에서는 헤더를 쓰셔야
합니다. `localhost`와 `handover-card.o-r.kr`은 상위 도메인이 달라 브라우저가 cross-site로 보고,
인증 쿠키(`SameSite=Lax`)를 아예 전송하지 않기 때문입니다. CORS를 열어도 이건 해결되지 않습니다.

프론트가 `https://www.handover-card.o-r.kr`에 배포되면 같은 상위 도메인이 되어 쿠키 방식으로
전환할 수 있습니다. 그때는 서버에서 쿠키 `Domain` 속성을 조정합니다.

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

- CORS 오류 → 개발 서버 주소를 백엔드에 알려주고 허용 목록에 추가 요청
- 401이 계속 → 토큰 만료 여부, `Bearer ` 접두사 확인
- 500 → 서버 로그를 봐야 하니 요청 시각과 내용을 백엔드에 전달
