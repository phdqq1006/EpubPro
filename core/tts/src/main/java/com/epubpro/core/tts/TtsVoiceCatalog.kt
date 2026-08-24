package com.epubpro.core.tts

data class AiTtsVoiceModel(
    val id: String,
    val displayName: String,
    val language: String,
    val downloadSize: String,
    val onnxFileName: String
)

object TtsVoiceCatalog {
    private const val MODEL_BASE_URL =
        "https://huggingface.co/doof-ferb/nghitts-copy/resolve/main/sherpa-onnx"

    const val TOKENS_URL = "$MODEL_BASE_URL/tokens.txt"

    val aiVoices: List<AiTtsVoiceModel> = listOf(
        AiTtsVoiceModel("ngoc_ngan", "Ngọc Ngạn", "vi", "60.6 MB", "ngocngan3701.onnx"),
        AiTtsVoiceModel("quang_minh", "Quang Minh", "vi", "60.6 MB", "minhquang.onnx"),
        AiTtsVoiceModel("ngoc_huyen", "Ngọc Huyền", "vi", "60.6 MB", "ngochuyen.onnx"),
        AiTtsVoiceModel("ngoc_huyen_new", "Ngọc Huyền (Bản mới)", "vi", "60.6 MB", "ngochuyennew.onnx"),
        AiTtsVoiceModel("phuong_mai", "Phương Mai", "vi", "60.6 MB", "maiphuong.onnx"),
        AiTtsVoiceModel("lac_phi", "Lạc Phi", "vi", "60.6 MB", "lacphi.onnx"),
        AiTtsVoiceModel("duy", "Duy", "vi", "60.6 MB", "duyoryx3175.onnx"),
        AiTtsVoiceModel("vais1000", "Minh Khang", "vi", "60.6 MB", "minhkhang.onnx"),
        AiTtsVoiceModel("ban_mai", "Ban Mai", "vi", "60.6 MB", "banmai.onnx"),
        AiTtsVoiceModel("chieu_thanh", "Chiếu Thành", "vi", "60.6 MB", "chieuthanh.onnx"),
        AiTtsVoiceModel("manh_dung", "Mạnh Dũng", "vi", "60.6 MB", "manhdung.onnx"),
        AiTtsVoiceModel("minh_thu", "Minh Thu", "vi", "60.6 MB", "minhthu.onnx"),
        AiTtsVoiceModel("my_tam_2", "Mỹ Tâm (Bản 2)", "vi", "60.6 MB", "mytam2.onnx"),
        AiTtsVoiceModel("my_tam_2794", "Mỹ Tâm (Bản 2794)", "vi", "60.6 MB", "mytam2794.onnx"),
        AiTtsVoiceModel("phuong_trang", "Phương Trang", "vi", "60.6 MB", "phuongtrang.onnx"),
        AiTtsVoiceModel("tai_an_2", "Tài An (Bản 2)", "vi", "60.6 MB", "taian2.onnx"),
        AiTtsVoiceModel("tai_an_4", "Tài An (Bản 4)", "vi", "60.6 MB", "taian4.onnx"),
        AiTtsVoiceModel("thanh_phuong", "Thanh Phương", "vi", "60.6 MB", "thanhphuong2.onnx"),
        AiTtsVoiceModel("thien_tam", "Thiện Tâm", "vi", "60.6 MB", "thientam.onnx"),
        AiTtsVoiceModel("tran_thanh", "Trấn Thành", "vi", "60.6 MB", "tranthanh3870.onnx"),
        AiTtsVoiceModel("viet_thao", "Việt Thảo", "vi", "60.6 MB", "vietthao3886.onnx"),
        AiTtsVoiceModel("calm_woman", "Calm Woman", "vi", "60.6 MB", "calmwoman3688.onnx"),
        AiTtsVoiceModel("deep_man", "Deep Man", "vi", "60.6 MB", "deepman3909.onnx"),
        AiTtsVoiceModel("adam", "Adam", "vi", "60.6 MB", "adam1.onnx"),
        AiTtsVoiceModel("yan_new", "Yan", "vi", "60.6 MB", "yannew.onnx")
    )

    fun find(id: String?): AiTtsVoiceModel? = id?.let { voiceId ->
        aiVoices.firstOrNull { it.id == voiceId }
    }

    fun forLanguage(language: String): List<AiTtsVoiceModel> =
        aiVoices.filter { it.language.equals(language, ignoreCase = true) }

    fun modelUrl(model: AiTtsVoiceModel): String = "$MODEL_BASE_URL/${model.onnxFileName}"
}
