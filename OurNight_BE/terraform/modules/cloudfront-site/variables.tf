variable "name_prefix" { type = string }
variable "domain_name" {
  type        = string
  description = "CloudFront alias 도메인 (예: alpha.ournight.co.kr)"
}
variable "s3_bucket_name" {
  type        = string
  description = "정적 파일을 담을 S3 버킷 이름"
}
variable "route53_zone_id" { type = string }

variable "is_spa" {
  type        = bool
  default     = false
  description = "true면 SPA 라우팅 설정 (default_root_object + 4xx → index.html)"
}

variable "price_class" {
  type    = string
  default = "PriceClass_100"
}

# brownfield import 시 기존 CloudFront/S3/OAC 문자열·태그와 맞추기 (null 이면 모듈 기본)
variable "distribution_comment" {
  type        = string
  nullable    = true
  default     = null
  description = "CloudFront Comment; null 이면 \"{name_prefix}-cdn\""
}

variable "origin_id" {
  type        = string
  nullable    = true
  default     = null
  description = "origin_id / target_origin_id; null 이면 \"{name_prefix}-s3-origin\""
}

variable "oac_name" {
  type     = string
  nullable = true
  default  = null
}

variable "oac_description" {
  type     = string
  nullable = true
  default  = null
}

variable "cloudfront_tags" {
  type        = map(string)
  nullable    = true
  default     = null
  description = "null 이면 Name = \"{name_prefix}-cdn\"; {} 이면 태그 없음"
}

variable "s3_tags" {
  type        = map(string)
  nullable    = true
  default     = null
  description = "null 이면 Name 만 버킷명으로"
}

variable "cors_allowed_origins" {
  type        = list(string)
  default     = []
  description = "비어있지 않으면 CloudFront 응답에 CORS 헤더를 추가한다. 이미지 CDN 다운로드용."
}
