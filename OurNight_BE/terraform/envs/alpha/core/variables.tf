variable "account_id" {
  type        = string
  default     = "265988882003"
  description = "Alpha(개발) — 기존 프로비저닝 계정"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "rds_restore_snapshot_identifier" {
  type     = string
  nullable = true
  default  = null
}

# true 시 alpha.ournight.co.kr 용 CloudFront+S3+Route53 관리. 이미 같은 이름 버킷/배포가 있으면 import 하거나 false.
variable "manage_alpha_frontend_cdn" {
  type        = bool
  default     = true
  description = "alpha SPA 프론트 CDN(S3+CloudFront+ACM+DNS). 기존 리소스와 이름 충돌 시 false 또는 import"
}
