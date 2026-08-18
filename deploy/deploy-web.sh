#!/usr/bin/env bash
#
# 프론트엔드 빌드 산출물을 nginx가 서비스하는 위치에 배치한다.
#
#   ./deploy/deploy-web.sh ~/handover-card-web/dist
#
# 프론트는 저장소가 따로 있으므로 여기서 빌드하지는 않는다. 빌드까지 서버에서 하려면
# 프론트 저장소를 클론해 두고 `npm ci && npm run build` 후 그 dist 경로를 넘긴다.
#
# 교체는 심볼릭 링크를 바꾸는 방식이다. 파일을 직접 덮어쓰면 복사가 도는 몇 초 동안
# 새 index.html이 아직 없는 번들을 가리키는 상태가 생겨, 그 사이 접속한 사용자가
# 빈 화면을 본다. 링크 교체는 한 번의 원자적 연산이라 그 틈이 없다.

set -euo pipefail

SRC=${1:-}
WEB_ROOT=${WEB_ROOT:-/var/www/handover-card}
RELEASES=${RELEASES:-/var/www/handover-card-releases}
KEEP=${KEEP:-3}

if [ -z "$SRC" ]; then
    echo "사용법: $0 <빌드 산출물 디렉터리>" >&2
    exit 1
fi

if [ ! -f "$SRC/index.html" ]; then
    echo "index.html이 없다: $SRC — 빌드 산출물 디렉터리가 맞는지 확인할 것" >&2
    exit 1
fi

log() { printf '\n[deploy-web] %s\n' "$1"; }

# 처음 실행이라 WEB_ROOT가 심볼릭 링크가 아닌 실제 디렉터리면 링크 방식으로 바꿀 수 없다.
if [ -e "$WEB_ROOT" ] && [ ! -L "$WEB_ROOT" ]; then
    echo "$WEB_ROOT 가 심볼릭 링크가 아니다. 기존 디렉터리를 지우거나 옮긴 뒤 다시 실행할 것." >&2
    exit 1
fi

RELEASE="$RELEASES/$(date +%Y%m%d%H%M%S)"

log "산출물 복사 → $RELEASE"
sudo mkdir -p "$RELEASE"
sudo cp -r "$SRC"/. "$RELEASE/"
# nginx 프로세스 사용자(Rocky는 nginx)가 읽을 수 있어야 한다.
sudo chmod -R a+rX "$RELEASE"

# SELinux(Rocky 기본 enforcing)에서는 파일 권한이 맞아도 라벨이 틀리면 nginx가 403을 낸다.
# 새로 만든 디렉터리는 부모의 라벨을 그대로 물려받지 않으므로 배포할 때마다 다시 입힌다.
# semanage로 등록해 둔 규칙(deploy/README.md 1-3절)을 restorecon이 적용하는 구조다.
if command -v getenforce >/dev/null 2>&1 && [ "$(getenforce)" != "Disabled" ]; then
    log "SELinux 라벨 적용"
    sudo restorecon -R "$RELEASE"
fi

log "링크 교체"
# -n 이 없으면 WEB_ROOT가 이미 링크일 때 그 안쪽에 링크를 만들어 버린다.
sudo ln -sfn "$RELEASE" "$WEB_ROOT"

# nginx는 심볼릭 링크를 매 요청마다 따라가므로 reload가 필요 없다. 다만 open_file_cache를
# 켜 두었다면 캐시가 만료될 때까지 옛 경로를 물 수 있어, 그 경우에만 reload 한다.
if grep -rqs "open_file_cache" /etc/nginx/; then
    log "open_file_cache가 설정되어 있어 nginx를 reload 한다"
    sudo nginx -t && sudo systemctl reload nginx
fi

log "예전 릴리스 정리 (최근 ${KEEP}개 유지)"
# shellcheck disable=SC2012
ls -1dt "$RELEASES"/*/ 2>/dev/null | tail -n +$((KEEP + 1)) | while read -r old; do
    sudo rm -rf "$old"
done

log "완료. 되돌리려면 이전 릴리스로 링크를 다시 걸면 된다:"
echo "  ls -1dt $RELEASES/*/"
echo "  sudo ln -sfn <이전 릴리스> $WEB_ROOT"
