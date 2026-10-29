output "vpc_id" { value = module.vpc.vpc_id }
output "public_subnet_a_id" { value = module.vpc.public_subnet_a_id }
output "public_subnet_c_id" { value = module.vpc.public_subnet_c_id }
output "private_subnet_a_id" { value = module.vpc.private_subnet_a_id }
output "private_subnet_c_id" { value = module.vpc.private_subnet_c_id }
output "rds_endpoint" { value = module.rds.endpoint }
output "ecr_repository_url" { value = module.ecr.repository_url }
output "route53_zone_id" { value = aws_route53_zone.alpha.zone_id }
output "alpha_zone_id" { value = aws_route53_zone.alpha.zone_id }
output "alpha_name_servers" { value = aws_route53_zone.alpha.name_servers }
output "alpha_image_zone_id" { value = aws_route53_zone.alpha_image.zone_id }
output "alpha_image_name_servers" {
  value = aws_route53_zone.alpha_image.name_servers
}
