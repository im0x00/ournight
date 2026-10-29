package com.ournight.api_server.infra.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.ournight.api_server.domain.store.dao.StoreTableRepository
import com.ournight.api_server.domain.store.domain.StoreTable
import com.ournight.api_server.infra.s3.S3Service
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class QrCodeService(
    private val storeTableRepository: StoreTableRepository,
    private val s3Service: S3Service,
    @Value("\${aws.s3.local-storage-enabled:false}")
    private val localStorageEnabled: Boolean,
    @Value("\${aws.s3.local-base-url:http://localhost:8080/local-uploads}")
    private val localBaseUrl: String
) {

    @Transactional
    fun getOrGenerateQrImages(qrLinks: List<String>): List<QrGenerateResult> {
        val tables = storeTableRepository.findByQrLinkIn(qrLinks)
        val tableMap = tables.associateBy { it.qrLink }

        return qrLinks.map { link ->
            val table = tableMap[link] ?: findTableByLink(link)
                ?: throw IllegalArgumentException("해당 qrLink에 매칭되는 테이블이 없습니다: $link")

            if (table.qrLink != link) {
                table.qrLink = link
                table.qrImageUrl = null
            }

            val imageUrl = table.qrImageUrl?.takeIf { canReuseQrImageUrl(it) } ?: run {
                val qrBytes = generateQrPng(link)
                val uploadedUrl = s3Service.uploadBytes(qrBytes, "image/png", "qr-codes")
                table.qrImageUrl = uploadedUrl
                uploadedUrl
            }

            QrGenerateResult(qrLink = link, qrImageUrl = imageUrl)
        }
    }

    private fun canReuseQrImageUrl(imageUrl: String): Boolean {
        if (!localStorageEnabled) return true
        return imageUrl.startsWith(localBaseUrl.trimEnd('/'))
    }

    private fun findTableByLink(link: String): StoreTable? {
        val tableId = parseTableId(link) ?: return null
        return storeTableRepository.findById(tableId).orElse(null)
    }

    private fun parseTableId(link: String): UUID? {
        val decoded = URLDecoder.decode(link, StandardCharsets.UTF_8)
        val queryId = Regex("""(?:[?&])tableId=([0-9a-fA-F-]{36})(?:&|$)""")
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
        val pathId = Regex("""/tables/([0-9a-fA-F-]{36})(?:[/?#]|$)""")
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
        return (queryId ?: pathId)?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    private fun generateQrPng(content: String, size: Int = 300): ByteArray {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = MultiFormatWriter().encode(
            content, BarcodeFormat.QR_CODE, size, size, hints
        )
        val output = ByteArrayOutputStream()
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", output)
        return output.toByteArray()
    }
}

data class QrGenerateResult(
    val qrLink: String,
    val qrImageUrl: String
)
