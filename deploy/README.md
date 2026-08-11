# 배포 (EC2 단일 인스턴스)

애플리케이션 한 대가 화면(Thymeleaf SSR)과 API를 모두 서비스하는 구성이다.
프론트를 분리하는 구성으로 넘어갈 것을 염두에 두고, WAS는 처음부터 `api.` 서브도메인에 둔다.

```
브라우저 ──HTTPS──> nginx (80/443) ──HTTP──> 앱 컨테이너 (127.0.0.1:8080)
                                                 ├── MySQL (RDS 권장)
                                                 └── S3 (IAM Role)
```

## 1. EC2 준비

- 타입: `t3.small` 최소, `t3.medium` 권장 / 디스크 30GB
- 탄력적 IP(EIP) 할당 — 도메인 A레코드가 물릴 주소라 고정되어야 한다
- 보안 그룹: 22(내 IP만), 80, 443만 연다. **8080은 열지 않는다** (nginx가 루프백으로 프록시)
- 설치: `docker`, `git`, `nginx`, `certbot`

### 스왑을 먼저 잡을 것

`deploy.sh`는 EC2에서 직접 이미지를 빌드한다. Gradle 빌드는 메모리를 꽤 먹어서 2GB 인스턴스에서는
빌드가 OOM으로 죽을 수 있다. 스왑 2GB를 미리 잡아두면 대부분 넘어간다.

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 2. 환경변수 파일

```bash
sudo cp deploy/handover-card.env.example /etc/handover-card.env
sudo chmod 600 /etc/handover-card.env
sudo vi /etc/handover-card.env      # 값 채우기
```

`JWT_SECRET`은 `openssl rand -base64 48`로 만든다. S3 액세스 키는 넣지 않는다 — EC2에 IAM Role을
붙이면 SDK가 알아서 쓴다.

## 3. DB 준비

RDS MySQL 8.0 권장. 기존 DB를 옮겨 오는 경우 아래를 먼저 실행해야 `ddl-auto=validate`가 통과한다.
소셜 전용 회원은 비밀번호가 없기 때문이다.

```sql
ALTER TABLE members MODIFY password VARCHAR(255) NULL;
```

**새 DB라면 최초 1회만** `/etc/handover-card.env`의 `JPA_DDL_AUTO=update` 주석을 풀고 배포해서
스키마를 만든 뒤, 다시 주석 처리하고 재배포한다. 이 값을 켜 둔 채로 운영하면 엔티티를 고칠 때마다
스키마가 말없이 바뀐다.

## 4. 첫 배포

```bash
git clone <저장소> ~/Handover_Card && cd ~/Handover_Card
./deploy/deploy.sh
curl -s localhost:8080/actuator/health     # {"status":"UP"} 확인
```

## 5. nginx + HTTPS

```bash
sudo cp deploy/nginx/handover-card.conf /etc/nginx/conf.d/
sudo vi /etc/nginx/conf.d/handover-card.conf    # server_name을 실제 도메인으로
sudo nginx -t && sudo systemctl reload nginx

sudo certbot --nginx -d api.도메인.com          # 443 블록과 리다이렉트를 자동으로 채워 준다
```

HTTPS는 선택이 아니다. 브라우저 녹음(`getUserMedia`)과 `Secure` 쿠키가 둘 다 HTTPS를 요구한다.

## 6. 소셜 로그인 리다이렉트 URI 등록

Google Cloud Console / GitHub Developer Settings에 등록한다. 나중에 프론트를 분리할 때 쓸 주소까지
지금 같이 넣어두면 콘솔을 다시 안 건드려도 된다.

```
https://api.도메인.com/login/oauth2/code/google
https://api.도메인.com/login/oauth2/code/github
https://www.도메인.com/oauth2/callback          ← 프론트 분리 후 사용
```

## 7. 이후 배포

```bash
cd ~/Handover_Card && git pull && ./deploy/deploy.sh
```

헬스체크가 통과하지 못하면 직전 이미지로 자동 롤백한다. 다만 롤백은 **잘못된 코드**를 되돌릴 뿐이고,
환경변수 파일은 그대로 쓰기 때문에 **설정 오류는 롤백해도 낫지 않는다.** 그때는 `/etc/handover-card.env`를
고치고 다시 배포해야 한다.

## 알아둘 것

- 인스턴스 한 대라 배포할 때마다 짧게 끊긴다. `server.shutdown: graceful`이 켜져 있어 처리 중이던
  카드는 최대 30초까지 마무리되지만, 그 시점에 진행 중이던 작업은 재시작 후 `PROCESSING`으로 남는다.
  트래픽이 적은 시간대에 배포한다.
- 로그는 `docker logs -f handover-card`. 컨테이너에 10MB × 3개로 로테이션을 걸어 두었다.
