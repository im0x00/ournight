# 기존 AWS 리소스(계정 265988882003, 예전 1_core / image CDN)를 state 에 넣기 위한 import 블록.
# 첫 plan/apply 후 이 파일은 제거해도 됩니다(이미 state 에 반영됨).
#
# Route53 레코드는 prod 루트 zone에서 alpha 서브도메인을 dev hosted zone으로 위임한 뒤
# dev 계정의 alpha hosted zone에 새로 생성합니다. 예전 루트 zone 레코드는 import 하지 않습니다.
# manage_alpha_frontend_cdn = true 이고 기존 S3 버킷이 있으면 아래 frontend 버킷 import 가 필요할 수 있음.

import {
  to = module.vpc.aws_vpc.main
  id = "vpc-099d832d49dc1861a"
}

import {
  to = module.vpc.aws_internet_gateway.igw
  id = "igw-0cbef3e4f0df3d888"
}

import {
  to = module.vpc.aws_subnet.public_a
  id = "subnet-01391e2aade8623b9"
}

import {
  to = module.vpc.aws_subnet.public_c
  id = "subnet-0360e1e8644acbc05"
}

import {
  to = module.vpc.aws_subnet.private_a
  id = "subnet-09f6e974f13b3d10c"
}

import {
  to = module.vpc.aws_subnet.private_c
  id = "subnet-0d3fb271dbd6a3795"
}

import {
  to = module.vpc.aws_route_table.public_rt
  id = "rtb-03a9a2c0874cbc05b"
}

import {
  to = module.vpc.aws_route_table_association.public_a
  id = "subnet-01391e2aade8623b9/rtb-03a9a2c0874cbc05b"
}

import {
  to = module.vpc.aws_route_table_association.public_c
  id = "subnet-0360e1e8644acbc05/rtb-03a9a2c0874cbc05b"
}

import {
  to = module.rds.aws_security_group.rds
  id = "sg-0ad632d72996980fb"
}

import {
  to = module.rds.aws_db_subnet_group.main
  id = "ournight-db-subnet"
}

import {
  to = module.rds.aws_db_instance.postgres
  id = "ournight-db"
}

import {
  to = module.ecr.aws_ecr_repository.main
  id = "ournight-api-server"
}

# --- alpha.image.ournight.co.kr (기존 CloudFront + S3) ---

import {
  to = module.image_cdn.aws_s3_bucket.main
  id = "ournight-s3-alpha-runtime"
}

import {
  to = module.image_cdn.aws_cloudfront_origin_access_control.main
  id = "E2J1W636PRFJ06"
}

import {
  to = module.image_cdn.aws_acm_certificate.main
  id = "arn:aws:acm:us-east-1:265988882003:certificate/9c23ac33-6925-4705-a887-a185010e5588"
}

# aws_acm_certificate_validation 은 provider 가 import 를 지원하지 않음.
# 인증서·검증 DNS 는 import 되었으므로, 첫 apply 에서 validation 리소스만 생성되며 이미 ISSUED 이면 곧바로 완료됩니다.

import {
  to = module.image_cdn.aws_cloudfront_distribution.main
  id = "E32NKYVSL2W0O7"
}

import {
  to = module.image_cdn.aws_s3_bucket_policy.cloudfront_access
  id = "ournight-s3-alpha-runtime"
}

# --- alpha.ournight.co.kr SPA: 이미 버킷 ournight-s3-alpha 가 있을 때 (CreateBucket 409)
import {
  to = module.frontend_cdn[0].aws_s3_bucket.main
  id = "ournight-s3-alpha"
}
