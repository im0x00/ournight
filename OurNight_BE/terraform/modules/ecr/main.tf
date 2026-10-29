resource "aws_ecr_repository" "main" {
  name         = var.repository_name
  force_delete = false
}
