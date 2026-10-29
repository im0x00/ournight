package com.ournight.api_server.infra.s3

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

@Configuration
class S3Config(
    @Value("\${aws.s3.region:ap-northeast-2}")
    private val region: String,
    @Value("\${aws.s3.access-key-id:}")
    private val accessKeyId: String,
    @Value("\${aws.s3.secret-access-key:}")
    private val secretAccessKey: String
) {
    @Bean
    fun s3Client(): S3Client {
        val credentials = if (accessKeyId.isNotEmpty() && secretAccessKey.isNotEmpty()) {
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            )
        } else {
            // 환경 변수에서 자동으로 읽기 시도
            software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider.create()
        }

        return S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentials)
            .build()
    }
}
