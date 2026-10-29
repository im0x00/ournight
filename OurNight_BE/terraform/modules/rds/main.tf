data "aws_db_snapshot" "latest_manual" {
  count = var.restore_from_latest_manual_snapshot && var.restore_snapshot_identifier == null ? 1 : 0

  db_instance_identifier = var.restore_latest_snapshot_source_db_identifier
  snapshot_type          = "manual"
  most_recent            = true
}

locals {
  snapshot_id = (
    var.restore_snapshot_identifier != null
    ? var.restore_snapshot_identifier
    : length(data.aws_db_snapshot.latest_manual) > 0
    ? data.aws_db_snapshot.latest_manual[0].db_snapshot_identifier
    : null
  )
  from_snapshot = local.snapshot_id != null
}

resource "aws_security_group" "rds" {
  name        = "${var.name_prefix}-rds-sg"
  description = var.security_group_description
  vpc_id      = var.vpc_id

  ingress {
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr_block]
  }
}

resource "aws_db_subnet_group" "main" {
  name       = "${var.name_prefix}-db-subnet"
  subnet_ids = var.private_subnet_ids
}

resource "aws_db_instance" "postgres" {
  identifier = "${var.name_prefix}-db"

  snapshot_identifier = local.from_snapshot ? local.snapshot_id : null

  engine            = local.from_snapshot ? null : "postgres"
  engine_version    = local.from_snapshot ? null : var.engine_version
  allocated_storage = local.from_snapshot ? null : var.allocated_storage
  db_name           = local.from_snapshot ? null : var.db_name
  username          = local.from_snapshot ? null : var.db_username
  password          = var.db_password

  instance_class         = var.instance_class
  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  skip_final_snapshot       = var.skip_final_snapshot
  final_snapshot_identifier = var.skip_final_snapshot ? null : var.final_snapshot_identifier

  lifecycle {
    # prevent_destroy = true
    # import 후 state 에 비밀번호가 없어 매 plan 마다 재설정 시도할 수 있음 — 실제 마스터 비번은 AWS 가 노출하지 않음
    ignore_changes = [password]
  }
}
