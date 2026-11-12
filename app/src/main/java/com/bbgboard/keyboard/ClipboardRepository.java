package com.bbgboard.keyboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class ClipboardRepository {

    public static class ClipEntry {
        public final String text;
        public boolean pinned;
        public long time;

        public ClipEntry(String text, boolean pinned) {
            this(text, pinned, System.currentTimeMillis());
        }

        public ClipEntry(String text, boolean pinned, long time) {
            this.text = text;
            this.pinned = pinned;
            this.time = time;
        }
    }

    private static final String KEY_PLAIN = "clips_v1";
    private static final String KEY_ENC = "clips_v2_enc";
    private static final String KS_ALIAS = "bkey_clip_key";
    private static final long HOUR_MS = 60L * 60L * 1000L;

    private final SharedPreferences prefs;
    private final KeyboardPreferences keyboardPrefs;
    private final List<ClipEntry> items = new ArrayList<>();

    private static volatile SecretKey cachedKey;

    public ClipboardRepository(Context context) {
        this.prefs = context.getSharedPreferences("bb_keyboard_clipboard_prefs", Context.MODE_PRIVATE);
        this.keyboardPrefs = new KeyboardPreferences(context);

        boolean firstRun = !prefs.contains(KEY_PLAIN) && !prefs.contains(KEY_ENC);
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

        if (prefs.contains(KEY_PLAIN)) {
            save();
        }
    }

    public synchronized List<ClipEntry> getClips() {
        load();
        if (pruneOld()) save();
        List<ClipEntry> sorted = new ArrayList<>(items);
        Collections.sort(sorted, (a, b) -> Boolean.compare(b.pinned, a.pinned));
        return sorted;
    }

    private boolean pruneOld() {
        int hours = keyboardPrefs.getClipRetentionHours();
        if (hours <= 0) return false;
        long limit = System.currentTimeMillis() - hours * HOUR_MS;
        boolean changed = false;
        for (int i = items.size() - 1; i >= 0; i--) {
            ClipEntry e = items.get(i);
            if (!e.pinned && e.time < limit) {
                items.remove(i);
                changed = true;
            }
        }
        return changed;
    }

    public synchronized void addClip(String rawText, boolean pinned) {
        if (rawText == null) return;
        String clean = rawText.trim();
        if (clean.isEmpty()) return;
        load();

        int existingIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).text.equals(clean)) {
                existingIndex = i;
                break;
            }
        }

        if (existingIndex != -1) {
            ClipEntry existing = items.remove(existingIndex);
            existing.time = System.currentTimeMillis();
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
        pruneOld();
        save();
    }

    public synchronized void togglePin(String text) {
        load();
        for (ClipEntry item : items) {
            if (item.text.equals(text)) {
                item.pinned = !item.pinned;
                save();
                return;
            }
        }
    }

    public synchronized void removeClip(String text) {
        load();
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
                o.put("time", item.time);
                arr.put(o);
            }
            String json = arr.toString();
            String enc = encrypt(json);
            SharedPreferences.Editor ed = prefs.edit();
            if (enc != null) {
                ed.putString(KEY_ENC, enc);
                ed.remove(KEY_PLAIN);
            } else {

                ed.putString(KEY_PLAIN, json);
            }
            ed.apply();
        } catch (Exception ignored) {}
    }

    private void load() {
        try {
            String json = null;
            String enc = prefs.getString(KEY_ENC, null);
            if (enc != null && !enc.isEmpty()) {
                json = decrypt(enc);
            }
            if (json == null) {
                json = prefs.getString(KEY_PLAIN, null);
            }
            if (json == null || json.isEmpty()) return;
            JSONArray arr = new JSONArray(json);
            long migrated = System.currentTimeMillis();
            items.clear();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String t = o.optString("text", "");
                if (!t.isEmpty()) {

                    items.add(new ClipEntry(t, o.optBoolean("pinned", false), o.optLong("time", migrated)));
                }
            }
        } catch (Exception ignored) {}
    }

    private static SecretKey getKey() throws Exception {
        SecretKey k = cachedKey;
        if (k != null) return k;
        synchronized (ClipboardRepository.class) {
            if (cachedKey != null) return cachedKey;
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(KS_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                kg.init(new KeyGenParameterSpec.Builder(KS_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                cachedKey = kg.generateKey();
            } else {
                cachedKey = (SecretKey) ks.getKey(KS_ALIAS, null);
            }
            return cachedKey;
        }
    }

    private static String encrypt(String plain) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, getKey());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(plain.getBytes("UTF-8"));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.encodeToString(out, Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decrypt(String b64) {
        try {
            byte[] all = Base64.decode(b64, Base64.NO_WRAP);
            if (all.length < 13) return null;
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, getKey(), new GCMParameterSpec(128, all, 0, 12));
            return new String(c.doFinal(all, 12, all.length - 12), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
