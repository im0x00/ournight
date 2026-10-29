data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  legacy_names = {
    alb_sg         = "ournight-alb-sg"
    ecs_task_sg    = "ournight-ecs-task-sg"
    redis_sg       = "ournight-redis-sg"
    alb            = "ournight-api-alb"
    target_group   = "ournight-api-ecs-tg"
    ecs_cluster    = "ournight-api-cluster"
    log_group      = "/ecs/ournight-api"
    execution_role = "ournight-ecs-task-execution-role"
    execution_ssm  = "ournight-ecs-task-execution-ssm"
    task_role      = "ournight-ecs-task-role"
    task_s3_policy = "ournight-ecs-task-s3"
    task_family    = "ournight-api"
    ecs_service    = "ournight-api-service"
    container      = "ournight-api"
    cpu_policy     = "ournight-api-cpu-target-tracking"
    memory_policy  = "ournight-api-memory-target-tracking"
    redis_subnet   = "ournight-redis-subnet"
    redis_cluster  = "ournight-redis"
  }

  n = var.use_legacy_ournight_names ? local.legacy_names : {
    alb_sg         = "${var.name_prefix}-alb-sg"
    ecs_task_sg    = "${var.name_prefix}-ecs-task-sg"
    redis_sg       = "${var.name_prefix}-redis-sg"
    alb            = "${var.name_prefix}-alb"
    target_group   = "${var.name_prefix}-tg"
    ecs_cluster    = "${var.name_prefix}-cluster"
    log_group      = "/ecs/${var.name_prefix}"
    execution_role = "${var.name_prefix}-ecs-execution-role"
    execution_ssm  = "${var.name_prefix}-ecs-execution-ssm"
    task_role      = "${var.name_prefix}-ecs-task-role"
    task_s3_policy = "${var.name_prefix}-ecs-task-s3"
    task_family    = "${var.name_prefix}-api"
    ecs_service    = "${var.name_prefix}-service"
    container      = "${var.name_prefix}-api"
    cpu_policy     = "${var.name_prefix}-cpu-tracking"
    memory_policy  = "${var.name_prefix}-memory-tracking"
    redis_subnet   = "${var.name_prefix}-redis-subnet"
    redis_cluster  = "${var.name_prefix}-redis"
  }

  app_environment = [
    { name = "SPRING_PROFILES_ACTIVE", value = var.spring_profile },
    { name = "APP_CLIENT_BASE_URL", value = var.app_client_base_url },
    { name = "CORS_ALLOWED_ORIGINS", value = coalesce(var.cors_allowed_origins, var.app_client_base_url) },
    { name = "AWS_REGION", value = data.aws_region.current.name },
    { name = "AWS_S3_BUCKET", value = var.aws_s3_bucket },
    { name = "AWS_S3_DOMAIN", value = var.aws_s3_domain },
    { name = "REDIS_HOST", value = aws_elasticache_cluster.redis.cache_nodes[0].address },
    { name = "REDIS_PORT", value = tostring(aws_elasticache_cluster.redis.port) },
  ]

  ssm_secret_names = [
    "DB_URL", "DB_USERNAME", "DB_PASSWORD",
    "JWT_SECRET",
    "KAKAO_CLIENT_ID", "KAKAO_CLIENT_SECRET", "KAKAO_REDIRECT_URI",
  ]

  app_secrets = [
    for name in local.ssm_secret_names : {
      name      = name
      valueFrom = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_parameter_prefix}/${name}"
    }
  ]
}

# ---------------------------------------------------
# 보안 그룹
# ---------------------------------------------------
resource "aws_security_group" "alb" {
  name   = local.n.alb_sg
  vpc_id = var.vpc_id

  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "ecs_task" {
  name   = local.n.ecs_task_sg
  vpc_id = var.vpc_id

  ingress {
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_security_group" "redis" {
  name   = local.n.redis_sg
  vpc_id = var.vpc_id

  ingress {
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_task.id]
  }
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

# ---------------------------------------------------
# ALB
# ---------------------------------------------------
resource "aws_lb" "api" {
  name               = local.n.alb
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = var.public_subnet_ids
}

resource "aws_lb_target_group" "api" {
  name        = local.n.target_group
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    path                = "/api/health"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
    interval            = 30
    timeout             = 5
  }

  lifecycle { create_before_destroy = true }
}

# ---------------------------------------------------
# ACM + Route53 (API 도메인)
# ---------------------------------------------------
resource "aws_acm_certificate" "api" {
  domain_name       = var.api_domain
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
}

resource "aws_route53_record" "cert_validation" {
  for_each = {
    for dvo in aws_acm_certificate.api.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }
  allow_overwrite = true
  name            = each.value.name
  records         = [each.value.record]
  ttl             = 60
  type            = each.value.type
  zone_id         = var.route53_zone_id
}

resource "aws_acm_certificate_validation" "api" {
  certificate_arn         = aws_acm_certificate.api.arn
  validation_record_fqdns = [for r in aws_route53_record.cert_validation : r.fqdn]
}

# ---------------------------------------------------
# ALB 리스너
# ---------------------------------------------------
resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.api.arn
  port              = "443"
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-2016-08"
  certificate_arn   = aws_acm_certificate_validation.api.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.api.arn
  port              = "80"
  protocol          = "HTTP"

  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_route53_record" "api_alias" {
  zone_id = var.route53_zone_id
  name    = var.api_domain
  type    = "A"

  alias {
    name                   = aws_lb.api.dns_name
    zone_id                = aws_lb.api.zone_id
    evaluate_target_health = true
  }
}

# ---------------------------------------------------
# ECS Fargate
# ---------------------------------------------------
resource "aws_ecs_cluster" "api" {
  name = local.n.ecs_cluster
}

resource "aws_cloudwatch_log_group" "api" {
  name              = local.n.log_group
  retention_in_days = var.log_retention_days
}

resource "aws_iam_role" "execution" {
  name = local.n.execution_role

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "execution_managed" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_ssm" {
  name = local.n.execution_ssm
  role = aws_iam_role.execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["ssm:GetParameter", "ssm:GetParameters"]
        Resource = "arn:aws:ssm:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:parameter${var.ssm_parameter_prefix}/*"
      },
      {
        Effect   = "Allow"
        Action   = "kms:Decrypt"
        Resource = "*"
      }
    ]
  })
}

resource "aws_iam_role" "task" {
  name = local.n.task_role

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "task_s3" {
  name = local.n.task_s3_policy
  role = aws_iam_role.task.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"]
        Resource = "arn:aws:s3:::${var.aws_s3_bucket}/*"
      },
      {
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = "arn:aws:s3:::${var.aws_s3_bucket}"
      }
    ]
  })
}

resource "aws_ecs_task_definition" "api" {
  family                   = local.n.task_family
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.container_cpu
  memory                   = var.container_memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name      = local.n.container
    image     = var.ecr_image
    essential = true

    portMappings = [
      {
        containerPort = 8080
        hostPort      = 8080
        protocol      = "tcp"
      }
    ]

    mountPoints    = []
    systemControls = []
    volumesFrom    = []

    environment = local.app_environment
    secrets     = local.app_secrets

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.api.name
        awslogs-region        = data.aws_region.current.name
        awslogs-stream-prefix = "api"
      }
    }
  }])

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_ecs_service" "api" {
  name                              = local.n.ecs_service
  cluster                           = aws_ecs_cluster.api.id
  task_definition                   = aws_ecs_task_definition.api.arn
  desired_count                     = var.ecs_desired_count
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 180

  network_configuration {
    subnets          = var.public_subnet_ids
    security_groups  = [aws_security_group.ecs_task.id]
    assign_public_ip = true
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = local.n.container
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.https, aws_iam_role_policy_attachment.execution_managed]
}

resource "aws_appautoscaling_target" "api" {
  max_capacity       = var.ecs_max_capacity
  min_capacity       = var.ecs_min_capacity
  resource_id        = "service/${aws_ecs_cluster.api.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  service_namespace  = "ecs"
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = local.n.cpu_policy
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.api.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value = 60
    predefined_metric_specification { predefined_metric_type = "ECSServiceAverageCPUUtilization" }
  }
}

resource "aws_appautoscaling_policy" "memory" {
  name               = local.n.memory_policy
  policy_type        = "TargetTrackingScaling"
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension
  service_namespace  = aws_appautoscaling_target.api.service_namespace

  target_tracking_scaling_policy_configuration {
    target_value = 75
    predefined_metric_specification { predefined_metric_type = "ECSServiceAverageMemoryUtilization" }
  }
}

# ---------------------------------------------------
# Redis (ElastiCache)
# ---------------------------------------------------
resource "aws_elasticache_subnet_group" "redis" {
  name       = local.n.redis_subnet
  subnet_ids = var.private_subnet_ids
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id           = local.n.redis_cluster
  engine               = "redis"
  node_type            = "cache.t3.micro"
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  subnet_group_name    = aws_elasticache_subnet_group.redis.name
  security_group_ids   = [aws_security_group.redis.id]
}
