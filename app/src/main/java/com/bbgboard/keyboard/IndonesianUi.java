package com.bbgboard.keyboard;

import java.util.HashMap;
import java.util.Map;

/**
 * Indonesian text for the keyboard's own drawers (clipboard, emoji, GIF), keyed by the English
 * string the drawer code already carries. Anything missing falls back to the English text.
 */
final class IndonesianUi {
    private IndonesianUi() {}

    private static final Map<String, String> TEXT = new HashMap<>();
    static {
        TEXT.put("Screenshot copied — paste it here", "Tangkapan layar disalin — tempel di sini");
        TEXT.put("Delete", "Hapus");
        TEXT.put("Screenshot", "Tangkapan layar");
        TEXT.put("Incognito mode is on\nClipboard is hidden and nothing new is being saved", "Mode penyamaran aktif\nPapan klip disembunyikan dan tidak ada yang baru disimpan");
        TEXT.put("Nothing copied yet", "Belum ada yang disalin");
        TEXT.put("Unpin", "Lepas sematan");
        TEXT.put("Pin", "Sematkan");
        TEXT.put("Expand", "Perluas");
        TEXT.put("Collapse", "Ciutkan");
        TEXT.put("Powered by KLIPY", "Didukung oleh KLIPY");
        TEXT.put("Search KLIPY...", "Cari di KLIPY...");
        TEXT.put("Undo", "Urungkan");
        TEXT.put("No recent emojis yet", "Belum ada emoji terbaru");
        TEXT.put("  •  Long-press any emoji to delete it", "  •  Tekan lama emoji untuk menghapusnya");
        TEXT.put("  •  Long-press any GIF to delete it", "  •  Tekan lama GIF untuk menghapusnya");
        TEXT.put("Loading...", "Memuat...");
        TEXT.put("Couldn't load: ", "Gagal memuat: ");
        TEXT.put("No results", "Tidak ada hasil");
        TEXT.put("Recently used", "Baru saja digunakan");
        TEXT.put("Search results", "Hasil penelusuran");
        TEXT.put("Trending now", "Sedang tren");
        TEXT.put("This app doesn't allow sending GIFs here", "Aplikasi ini tidak mengizinkan pengiriman GIF di sini");
        TEXT.put("This app doesn't allow sending screenshots here", "Aplikasi ini tidak mengizinkan pengiriman tangkapan layar di sini");
        TEXT.put("Couldn't load GIF", "Gagal memuat GIF");
        TEXT.put("Microphone permission is needed", "Izin mikrofon diperlukan");
        TEXT.put("No internet connection for voice typing", "Tidak ada koneksi internet untuk mengetik dengan suara");
        TEXT.put("Voice typing failed", "Pengetikan suara gagal");
        TEXT.put("Voice typing isn't available on this device", "Pengetikan suara tidak tersedia di perangkat ini");
        TEXT.put("GIF copied — paste it here", "GIF disalin — tempel di sini");
    }

    /** Indonesian for the given English drawer text, or the English text itself if not translated yet. */
    static String get(String english) {
        String id = TEXT.get(english);
        return id != null ? id : english;
    }

    static final String[] EMOJI_CATEGORIES = {
        "Baru saja digunakan",
        "Wajah dan emosi",
        "Orang dan tubuh",
        "Alam, acara, dan Islam",
        "Hewan",
        "Makanan dan minuman",
        "Perjalanan dan tempat",
        "Simbol dan bendera"
    };
}
