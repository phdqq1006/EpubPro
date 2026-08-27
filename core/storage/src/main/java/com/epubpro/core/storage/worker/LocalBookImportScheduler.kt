package com.epubpro.core.storage.worker

import android.content.Context
import android.net.Uri
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.epubpro.core.bookconverter.BookConversionErrorCode
import com.epubpro.core.bookconverter.BookConversionException
import com.epubpro.core.bookconverter.BookFormatSniffer
import com.epubpro.core.storage.EpubStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sao lưu và lập lịch chuyển đổi ebook local bằng WorkManager.
 *
 * URI chỉ được đọc một lần ở foreground ngắn; toàn bộ phần nặng chạy sau đó trên Worker,
 * vì vậy việc đóng ứng dụng hoặc process death không làm mất file nguồn.
 */
@Singleton
class LocalBookImportScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageManager: EpubStorageManager,
    private val formatSniffer: BookFormatSniffer
) {
    /**
     * Sao lưu URI, kiểm tra phần mở rộng và enqueue một tác vụ chuyển đổi riêng.
     *
     * @param uri URI file người dùng chọn.
     * @param originalName Tên hiển thị gốc của file.
     * @return ID WorkManager để UI theo dõi tiến độ.
     * @throws BookConversionException Nếu phần mở rộng không được hỗ trợ.
     */
    suspend fun enqueue(uri: Uri, originalName: String?): UUID = withContext(Dispatchers.IO) {
        val sourceFile = storageManager.importLocalBookSource(uri, originalName)
        val sourceFormat = formatSniffer.sniff(sourceFile)
        if (sourceFormat == null) {
            sourceFile.delete()
            throw BookConversionException(BookConversionErrorCode.UNSUPPORTED_EXTENSION)
        }

        val outputFile = storageManager.createConvertedEpubFile()
        val request = OneTimeWorkRequestBuilder<LocalBookImportWorker>()
            .setInputData(
                workDataOf(
                    LocalBookImportWorker.KEY_SOURCE_PATH to sourceFile.absolutePath,
                    LocalBookImportWorker.KEY_OUTPUT_PATH to outputFile.absolutePath,
                    LocalBookImportWorker.KEY_ORIGINAL_NAME to (originalName ?: sourceFile.name),
                    LocalBookImportWorker.KEY_SOURCE_FORMAT to sourceFormat.name
                )
            )
            .addTag(LocalBookImportWorker.TAG)
            .build()
        try {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "${LocalBookImportWorker.UNIQUE_WORK_NAME}_${sourceFile.nameWithoutExtension}",
                ExistingWorkPolicy.KEEP,
                request
            )
            request.id
        } catch (error: Throwable) {
            sourceFile.delete()
            outputFile.delete()
            throw error
        }
    }
}
