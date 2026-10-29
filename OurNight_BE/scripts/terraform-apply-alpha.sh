#!/usr/bin/env bash
# Alpha(265988882003) Terraform 반영: core → app 순서.
# 로컬 터미널에서 실행하세요 (AWS CLI 로그인 필요).
#
# 사용법:
#   export TF_VAR_db_password='...'   또는  export DB_PASSWORD='...'
#   프로젝트 루트에 .env.alpha 가 있고 DB_PASSWORD= 가 있으면 자동 사용
#   ./scripts/terraform-apply-alpha.sh
#
# State 는 각 디렉터리의 terraform.tfstate (로컬).
# 이미 AWS에 같은 이름의 리소스가 있고 state 가 비어 있으면 apply 가 충돌합니다.
# 그때는 먼저 import 하거나 진행하세요.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export AWS_REGION="${AWS_REGION:-ap-northeast-2}"
export AWS_DEFAULT_REGION="${AWS_DEFAULT_REGION:-$AWS_REGION}"
# provider / AWS CLI 가 ~/.aws/config (SSO 등) 를 읽도록
export AWS_SDK_LOAD_CONFIG=1

terraform_init() {
  if ! terraform init -input=false "$@"; then
    echo ""
    echo "terraform init 실패. 자격 증명 확인:"
    echo "  aws sts get-caller-identity"
    echo "  SSO: aws sso login --profile <프로필> && export AWS_PROFILE=<프로필>"
    exit 1
  fi
}

if ! aws sts get-caller-identity >/dev/null 2>&1; then
  echo "ERROR: AWS 자격 증명이 없습니다. aws configure 또는 AWS_PROFILE 을 설정하세요."
  exit 1
fi

ACCOUNT="$(aws sts get-caller-identity --query Account --output text)"
if [[ "$ACCOUNT" != "265988882003" ]]; then
  echo "WARN: 현재 계정이 265988882003 이 아닙니다: $ACCOUNT"
  read -r -p "계속할까요? [y/N] " ok || true
  [[ "${ok:-}" == "y" ]] || exit 1
fi

if [[ -z "${TF_VAR_db_password:-}" && -n "${DB_PASSWORD:-}" ]]; then
  export TF_VAR_db_password="$DB_PASSWORD"
fi

if [[ -z "${TF_VAR_db_password:-}" && -f "$REPO_ROOT/.env.alpha" ]]; then
  # .env.alpha 한 줄만 파싱 (전체 source 하지 않음)
  _line="$(grep -E '^[[:space:]]*DB_PASSWORD=' "$REPO_ROOT/.env.alpha" | tail -1 || true)"
  if [[ -n "$_line" ]]; then
    _val="${_line#*=}"
    _val="${_val%$'\r'}"
    _val="${_val#\"}"
    _val="${_val%\"}"
    _val="${_val#\'}"
    _val="${_val%\'}"
    export TF_VAR_db_password="$_val"
  fi
  unset _line _val
fi

if [[ -z "${TF_VAR_db_password:-}" ]]; then
  echo "ERROR: RDS(ournight-db) 비밀번호가 필요합니다. 아래 중 하나를 하세요."
  echo "  export TF_VAR_db_password='...'"
  echo "  export DB_PASSWORD='...'   # 또는 .env.alpha 에 DB_PASSWORD= 가 있으면 프로젝트 루트에 두기"
  exit 1
fi

apply_plan() {
  local planfile="$1"
  if [[ "${APPLY_AUTO:-}" == "1" ]]; then
    terraform apply -input=false -auto-approve "$planfile"
  else
    terraform apply -input=false "$planfile"
  fi
}

echo "=== terraform/envs/alpha/core ==="
cd "$REPO_ROOT/terraform/envs/alpha/core"
terraform_init
terraform plan -input=false -out=tfcore.tfplan
if [[ "${PLAN_ONLY:-}" == "1" ]]; then
  echo "PLAN_ONLY=1 이라 apply 는 건너뜁니다."
else
  apply_plan tfcore.tfplan
fi

echo "=== terraform/envs/alpha/app ==="
cd "$REPO_ROOT/terraform/envs/alpha/app"
terraform_init
terraform plan -input=false -out=tfapp.tfplan
if [[ "${PLAN_ONLY:-}" == "1" ]]; then
  echo "PLAN_ONLY=1 이라 apply 는 건너뜁니다."
else
  apply_plan tfapp.tfplan
fi

echo "완료."
