package com.epubpro.core.bookconverter

/**
 * Cầu nối JNI tối giản tới libmobi.
 *
 * JNI chỉ nhận đường dẫn app-private và ghi ra thư mục tạm do Kotlin tạo.
 */
internal object MobiNativeDecoder {
    init {
        System.loadLibrary("book_converter")
    }

    /**
     * Giải mã ebook không DRM và trích xuất các part tái dựng ra thư mục đích.
     *
     * @param inputPath Đường dẫn file nguồn.
     * @param outputDirectory Thư mục tạm app-private.
     * @return Mã kết quả libmobi hoặc mã lỗi converter >= 100.
     */
    external fun decodeToDirectory(inputPath: String, outputDirectory: String): Int
}
