# Backend Server

## 실행 방법

### 1. DB & Redis 실행
```bash
docker compose up -d
```

### 2. Spring Boot 실행
```bash
./gradlew bootRun
```

## TEST API 엔드포인트
- Health Check: `GET /api/health`


## 배포순서
- IMAGE_TAG=$(git rev-parse --short HEAD)
- make ecr-release IMAGE_TAG=$IMAGE_TAG
- cd terraform/2_app_infra
- terraform apply -var="ecr_image_tag=$IMAGE_TAG"