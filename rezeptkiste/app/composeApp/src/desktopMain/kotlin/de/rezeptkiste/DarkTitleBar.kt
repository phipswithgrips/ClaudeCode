package de.rezeptkiste

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef

private interface DwmApi : Library {
    fun DwmSetWindowAttribute(hwnd: WinDef.HWND, attribute: Int, value: Pointer, size: Int): Int
}

/**
 * Dunkle Titelleiste unter Windows 10/11 (wie Recipe Keeper).
 * 20 = DWMWA_USE_IMMERSIVE_DARK_MODE, 35 = DWMWA_CAPTION_COLOR, 36 = DWMWA_TEXT_COLOR (Windows 11).
 */
fun applyDarkTitleBar(window: java.awt.Window) {
    if (!isWindows) return
    runCatching {
        val hwnd = WinDef.HWND(Native.getWindowPointer(window))
        val dwm = Native.load("dwmapi", DwmApi::class.java)
        val mem = Memory(4)
        fun set(attr: Int, v: Int) { mem.setInt(0, v); dwm.DwmSetWindowAttribute(hwnd, attr, mem, 4) }
        set(20, 1)
        set(35, 0x00202020) // COLORREF 0x00BBGGRR
        set(36, 0x00FFFFFF)
    }
}
