variable "name_prefix" { type = string }
variable "vpc_id" { type = string }
variable "vpc_cidr_block" { type = string }

variable "security_group_description" {
  type        = string
  default     = "Allow PostgreSQL from VPC"
  description = "변경 시 SG 가 교체될 수 있음(기존 리소스 import 시 실제 description 과 동일하게)"
}

variable "engine_version" {
  type    = string
  default = "15"
}

variable "skip_final_snapshot" {
  type        = bool
  default     = false
  description = "true 이면 삭제 시 최종 스냅샷 없음(기존 인스턴스와 맞출 때)"
}

variable "private_subnet_ids" {
  type        = list(string)
  description = "RDS를 배치할 프라이빗 서브넷 2개"
}

variable "db_password" {
  type      = string
  sensitive = true
}

variable "db_name" {
  type    = string
  default = "ournight"
}
variable "db_username" {
  type    = string
  default = "ournight"
}
variable "instance_class" {
  type    = string
  default = "db.t3.micro"
}
variable "allocated_storage" {
  type    = number
  default = 20
}

variable "final_snapshot_identifier" {
  type    = string
  default = "ournight-db-final-snapshot"
}

variable "restore_snapshot_identifier" {
  type     = string
  nullable = true
  default  = null
}

variable "restore_from_latest_manual_snapshot" {
  type    = bool
  default = false
}

variable "restore_latest_snapshot_source_db_identifier" {
  type    = string
  default = "ournight-db"
}
