# AWS ECR / Docker
# Alpha(개발): 265988882003  |  Prod(운영): 100984277580 — 계정별 ECR 레지스트리가 다릅니다.
#
# Alpha 예:
#   IMAGE_TAG=$(git rev-parse --short HEAD) make ecr-release-alpha
#
# Prod 예 (AWS_PROFILE=prod 권장):
#   IMAGE_TAG=$(git rev-parse --short HEAD) make ecr-release-prod
#
AWS_REGION              ?= ap-northeast-2
AWS_ACCOUNT_ID_ALPHA    ?= 265988882003
AWS_ACCOUNT_ID_PROD     ?= 100984277580
AWS_ACCOUNT_ID          ?= $(AWS_ACCOUNT_ID_ALPHA)

ECR_REPO        ?= ournight-api-server
LOCAL_IMAGE     ?= ournight-api-server
IMAGE_TAG       ?= amd64

ECR_REGISTRY = $(AWS_ACCOUNT_ID).dkr.ecr.$(AWS_REGION).amazonaws.com
ECR_IMAGE    = $(ECR_REGISTRY)/$(ECR_REPO):$(IMAGE_TAG)
BUILD_COMMIT ?= $(shell git rev-parse HEAD 2>/dev/null || echo unknown)
BUILD_TIME   ?= $(shell date -u +"%Y-%m-%dT%H:%M:%SZ")

.PHONY: ecr-verify-account ecr-login docker-build docker-tag ecr-push ecr-push-latest \
	ecr-release ecr-release-alpha ecr-release-prod \
	deploy-alpha terraform-plan-alpha terraform-apply-alpha \
	terraform-plan-prod terraform-apply-prod terraform-apply-prod-app

# --- 공통 단계 ---

# push 403(HEAD blob Forbidden)은 보통 잘못된 AWS 계정 토큰 또는 Docker credential 캐시 때문
ecr-verify-account:
	@ACCOUNT=$$(aws sts get-caller-identity --query Account --output text 2>/dev/null) && \
	 test "$$ACCOUNT" = "$(AWS_ACCOUNT_ID)" || \
	 (echo "ERROR: 현재 AWS 계정($$ACCOUNT) ≠ 대상 ECR 계정($(AWS_ACCOUNT_ID))." >&2; \
	  echo "       prod: AWS_PROFILE=prod make ecr-release-prod IMAGE_TAG=..." >&2; exit 1)

ecr-login: ecr-verify-account
	-docker logout $(ECR_REGISTRY) 2>/dev/null
	aws ecr get-login-password --region $(AWS_REGION) | docker login --username AWS --password-stdin $(ECR_REGISTRY)

docker-build:
	docker build --platform linux/amd64 \
		--provenance=false --sbom=false \
		--build-arg BUILD_VERSION=$(IMAGE_TAG) \
		--build-arg BUILD_COMMIT=$(BUILD_COMMIT) \
		--build-arg BUILD_TIME=$(BUILD_TIME) \
		-t $(LOCAL_IMAGE):$(IMAGE_TAG) --load .

docker-tag:
	docker tag $(LOCAL_IMAGE):$(IMAGE_TAG) $(ECR_IMAGE)

ecr-push:
	docker push $(ECR_IMAGE)

ecr-push-latest:
	docker tag $(LOCAL_IMAGE):$(IMAGE_TAG) $(ECR_REGISTRY)/$(ECR_REPO):latest
	docker push $(ECR_REGISTRY)/$(ECR_REPO):latest

# 로그인 → 빌드 → 태그(:IMAGE_TAG) → 푸시 (기본: alpha 계정)
ecr-release: ecr-login docker-build docker-tag ecr-push

# Alpha(dev) ECR — AWS_PROFILE 미설정 시 기본 계정 자격 증명 사용
ecr-release-alpha: AWS_ACCOUNT_ID = $(AWS_ACCOUNT_ID_ALPHA)
ecr-release-alpha: ecr-release

# Prod ECR — :IMAGE_TAG + :latest (deploy-prod.yml / terraform 기본 latest 와 동일)
# 기본 AWS_PROFILE=prod (다른 프로필이면 make AWS_PROFILE=... 로 덮어쓰기)
ecr-release-prod: AWS_ACCOUNT_ID = $(AWS_ACCOUNT_ID_PROD)
ecr-release-prod: export AWS_PROFILE = prod
ecr-release-prod: ecr-release ecr-push-latest

# EC2에서 실행: IMAGE_TAG=<tag> make deploy-alpha
deploy-alpha:
	@test -n "$(IMAGE_TAG)" || (echo "Usage: make deploy-alpha IMAGE_TAG=<tag>" >&2; exit 64)
	./scripts/deploy-alpha.sh $(IMAGE_TAG)

# Terraform alpha — 로컬 터미널, export TF_VAR_db_password=... 후 실행
terraform-plan-alpha:
	PLAN_ONLY=1 ./scripts/terraform-apply-alpha.sh

terraform-apply-alpha:
	./scripts/terraform-apply-alpha.sh

# Terraform prod — AWS_PROFILE=prod (스크립트 기본값), IMAGE_TAG 필수
terraform-plan-prod:
	@test -n "$(IMAGE_TAG)" || (echo "Usage: make terraform-plan-prod IMAGE_TAG=<tag>" >&2; exit 64)
	PLAN_ONLY=1 IMAGE_TAG=$(IMAGE_TAG) ./scripts/terraform-apply-prod.sh

terraform-apply-prod:
	@test -n "$(IMAGE_TAG)" || (echo "Usage: make terraform-apply-prod IMAGE_TAG=<tag>" >&2; exit 64)
	IMAGE_TAG=$(IMAGE_TAG) ./scripts/terraform-apply-prod.sh

# ECS 이미지만 반영 (core 생략)
terraform-apply-prod-app:
	@test -n "$(IMAGE_TAG)" || (echo "Usage: make terraform-apply-prod-app IMAGE_TAG=<tag>" >&2; exit 64)
	APP_ONLY=1 IMAGE_TAG=$(IMAGE_TAG) ./scripts/terraform-apply-prod.sh
