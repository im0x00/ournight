provider "aws" {
  region              = "ap-northeast-2"
  allowed_account_ids = [var.account_id]
}

data "terraform_remote_state" "core" {
  backend = "local"
  config = {
    path = "${path.module}/../core/terraform.tfstate"
  }
}

module "ecs_api" {
  source = "../../../modules/ecs-api"

  # name_prefix는 레거시 네이밍 사용 시 무시됨
  name_prefix               = "unused"
  use_legacy_ournight_names = true

  vpc_id = data.terraform_remote_state.core.outputs.vpc_id
  public_subnet_ids = [
    data.terraform_remote_state.core.outputs.public_subnet_a_id,
    data.terraform_remote_state.core.outputs.public_subnet_c_id,
  ]
  private_subnet_ids = [
    data.terraform_remote_state.core.outputs.private_subnet_a_id,
    data.terraform_remote_state.core.outputs.private_subnet_c_id,
  ]
  route53_zone_id = data.terraform_remote_state.core.outputs.route53_zone_id

  ecr_image  = "${data.terraform_remote_state.core.outputs.ecr_repository_url}:${var.ecr_image_tag}"
  api_domain = var.api_domain

  spring_profile       = "alpha"
  app_client_base_url  = var.app_client_base_url
  cors_allowed_origins = var.app_client_base_url
  aws_s3_bucket        = "ournight-s3-alpha-runtime"
  aws_s3_domain        = "alpha.image.ournight.co.kr"
  ssm_parameter_prefix = "/ournight/alpha"

  # alpha와 prod 동일 스펙
  container_cpu      = 512
  container_memory   = 1024
  ecs_desired_count  = 1
  ecs_min_capacity   = 1
  ecs_max_capacity   = 4
  log_retention_days = 14
}
