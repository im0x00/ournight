package com.ournight.api_server.api.host.controller

import com.ournight.api_server.infra.s3.S3Service
import com.ournight.api_server.domain.user.model.User
import com.ournight.api_server.global.annotation.LoginUser
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/host/files")
class FileUploadController(
    private val s3Service: S3Service
) {
    /**
     * 이미지 파일들을 S3에 업로드합니다.
     * 
     * @param user 로그인한 사용자 (필수)
     * @param files 업로드할 이미지 파일 리스트
     * @param folder S3에 저장할 폴더 경로 (예: "stores", "menus", "users")
     * @return 업로드된 파일들의 URL 리스트
     */
    @PostMapping("/upload")
    fun uploadImages(
        @LoginUser user: User,
        @RequestParam("files") files: List<MultipartFile>,
        @RequestParam("folder") folder: String
    ): ResponseEntity<ImageUploadListResponse> {
        if (files.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ImageUploadListResponse(urls = emptyList(), message = "파일이 없습니다."))
        }

        val uploadedUrls = mutableListOf<String>()
        val errors = mutableListOf<String>()

        files.forEachIndexed { index, file ->
            if (file.isEmpty) {
                errors.add("파일 ${index + 1}: 파일이 비어있습니다.")
                return@forEachIndexed
            }

            // 이미지 파일인지 확인
            val contentType = file.contentType
            if (contentType == null || !contentType.startsWith("image/")) {
                errors.add("파일 ${index + 1}: 이미지 파일만 업로드 가능합니다.")
                return@forEachIndexed
            }

            try {
                val fileBytes = file.bytes
                val fileName = file.originalFilename ?: "image"
                // user 정보를 경로에 포함: {folder}/{userId}/{uuid}.{ext}
                val url = s3Service.uploadFile(fileBytes, fileName, folder, user.id!!)
                uploadedUrls.add(url)
            } catch (e: Exception) {
                errors.add("파일 ${index + 1}: 업로드 실패 - ${e.message}")
            }
        }

        val message = if (errors.isEmpty()) {
            "모든 파일 업로드 성공 (${uploadedUrls.size}개)"
        } else {
            "일부 파일 업로드 실패: ${errors.joinToString(", ")}"
        }

        return if (uploadedUrls.isNotEmpty()) {
            ResponseEntity.status(HttpStatus.CREATED)
                .body(ImageUploadListResponse(urls = uploadedUrls, message = message))
        } else {
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ImageUploadListResponse(urls = emptyList(), message = message))
        }
    }
}

data class ImageUploadListResponse(
    val urls: List<String>,
    val message: String
)
