package com.bbgboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClipboardRepository {

    public static class ClipEntry {
        public final String text;
        public boolean pinned;

        public ClipEntry(String text, boolean pinned) {
            this.text = text;
            this.pinned = pinned;
        }
    }

    private final SharedPreferences prefs;
    private final List<ClipEntry> items = new ArrayList<>();

    public ClipboardRepository(Context context) {
        this.prefs = context.getSharedPreferences("bb_keyboard_clipboard_prefs", Context.MODE_PRIVATE);
        // الجمل الافتراضية تُضاف مرة واحدة فقط لمن لا توجد عنده حافظة محفوظة، ولا تعود بعد حذفها
        boolean firstRun = !prefs.contains("clips_v1");
        load();
        if (!prefs.getBoolean("seeded_v14", false)) {
            if (firstRun && items.isEmpty()) {
                items.add(new ClipEntry("السلام عليكم ورحمة الله وبركاته", true));
                items.add(new ClipEntry("جزاك الله خيراً ويعطيك العافية", true));
                items.add(new ClipEntry("إن شاء الله أكون معك بعد قليل", false));
                save();
            }
            prefs.edit().putBoolean("seeded_v14", true).apply();
        }
    }

    public List<ClipEntry> getClips() {
        load();
        List<ClipEntry> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Boolean.compare(b.pinned, a.pinned));
        return sorted;
    }

    public void addClip(String rawText, boolean pinned) {
        if (rawText == null) return;
        String clean = rawText.trim();
        if (clean.isEmpty()) return;

        int existingIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).text.equals(clean)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            ClipEntry existing = items.remove(existingIndex);
            items.add(0, existing);
        } else {
            items.add(0, new ClipEntry(clean, pinned));
            if (items.size() > 35) {
                for (int i = items.size() - 1; i >= 0; i--) {
                    if (!items.get(i).pinned) {
                        items.remove(i);
                        break;
                    }
                }
            }
        }
        save();
    }

    public void togglePin(String text) {
        for (ClipEntry item : items) {
            if (item.text.equals(text)) {
                item.pinned = !item.pinned;
                save();
                return;
            }
        }
    }

    public void removeClip(String text) {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).text.equals(text)) {
                items.remove(i);
            }
        }
        save();
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (ClipEntry item : items) {
                JSONObject o = new JSONObject();
                o.put("text", item.text);
                o.put("pinned", item.pinned);
                arr.put(o);
            }
            prefs.edit().putString("clips_v1", arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void load() {
        try {
            String str = prefs.getString("clips_v1", null);
            if (str == null || str.isEmpty()) return;
            JSONArray arr = new JSONArray(str);
            items.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String t = o.optString("text", "");
                if (!t.isEmpty()) {
                    items.add(new ClipEntry(t, o.optBoolean("pinned", false)));
                }
            }
        } catch (Exception ignored) {}
    }
}
