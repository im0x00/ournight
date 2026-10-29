#!/usr/bin/env bash
# Prod(100984277580) Terraform 반영: core → app 순서 (APP_ONLY=1 이면 app 만).
#
# 사용법:
#   export TF_VAR_db_password='...'   # core apply 시 필요
#   IMAGE_TAG=$(git rev-parse --short HEAD) ./scripts/terraform-apply-prod.sh
#   IMAGE_TAG=69a93ed APP_ONLY=1 ./scripts/terraform-apply-prod.sh   # ECS 이미지만
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export AWS_PROFILE="${AWS_PROFILE:-prod}"
export AWS_REGION="${AWS_REGION:-ap-northeast-2}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"
export AWS_SDK_LOAD_CONFIG=1

PROD_ACCOUNT="100984277580"

terraform_init() {
  if ! terraform init -input=false "$@"; then
    echo ""
    echo "terraform init 실패. 자격 증명 확인:"
    echo "  AWS_PROFILE=prod aws sts get-caller-identity"
    echo "  SSO: aws sso login --profile prod"
    exit 1
  fi
}

if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "ERROR: AWS 자격 증명이 없습니다. aws configure 또는 AWS_PROFILE=prod 를 설정하세요."
  exit 1
fi

ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
if [[ "$ACCOUNT" != "$PROD_ACCOUNT" ]]; then
  echo "ERROR: 현재 AWS 계정($ACCOUNT) ≠ prod($PROD_ACCOUNT)."
  echo "       AWS_PROFILE=prod ./scripts/terraform-apply-prod.sh"
  exit 1
fi

if [[ -z "${TF_VAR_db_password:-}" && -n "${DB_PASSWORD:-}" ]]; then
  export TF_VAR_db_password="$DB_PASSWORD"
fi

if [[ -n "${IMAGE_TAG:-}" && -z "${TF_VAR_ecr_image_tag:-}" ]]; then
  export TF_VAR_ecr_image_tag="$IMAGE_TAG"
fi

apply_plan() {
  local planfile="$1"
  if [[ "${APPLY_AUTO:-}" == "1" ]]; then
    terraform apply -input=false -auto-approve "$planfile"
  else
    terraform apply -input=false "$planfile"
  fi
}

if [[ "${APP_ONLY:-}" != "1" ]]; then
  if [[ -z "${TF_VAR_db_password:-}" ]]; then
    echo "ERROR: core apply 에 RDS 비밀번호가 필요합니다."
    echo "  export TF_VAR_db_password='...'"
    exit 1
  fi

  echo "=== terraform/envs/prod/core (AWS_PROFILE=$AWS_PROFILE) ==="
  cd "$REPO_ROOT/terraform/envs/prod/core"
  terraform_init
  terraform plan -input=false -out=tfcore.tfplan
  if [[ "${PLAN_ONLY:-}" == "1" ]]; then
    echo "PLAN_ONLY=1 이라 core apply 는 건너뜁니다."
  else
    apply_plan tfcore.tfplan
  fi
fi

if [[ -z "${TF_VAR_ecr_image_tag:-}" ]]; then
  echo "ERROR: app apply 에 ecr_image_tag 가 필요합니다."
  echo "  IMAGE_TAG=\$(git rev-parse --short HEAD) ./scripts/terraform-apply-prod.sh"
  exit 1
fi

echo "=== terraform/envs/prod/app (ecr_image_tag=$TF_VAR_ecr_image_tag, AWS_PROFILE=$AWS_PROFILE) ==="
cd "$REPO_ROOT/terraform/envs/prod/app"
terraform_init
terraform plan -input=false -out=tfapp.tfplan
if [[ "${PLAN_ONLY:-}" == "1" ]]; then
  echo "PLAN_ONLY=1 이라 app apply 는 건너뜁니다."
else
  apply_plan tfapp.tfplan
fi

echo "완료."
