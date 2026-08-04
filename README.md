# Handover_Card
비동기 음성 인계 서비스

## 요구 사항
- JDK 25 (Gradle wrapper 9.6.1 기준, 빌드 툴체인이 25로 고정되어 있어 Gradle 실행용 JDK와 별개로 25가 설치되어 있어야 함)
- MySQL (로컬 또는 Docker)
- OpenAI API Key

## 아키텍처
- 음성 파일 업로드 → 전사/번역(`transcription` 패키지, OpenAI `gpt-4o-mini-transcribe`(STT) + `gpt-4o-mini`(번역) 실연동, `transcription.provider=openai`) → OpenAI 요약(`summarization` 패키지, 실연동) → 인계 카드 저장
- 전체 파이프라인은 비동기(`pipeline.HandoverProcessingPipeline`)로 처리되며, 카드의 `status`를 폴링해서 진행 상황을 확인
- `transcription.TranscriptionService` 인터페이스로 추상화되어 있어, 기본값은 여전히 `mock`이며 `transcription.provider=openai`로 전환 시 실제 STT/번역이 동작. 추후 실시간 Agora RTC 채널 기반 구현체로도 교체 가능 (`transcription.provider=agora`)
- 회원가입/로그인은 Spring Security + JWT(Access/Refresh) 기반(`member`, `auth`, `security` 패키지). `/api/handover-cards/**`를 포함한 모든 API는 `/api/auth/**`를 제외하고 인증 필요. Refresh Token은 DB(`refresh_tokens`)에 저장되어 재발급 시 회전(rotation)되고, 로그아웃/재사용 시 폐기됨

## 실행 방법

```bash
# DB 준비 (예시)
mysql -u root -e "CREATE DATABASE handover_card; CREATE USER 'handover'@'%' IDENTIFIED BY 'handover'; GRANT ALL ON handover_card.* TO 'handover'@'%';"

export DB_USERNAME=handover
export DB_PASSWORD=handover
export OPENAI_API_KEY=sk-...
export TRANSCRIPTION_PROVIDER=openai  # 실제 STT/번역 사용 시 (기본값은 mock)
export JWT_SECRET=...  # 프로덕션에서는 필수 (미설정 시 개발용 기본값 사용, 32바이트 이상)

./gradlew bootRun
```

## API

### 인증 (`/api/auth/**`, 인증 불필요)

- `POST /api/auth/signup` (JSON: `email`, `password`, `name`) → 201 Created
- `POST /api/auth/login` (JSON: `email`, `password`) → `{ accessToken, refreshToken, tokenType, expiresInSeconds }`
- `POST /api/auth/refresh` (JSON: `refreshToken`) → 새 Access/Refresh Token 쌍 (기존 refresh token은 즉시 폐기됨)
- `POST /api/auth/logout` (JSON: `refreshToken`) → 204 No Content, 해당 refresh token 폐기

```bash
curl -s -X POST http://localhost:8080/api/auth/signup \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com","password":"password123","name":"Alex"}'

curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alex@example.com","password":"password123"}'
```

### 인계 카드 (`/api/handover-cards/**`, `Authorization: Bearer <accessToken>` 필요)

- `POST /api/handover-cards` (multipart: `audio`, `senderName`, `receiverName`, `sourceLanguage`, `targetLanguage`) → 202 Accepted
- `GET /api/handover-cards/{id}` → 카드 상세 및 진행 상태 조회

```bash
curl -i -X POST http://localhost:8080/api/handover-cards \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -F "audio=@sample.mp3;type=audio/mpeg" \
  -F "senderName=Alex" -F "receiverName=Minji" \
  -F "sourceLanguage=en" -F "targetLanguage=ko"

curl -s -H "Authorization: Bearer $ACCESS_TOKEN" http://localhost:8080/api/handover-cards/1
```
