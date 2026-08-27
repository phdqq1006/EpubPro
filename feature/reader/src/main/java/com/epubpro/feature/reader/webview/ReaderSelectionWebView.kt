package com.epubpro.feature.reader.webview

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.WebView
import com.epubpro.core.designsystem.R
import org.json.JSONTokener

private const val FILTER_SELECTION_MENU_ITEM_ID = 0x45504601

/**
 * WebView của màn đọc có thêm action của EpubPro vào thanh công cụ chọn văn bản hệ thống.
 *
 * @param context Context dùng để khởi tạo WebView.
 * @param attrs Thuộc tính XML tùy chọn khi View được inflate.
 */
internal class ReaderSelectionWebView(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    /** Callback nhận đoạn văn bản người dùng chọn để mở hộp thoại thay thế từ ngữ. */
    var onOpenReplaceDialog: (String) -> Unit = {}

    /** Callback tương thích cho việc thêm selection vào bộ lọc. */
    var onAddSelectionToFilter: (String) -> Unit
        get() = onOpenReplaceDialog
        set(value) {
            onOpenReplaceDialog = value
        }

    /**
     * Bọc callback ActionMode mặc định để giữ nguyên các action hệ thống và bổ sung action thay thế.
     *
     * @param callback Callback ActionMode do WebView cung cấp.
     * @return ActionMode đã được khởi tạo hoặc `null` nếu WebView từ chối tạo.
     */
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        super.startActionMode(SelectionActionModeCallback(callback))

    /**
     * Bọc callback ActionMode có type để hỗ trợ floating toolbar trên Android hiện đại.
     *
     * @param callback Callback ActionMode do WebView cung cấp.
     * @param type Loại ActionMode cần khởi tạo.
     * @return ActionMode đã được khởi tạo hoặc `null` nếu WebView từ chối tạo.
     */
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionMode(SelectionActionModeCallback(callback), type)

    /**
     * Đọc selection hiện tại trong DOM rồi chuyển văn bản hợp lệ về callback của màn đọc.
     *
     * @param actionMode ActionMode cần đóng sau khi đã đọc selection.
     */
    private fun addCurrentSelectionToFilter(actionMode: ActionMode) {
        evaluateJavascript(SELECTED_TEXT_JAVASCRIPT) { encodedText ->
            decodeSelectedText(encodedText)
                .takeIf(String::isNotBlank)
                ?.let(onOpenReplaceDialog)
            actionMode.finish()
        }
    }

    /**
     * Giải mã chuỗi JSON do [evaluateJavascript] trả về thành văn bản thuần.
     *
     * @param encodedText Giá trị JSON được WebView trả về.
     * @return Văn bản selection đã loại bỏ khoảng trắng ở hai đầu, hoặc chuỗi rỗng khi không hợp lệ.
     */
    private fun decodeSelectedText(encodedText: String?): String =
        runCatching { JSONTokener(encodedText.orEmpty()).nextValue() as? String }
            .getOrNull()
            .orEmpty()
            .trim()

    /** Callback ủy quyền cho WebView và chèn thêm action thay thế từ ngữ của EpubPro. */
    private inner class SelectionActionModeCallback(
        private val delegate: ActionMode.Callback
    ) : ActionMode.Callback2() {

        /**
         * Cho delegate khởi tạo ActionMode rồi thêm action thay thế vào menu vừa tạo.
         *
         * @param mode ActionMode hiện tại.
         * @param menu Menu chọn văn bản cần bổ sung action.
         * @return Kết quả khởi tạo ActionMode từ delegate.
         */
        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            val created = delegate.onCreateActionMode(mode, menu)
            if (created) addFilterMenuItem(menu)
            return created
        }

        /**
         * Đồng bộ thay đổi menu của delegate và khôi phục action thay thế nếu WebView dựng lại menu.
         *
         * @param mode ActionMode hiện tại.
         * @param menu Menu chọn văn bản đang được chuẩn bị.
         * @return `true` khi delegate hoặc EpubPro đã thay đổi menu.
         */
        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            val prepared = delegate.onPrepareActionMode(mode, menu)
            return addFilterMenuItem(menu) || prepared
        }

        /**
         * Xử lý action thay thế của EpubPro và chuyển các action hệ thống về delegate gốc.
         *
         * @param mode ActionMode hiện tại.
         * @param item MenuItem vừa được người dùng chọn.
         * @return `true` khi action đã được xử lý.
         */
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            if (item.itemId == FILTER_SELECTION_MENU_ITEM_ID) {
                addCurrentSelectionToFilter(mode)
                return true
            }
            return delegate.onActionItemClicked(mode, item)
        }

        /**
         * Chuyển sự kiện kết thúc ActionMode về delegate gốc để WebView dọn selection đúng cách.
         *
         * @param mode ActionMode vừa kết thúc.
         */
        override fun onDestroyActionMode(mode: ActionMode) {
            delegate.onDestroyActionMode(mode)
        }

        /**
         * Giữ nguyên vùng neo floating toolbar do Callback2 gốc của WebView cung cấp.
         *
         * @param mode ActionMode hiện tại.
         * @param view View đang sở hữu selection.
         * @param outRect Rect đích nhận vùng nội dung selection.
         */
        override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
            val callback2 = delegate as? ActionMode.Callback2
            if (callback2 != null) {
                callback2.onGetContentRect(mode, view, outRect)
            } else {
                super.onGetContentRect(mode, view, outRect)
            }
        }

        /**
         * Thêm action thay thế nếu menu chưa chứa item tương ứng.
         *
         * @param menu Menu ActionMode hiện tại.
         * @return `true` khi menu vừa được thay đổi.
         */
        private fun addFilterMenuItem(menu: Menu): Boolean {
            if (menu.findItem(FILTER_SELECTION_MENU_ITEM_ID) != null) return false

            menu.add(
                Menu.NONE,
                FILTER_SELECTION_MENU_ITEM_ID,
                Menu.NONE,
                R.string.reader_action_replace
            ).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            return true
        }
    }

    private companion object {
        const val SELECTED_TEXT_JAVASCRIPT =
            "(function(){var selection=window.getSelection();return selection ? selection.toString() : '';})()"
    }
}
