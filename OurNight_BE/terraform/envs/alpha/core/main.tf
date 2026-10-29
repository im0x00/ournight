provider "aws" {
  region              = "ap-northeast-2"
  allowed_account_ids = [var.account_id]
}

provider "aws" {
  alias               = "us_east_1"
  region              = "us-east-1"
  allowed_account_ids = [var.account_id]
}

resource "aws_route53_zone" "alpha" {
  name = "alpha.ournight.co.kr"
}

resource "aws_route53_zone" "alpha_image" {
  name = "alpha.image.ournight.co.kr"
}

resource "aws_route53_record" "alpha_image_delegation" {
  zone_id = aws_route53_zone.alpha.zone_id
  name    = "alpha.image.ournight.co.kr."
  type    = "NS"
  ttl     = 300
  records = aws_route53_zone.alpha_image.name_servers
}

# ---------------------------------------------------
# VPC (기존 1_core_infra와 동일 태그: ournight-vpc 등)
# ---------------------------------------------------
module "vpc" {
  source      = "../../../modules/vpc"
  name_prefix = "ournight"
}

# ---------------------------------------------------
# RDS (인스턴스 식별자 ournight-db)
# ---------------------------------------------------
module "rds" {
  source      = "../../../modules/rds"
  name_prefix = "ournight"

  vpc_id             = module.vpc.vpc_id
  vpc_cidr_block     = module.vpc.vpc_cidr_block
  private_subnet_ids = [module.vpc.private_subnet_a_id, module.vpc.private_subnet_c_id]

  db_password                 = var.db_password
  db_username                 = "yyoungjin"
  final_snapshot_identifier   = "ournight-db-final-snapshot"
  restore_snapshot_identifier = var.rds_restore_snapshot_identifier

  restore_latest_snapshot_source_db_identifier = "ournight-db"

  # 기존(import) 인스턴스와 맞춤 — description 만 달라도 SG 교체·RDS 재연결됨
  security_group_description = "Allow DB connection from VPC"
  engine_version             = "15.17"
  skip_final_snapshot        = true
}

# ---------------------------------------------------
# ECR
# ---------------------------------------------------
module "ecr" {
  source          = "../../../modules/ecr"
  repository_name = "ournight-api-server"
}

# ---------------------------------------------------
# 프론트엔드 CDN (alpha.ournight.co.kr) — 선택. 기본 off.
# ---------------------------------------------------
module "frontend_cdn" {
  count  = var.manage_alpha_frontend_cdn ? 1 : 0
  source = "../../../modules/cloudfront-site"

  name_prefix     = "ournight-alpha-frontend"
  domain_name     = "alpha.ournight.co.kr"
  s3_bucket_name  = "ournight-s3-alpha"
  route53_zone_id = aws_route53_zone.alpha.zone_id
  is_spa          = true
  price_class     = "PriceClass_100"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }
}

# ---------------------------------------------------
# 이미지 CDN (alpha.image.ournight.co.kr)
# ---------------------------------------------------
module "image_cdn" {
  source = "../../../modules/cloudfront-site"

  name_prefix     = "ournight-alpha-images"
  domain_name     = "alpha.image.ournight.co.kr"
  s3_bucket_name  = "ournight-s3-alpha-runtime"
  route53_zone_id = aws_route53_zone.alpha_image.zone_id
  is_spa          = false
  price_class     = "PriceClass_100"
  cors_allowed_origins = [
    "https://alpha.ournight.co.kr",
    "http://localhost:3000",
  ]

  # import 한 기존 리소스와 동일하게 (origin_id/코멘트/OAC/태그 변경 시 CF 재배포)
  distribution_comment = "alpha runtime images (ournight-s3-alpha-runtime)"
  origin_id            = "s3-ournight-s3-alpha-runtime"
  oac_name             = "ournight-s3-alpha-runtime-oac"
  oac_description      = "CloudFront OAC for ournight-s3-alpha-runtime"
  cloudfront_tags      = {}
  s3_tags = {
    Env     = "alpha-runtime"
    Project = "OurNight"
    Name    = "ournight-s3-alpha-runtime"
  }

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }
}
