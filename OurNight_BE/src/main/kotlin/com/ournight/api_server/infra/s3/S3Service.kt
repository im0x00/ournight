package com.ournight.api_server.infra.s3

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

@Service
class S3Service(
    private val s3Client: S3Client,
    @Value("\${aws.s3.bucket}")
    private val bucketName: String,
    @Value("\${aws.s3.region:ap-northeast-2}")
    private val region: String,
    @Value("\${aws.s3.domain:image.ournight.co.kr}")
    private val domain: String,
    @Value("\${aws.s3.local-storage-enabled:false}")
    private val localStorageEnabled: Boolean,
    @Value("\${aws.s3.local-storage-dir:build/local-uploads}")
    private val localStorageDir: String,
    @Value("\${aws.s3.local-base-url:http://localhost:8080/local-uploads}")
    private val localBaseUrl: String
) {
    /**
     * 파일을 S3에 업로드하고 URL을 반환합니다.
     * @param fileBytes 파일 바이트 배열
     * @param fileName 원본 파일명
     * @param folder 저장할 폴더 경로 (예: "stores", "menus")
     * @param userId 사용자 ID (경로에 포함)
     * @return S3에 저장된 파일의 URL
     */
    fun uploadFile(
        fileBytes: ByteArray,
        fileName: String,
        folder: String,
        userId: java.util.UUID
    ): String {
        val fileExtension = fileName.substringAfterLast('.', "")
        val uniqueFileName = "${UUID.randomUUID()}.${fileExtension}"
        val key = "$folder/$userId/$uniqueFileName"

        if (localStorageEnabled) {
            return storeLocal(key, fileBytes)
        }

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(getContentType(fileExtension))
            .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(fileBytes))

        // 커스텀 도메인으로 URL 반환
        return "https://$domain/$key"
    }

    fun uploadBytes(
        bytes: ByteArray,
        contentType: String,
        folder: String,
        fileExtension: String = "png"
    ): String {
        val key = "$folder/${UUID.randomUUID()}.${fileExtension}"

        if (localStorageEnabled) {
            return storeLocal(key, bytes)
        }

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .build()

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes))

        return "https://$domain/$key"
    }

    private fun storeLocal(key: String, bytes: ByteArray): String {
        val safeKey = key.trimStart('/').replace("..", "")
        val target = Path.of(localStorageDir).resolve(safeKey).normalize()
        Files.createDirectories(target.parent)
        Files.write(target, bytes)
        return "${localBaseUrl.trimEnd('/')}/$safeKey"
    }

    /**
     * 파일 확장자에 따른 Content-Type 반환
     */
    private fun getContentType(fileExtension: String): String {
        return when (fileExtension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> "application/octet-stream"
        }
    }
}
