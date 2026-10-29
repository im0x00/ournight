output "alb_dns_name" { value = aws_lb.api.dns_name }
output "ecs_cluster_name" { value = aws_ecs_cluster.api.name }
output "ecs_service_name" { value = aws_ecs_service.api.name }
output "redis_host" { value = aws_elasticache_cluster.redis.cache_nodes[0].address }
output "redis_port" { value = aws_elasticache_cluster.redis.port }
output "container_name" { value = local.n.container }
