variable "account_id" {
  type        = string
  default     = "265988882003"
  description = "Alpha(개발) 계정"
}

variable "api_domain" {
  type        = string
  default     = "api.alpha.ournight.co.kr"
  description = "Alpha API 호스트네임"
}

variable "ecr_image_tag" {
  type        = string
  default     = "alpha"
  description = "import 직후 태스크 정의 교체 없으려면 실행 중과 동일한 태그(예: 커밋 sha) 사용"
}

variable "app_client_base_url" {
  type        = string
  default     = "https://alpha.ournight.co.kr"
  description = "Alpha 프론트 URL"
}
