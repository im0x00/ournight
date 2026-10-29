terraform {
  required_providers {
    aws = {
      source                = "hashicorp/aws"
      configuration_aliases = [aws.us_east_1]
    }
  }
}

locals {
  origin_id            = coalesce(var.origin_id, "${var.name_prefix}-s3-origin")
  distribution_comment = coalesce(var.distribution_comment, "${var.name_prefix}-cdn")
  oac_name             = coalesce(var.oac_name, "${var.name_prefix}-oac")
  oac_description      = coalesce(var.oac_description, "OAC for ${var.s3_bucket_name}")
  cloudfront_tags      = var.cloudfront_tags != null ? var.cloudfront_tags : { Name = "${var.name_prefix}-cdn" }
  s3_tags              = var.s3_tags != null ? var.s3_tags : { Name = var.s3_bucket_name }
}

# ---------------------------------------------------
# S3 버킷
# ---------------------------------------------------
resource "aws_s3_bucket" "main" {
  bucket        = var.s3_bucket_name
  force_destroy = false
  tags          = local.s3_tags
}

# ---------------------------------------------------
# OAC (Origin Access Control)
# ---------------------------------------------------
resource "aws_cloudfront_origin_access_control" "main" {
  name                              = local.oac_name
  description                       = local.oac_description
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# ---------------------------------------------------
# ACM (us-east-1 필수)
# ---------------------------------------------------
resource "aws_acm_certificate" "main" {
  provider          = aws.us_east_1
  domain_name       = var.domain_name
  validation_method = "DNS"
  lifecycle { create_before_destroy = true }
}

resource "aws_route53_record" "cert_validation" {
  provider = aws.us_east_1
  for_each = {
    for dvo in aws_acm_certificate.main.domain_validation_options : dvo.domain_name => {
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

resource "aws_acm_certificate_validation" "main" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.main.arn
  validation_record_fqdns = [for r in aws_route53_record.cert_validation : r.fqdn]
}

resource "aws_cloudfront_response_headers_policy" "main" {
  count = length(var.cors_allowed_origins) > 0 ? 1 : 0

  name    = "${var.name_prefix}-response-headers"
  comment = "Security and CORS response headers for ${var.domain_name}"

  cors_config {
    access_control_allow_credentials = false

    access_control_allow_headers {
      items = ["*"]
    }

    access_control_allow_methods {
      items = ["GET", "HEAD", "OPTIONS"]
    }

    access_control_allow_origins {
      items = var.cors_allowed_origins
    }

    access_control_expose_headers {
      items = ["ETag"]
    }

    access_control_max_age_sec = 600
    origin_override            = true
  }

  security_headers_config {
    content_type_options {
      override = true
    }

    frame_options {
      frame_option = "SAMEORIGIN"
      override     = true
    }

    referrer_policy {
      referrer_policy = "strict-origin-when-cross-origin"
      override        = true
    }

    strict_transport_security {
      access_control_max_age_sec = 31536000
      include_subdomains         = true
      override                   = true
      preload                    = false
    }

    xss_protection {
      mode_block = true
      override   = true
      protection = true
    }
  }
}

# ---------------------------------------------------
# CloudFront
# ---------------------------------------------------
resource "aws_cloudfront_distribution" "main" {
  aliases             = [var.domain_name]
  comment             = local.distribution_comment
  enabled             = true
  default_root_object = var.is_spa ? "index.html" : null
  http_version        = "http2"
  is_ipv6_enabled     = true
  price_class         = var.price_class
  retain_on_delete    = false
  wait_for_deployment = true

  dynamic "custom_error_response" {
    for_each = var.is_spa ? [403, 404] : []
    content {
      error_caching_min_ttl = 0
      error_code            = custom_error_response.value
      response_code         = 200
      response_page_path    = "/index.html"
    }
  }

  origin {
    connection_attempts      = 3
    connection_timeout       = 10
    domain_name              = aws_s3_bucket.main.bucket_regional_domain_name
    origin_access_control_id = aws_cloudfront_origin_access_control.main.id
    origin_id                = local.origin_id
  }

  default_cache_behavior {
    allowed_methods            = ["GET", "HEAD"]
    cached_methods             = ["GET", "HEAD"]
    target_origin_id           = local.origin_id
    viewer_protocol_policy     = "redirect-to-https"
    compress                   = true
    cache_policy_id            = "658327ea-f89d-4fab-a63d-7e88639e58f6"
    origin_request_policy_id   = "88a5eaf4-2fd4-4709-b370-b4c650ea3fcf"
    response_headers_policy_id = length(var.cors_allowed_origins) > 0 ? aws_cloudfront_response_headers_policy.main[0].id : "5cc3b908-e619-4b99-88e5-2cf7f45965bd"
    smooth_streaming           = false
  }

  restrictions {
    geo_restriction { restriction_type = "none" }
  }

  viewer_certificate {
    acm_certificate_arn            = aws_acm_certificate_validation.main.certificate_arn
    cloudfront_default_certificate = false
    minimum_protocol_version       = "TLSv1.2_2021"
    ssl_support_method             = "sni-only"
  }

  tags = local.cloudfront_tags
}

resource "aws_s3_bucket_policy" "cloudfront_access" {
  bucket = aws_s3_bucket.main.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowCloudFrontServicePrincipalReadOnly"
      Effect    = "Allow"
      Principal = { Service = "cloudfront.amazonaws.com" }
      Action    = "s3:GetObject"
      Resource  = "${aws_s3_bucket.main.arn}/*"
      Condition = {
        StringEquals = { "AWS:SourceArn" = aws_cloudfront_distribution.main.arn }
      }
    }]
  })

  depends_on = [aws_cloudfront_distribution.main]
}

# ---------------------------------------------------
# Route53 Alias
# ---------------------------------------------------
resource "aws_route53_record" "alias" {
  zone_id = var.route53_zone_id
  name    = var.domain_name
  type    = "A"

  alias {
    name                   = aws_cloudfront_distribution.main.domain_name
    zone_id                = aws_cloudfront_distribution.main.hosted_zone_id
    evaluate_target_health = false
  }
}
