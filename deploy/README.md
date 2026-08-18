# 배포 (가비아 클라우드 단일 서버)

서버 한 대가 프론트엔드(정적 파일)와 API를 **같은 도메인**에서 서비스하는 구성이다.
브라우저 입장에서 출처가 하나라 CORS가 발생하지 않는다.

```
                    ┌─ /            → /var/www/handover-card (SPA 빌드 산출물)
브라우저 ──HTTPS──> nginx (80/443) ─┤
                    └─ /api/** 등   → 앱 컨테이너 (127.0.0.1:8080)
                                          ├── MySQL (AWS RDS, 공인망)
                                          └── S3 (IAM 사용자 액세스 키)
```

> **데이터는 AWS에 그대로 두고 서버만 가비아로 옮긴 구성이다.** 그래서 두 가지가 EC2 때와 다르다.
> - S3: IAM **인스턴스 역할을 쓸 수 없다.** 액세스 키를 발급받아 환경변수로 넣는다 (4절)
> - RDS: 연결이 **공인망을 지나간다.** 퍼블릭 액세스 + IP 화이트리스트가 필요하다 (5절)
>
> 나중에 DB와 파일까지 서버 안으로 들여오면 두 항목 모두 없어진다.

## 1. 서버 준비 (Rocky Linux 8.10)

가비아 클라우드(g클라우드)에서 **root 접근이 되는 가상서버**를 만든다. 웹호스팅 상품으로는
Java 프로세스를 상주시킬 수 없어 이 구성이 성립하지 않는다.

- 사양: vCPU 2 / 메모리 4GB 권장 (최소 2GB) / 디스크 50GB
- OS: **Rocky Linux 8.10** 기준. 아래 명령은 `dnf`·`firewalld`·SELinux를 전제로 한다
- 공인 IP 고정 — 도메인 A레코드가 물릴 주소이자 RDS 화이트리스트에 등록할 주소다

### 1-1. 방화벽 (firewalld) — 도커보다 먼저

**순서가 중요하다.** Rocky의 firewalld는 nftables 기반인데 Docker는 자기 규칙을 iptables로
심는다. 도커가 뜬 뒤에 `firewall-cmd --reload`를 하면 그 규칙이 통째로 날아가 **컨테이너가
바깥으로 나가지 못하게 된다.** 그래서 방화벽을 먼저 잡고 도커를 설치한다.

가비아는 **콘솔의 방화벽과 서버 안의 방화벽이 별개**다. 둘 다 열려 있어야 접속된다.
22(내 IP만), 80, 443만 연다. **8080은 열지 않는다** — nginx가 루프백으로 프록시하므로
외부에서 직접 닿을 이유가 없다.

```bash
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload
sudo firewall-cmd --list-all
```

> `--permanent` 없이 실행한 규칙은 재부팅하면 사라진다. 반대로 `--permanent`만 주고 `--reload`를
> 하지 않으면 지금 세션에는 적용되지 않는다. 위처럼 둘 다 해야 한다.

**나중에 방화벽 규칙을 바꿀 일이 생기면 `--reload` 뒤에 반드시 도커를 재시작한다.**

```bash
sudo firewall-cmd --reload && sudo systemctl restart docker
```

### 1-2. 패키지 설치

Rocky 8에는 `docker` 패키지가 없다. 기본으로 들어 있는 podman과 충돌하므로 그것부터 걷어내고
Docker 공식 저장소를 추가한다.

```bash
sudo dnf -y update
sudo dnf -y install dnf-plugins-core epel-release

# podman/buildah가 깔려 있으면 docker-ce와 파일이 겹쳐 설치가 실패한다
sudo dnf -y remove podman buildah runc || true

sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf -y install docker-ce docker-ce-cli containerd.io
sudo systemctl enable --now docker

sudo dnf -y install git nginx bind-utils
sudo dnf -y install certbot python3-certbot-nginx    # epel-release가 먼저 설치되어 있어야 한다

sudo usermod -aG docker $USER    # 다시 로그인해야 적용된다
```

`bind-utils`는 `dig`를 쓰기 위한 것이다. 최소 설치 이미지에는 들어 있지 않다.

설치 직후 **컨테이너가 바깥으로 나갈 수 있는지 먼저 확인한다.** 이게 막혀 있으면 나중에
이미지 빌드가 `UnknownHostException: services.gradle.org`로 죽는데, 로그만 봐서는 Gradle
문제처럼 보여서 엉뚱한 곳을 뒤지게 된다.

```bash
docker run --rm alpine ping -c1 1.1.1.1                # 네트워크
docker run --rm alpine nslookup services.gradle.org    # DNS
```

둘 다 성공해야 다음으로 넘어간다. 실패하면 아래 "컨테이너가 바깥으로 못 나갈 때"를 본다.

#### 컨테이너가 바깥으로 못 나갈 때

호스트는 멀쩡한데(이미지 pull은 되는데) 컨테이너 안에서만 실패하는 게 특징이다.
`ping`과 `nslookup` 중 어느 쪽이 실패하느냐로 원인이 갈린다.

**`ping`부터 실패** — firewalld가 도커 규칙을 지웠다. 위 1-1의 순서를 지키지 않았을 때 그렇다.

```bash
sudo systemctl restart docker
```

**`ping`은 되고 `nslookup`만 실패** — 컨테이너가 쓰는 DNS가 막혔다. 호스트
`/etc/resolv.conf`가 `127.0.0.53`(systemd-resolved 스텁)을 가리키면 도커는 그 주소를 컨테이너에
넘길 수 없어 `8.8.8.8`로 대체하는데, 외부 DNS로 나가는 경로가 막혀 있으면 컨테이너만 이름
해석에 실패한다. 실제 리졸버를 도커에 직접 알려준다.

```bash
resolvectl status | grep 'DNS Server'      # 가비아가 준 DNS 주소 확인

sudo tee /etc/docker/daemon.json <<'EOF'
{ "dns": ["확인한_DNS_IP", "168.126.63.1"] }
EOF
sudo systemctl restart docker
```

### 1-3. SELinux — 이 구성에서 반드시 건드려야 한다

Rocky는 SELinux가 기본 `enforcing`이다. **끄지 말고** 필요한 두 가지만 허용한다.
아래를 하지 않으면 애플리케이션과 nginx 각각은 정상인데 **브라우저에서만 502가 뜬다.**

```bash
getenforce        # Enforcing 이 정상

# (1) nginx가 127.0.0.1:8080으로 프록시할 수 있게 한다.
#     이게 없으면 nginx 에러 로그에 (13: Permission denied) 와 함께 502가 찍힌다.
sudo setsebool -P httpd_can_network_connect 1

# (2) /var/www 아래의 정적 파일을 nginx가 읽을 수 있게 라벨을 지정한다.
#     RHEL 계열의 기본 웹 루트는 /usr/share/nginx/html 이라 /var/www 는 라벨이 다르다.
#     릴리스 디렉터리와 그것을 가리키는 심볼릭 링크가 모두 대상이라 /var/www 전체에 건다.
sudo dnf -y install policycoreutils-python-utils
sudo mkdir -p /var/www/handover-card-releases
sudo semanage fcontext -a -t httpd_sys_content_t "/var/www(/.*)?"
sudo restorecon -Rv /var/www
```

`/var/www/handover-card` 자체는 아직 만들지 않는다. 프론트를 처음 배포할 때 `deploy-web.sh`가
릴리스 디렉터리를 가리키는 심볼릭 링크로 만든다(8절).

`semanage fcontext`는 **규칙만 등록**하고 이미 있는 파일에는 적용되지 않는다. 실제 라벨을 입히는
건 `restorecon`이다. 그래서 `deploy-web.sh`가 릴리스를 만들 때마다 `restorecon`을 다시 돌린다.

> SELinux를 `permissive`로 바꾸면 당장은 되지만, 그건 문제를 없앤 게 아니라 로그로만 남기게 한
> 것이다. 위 두 줄이면 끝나므로 끄지 않는 편이 낫다.

### 스왑을 먼저 잡을 것

`deploy.sh`는 서버에서 직접 이미지를 빌드한다. Gradle 빌드는 메모리를 꽤 먹어서 2GB 서버에서는
빌드가 OOM으로 죽을 수 있다. 스왑 2GB를 미리 잡아두면 대부분 넘어간다.

```bash
sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 2. 도메인 연결

도메인은 `handover-card.o-r.kr` 하나만 쓴다. [내도메인.한국](https://내도메인.한국)에서 발급받은
주소이므로 DNS도 거기서 관리한다. `www.` 는 두지 않는다 — 별도 레코드를 하나 더 만들고 인증서에도
같이 넣어야 하는데, 얻는 게 없다.

| 호스트 | 타입 | 값 |
| --- | --- | --- |
| `handover-card.o-r.kr` | A | 가비아 서버 공인 IP |

**기존 `api.handover-card.o-r.kr` 레코드는 전환이 끝날 때까지 EC2를 가리킨 채로 둔다.** 새 서버에서
동작을 확인하고 프론트를 올린 뒤에 지우면, 중간에 문제가 생겨도 돌아갈 곳이 남는다.

전파를 확인하고 다음으로 넘어간다. 인증서 발급이 이 레코드로 소유를 검증하기 때문에, 전파 전에
certbot을 돌리면 실패한다.

```bash
dig +short handover-card.o-r.kr     # 가비아 서버 IP가 나와야 한다
```

## 3. 환경변수 파일

```bash
sudo cp deploy/handover-card.env.example /etc/handover-card.env
sudo chown $USER:$USER /etc/handover-card.env
sudo chmod 600 /etc/handover-card.env
vi /etc/handover-card.env      # 값 채우기
```

소유자를 root로 두면 배포할 때 `open /etc/handover-card.env: permission denied`가 난다.
`--env-file`은 도커 데몬이 아니라 **도커 CLI가 읽으므로** 명령을 실행하는 사용자에게 읽기 권한이
필요하다.

- `JWT_SECRET`은 `openssl rand -base64 48`로 만든다
- `CORS_ALLOWED_ORIGINS`는 **비워 둔다.** 단일 오리진이라 필요 없다
- `DB_URL`에는 RDS **엔드포인트**를 쓴다. 콘솔에 같이 보이는 ARN(`arn:aws:rds:...`)은 리소스
  식별자일 뿐 접속 주소가 아니라서, 넣으면 호스트를 찾지 못해 기동에 실패한다

## 4. S3 접근 권한 (액세스 키)

EC2에서 쓰던 IAM 인스턴스 역할은 **여기서 동작하지 않는다.** 역할은 EC2 메타데이터
주소(`169.254.169.254`)에서 임시 자격증명을 받아 오는 방식인데, 그 주소는 AWS 인스턴스 안에서만
응답하기 때문이다. 가비아 서버에서는 응답이 없어 자격증명 체인이 비게 되고, 앱은 정상 기동하다가
**업로드할 때만** "Failed to store audio file"로 실패한다.

1. IAM → 정책 생성. 앱이 실제로 쓰는 연산만 준다 (`HeadObject`는 `s3:GetObject`로 인가된다):

   ```json
   {
     "Version": "2012-10-17",
     "Statement": [{
       "Effect": "Allow",
       "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
       "Resource": "arn:aws:s3:::버킷이름/*"
     }]
   }
   ```

2. IAM → **사용자** 생성(콘솔 접근 권한 없이, 프로그래밍 방식만) → 위 정책 연결
3. 액세스 키 발급 → `/etc/handover-card.env`의 `S3_ACCESS_KEY` / `S3_SECRET_KEY`에 넣는다

> 키는 발급 화면을 벗어나면 다시 볼 수 없다. 잃어버리면 새로 만들고 옛 키는 비활성화한다.
> 그리고 이 값이 담긴 `/etc/handover-card.env`는 절대 저장소에 커밋하지 않는다 — 역할과 달리
> 액세스 키는 새어 나가면 만료되지 않는다.

확인 (서버에서):

```bash
docker run --rm --env-file /etc/handover-card.env amazon/aws-cli \
  --region $S3_REGION s3api put-object --bucket 버킷이름 --key perm-check.txt
```

> 권한 확인은 `aws s3 ls`가 아니라 `put-object`로 한다. 위 정책에는 목록 조회(`ListBucket`)가
> 없어서, 권한이 멀쩡해도 `s3 ls`는 `AccessDenied`가 난다.

## 5. DB 준비 (AWS RDS)

서버가 AWS 밖에 있으므로 RDS를 밖에서 접속할 수 있게 만들어야 하는데, **세 가지가 모두 맞아야
한다.** 하나라도 빠지면 증상이 똑같이 "10초 타임아웃"으로 나와서 어느 것이 문제인지 구분되지
않는다.

1. **퍼블릭 액세스 = 예** — 엔드포인트가 공인 IP로 해석되게 한다
2. **보안 그룹 인바운드** — 3306, 소스는 **이 서버의 공인 IP `/32`** 하나만.
   `0.0.0.0/0`으로 열지 않는다. 인터넷 전체에 MySQL 포트를 여는 것이고 자동 스캐너가 곧바로 붙는다
3. **서브넷 라우팅** — RDS가 앉은 서브넷의 라우팅 테이블에 `0.0.0.0/0 → igw-...` 가 있어야 한다

3번이 가장 놓치기 쉽다. **서브넷 그룹에는 AZ별로 서브넷이 여러 개 들어 있는데 인스턴스는 그중
하나에만 있고, 서브넷마다 라우팅 테이블이 다를 수 있다.** RDS 상세의 **가용 영역**을 먼저 확인하고
그 AZ의 서브넷만 봐야 한다. 다른 서브넷의 라우팅 테이블을 보고 "IGW 있음"이라고 판단하기 쉽다.

확인 순서:

```bash
curl -s ifconfig.me                                    # 보안 그룹에 넣을 IP
dig +short <rds-endpoint>                              # 공인 IP로 풀려야 한다
time timeout 10 bash -c '</dev/tcp/<rds-endpoint>/3306'   # 열렸는지
```

마지막 명령이 **10초를 꽉 채우고 실패하면** 패킷이 조용히 버려지는 것이다(보안 그룹·NACL·라우팅).
즉시 `Connection refused`면 도달은 했다는 뜻이라 다른 문제다.

어디서 막혔는지 확실히 알고 싶으면 VPC 콘솔의 **Reachability Analyzer**를 쓴다. 소스를 인터넷
게이트웨이, 대상을 RDS의 네트워크 인터페이스, 포트 3306으로 두고 돌리면 **차단한 구성 요소를
이름으로 찍어 준다.** 콘솔을 헤매는 것보다 빠르다.

`DB_URL`의 `useSSL=true`를 유지할 것. 연결이 공인망을 지나가므로 이게 없으면 쿼리와 자격증명이
평문으로 오간다.

기존 DB를 그대로 쓰는 경우 아래를 먼저 실행해야 `ddl-auto=validate`가 통과한다. 소셜 전용 회원은
비밀번호가 없기 때문이다.

```sql
ALTER TABLE members MODIFY password VARCHAR(255) NULL;
```

**새 DB라면 최초 1회만** `/etc/handover-card.env`의 `SPRING_JPA_HIBERNATE_DDL_AUTO=update` 주석을
풀고 배포해서 스키마를 만든 뒤, 다시 주석 처리하고 재배포한다. 이 값을 켜 둔 채로 운영하면
엔티티를 고칠 때마다 스키마가 말없이 바뀐다.

`#`을 확실히 지웠는지 확인할 것. 남아 있으면 도커가 그 줄을 건너뛰어 `validate`로 뜨고,
`Schema validation: missing table [...]`로 기동에 실패한다. 실제로 전달됐는지는 이렇게 본다:

```bash
docker run --rm --env-file /etc/handover-card.env alpine sh -c 'echo "[$SPRING_JPA_HIBERNATE_DDL_AUTO]"'
```

## 6. 백엔드 첫 배포

```bash
git clone <저장소> ~/Handover_Card && cd ~/Handover_Card
./deploy/deploy.sh
curl -s localhost:8080/actuator/health     # {"status":"UP"} 확인
```

## 7. nginx + HTTPS

설정이 세 파일로 나뉘어 있다. 나눈 이유는 **certbot이 `conf.d/handover-card.conf`를 직접 고치기
때문**이다. 인증서를 받고 나면 그 파일에 `listen 443 ssl`과 인증서 경로가 들어가는데, 저장소
버전으로 덮어쓰면 그게 사라져 HTTPS가 통째로 없어진다.

| 파일 | 놓는 곳 | 갱신 |
| --- | --- | --- |
| `handover-card.conf` | `/etc/nginx/conf.d/` | **최초 1회만.** 이후 덮어쓰지 않는다 |
| `handover-card-locations.conf` | `/etc/nginx/snippets/` | 경로 규칙이 바뀔 때마다 덮어쓴다 |
| `handover-card-proxy.conf` | `/etc/nginx/snippets/` | 프록시 설정이 바뀔 때마다 덮어쓴다 |

`/etc/nginx/snippets/`는 데비안 계열의 관례라 Rocky에는 없다. 직접 만든다. snippet을 빠뜨리면
`nginx -t`가 `include` 대상을 찾지 못해 실패한다.

```bash
sudo mkdir -p /etc/nginx/snippets
sudo cp deploy/nginx/handover-card-proxy.conf /etc/nginx/snippets/
sudo cp deploy/nginx/handover-card-locations.conf /etc/nginx/snippets/
sudo cp deploy/nginx/handover-card.conf /etc/nginx/conf.d/      # 최초 1회만

sudo nginx -t && sudo systemctl enable --now nginx
```

### Rocky 기본 server 블록을 먼저 치울 것

Rocky의 `/etc/nginx/nginx.conf` 안에는 80 포트를 잡는 `default_server` 블록이 이미 들어 있다.
우리 설정과 `server_name`이 달라 정상 요청은 제대로 찾아가지만, **certbot이 어느 블록에 인증서를
넣을지 헷갈려 엉뚱한 곳을 고치는 일이 있다.** `/etc/nginx/nginx.conf`에서 그 `server { ... }`
블록을 통째로 주석 처리하고 `nginx -t`를 다시 돌린 뒤 다음으로 넘어간다.

```bash
sudo vi /etc/nginx/nginx.conf     # listen 80 default_server 가 있는 server 블록 주석 처리
sudo nginx -t && sudo systemctl reload nginx
```

### 인증서 발급

```bash
sudo certbot --nginx -d handover-card.o-r.kr
```

certbot이 443 블록과 80→443 리다이렉트를 자동으로 채워 준다.

HTTPS는 선택이 아니다. 브라우저 녹음(`getUserMedia`)과 `Secure` 쿠키가 둘 다 HTTPS를 요구한다.

발급 후 자동 갱신 타이머가 도는지 확인한다. Rocky에서는 systemd 타이머로 들어간다.

```bash
systemctl list-timers | grep certbot
sudo certbot renew --dry-run
```

### 실수로 conf.d/handover-card.conf 를 덮어썼다면

증상은 "사이트가 갑자기 안 열림"이다. 인증서 자체는 남아 있으므로 **재발급이 아니라 재설치**로
복구한다.

```bash
sudo nginx -T | grep -c "listen 443"     # 0이면 이 경우가 맞다

sudo certbot install --nginx --cert-name handover-card.o-r.kr
sudo nginx -t && sudo systemctl reload nginx
curl -I https://handover-card.o-r.kr
```

## 8. 프론트엔드 배포

프론트는 저장소가 따로 있다. 빌드 산출물(`dist`)을 서버에 올리고 스크립트로 배치한다.

**로컬에서 빌드해 올리는 경우** — 서버 메모리를 아낄 수 있어 이쪽을 권한다:

```bash
# 로컬
npm run build
rsync -az --delete dist/ 계정@서버IP:~/web-dist/

# 서버
~/Handover_Card/deploy/deploy-web.sh ~/web-dist
```

**서버에서 빌드하는 경우**:

```bash
cd ~/handover-card-web && git pull && npm ci && npm run build
~/Handover_Card/deploy/deploy-web.sh ~/handover-card-web/dist
```

`deploy-web.sh`는 릴리스 디렉터리를 새로 만들고 심볼릭 링크만 바꾼다. 파일을 직접 덮어쓰면 복사가
도는 몇 초 동안 index.html과 번들의 짝이 맞지 않아 빈 화면이 나올 수 있어서다. 되돌리려면 이전
릴리스로 링크를 다시 걸면 된다(스크립트가 마지막에 명령을 출력한다).

### 프론트 코드에서 바꿀 것

API 기준 주소를 **빈 값(상대 경로)** 으로 둔다. 경로가 이미 `/api/**`로 시작하므로 호출 코드는
그대로 두면 된다.

```diff
- VITE_API_BASE_URL=https://api.handover-card.o-r.kr
+ VITE_API_BASE_URL=
```

## 9. 소셜 로그인 리다이렉트 URI 등록

Google Cloud Console / GitHub Developer Settings에 등록한다. 도메인이 바뀌었으므로 **새 주소를
추가하지 않으면 소셜 로그인만 `redirect_uri_mismatch`로 실패한다.**

흐름이 둘이라 콜백 주소도 둘이다. 둘은 서로 다른 경로이고 처리 주체도 다르다.

| 흐름 | 콜백 주소 | 누가 받나 |
| --- | --- | --- |
| 서버(Thymeleaf `/web` 화면) | `https://handover-card.o-r.kr/login/oauth2/code/{google\|github}` | 백엔드 |
| 프론트(SPA) | `https://handover-card.o-r.kr/oauth2/callback/{google\|github}` | **SPA** |

프론트 콜백은 브라우저가 그 주소로 **돌아오는** 것이고, 쿼리스트링의 `code`를 읽어
`POST /api/auth/oauth2/{provider}`로 넘기는 일은 SPA가 한다. 그래서 이 경로는 백엔드로
프록시하면 안 된다 — nginx에서 `/oauth2/` 전체가 아니라 `/oauth2/authorization/` 만 넘기는
이유가 이것이다.

`redirectUri`는 서버가 검증하지 않고 공급자에게 그대로 전달하므로(`OAuth2CodeExchangeService`)
**프론트가 보내는 값과 공급자 콘솔에 등록한 값이 글자 하나까지 같으면** 백엔드는 손댈 것이 없다.

> **GitHub은 콜백 URL을 하나만 등록할 수 있다.** Google은 여러 개를 정확히 나열할 수 있지만,
> GitHub OAuth App에는 "Authorization callback URL" 칸이 하나뿐이고 등록한 경로의 하위 경로만
> 허용된다. 즉 위 두 주소를 동시에 쓸 수 없다.
>
> 프론트를 쓸 것이므로 GitHub에는 `https://handover-card.o-r.kr/oauth2/callback/github`를
> 등록한다. 그 대신 **`/web` 화면의 GitHub 로그인 버튼은 동작하지 않게 된다**(Google은 양쪽 다
> 등록되므로 그대로 된다). `/web`은 백엔드 확인용 화면이라 감수할 만한 손실이다.
>
> 둘 다 살리려면 GitHub OAuth App을 하나 더 만들어 `GITHUB_CLIENT_ID`를 나누는 방법밖에 없다.

## 10. 이후 배포

```bash
# 백엔드
cd ~/Handover_Card && git pull && ./deploy/deploy.sh

# 프론트
./deploy/deploy-web.sh ~/web-dist

# nginx 경로 규칙이 바뀌었을 때 (snippets 만 복사한다)
sudo cp deploy/nginx/handover-card-locations.conf deploy/nginx/handover-card-proxy.conf /etc/nginx/snippets/
sudo nginx -t && sudo systemctl reload nginx
```

**`/etc/nginx/conf.d/handover-card.conf` 는 다시 복사하지 않는다.** certbot이 넣어 둔 HTTPS
설정이 그 파일에 있다(7절).

백엔드는 헬스체크가 통과하지 못하면 직전 이미지로 자동 롤백한다. 다만 롤백은 **잘못된 코드**를
되돌릴 뿐이고, 환경변수 파일은 그대로 쓰기 때문에 **설정 오류는 롤백해도 낫지 않는다.** 그때는
`/etc/handover-card.env`를 고치고 다시 배포해야 한다.

## 막혔을 때 (Rocky/SELinux 관련)

증상만 보면 애플리케이션 문제 같은데 원인이 SELinux인 경우가 많다. 먼저 차단 로그부터 본다.

```bash
sudo ausearch -m AVC -ts recent          # 최근 차단 기록
sudo tail -f /var/log/nginx/error.log
```

| 증상 | 원인 | 해결 |
| --- | --- | --- |
| 브라우저 502, nginx 로그에 `connect() ... failed (13: Permission denied)` | nginx가 8080으로 못 나감 | `sudo setsebool -P httpd_can_network_connect 1` |
| 프론트 화면만 403 (API는 정상) | 정적 파일 라벨이 틀림 | `sudo restorecon -Rv /var/www` |
| `nginx -t`에서 `include ... failed` | snippets 파일 미복사 | 7절의 `handover-card-proxy.conf` 복사 |
| 도메인으로 들어가면 Rocky 기본 페이지 | nginx.conf의 default_server 블록 | 7절대로 주석 처리 |
| 잘 되던 사이트가 `git pull` 후 안 열림 | `conf.d/handover-card.conf`를 덮어써 HTTPS 설정이 날아감 | `sudo certbot install --nginx --cert-name handover-card.o-r.kr` |
| `docker` 명령이 `permission denied` | docker 그룹 반영 안 됨 | 로그아웃 후 재접속 |
| 이미지 빌드가 `UnknownHostException` | 컨테이너가 바깥으로 못 나감 | 1-2절 "컨테이너가 바깥으로 못 나갈 때" |
| 기동 시 `Unable to determine Dialect without JDBC metadata` | DB에 못 붙음 (커넥션을 못 얻어 DB 종류조차 모름) | 5절. 스택트레이스 꼬리가 아니라 **앞부분**을 봐야 진짜 원인이 나온다 |

`ausearch`에 아무것도 안 나오면 SELinux 문제가 아니다. 그때는 방화벽(`firewall-cmd --list-all`)과
가비아 콘솔 방화벽을 확인한다 — 둘 다 열려 있어야 한다.

## 알아둘 것

- 서버 한 대라 배포할 때마다 API가 짧게 끊긴다. `server.shutdown: graceful`이 켜져 있어 처리 중이던
  카드는 최대 30초까지 마무리되지만, 그 시점에 진행 중이던 작업은 재시작 후 `PROCESSING`으로 남는다.
  트래픽이 적은 시간대에 배포한다. 프론트 배포(`deploy-web.sh`)는 링크 교체라 끊기지 않는다.
- 로그는 `docker logs -f handover-card`. 컨테이너에 10MB × 3개로 로테이션을 걸어 두었다.
- 재부팅 후 서비스가 자동으로 뜨는지 확인해 둘 것. 컨테이너는 `--restart unless-stopped`,
  docker/nginx는 `systemctl enable`이 되어 있어야 한다 (`systemctl is-enabled docker nginx`).
- DB와 S3가 외부에 있으므로 **네트워크가 끊기면 앱은 떠 있는데 기능만 실패한다.** 업로드가 안 될
  때는 앱 로그보다 먼저 RDS 보안 그룹과 액세스 키 만료를 확인하는 게 빠르다.
