package com.bbgboard.keyboard;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SoundController {

    private static final int SAMPLE_RATE = 44100;
    private final Context context;
    private final KeyboardPreferences prefs;
    private final AudioManager audioManager;
    private SoundPool soundPool;

    private final int[] tapSoundIds = new int[11];
    private final int[] swipeSoundIds = new int[11];
    private int backspaceSoundId = 0;

    public SoundController(Context context, KeyboardPreferences prefs) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
        ensureLoaded();
    }

    public synchronized void ensureLoaded() {
        if (soundPool != null) return;
        boolean needed = (prefs.isSoundEnabled() && prefs.getSoundProfile() != KeyboardPreferences.SOUND_PROFILE_SYSTEM)
                || (prefs.isSwipeSoundEnabled() && prefs.getSwipeSoundProfile() != KeyboardPreferences.SWIPE_SOUND_SILENT);
        if (!needed) return;
        initSoundPool();
    }

    private void initSoundPool() {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(12)
                    .setAudioAttributes(attrs)
                    .build();
        } catch (Exception e) {
            AppLogger.log("SoundController", "initSoundPool()", e);
        }

        new Thread(this::loadOrBuildSounds, "SoundController-load").start();
    }

    public void release() {
        releaseSounds();
    }

    public synchronized void releaseSounds() {
        try {
            if (soundPool != null) soundPool.release();
        } catch (Exception e) {
            AppLogger.log("SoundController", "releaseSounds()", e);
        }
        soundPool = null;
        java.util.Arrays.fill(tapSoundIds, 0);
        java.util.Arrays.fill(swipeSoundIds, 0);
        backspaceSoundId = 0;
    }

    private void loadOrBuildSounds() {
        try {
            File cacheDir = new File(context.getCacheDir(), "bb_sounds_v10");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }

            final int[] ids = {0, 1, 2, 3, 4, 6, 7, 8, 9, 10};
            final String[] tapNames = {"tap_bb_classic.wav", "tap_bb_q10.wav", "tap_gboard_pop.wav", "tap_mech_switch.wav",
                    "tap_ios_tick.wav", "tap_typewriter.wav", "tap_wooden.wav", "tap_waterdrop.wav", "tap_glass.wav", "tap_bb_original.wav"};
            final String[] swNames = {"sw_air_flick.wav", "sw_velvet.wav", "sw_double_pulse.wav", "sw_harmonic.wav",
                    "sw_whoosh.wav", "sw_glass_ping.wav", "sw_paper.wav", "sw_bubble.wav", "sw_triple_tick.wav", "sw_bb_original.wav"};
            final String bsName = "tap_backspace_soft.wav";

            boolean allCached = new File(cacheDir, bsName).exists();
            for (String n : tapNames) allCached &= new File(cacheDir, n).exists();
            for (String n : swNames) allCached &= new File(cacheDir, n).exists();
            if (allCached) {
                for (int i = 0; i < ids.length; i++) {
                    tapSoundIds[ids[i]] = soundPool.load(new File(cacheDir, tapNames[i]).getAbsolutePath(), 1);
                    swipeSoundIds[ids[i]] = soundPool.load(new File(cacheDir, swNames[i]).getAbsolutePath(), 1);
                }
                backspaceSoundId = soundPool.load(new File(cacheDir, bsName).getAbsolutePath(), 1);
                return;
            }

            tapSoundIds[0] = buildBlackBerryClassicTapWav(cacheDir, tapNames[0]);
            tapSoundIds[1] = buildBlackBerryQ10DeepWav(cacheDir, tapNames[1]);
            tapSoundIds[2] = buildGboardRisingPopWav(cacheDir, tapNames[2]);
            tapSoundIds[3] = buildMechanicalDoubleClickWav(cacheDir, tapNames[3]);
            tapSoundIds[4] = buildIosCrispTickWav(cacheDir, tapNames[4]);
            tapSoundIds[6] = buildTypewriterTapWav(cacheDir, tapNames[5]);
            tapSoundIds[7] = buildWoodenTapWav(cacheDir, tapNames[6]);
            tapSoundIds[8] = buildWaterDropTapWav(cacheDir, tapNames[7]);
            tapSoundIds[9] = buildSoftGlassTapWav(cacheDir, tapNames[8]);
            tapSoundIds[10] = buildBlackBerryOriginalTapWav(cacheDir, tapNames[9]);

            backspaceSoundId = buildSoftBackspaceWav(cacheDir, bsName);

            swipeSoundIds[0] = buildSwipeAirFlickWav(cacheDir, swNames[0]);
            swipeSoundIds[1] = buildSwipeVelvetShimmerWav(cacheDir, swNames[1]);
            swipeSoundIds[2] = buildSwipeDoublePulseWav(cacheDir, swNames[2]);
            swipeSoundIds[3] = buildSwipeHarmonicGlideWav(cacheDir, swNames[3]);
            swipeSoundIds[4] = buildSwipeWarmWhooshWav(cacheDir, swNames[4]);
            swipeSoundIds[6] = buildSwipeGlassPingWav(cacheDir, swNames[5]);
            swipeSoundIds[7] = buildSwipePaperSwishWav(cacheDir, swNames[6]);
            swipeSoundIds[8] = buildSwipeBubbleRiseWav(cacheDir, swNames[7]);
            swipeSoundIds[9] = buildSwipeTripleTickWav(cacheDir, swNames[8]);
            swipeSoundIds[10] = buildSwipeBlackBerryOriginalWav(cacheDir, swNames[9]);

        } catch (Exception e) {
            AppLogger.log("SoundController", "loadOrBuildSounds()", e);
        }
    }

    private static class Bandpass {
        private double low = 0.0, band = 0.0;
        private final double q;
        Bandpass(double q) { this.q = q; }
        double process(double in, double fc) {
            double f = 2.0 * Math.sin(Math.PI * Math.min(fc, SAMPLE_RATE * 0.45) / SAMPLE_RATE);
            low += f * band;
            double high = in - low - q * band;
            band += f * high;
            return band;
        }
    }

    private static class Lowpass {
        private double low = 0.0, band = 0.0;
        private final double q;
        Lowpass(double q) { this.q = q; }
        double process(double in, double fc) {
            double f = 2.0 * Math.sin(Math.PI * Math.min(fc, SAMPLE_RATE * 0.45) / SAMPLE_RATE);
            low += f * band;
            double high = in - low - q * band;
            band += f * high;
            return low;
        }
    }

    private static void addResonance(double[] buf, int startSample, double freq, double freqEnd,
                                     double decayMs, double amp, double attackMs) {
        int n = buf.length - startSample;
        double ph = 0.0;
        double tau = decayMs / 1000.0 * SAMPLE_RATE;
        double atk = Math.max(1.0, attackMs / 1000.0 * SAMPLE_RATE);
        for (int i = 0; i < n; i++) {
            double p = (double) i / Math.max(1, n);
            double fr = freq + (freqEnd - freq) * Math.min(1.0, p * 3.0);
            ph += 2.0 * Math.PI * fr / SAMPLE_RATE;
            double env = Math.exp(-i / tau) * Math.min(1.0, i / atk);
            buf[startSample + i] += Math.sin(ph) * env * amp;
        }
    }

    private static void addNoiseBurst(double[] buf, int startSample, double centerHz, double q,
                                      double decayMs, double amp, long seed) {
        java.util.Random rnd = new java.util.Random(seed);
        Bandpass bp = new Bandpass(q);
        double tau = decayMs / 1000.0 * SAMPLE_RATE;
        int n = buf.length - startSample;
        for (int i = 0; i < n; i++) {
            double noise = rnd.nextDouble() * 2.0 - 1.0;
            double env = Math.exp(-i / tau);
            buf[startSample + i] += bp.process(noise, centerHz) * env * amp;
        }
    }

    private int finishSound(File dir, String name, double[] buf, double peak) {
        try {
            double max = 1e-9;
            for (double v : buf) max = Math.max(max, Math.abs(v));
            int fade = Math.min(buf.length / 8, (SAMPLE_RATE * 3) / 1000);
            short[] pcm = new short[buf.length];
            for (int i = 0; i < buf.length; i++) {
                double v = buf[i] / max * peak;
                if (i >= buf.length - fade) v *= (double) (buf.length - i) / fade;
                pcm[i] = (short) (Math.max(-1.0, Math.min(1.0, v)) * 32000);
            }
            File f = new File(dir, name);
            writeWavFile(f, pcm, SAMPLE_RATE);
            return soundPool.load(f.getAbsolutePath(), 1);
        } catch (Exception e) { return 0; }
    }

    private static int ms(int millis) { return (SAMPLE_RATE * millis) / 1000; }

    private int buildBlackBerryClassicTapWav(File dir, String name) {
        double[] b = new double[ms(38)];
        addNoiseBurst(b, 0, 3200.0, 1.6, 2.2, 0.9, 11);
        addResonance(b, 0, 1250.0, 900.0, 5.0, 0.55, 0.15);
        addResonance(b, ms(1), 240.0, 180.0, 13.0, 0.85, 0.5);
        return finishSound(dir, name, b, 0.80);
    }

    private int buildBlackBerryOriginalTapWav(File dir, String name) {
        double[] b = new double[ms(26)];
        addResonance(b, 0, 115.0, 95.0, 6.5, 1.0, 0.3);
        addResonance(b, 0, 205.0, 170.0, 4.5, 0.75, 0.3);
        addResonance(b, ms(5), 170.0, 130.0, 5.0, 0.45, 0.3);
        addNoiseBurst(b, 0, 520.0, 1.1, 2.2, 0.32, 101);
        addNoiseBurst(b, 0, 1500.0, 1.2, 1.2, 0.10, 103);
        return finishSound(dir, name, b, 0.78);
    }

    private int buildBlackBerryQ10DeepWav(File dir, String name) {
        double[] b = new double[ms(52)];
        addNoiseBurst(b, 0, 700.0, 1.2, 4.0, 0.45, 23);
        addResonance(b, 0, 165.0, 120.0, 22.0, 1.0, 0.8);
        addResonance(b, 0, 330.0, 240.0, 12.0, 0.35, 0.8);
        return finishSound(dir, name, b, 0.85);
    }

    private int buildGboardRisingPopWav(File dir, String name) {
        double[] b = new double[ms(46)];
        addResonance(b, 0, 1150.0, 480.0, 11.0, 1.0, 0.35);
        addResonance(b, 0, 2300.0, 960.0, 5.0, 0.18, 0.3);
        addNoiseBurst(b, 0, 1800.0, 1.0, 1.5, 0.12, 31);
        return finishSound(dir, name, b, 0.72);
    }

    private int buildMechanicalDoubleClickWav(File dir, String name) {
        double[] b = new double[ms(55)];
        addNoiseBurst(b, 0, 4200.0, 2.2, 1.6, 0.9, 41);
        addResonance(b, 0, 2600.0, 2200.0, 3.0, 0.35, 0.1);
        int s2 = ms(9);
        addNoiseBurst(b, s2, 1700.0, 1.4, 3.0, 0.95, 42);
        addResonance(b, s2, 420.0, 300.0, 14.0, 0.9, 0.4);
        return finishSound(dir, name, b, 0.85);
    }

    private int buildIosCrispTickWav(File dir, String name) {
        double[] b = new double[ms(22)];
        addResonance(b, 0, 2800.0, 2300.0, 2.6, 1.0, 0.08);
        addResonance(b, 0, 4300.0, 3600.0, 1.6, 0.4, 0.08);
        addNoiseBurst(b, 0, 5200.0, 1.5, 1.0, 0.25, 51);
        return finishSound(dir, name, b, 0.70);
    }

    private int buildSoftBackspaceWav(File dir, String name) {
        double[] b = new double[ms(40)];
        addNoiseBurst(b, 0, 900.0, 1.2, 3.0, 0.4, 61);
        addResonance(b, 0, 205.0, 135.0, 14.0, 1.0, 0.6);
        return finishSound(dir, name, b, 0.70);
    }

    private int buildSwipeBlackBerryOriginalWav(File dir, String name) {
        int n = ms(122);
        double[] b = new double[n];
        java.util.Random rnd = new java.util.Random(131);
        Bandpass bp = new Bandpass(0.8);
        for (int i = 0; i < n; i++) {
            double t = i * 1000.0 / SAMPLE_RATE;
            double fc = 1500.0 + 2500.0 * Math.exp(-t / 28.0);
            double attack = t < 24.0 ? Math.pow(t / 24.0, 1.3) : 1.0;
            double decay = t < 24.0 ? 1.0 : Math.exp(-(t - 24.0) / 20.0);
            double tail = 0.10 * Math.exp(-Math.pow((t - 104.0) / 7.0, 2.0));
            b[i] = bp.process(rnd.nextDouble() * 2.0 - 1.0, fc) * (attack * decay + tail);
        }
        addResonance(b, 0, 190.0, 150.0, 30.0, 0.25, 4.0);
        return finishSound(dir, name, b, 0.80);
    }

    private int buildSwipeAirFlickWav(File dir, String name) {
        int n = ms(150);
        double[] b = new double[n];
        java.util.Random rnd = new java.util.Random(71);
        Bandpass bp = new Bandpass(0.9);
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double fc = 700.0 + 3600.0 * Math.pow(p, 1.4);
            double env = Math.pow(Math.sin(Math.PI * Math.pow(p, 0.65)), 1.5);
            b[i] = bp.process(rnd.nextDouble() * 2.0 - 1.0, fc) * env;
        }
        addResonance(b, 0, 900.0, 1900.0, 70.0, 0.20, 6.0);
        return finishSound(dir, name, b, 0.85);
    }

    private int buildSwipeVelvetShimmerWav(File dir, String name) {
        int n = ms(170);
        double[] b = new double[n];
        java.util.Random rnd = new java.util.Random(72);
        Lowpass lp = new Lowpass(1.1);
        double ph = 0.0;
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double fc = 450.0 + 1500.0 * p;
            double env = Math.sin(Math.PI * p);
            env *= env;
            double noise = lp.process(rnd.nextDouble() * 2.0 - 1.0, fc) * env;
            ph += 2.0 * Math.PI * (480.0 + 380.0 * p) / SAMPLE_RATE;
            b[i] = noise * 0.9 + Math.sin(ph) * env * 0.35;
        }
        return finishSound(dir, name, b, 0.82);
    }

    private int buildSwipeDoublePulseWav(File dir, String name) {
        double[] b = new double[ms(130)];
        addNoiseBurst(b, 0, 1400.0, 1.4, 2.5, 0.8, 81);
        addResonance(b, 0, 520.0, 400.0, 14.0, 1.0, 0.4);
        int s2 = ms(62);
        addNoiseBurst(b, s2, 2400.0, 1.6, 2.0, 0.7, 82);
        addResonance(b, s2, 820.0, 660.0, 16.0, 1.0, 0.4);
        return finishSound(dir, name, b, 0.85);
    }

    private int buildSwipeHarmonicGlideWav(File dir, String name) {
        int n = ms(190);
        double[] b = new double[n];
        double ph1 = 0.0, ph2 = 0.0;
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double f1 = 660.0 + 900.0 * Math.min(1.0, p * 1.8);
            ph1 += 2.0 * Math.PI * f1 / SAMPLE_RATE;
            ph2 += 2.0 * Math.PI * f1 * 2.01 / SAMPLE_RATE;
            double env = Math.min(1.0, i / 90.0) * Math.exp(-p * 4.2);
            b[i] = (Math.sin(ph1) * 0.8 + Math.sin(ph2) * 0.25) * env;
        }
        addNoiseBurst(b, 0, 3000.0, 1.0, 10.0, 0.10, 91);
        return finishSound(dir, name, b, 0.78);
    }

    private int buildSwipeWarmWhooshWav(File dir, String name) {
        int n = ms(210);
        double[] b = new double[n];
        java.util.Random rnd = new java.util.Random(92);
        Bandpass bp = new Bandpass(1.3);
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double fc = 420.0 + 1500.0 * Math.sin(Math.PI * p);
            double env = Math.pow(Math.sin(Math.PI * p), 1.3);
            b[i] = bp.process(rnd.nextDouble() * 2.0 - 1.0, fc) * env;
        }
        addResonance(b, 0, 300.0, 420.0, 120.0, 0.15, 25.0);
        return finishSound(dir, name, b, 0.85);
    }

    private int buildTypewriterTapWav(File dir, String name) {
        double[] b = new double[ms(60)];
        addNoiseBurst(b, 0, 2600.0, 1.0, 3.0, 0.9, 101);
        addResonance(b, 0, 1750.0, 1500.0, 9.0, 0.8, 0.1);
        addResonance(b, 0, 3400.0, 3000.0, 6.0, 0.35, 0.1);
        addResonance(b, ms(2), 150.0, 110.0, 16.0, 0.9, 0.4);
        return finishSound(dir, name, b, 0.82);
    }

    private int buildWoodenTapWav(File dir, String name) {
        double[] b = new double[ms(50)];
        addNoiseBurst(b, 0, 1300.0, 1.4, 2.5, 0.4, 111);
        addResonance(b, 0, 540.0, 420.0, 14.0, 1.0, 0.3);
        addResonance(b, 0, 1130.0, 900.0, 8.0, 0.30, 0.3);
        return finishSound(dir, name, b, 0.80);
    }

    private int buildWaterDropTapWav(File dir, String name) {
        int n = ms(70);
        double[] b = new double[n];
        double ph = 0.0;
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double freq = 520.0 + 1000.0 * (1.0 - Math.exp(-p * 9.0));
            ph += 2.0 * Math.PI * freq / SAMPLE_RATE;
            double env = Math.min(1.0, i / 30.0) * Math.exp(-p * 7.0);
            b[i] = Math.sin(ph) * env;
        }
        return finishSound(dir, name, b, 0.72);
    }

    private int buildSoftGlassTapWav(File dir, String name) {
        double[] b = new double[ms(70)];
        addResonance(b, 0, 1760.0, 1760.0, 20.0, 1.0, 0.8);
        addResonance(b, 0, 2637.0, 2637.0, 12.0, 0.45, 0.8);
        addResonance(b, 0, 3520.0, 3520.0, 6.0, 0.15, 0.8);
        return finishSound(dir, name, b, 0.62);
    }

    private int buildSwipeGlassPingWav(File dir, String name) {
        double[] b = new double[ms(260)];
        addResonance(b, 0, 1319.0, 1319.0, 70.0, 0.85, 1.0);
        addResonance(b, 0, 2638.0, 2638.0, 35.0, 0.25, 1.0);
        addResonance(b, ms(55), 1760.0, 1760.0, 90.0, 1.0, 1.0);
        addResonance(b, ms(55), 3520.0, 3520.0, 40.0, 0.25, 1.0);
        return finishSound(dir, name, b, 0.72);
    }

    private int buildSwipePaperSwishWav(File dir, String name) {
        int n = ms(110);
        double[] b = new double[n];
        java.util.Random rnd = new java.util.Random(121);
        Bandpass bp = new Bandpass(0.7);
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double fc = 1800.0 + 4200.0 * p;
            double env = Math.pow(Math.sin(Math.PI * Math.pow(p, 0.8)), 2.0);
            b[i] = bp.process(rnd.nextDouble() * 2.0 - 1.0, fc) * env;
        }
        return finishSound(dir, name, b, 0.80);
    }

    private int buildSwipeBubbleRiseWav(File dir, String name) {
        int n = ms(150);
        double[] b = new double[n];
        double ph = 0.0;
        for (int i = 0; i < n; i++) {
            double p = (double) i / n;
            double freq = 380.0 + 820.0 * Math.pow(p, 0.8) + 25.0 * Math.sin(2.0 * Math.PI * 22.0 * p);
            ph += 2.0 * Math.PI * freq / SAMPLE_RATE;
            double env = Math.min(1.0, i / 120.0) * Math.exp(-Math.pow(p, 1.5) * 4.5);
            b[i] = Math.sin(ph) * env;
        }
        return finishSound(dir, name, b, 0.78);
    }

    private int buildSwipeTripleTickWav(File dir, String name) {
        double[] b = new double[ms(120)];
        addResonance(b, 0, 1800.0, 1500.0, 5.0, 0.9, 0.1);
        addNoiseBurst(b, 0, 3500.0, 1.4, 1.5, 0.4, 131);
        addResonance(b, ms(32), 2200.0, 1900.0, 5.0, 0.95, 0.1);
        addNoiseBurst(b, ms(32), 4000.0, 1.4, 1.5, 0.4, 132);
        addResonance(b, ms(64), 2800.0, 2400.0, 6.0, 1.0, 0.1);
        addNoiseBurst(b, ms(64), 4500.0, 1.4, 1.5, 0.4, 133);
        return finishSound(dir, name, b, 0.80);
    }

    private void writeWavFile(File file, short[] pcmData, int sampleRate) throws Exception {
        int byteRate = sampleRate * 2;
        int dataSize = pcmData.length * 2;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put("RIFF".getBytes("US-ASCII"));
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes("US-ASCII"));
        buffer.put("fmt ".getBytes("US-ASCII"));
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes("US-ASCII"));
        buffer.putInt(dataSize);

        for (short s : pcmData) {
            buffer.putShort(s);
        }

        FileOutputStream fos = new FileOutputStream(file);
        try {
            fos.write(buffer.array());
        } finally {
            fos.close();
        }
    }

    public void playKeyClick() {
        if (!prefs.isSoundEnabled()) return;
        float vol = prefs.getSoundVolume() / 100f;
        if (vol <= 0.01f) return;

        int profile = prefs.getSoundProfile();
        if (profile == KeyboardPreferences.SOUND_PROFILE_SYSTEM) {
            if (audioManager != null) {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, vol * 0.70f);
            }
            return;
        }

        int idx = Math.max(0, Math.min(tapSoundIds.length - 1, profile));
        int soundId = tapSoundIds[idx];
        if (soundPool != null && soundId != 0) {
            soundPool.play(soundId, vol * 0.85f, vol * 0.85f, 1, 0, 1.0f);
        }
    }

    public void playBackspaceClick() {
        if (!prefs.isSoundEnabled()) return;
        float vol = (prefs.getSoundVolume() / 100f) * 0.45f;
        if (vol <= 0.01f) return;

        int profile = prefs.getSoundProfile();
        if (profile == KeyboardPreferences.SOUND_PROFILE_SYSTEM) {
            if (audioManager != null) {
                audioManager.playSoundEffect(AudioManager.FX_KEY_CLICK, vol * 0.50f);
            }
            return;
        }

        if (soundPool != null && backspaceSoundId != 0) {
            soundPool.play(backspaceSoundId, vol, vol, 1, 0, 0.95f);
        }
    }

    private float swipeGain() {
        float x = prefs.getSwipeSoundVolume() / 100f;
        return (float) (0.6 * Math.pow(x, 3.0));
    }

    public void playSwipeUpSound() {
        if (!prefs.isSwipeSoundEnabled()) return;
        int swipeProfile = prefs.getSwipeSoundProfile();
        if (swipeProfile == KeyboardPreferences.SWIPE_SOUND_SILENT) return;

        float vol = swipeGain();
        if (vol <= 0.003f) return;

        int idx = Math.max(0, Math.min(swipeSoundIds.length - 1, swipeProfile));
        int soundId = swipeSoundIds[idx];
        if (soundPool != null && soundId != 0) {
            soundPool.play(soundId, vol, vol, 1, 0, 1.0f);
        }
    }

    public void playSwipeDeleteSound() {
        if (!prefs.isSwipeSoundEnabled()) return;
        int swipeProfile = prefs.getSwipeSoundProfile();
        if (swipeProfile == KeyboardPreferences.SWIPE_SOUND_SILENT) return;

        float vol = swipeGain() * 0.75f;
        if (vol <= 0.003f) return;

        int idx = Math.max(0, Math.min(swipeSoundIds.length - 1, swipeProfile));
        int soundId = swipeSoundIds[idx];
        if (soundPool != null && soundId != 0) {
            soundPool.play(soundId, vol, vol, 1, 0, 0.82f);
        }
    }

    public void playLanguageSwitchSound() {
        playSwipeUpSound();
    }

    private static String getProfileDisplayNameAr(int profile) {
        switch (profile) {
            case KeyboardPreferences.SOUND_PROFILE_BB_CLASSIC:
                return "بلاك بيري الأصلي (BlackBerry Classic Thock)";
            case KeyboardPreferences.SOUND_PROFILE_BB_Q10_SOFT:
                return "بلاك بيري العميق الدافئ (BlackBerry Q10 Deep)";
            case KeyboardPreferences.SOUND_PROFILE_GBOARD_POP:
                return "فقاعة جي بورد الصاعدة (Gboard Water Pop)";
            case KeyboardPreferences.SOUND_PROFILE_MECHANICAL:
                return "كيبورد ميكانيكي مزدوج (Mechanical Click-Clack)";
            case KeyboardPreferences.SOUND_PROFILE_IOS_TICK:
                return "تكة كريستالية حادة (iOS Crisp Tick)";
            case 6:
                return "آلة كاتبة (Typewriter)";
            case 7:
                return "طرقة خشبية دافئة (Wooden Tap)";
            case 8:
                return "قطرة ماء (Water Drop)";
            case 9:
                return "كريستال ناعم (Soft Glass)";
            case 10:
                return "بلاك بيري الحقيقي — مقاس من التسجيل (BlackBerry Original)";
            case KeyboardPreferences.SOUND_PROFILE_SYSTEM:
                return "صوت نظام أندرويد (System Default)";
            default:
                return "بلاك بيري الأصلي";
        }
    }

    private static String getSwipeProfileDisplayNameAr(int swipeProfile) {
        switch (swipeProfile) {
            case KeyboardPreferences.SWIPE_SOUND_BB_AIR_FLICK:
                return "انطلاقة بلاك بيري الهوائية (BlackBerry Air Flick)";
            case KeyboardPreferences.SWIPE_SOUND_VELVET_BRUSH:
                return "تموج مخملي ناعم (Velvet Shimmer)";
            case KeyboardPreferences.SWIPE_SOUND_MICRO_THOCK:
                return "نبضة مزدوجة سريعة (Double Pulse)";
            case KeyboardPreferences.SWIPE_SOUND_PAPER_SWIPE:
                return "انزلاق رنان حديث (Harmonic Glide)";
            case KeyboardPreferences.SWIPE_SOUND_SOFT_WHOOSH:
                return "موجة دافئة منحنية (Warm Whoosh)";
            case 6:
                return "رنين زجاجي (Glass Ping)";
            case 7:
                return "حفيف ورقة (Paper Swish)";
            case 8:
                return "فقاعة صاعدة (Bubble Rise)";
            case 9:
                return "ثلاث نقرات سريعة (Triple Tick)";
            case 10:
                return "حفيف بلاك بيري الحقيقي — مقاس من التسجيل (BlackBerry Original)";
            case KeyboardPreferences.SWIPE_SOUND_SILENT:
                return "صامت للسحب فقط (Silent Swipe)";
            default:
                return "انطلاقة بلاك بيري الهوائية";
        }
    }

    private static String localized(String full) {
        if (java.util.Locale.getDefault().getLanguage().startsWith("ar")) return full;
        int a = full.indexOf('('), b = full.lastIndexOf(')');
        return (a >= 0 && b > a) ? full.substring(a + 1, b) : "BlackBerry Classic";
    }

    public static String getProfileDisplayName(int profile) {
        return localized(getProfileDisplayNameAr(profile));
    }

    public static String getSwipeProfileDisplayName(int swipeProfile) {
        return localized(getSwipeProfileDisplayNameAr(swipeProfile));
    }
}
