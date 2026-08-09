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
        AiTtsVoiceModel("quang_minh", "Quang Minh", "vi", "60.6 MB", "minhquang.onnx"),
        AiTtsVoiceModel("ngoc_huyen", "Ngọc Huyền", "vi", "60.6 MB", "ngochuyen.onnx"),
        AiTtsVoiceModel("ngoc_ngan", "Ngọc Ngạn", "vi", "60.6 MB", "ngocngan3701.onnx"),
        AiTtsVoiceModel("phuong_mai", "Phương Mai", "vi", "60.6 MB", "maiphuong.onnx"),
        AiTtsVoiceModel("lac_phi", "Lạc Phi", "vi", "60.6 MB", "lacphi.onnx"),
        AiTtsVoiceModel("duy", "Duy", "vi", "60.6 MB", "duyoryx3175.onnx"),
        AiTtsVoiceModel("vais1000", "Vais1000", "vi", "20.6 MB", "minhkhang.onnx")
    )

    fun find(id: String?): AiTtsVoiceModel? = id?.let { voiceId ->
        aiVoices.firstOrNull { it.id == voiceId }
    }

    fun forLanguage(language: String): List<AiTtsVoiceModel> =
        aiVoices.filter { it.language.equals(language, ignoreCase = true) }

    fun modelUrl(model: AiTtsVoiceModel): String = "$MODEL_BASE_URL/${model.onnxFileName}"
}
