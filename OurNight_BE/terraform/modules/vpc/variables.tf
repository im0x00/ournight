variable "name_prefix" {
  description = "리소스 이름 접두사 (예: ournight-alpha, ournight-prod)"
  type        = string
}

variable "cidr_block" {
  type    = string
  default = "10.0.0.0/16"
}
