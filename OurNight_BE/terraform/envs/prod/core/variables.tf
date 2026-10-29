variable "account_id" {
  type    = string
  default = "100984277580"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "db_username" {
  type        = string
  default     = "ournight"
  description = "RDS 마스터 사용자명"
}

variable "rds_restore_snapshot_identifier" {
  type     = string
  nullable = true
  default  = null
}
