variable "name_prefix" { type = string }

# true면 예전 2_app_infra (계정 265988882003)에서 쓰던 AWS 리소스 이름과 동일 (import·기존 인프라 인계용)
variable "use_legacy_ournight_names" {
  type    = bool
  default = false
}

variable "vpc_id" { type = string }
variable "public_subnet_ids" { type = list(string) }
variable "private_subnet_ids" { type = list(string) }
variable "route53_zone_id" { type = string }

variable "ecr_image" {
  type        = string
  description = "배포할 ECR 이미지 전체 URI"
}
variable "api_domain" {
  type        = string
  description = "API 도메인 (예: api.alpha.ournight.co.kr)"
}

# Spring 앱 환경변수
variable "spring_profile" { type = string }
variable "app_client_base_url" { type = string }
variable "cors_allowed_origins" {
  type        = string
  default     = null
  nullable    = true
  description = "Comma-separated CORS origins. Defaults to app_client_base_url."
}
variable "aws_s3_bucket" { type = string }
variable "aws_s3_domain" { type = string }
variable "ssm_parameter_prefix" {
  type        = string
  description = "SSM prefix (예: /ournight/alpha)"
}

# 컨테이너 스펙 — alpha와 prod 동일하게 유지
variable "container_cpu" {
  type    = number
  default = 512
}
variable "container_memory" {
  type    = number
  default = 1024
}

# ECS 스케일링
variable "ecs_desired_count" {
  type    = number
  default = 1
}
variable "ecs_min_capacity" {
  type    = number
  default = 1
}
variable "ecs_max_capacity" {
  type    = number
  default = 4
}

# CloudWatch
variable "log_retention_days" {
  type    = number
  default = 14
}
