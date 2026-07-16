#!/usr/bin/env bash
set -euo pipefail

# 실행 위치: Azure /home/azureuser/app
# 필요 환경변수: ACR_URI, IMAGE_TAG (GitHub Actions가 SSH로 전달)

APP_DIR="/home/azureuser/app"
DOCKER_DIR="$APP_DIR/docker"
ACTIVE_COLOR_FILE="$APP_DIR/active_color"
NGINX_BLUE_GREEN_DIR="/etc/nginx/blue-green"
NGINX_ACTIVE_LINK="$NGINX_BLUE_GREEN_DIR/active_upstream.conf"
HEALTH_RETRY=30
HEALTH_INTERVAL=2

: "${ACR_URI:?ACR_URI 환경변수가 필요합니다}"
: "${IMAGE_TAG:?IMAGE_TAG 환경변수가 필요합니다}"
export ACR_URI IMAGE_TAG

CURRENT_COLOR="$(cat "$ACTIVE_COLOR_FILE" 2>/dev/null || echo blue)"
if [ "$CURRENT_COLOR" = "blue" ]; then
    TARGET_COLOR="green"
    TARGET_PORT=8081
else
    TARGET_COLOR="blue"
    TARGET_PORT=8080
fi

echo "[deploy] current=$CURRENT_COLOR target=$TARGET_COLOR image=${ACR_URI}:${IMAGE_TAG}"

cd "$DOCKER_DIR"

echo "[deploy] pulling image"
docker compose -f "docker-compose.$TARGET_COLOR.yml" pull

echo "[deploy] starting $TARGET_COLOR"
docker compose -f "docker-compose.$TARGET_COLOR.yml" up -d

echo "[deploy] health check on port $TARGET_PORT"
healthy=false
for i in $(seq 1 "$HEALTH_RETRY"); do
    if curl -sf "http://localhost:${TARGET_PORT}/actuator/health" | grep -q '"status":"UP"'; then
        healthy=true
        break
    fi
    sleep "$HEALTH_INTERVAL"
done

if [ "$healthy" != "true" ]; then
    echo "[deploy] health check FAILED — rolling back, $CURRENT_COLOR stays live"
    docker compose -f "docker-compose.$TARGET_COLOR.yml" down
    exit 1
fi

echo "[deploy] health check OK — switching nginx to $TARGET_COLOR"
sudo ln -sfn "$NGINX_BLUE_GREEN_DIR/upstream-$TARGET_COLOR.conf" "$NGINX_ACTIVE_LINK"
sudo nginx -t
sudo systemctl reload nginx

echo "[deploy] stopping old $CURRENT_COLOR"
docker compose -f "docker-compose.$CURRENT_COLOR.yml" down

echo "$TARGET_COLOR" > "$ACTIVE_COLOR_FILE"
echo "[deploy] done — live color is now $TARGET_COLOR"
