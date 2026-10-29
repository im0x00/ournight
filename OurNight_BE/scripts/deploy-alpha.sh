#!/usr/bin/env bash
set -Eeuo pipefail

AWS_REGION="${AWS_REGION:-ap-northeast-2}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-265988882003}"
ECR_REPO="${ECR_REPO:-ournight-api-server}"
CONTAINER_NAME="${CONTAINER_NAME:-ournight-api}"
ENV_FILE="${ENV_FILE:-/home/ec2-user/ournight-api.env}"
APP_PORT="${APP_PORT:-8080}"
HEALTH_URL="${HEALTH_URL:-http://localhost:${APP_PORT}/api/health}"
SKIP_ECR_LOGIN="${SKIP_ECR_LOGIN:-false}"

if [[ $# -ne 1 || -z "${1:-}" ]]; then
  echo "Usage: $0 <image-tag>" >&2
  exit 64
fi

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Missing env file: $ENV_FILE" >&2
  exit 66
fi

IMAGE_TAG="$1"
ECR_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"
IMAGE="${ECR_REGISTRY}/${ECR_REPO}:${IMAGE_TAG}"
BACKUP_CONTAINER=""

health_check() {
  local attempt

  for attempt in {1..20}; do
    if curl -fsS "$HEALTH_URL" >/dev/null; then
      return 0
    fi

    sleep 3
  done

  return 1
}

rollback() {
  if [[ -z "$BACKUP_CONTAINER" ]]; then
    echo "No previous container to roll back to." >&2
    return 1
  fi

  echo "Rolling back to previous container: $BACKUP_CONTAINER" >&2
  docker rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  docker rename "$BACKUP_CONTAINER" "$CONTAINER_NAME"
  docker start "$CONTAINER_NAME" >/dev/null

  if health_check; then
    echo "Rollback completed." >&2
    return 0
  fi

  echo "Rollback started the previous container, but health check failed." >&2
  return 1
}

if [[ "$SKIP_ECR_LOGIN" == "true" ]]; then
  echo "Skipping ECR login."
else
  echo "Logging in to ECR: $ECR_REGISTRY"
  aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$ECR_REGISTRY"
fi

echo "Pulling image: $IMAGE"
docker pull "$IMAGE"

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  BACKUP_CONTAINER="${CONTAINER_NAME}-backup-$(date +%Y%m%d%H%M%S)"
  echo "Stopping current container: $CONTAINER_NAME"
  docker stop "$CONTAINER_NAME" >/dev/null
  docker rename "$CONTAINER_NAME" "$BACKUP_CONTAINER"
fi

echo "Starting new container: $CONTAINER_NAME"
if ! docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p "${APP_PORT}:8080" \
  --env-file "$ENV_FILE" \
  "$IMAGE" >/dev/null; then
  rollback
  exit 1
fi

echo "Checking health: $HEALTH_URL"
if ! health_check; then
  docker logs --tail 120 "$CONTAINER_NAME" >&2 || true
  rollback
  exit 1
fi

if [[ -n "$BACKUP_CONTAINER" ]]; then
  docker rm "$BACKUP_CONTAINER" >/dev/null
fi

echo "Deployment completed: $IMAGE"
