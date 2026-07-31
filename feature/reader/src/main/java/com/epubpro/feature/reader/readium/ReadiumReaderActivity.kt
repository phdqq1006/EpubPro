package com.epubpro.feature.reader.readium

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.lifecycleScope
import com.epubpro.core.designsystem.theme.EpubProTheme
import com.epubpro.feature.reader.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File

@AndroidEntryPoint
class ReadiumReaderActivity : AppCompatActivity() {

    @javax.inject.Inject
    lateinit var readerPreferencesManager: com.epubpro.core.storage.ReaderPreferencesManager

    @javax.inject.Inject
    lateinit var bookRepository: com.epubpro.domain.repository.BookRepository

    private var publication: Publication? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_readium_reader)

        val bookId = intent.getStringExtra(EXTRA_BOOK_ID) ?: ""
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH) ?: ""
        val bookTitle = intent.getStringExtra(EXTRA_BOOK_TITLE) ?: "Đọc truyện"

        val composeTopBar = findViewById<ComposeView>(R.id.compose_top_bar)
        composeTopBar.setContent {
            EpubProTheme {
                var showSettingsSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var showTtsChoiceSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                var settings by androidx.compose.runtime.remember { 
                    androidx.compose.runtime.mutableStateOf(readerPreferencesManager.getSettings()) 
                }

                TopAppBar(
                    title = {
                        Text(
                            text = bookTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Quay lại"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { showTtsChoiceSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Headset,
                                contentDescription = "Đọc sách TTS"
                            )
                        }
                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.FormatSize,
                                contentDescription = "Cài đặt hiển thị"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                if (showSettingsSheet) {
                    androidx.compose.material3.ModalBottomSheet(
                        onDismissRequest = { showSettingsSheet = false }
                    ) {
                        com.epubpro.feature.reader.ReaderSettingsContent(
                            settings = settings,
                            onSettingsChanged = { newSettings ->
                                settings = newSettings
                                readerPreferencesManager.saveSettings(newSettings)
                                val prefs = org.readium.r2.navigator.epub.EpubPreferences(
                                    fontSize = (newSettings.fontSizeSp / 16.0)
                                )
                                navigatorFragment?.submitPreferences(prefs)
                            }
                        )
                    }
                }

                if (showTtsChoiceSheet) {
                    androidx.compose.material3.ModalBottomSheet(
                        onDismissRequest = { showTtsChoiceSheet = false }
                    ) {
                        ReadiumTtsChoiceContent(
                            onSelectCustomTts = {
                                showTtsChoiceSheet = false
                                Toast.makeText(this@ReadiumReaderActivity, "Đang quay lại Thư viện để mở Custom Engine", Toast.LENGTH_SHORT).show()
                                finish()
                            },
                            onSelectReadiumTts = {
                                showTtsChoiceSheet = false
                                Toast.makeText(this@ReadiumReaderActivity, "Readium Native TTS sắp ra mắt", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }

        if (filePath.isBlank() || !File(filePath).exists()) {
            Toast.makeText(this, "Không tìm thấy file sách EPUB", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        openReadiumPublication(filePath, bookId)
    }

    private val navigatorListener = object : EpubNavigatorFragment.Listener {
        override fun onTap(point: android.graphics.PointF): Boolean {
            val screenWidth = resources.displayMetrics.widthPixels
            val edgeWidth = screenWidth * 0.2f // 20% cạnh màn hình

            return when {
                point.x < edgeWidth -> {
                    navigatorFragment?.goBackward(animated = false)
                    true
                }
                point.x > screenWidth - edgeWidth -> {
                    navigatorFragment?.goForward(animated = false)
                    true
                }
                else -> false 
            }
        }

        @ExperimentalReadiumApi
        override fun onExternalLinkActivated(url: AbsoluteUrl) {

        }
    }

    private var navigatorFragment: EpubNavigatorFragment? = null

    private fun openReadiumPublication(filePath: File, bookId: String) {
        lifecycleScope.launch {
            try {
                val httpClient = DefaultHttpClient()
                val assetRetriever = AssetRetriever(this@ReadiumReaderActivity.contentResolver, httpClient)
                
                // Dùng EpubParser thay vì DefaultPublicationParser để tránh lỗi pdfFactory
                val publicationOpener = PublicationOpener(
                    publicationParser = org.readium.r2.streamer.parser.epub.EpubParser()
                )

                val url = filePath.toUrl()
                
                // Xử lý an toàn Try/Result của AssetRetriever
                val assetResult = assetRetriever.retrieve(url)
                val asset = try {
                    assetResult.getOrNull()
                } catch(e: Exception) { null } ?: return@launch
                
                publicationOpener.open(asset, allowUserInteraction = false)
                    .onSuccess { pub ->
                        publication = pub
                        
                        // Khôi phục tiến trình đọc
                        var initialLocator: org.readium.r2.shared.publication.Locator? = null
                        val savedProgress = bookRepository.getReadingProgress(bookId).firstOrNull()
                        if (savedProgress != null) {
                            try {
                                if (savedProgress.currentCfi.startsWith("{")) {
                                    initialLocator = org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(savedProgress.currentCfi))
                                }
                            } catch(e: Exception) { e.printStackTrace() }
                            
                            if (initialLocator == null) {
                                val link = pub.readingOrder.getOrNull(savedProgress.chapterIndex)
                                if (link != null) {
                                    initialLocator = pub.locatorFromLink(link)
                                }
                            }
                        }

                        val factory = EpubNavigatorFactory(pub)
                        
                        supportFragmentManager.fragmentFactory = factory.createFragmentFactory(
                            initialLocator = initialLocator,
                            listener = navigatorListener
                        )
                        navigatorFragment = supportFragmentManager.fragmentFactory.instantiate(
                            classLoader,
                            EpubNavigatorFragment::class.java.name
                        ) as EpubNavigatorFragment

                        supportFragmentManager.beginTransaction()
                            .replace(R.id.readium_fragment_container, navigatorFragment!!)
                            .commitNow()
                            
                        lifecycleScope.launch {
                            navigatorFragment?.currentLocator?.collect { locator ->
                                val index = pub.readingOrder.indexOfFirst { it.href.toString() == locator.href.toString() }
                                val progress = com.epubpro.domain.model.ReadingProgress(
                                    bookId = bookId,
                                    currentCfi = locator.toJSON().toString(),
                                    chapterIndex = if (index >= 0) index else 0,
                                    pageIndex = 1,
                                    progressPercentage = locator.locations.progression?.toFloat() ?: 0f
                                )
                                bookRepository.saveReadingProgress(progress)
                            }
                        }
                        
                        // Initialize first preferences
                        val prefs = org.readium.r2.navigator.epub.EpubPreferences(
                            fontSize = (readerPreferencesManager.getSettings().fontSizeSp / 16.0)
                        )
                        navigatorFragment?.submitPreferences(prefs)
                    }
                    .onFailure { error ->
                        Log.e("epub", "openReadiumPublication: "+error.message )
                        Toast.makeText(
                            this@ReadiumReaderActivity,
                            "Lỗi mở sách Readium: ${error.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@ReadiumReaderActivity,
                    "Lỗi ngoại lệ Readium: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun openReadiumPublication(filePathString: String, bookId: String) {
        openReadiumPublication(File(filePathString), bookId)
    }

    override fun onDestroy() {
        super.onDestroy()
        publication?.close()
    }

    companion object {
        const val EXTRA_BOOK_ID = "extra_book_id"
        const val EXTRA_FILE_PATH = "extra_file_path"
        const val EXTRA_BOOK_TITLE = "extra_book_title"

        fun createIntent(context: Context, bookId: String, filePath: String, title: String): Intent {
            return Intent(context, ReadiumReaderActivity::class.java).apply {
                putExtra(EXTRA_BOOK_ID, bookId)
                putExtra(EXTRA_FILE_PATH, filePath)
                putExtra(EXTRA_BOOK_TITLE, title)
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun ReadiumTtsChoiceContent(
    onSelectCustomTts: () -> Unit,
    onSelectReadiumTts: () -> Unit
) {
    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        androidx.compose.material3.Text(
            text = "Chọn trình đọc giọng nói (TTS)",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            modifier = androidx.compose.ui.Modifier.padding(bottom = 16.dp)
        )

        androidx.compose.material3.Card(
            onClick = onSelectCustomTts,
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier.padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier.padding(end = 16.dp),
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("EpubPro Custom Engine", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.Text("Khuyên dùng: Hỗ trợ tô sáng đoạn đọc và điều khiển chi tiết.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }

        androidx.compose.material3.Card(
            onClick = onSelectReadiumTts,
            modifier = androidx.compose.ui.Modifier.fillMaxWidth()
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier.padding(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    modifier = androidx.compose.ui.Modifier.padding(end = 16.dp)
                )
                androidx.compose.foundation.layout.Column {
                    androidx.compose.material3.Text("Readium Native TTS", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    androidx.compose.material3.Text("Thử nghiệm: Trình đọc giọng nói cơ bản của bộ SDK Readium.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
