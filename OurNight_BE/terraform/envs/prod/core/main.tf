provider "aws" {
  region              = "ap-northeast-2"
  allowed_account_ids = [var.account_id]
}

provider "aws" {
  alias               = "us_east_1"
  region              = "us-east-1"
  allowed_account_ids = [var.account_id]
}

resource "aws_route53_zone" "main" {
  name = "ournight.co.kr"
}

# alpha 계정 core state 에서 hosted zone NS 를 읽어 위임 (수동 -var 불필요)
locals {
  alpha_core_state_path    = abspath("${path.module}/../../alpha/core/terraform.tfstate")
  alpha_core_state         = fileexists(local.alpha_core_state_path) ? jsondecode(file(local.alpha_core_state_path)) : null
  alpha_name_servers       = try(local.alpha_core_state.outputs.alpha_name_servers.value, [])
  alpha_image_name_servers = try(local.alpha_core_state.outputs.alpha_image_name_servers.value, [])
}

resource "aws_route53_record" "alpha_delegation" {
  count   = length(local.alpha_name_servers) > 0 ? 1 : 0
  zone_id = aws_route53_zone.main.zone_id
  name    = "alpha.ournight.co.kr"
  type    = "NS"
  ttl     = 300
  records = local.alpha_name_servers
}

resource "aws_route53_record" "alpha_image_delegation" {
  count   = length(local.alpha_image_name_servers) > 0 ? 1 : 0
  zone_id = aws_route53_zone.main.zone_id
  name    = "alpha.image.ournight.co.kr"
  type    = "NS"
  ttl     = 300
  records = local.alpha_image_name_servers
}

# ---------------------------------------------------
# VPC
# ---------------------------------------------------
module "vpc" {
  source      = "../../../modules/vpc"
  name_prefix = "ournight-prod"
}

# ---------------------------------------------------
# RDS
# ---------------------------------------------------
module "rds" {
  source      = "../../../modules/rds"
  name_prefix = "ournight-prod"

  vpc_id             = module.vpc.vpc_id
  vpc_cidr_block     = module.vpc.vpc_cidr_block
  private_subnet_ids = [module.vpc.private_subnet_a_id, module.vpc.private_subnet_c_id]

  db_password                 = var.db_password
  db_username                 = var.db_username
  final_snapshot_identifier   = "ournight-prod-db-final-snapshot"
  restore_snapshot_identifier = var.rds_restore_snapshot_identifier

  restore_latest_snapshot_source_db_identifier = "ournight-prod-db"
}

# ---------------------------------------------------
# ECR
# ---------------------------------------------------
module "ecr" {
  source          = "../../../modules/ecr"
  repository_name = "ournight-api-server"
}

# ---------------------------------------------------
# 프론트엔드 CDN (ournight.co.kr)
# ---------------------------------------------------
module "frontend_cdn" {
  source = "../../../modules/cloudfront-site"

  name_prefix     = "ournight-prod-frontend"
  domain_name     = "ournight.co.kr"
  s3_bucket_name  = "ournight-s3-prod"
  route53_zone_id = aws_route53_zone.main.zone_id
  is_spa          = true
  price_class     = "PriceClass_All"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }
}

# ---------------------------------------------------
# 이미지 CDN (image.ournight.co.kr)
# ---------------------------------------------------
module "image_cdn" {
  source = "../../../modules/cloudfront-site"

  name_prefix     = "ournight-prod-images"
  domain_name     = "image.ournight.co.kr"
  s3_bucket_name  = "ournight-s3-prod-runtime"
  route53_zone_id = aws_route53_zone.main.zone_id
  is_spa          = false
  price_class     = "PriceClass_All"
  cors_allowed_origins = [
    "https://ournight.co.kr",
    "https://alpha.ournight.co.kr",
    "http://localhost:3000",
  ]

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }
}
