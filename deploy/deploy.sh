#!/usr/bin/env bash
#
# EC2에서 실행하는 배포 스크립트. 저장소를 클론해 둔 디렉터리에서 돌린다.
#
#   ./deploy/deploy.sh
#
# 새 이미지를 빌드해 컨테이너를 갈아끼우고, 헬스체크가 통과하지 못하면 직전 이미지로 되돌린다.

set -euo pipefail

# 기본값은 EC2 기준이고, 리허설할 때만 환경변수로 덮어쓴다.
IMAGE=${IMAGE:-handover-card}
CONTAINER=${CONTAINER:-handover-card}
ENV_FILE=${ENV_FILE:-/etc/handover-card.env}
HOST_PORT=${HOST_PORT:-8080}
HEALTH_URL=http://127.0.0.1:${HOST_PORT}/actuator/health
HEALTH_TIMEOUT=${HEALTH_TIMEOUT:-120}

log() { printf '\n[deploy] %s\n' "$1"; }

if [ ! -f "$ENV_FILE" ]; then
    echo "환경변수 파일이 없다: $ENV_FILE (deploy/handover-card.env.example 참고)" >&2
    exit 1
fi

# 롤백 대상을 먼저 확보한다. 첫 배포라 latest가 없으면 롤백도 없다.
ROLLBACK_AVAILABLE=false
if docker image inspect "$IMAGE:latest" >/dev/null 2>&1; then
    docker tag "$IMAGE:latest" "$IMAGE:previous"
    ROLLBACK_AVAILABLE=true
fi

log "이미지 빌드"
# 빌드 실패 시 여기서 멈추므로 돌고 있는 컨테이너는 그대로 살아 있다.
docker build -t "$IMAGE:latest" .

start_container() {
    local tag="$1"
    docker rm -f "$CONTAINER" >/dev/null 2>&1 || true
    docker run -d \
        --name "$CONTAINER" \
        --restart unless-stopped \
        --env-file "$ENV_FILE" \
        -p "127.0.0.1:${HOST_PORT}:8080" \
        --log-opt max-size=10m \
        --log-opt max-file=3 \
        "$IMAGE:$tag" >/dev/null
}

wait_for_health() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT))
    while [ $SECONDS -lt $deadline ]; do
        if [ "$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)" = "200" ]; then
            return 0
        fi
        # 기동 중 컨테이너가 죽었으면 더 기다릴 이유가 없다.
        if [ "$(docker inspect -f '{{.State.Running}}' "$CONTAINER" 2>/dev/null || echo false)" != "true" ]; then
            return 1
        fi
        sleep 3
    done
    return 1
}

log "컨테이너 교체"
start_container latest

log "헬스체크 (최대 ${HEALTH_TIMEOUT}초)"
if wait_for_health; then
    log "배포 성공"
    docker image prune -f >/dev/null 2>&1 || true
    exit 0
fi

log "헬스체크 실패. 최근 로그:"
docker logs --tail 50 "$CONTAINER" 2>&1 || true

if [ "$ROLLBACK_AVAILABLE" = true ]; then
    log "직전 이미지로 롤백"
    start_container previous
    if wait_for_health; then
        log "롤백 완료. 이번 배포분은 반영되지 않았다."
    else
        log "롤백도 헬스체크에 실패했다. 수동 확인이 필요하다."
    fi
else
    log "첫 배포라 롤백할 이미지가 없다."
fi

exit 1
