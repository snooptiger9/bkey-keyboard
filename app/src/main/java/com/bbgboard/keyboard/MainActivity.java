package com.bbgboard.keyboard;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {

    private KeyboardPreferences prefs;
    private SoundController soundController;
    private PredictionEngine predictionEngine;
    private ClipboardRepository clipboardRepo;

    private FrameLayout mainFrame;
    private View homeMenuView;
    private EditText floatingTestEdit;
    private View floatingTestFab;
    private EditText quickTestEdit;
    private boolean floatingTesterKeyboardConfirmedOpen = false;

    private final java.util.ArrayDeque<Integer> scrollStack = new java.util.ArrayDeque<>();
    private boolean returningBack = false;
    private int pendingScrollY = 0;
    private Runnable currentBack = null;

    private int currentScrollY() {
        View v = mainFrame != null && mainFrame.getChildCount() > 0 ? mainFrame.getChildAt(0) : null;
        return v instanceof ScrollView ? v.getScrollY() : 0;
    }

    private void reopenKeepingScroll(Runnable open) {
        pendingScrollY = currentScrollY();
        returningBack = true;
        skipNavAnim = true;
        open.run();
    }

    private void goBack(Runnable parent) {
        pendingScrollY = scrollStack.isEmpty() ? 0 : scrollStack.pop();
        returningBack = true;
        parent.run();
    }

    private boolean skipNavAnim = false;

    private void restoreScroll(final ScrollView scroll) {
        final int y = pendingScrollY;
        final int dir = returningBack ? -1 : 1;
        final boolean animate = !skipNavAnim;
        pendingScrollY = 0;
        returningBack = false;
        skipNavAnim = false;
        if (animate) {
            float from = (isDeviceArabic() ? -1 : 1) * dir * dp(36);
            scroll.setAlpha(0f);
            scroll.setTranslationX(from);
            scroll.animate().alpha(1f).translationX(0f).setDuration(320)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
        if (y > 0) {
            scroll.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    scroll.getViewTreeObserver().removeOnPreDrawListener(this);
                    scroll.scrollTo(0, y);
                    return true;
                }
            });
        }
    }

    @Override
    public void finish() {
        super.finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private boolean isDeviceArabic() {
        String lang = java.util.Locale.getDefault().getLanguage();
        return lang != null && lang.toLowerCase(java.util.Locale.ROOT).startsWith("ar");
    }

    private String tr(String ar, String en) {
        return isDeviceArabic() ? ar : en;
    }

    private boolean isNotificationAccessGranted() {
        String enabled = android.provider.Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return enabled != null && enabled.contains(getPackageName());
    }

    private String[] tr(String[] ar, String[] en) {
        return isDeviceArabic() ? ar : en;
    }

    /**
     * Maps a light-mode UI-chrome hex color to its dark-mode equivalent when the app's own
     * dark mode is on (unrelated to the keyboard's own color theme). Hex values that represent
     * an actual selectable keyboard color (theme swatches, capsule colors, enter-key colors)
     * are never passed through this — only backgrounds/borders/text of the settings screens.
     */
    private String dm(String lightHex) {
        if (prefs == null || !prefs.isAppDarkMode()) return lightHex;
        switch (lightHex) {
            case "#111827": case "#1F2937": case "#1E293B": return "#F1F2F4";
            case "#374151": return "#D7DAE0";
            case "#4B5563": return "#B7BBC2";
            case "#6B7280": case "#5F6368": return "#9AA0AC";
            case "#9CA3AF": return "#868C97";
            case "#D1D5DB": return "#3A3B44";
            case "#E2E8F0": case "#E5E7EB": return "#33343C";
            case "#FFFFFF": return "#1E1F24";
            case "#F8FAFC": case "#F9FAFB": return "#1C1D22";
            case "#F3F4F6": case "#F1F3F5": return "#24252B";
            case "#F2F4F8": return "#121318";
            case "#2563EB": return "#3B82F6";
            case "#DC2626": return "#F87171";
            case "#FEE2E2": return "#3B1E1E";
            case "#B91C1C": return "#FCA5A5";
            case "#059669": case "#047857": return "#34D399";
            case "#16A34A": return "#4ADE80";
            case "#E5F0EA": return "#1C2E22";
            case "#EFF6FF": return "#1B2A3F";
            case "#78350F": case "#B45309": return "#FBBF24";
            default: return lightHex;
        }
    }

    /**
     * The Activity's own window theme (Theme.DeviceDefault.Light.NoActionBar) has a plain white
     * windowBackground, which otherwise flashes through for an instant during screen-switch
     * animations — most noticeable in dark mode, since it's a bright white flash against dark
     * screens. Overriding it at runtime keeps that gap the same color as the current mode.
     */
    private void applyWindowBackground() {
        getWindow().setBackgroundDrawable(new ColorDrawable(Color.parseColor(dm("#F2F4F8"))));
    }

    private GradientDrawable roundedCard(String fillHex, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(radiusDp));
        return gd;
    }

    private GradientDrawable borderedBox(String fillHex, String borderHex, int radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(dp(radiusDp));
        gd.setStroke(dp(1), Color.parseColor(borderHex));
        return gd;
    }

    /** Flat, rounded, ripple-feedback button background — the one consistent button look across every settings screen. */
    private android.graphics.drawable.Drawable rippleRounded(String fillHex, int radiusDp) {
        GradientDrawable base = roundedCard(fillHex, radiusDp);
        android.content.res.ColorStateList rippleColor =
                android.content.res.ColorStateList.valueOf(Color.argb(55, 255, 255, 255));
        return new android.graphics.drawable.RippleDrawable(rippleColor, base, base);
    }

    /** Applies the app's single modern flat-button style (no shadow, ripple feedback, consistent radius/typeface). */
    private void styleModernButton(Button btn, String fillHex) {
        btn.setBackground(rippleRounded(fillHex, 14));
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setStateListAnimator(null);
        btn.setElevation(0f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLogger.init(this);
        prefs = new KeyboardPreferences(this);
        soundController = new SoundController(this, prefs);
        predictionEngine = new PredictionEngine(this, prefs);
        clipboardRepo = new ClipboardRepository(this);
        applyWindowBackground();

        mainFrame = new FrameLayout(this);
        mainFrame.setBackgroundColor(Color.parseColor(dm("#F2F4F8")));
        mainFrame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        showHomeGboardSettingsMenu();

        FrameLayout rootContainer = new FrameLayout(this);
        rootContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rootContainer.addView(mainFrame);
        addFloatingKeyboardTester(rootContainer);
        setContentView(rootContainer);
    }

    private void addFloatingKeyboardTester(FrameLayout root) {
        floatingTestEdit = new EditText(this);
        floatingTestEdit.setHint(tr("جرّب الكيبورد هنا...", "Try the keyboard here..."));
        floatingTestEdit.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        floatingTestEdit.setTextColor(Color.parseColor(dm("#111827")));
        floatingTestEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        floatingTestEdit.setBackground(borderedBox(dm("#FFFFFF"), dm("#D1D5DB"), 14));
        floatingTestEdit.setPadding(dp(14), dp(10), dp(14), dp(10));
        floatingTestEdit.setElevation(dp(6));
        floatingTestEdit.setVisibility(View.GONE);
        FrameLayout.LayoutParams lpEdit = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpEdit.gravity = Gravity.BOTTOM;
        lpEdit.setMargins(dp(16), 0, dp(16), dp(8));
        floatingTestEdit.setLayoutParams(lpEdit);
        root.addView(floatingTestEdit);

        FrameLayout fab = new FrameLayout(this);
        fab.setBackground(roundedCard(dm("#2563EB"), 28));
        fab.setElevation(dp(8));
        ModernIconView fabIcon = new ModernIconView(this, ModernIconView.ICON_BACK_KEYBOARD, Color.WHITE, Color.parseColor(dm("#33FFFFFF")));
        fabIcon.setStyle(0.9f, 2.0f);
        FrameLayout.LayoutParams lpFabIcon = new FrameLayout.LayoutParams(dp(26), dp(26));
        lpFabIcon.gravity = Gravity.CENTER;
        fab.addView(fabIcon, lpFabIcon);
        FrameLayout.LayoutParams lpFab = new FrameLayout.LayoutParams(dp(56), dp(56));
        lpFab.gravity = Gravity.BOTTOM | (isDeviceArabic() ? Gravity.START : Gravity.END);
        lpFab.setMargins(dp(16), 0, dp(16), dp(16));
        fab.setLayoutParams(lpFab);
        fab.setOnClickListener(v -> openFloatingTester());
        root.addView(fab);
        floatingTestFab = fab;

        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (floatingTestEdit.getVisibility() != View.VISIBLE) return;
            Rect visibleFrame = new Rect();
            root.getWindowVisibleDisplayFrame(visibleFrame);
            int screenHeight = root.getRootView().getHeight();
            if (screenHeight <= 0) return;
            int keypadHeight = screenHeight - visibleFrame.bottom;
            boolean keyboardOpen = keypadHeight > screenHeight * 0.15f;

            if (keyboardOpen) {
                // The Activity already uses android:windowSoftInputMode="adjustResize", so the
                // window itself shrinks to end exactly above the keyboard — our view, anchored
                // with gravity=BOTTOM and a small fixed margin, is already glued to it with no
                // extra math needed. (Adding keypadHeight again here used to double-compensate
                // and push the box a whole keyboard-height too high.)
                floatingTesterKeyboardConfirmedOpen = true;
            } else if (floatingTesterKeyboardConfirmedOpen) {
                // The keyboard was genuinely open before and just closed for real
                // (back button or back gesture) — close the tester along with it.
                closeFloatingTester();
            }
            // else: keyboardOpen is false but we never confirmed it open yet — this is just
            // the brief moment right after opening, before the IME reports its real frame.
            // Ignoring it here is what fixes the "box never shows up" race.
        });
    }

    private void openFloatingTester() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        floatingTesterKeyboardConfirmedOpen = false;
        if (quickTestEdit != null) quickTestEdit.setVisibility(View.GONE);
        if (floatingTestFab != null) floatingTestFab.setVisibility(View.GONE);
        floatingTestEdit.setVisibility(View.VISIBLE);
        floatingTestEdit.requestFocus();
        floatingTestEdit.post(() -> {
            if (imm != null) imm.showSoftInput(floatingTestEdit, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void closeFloatingTester() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        floatingTestEdit.setText("");
        floatingTestEdit.setVisibility(View.GONE);
        floatingTestEdit.clearFocus();
        if (imm != null) imm.hideSoftInputFromWindow(floatingTestEdit.getWindowToken(), 0);
        floatingTesterKeyboardConfirmedOpen = false;
        if (floatingTestFab != null) floatingTestFab.setVisibility(View.VISIBLE);
        if (quickTestEdit != null) quickTestEdit.setVisibility(View.VISIBLE);
    }

    @Override
    public void onBackPressed() {
        if (mainFrame != null && mainFrame.getChildCount() > 0 && mainFrame.getChildAt(0) != homeMenuView) {
            goBack(currentBack != null ? currentBack : this::showHomeGboardSettingsMenu);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (prefs.isPendingReturnToOtpScreen()) {
            prefs.setPendingReturnToOtpScreen(false);
            openOtpSettingsScreen();
            return;
        }

        if (mainFrame != null && mainFrame.getChildCount() > 0 && mainFrame.getChildAt(0) == homeMenuView) {
            showHomeGboardSettingsMenu();
        }
    }

    private String ownImeId() {
        return new ComponentName(this, BlackBerryImeService.class).flattenToShortString();
    }

    private boolean isOwnImeEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm == null) return false;
        String myId = ownImeId();
        for (InputMethodInfo info : imm.getEnabledInputMethodList()) {
            if (myId.equals(info.getId())) return true;
        }
        return false;
    }

    private boolean isOwnImeSelected() {
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        return ownImeId().equals(current);
    }

    private void showHomeGboardSettingsMenu() {
        mainFrame.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor(dm("#F2F4F8")));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(36));

        root.setLayoutDirection(isDeviceArabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout titleRowTop = new LinearLayout(this);
        titleRowTop.setOrientation(LinearLayout.HORIZONTAL);
        titleRowTop.setGravity(Gravity.CENTER_VERTICAL);
        titleRowTop.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pageTitle = new TextView(this);
        pageTitle.setText(tr("الإعدادات", "Settings"));
        pageTitle.setTextColor(Color.parseColor(dm("#1F2937")));
        pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        pageTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pageTitle.setPadding(dp(6), dp(6), dp(6), dp(16));
        pageTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRowTop.addView(pageTitle);

        boolean darkNow = prefs.isAppDarkMode();
        ModernIconView themeToggleBtn = new ModernIconView(this,
                ModernIconView.ICON_CRESCENT_MOON,
                Color.parseColor(dm("#2563EB")), Color.parseColor(dm("#EFF6FF")));
        themeToggleBtn.setFilled(darkNow);
        themeToggleBtn.setStyle(0.78f, 2.0f);
        LinearLayout.LayoutParams lpToggle = new LinearLayout.LayoutParams(dp(42), dp(42));
        lpToggle.leftMargin = dp(6);
        lpToggle.rightMargin = dp(6);
        themeToggleBtn.setLayoutParams(lpToggle);
        themeToggleBtn.setTranslationY(-dp(3));
        themeToggleBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            prefs.setAppDarkMode(!prefs.isAppDarkMode());
            applyWindowBackground();
            mainFrame.setBackgroundColor(Color.parseColor(dm("#F2F4F8")));
            showHomeGboardSettingsMenu();
        });
        titleRowTop.addView(themeToggleBtn);

        root.addView(titleRowTop);

        LinearLayout quickSetupCard = new LinearLayout(this);
        quickSetupCard.setOrientation(LinearLayout.VERTICAL);
        quickSetupCard.setBackground(roundedCard(dm("#FFFFFF"), 20));
        quickSetupCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lpSetup = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpSetup.bottomMargin = dp(16);
        quickSetupCard.setLayoutParams(lpSetup);

        boolean imeEnabled = isOwnImeEnabled();
        boolean imeSelected = imeEnabled && isOwnImeSelected();

        if (!imeEnabled) {
            quickSetupCard.addView(createSetupStepRow(
                    ModernIconView.ICON_BACK_KEYBOARD, dm("#059669"),
                    tr("1. تفعيل الكيبورد", "1. Enable the keyboard"),
                    tr("يفتح إعدادات لوحات المفاتيح في النظام", "Opens the system's keyboard list"),
                    v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            ));
            LinearLayout.LayoutParams lpGap = (LinearLayout.LayoutParams) quickSetupCard.getChildAt(0).getLayoutParams();
            lpGap.bottomMargin = dp(10);
        }

        if (imeSelected) {
            quickSetupCard.addView(createSetupStepRow(
                    ModernIconView.ICON_SETTINGS_GEAR, dm("#16A34A"),
                    tr("✓ مفعّل ومختار حالياً", "✓ Enabled and currently selected"),
                    tr("هذا الكيبورد هو المستخدم الآن", "This keyboard is the one in use now"),
                    v -> {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showInputMethodPicker();
                    }
            ));
        } else {
            quickSetupCard.addView(createSetupStepRow(
                    ModernIconView.ICON_SETTINGS_GEAR, dm("#2563EB"),
                    imeEnabled ? tr("اختيار الكيبورد", "Switch to it") : tr("2. اختيار الكيبورد", "2. Switch to it"),
                    tr("يفتح قائمة اختيار لوحة المفاتيح الحالية", "Opens the input method picker"),
                    v -> {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showInputMethodPicker();
                    }
            ));
        }

        quickTestEdit = new EditText(this);
        quickTestEdit.setHint(tr("اضغط هنا لتجربة الكيبورد والبوب أب وأصوات السحب...", "Tap here to try the keyboard, popups and swipe sounds..."));
        quickTestEdit.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        quickTestEdit.setTextColor(Color.parseColor(dm("#111827")));
        quickTestEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        quickTestEdit.setBackground(borderedBox(dm("#F9FAFB"), dm("#E5E7EB"), 12));
        quickTestEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpQt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpQt.topMargin = dp(10);
        quickTestEdit.setLayoutParams(lpQt);
        quickSetupCard.addView(quickTestEdit);
        root.addView(quickSetupCard);

        LinearLayout group1 = createGroupedWhiteCard();
        group1.addView(createMenuRow(
                ModernIconView.ICON_GLOBE_LANG,
                tr("اللغة", "Language"),
                tr("العربية والإنجليزية • بوب أب الضغط المطول", "Arabic & English • long-press popup"),
                true,
                this::openLanguagesScreen
        ));
        group1.addView(createMenuRow(
                ModernIconView.ICON_THEME_PALETTE,
                tr("المظهر وشكل الكيبورد والحجم", "Theme, key style & size"),
                getThemeAndStyleSummary(),
                false,
                this::openThemeAndStyleScreen
        ));
        root.addView(group1);

        LinearLayout group2 = createGroupedWhiteCard();
        group2.addView(createMenuRow(
                ModernIconView.ICON_TEXT_CORRECTION,
                tr("التصحيح والاقتراحات والتعلّم", "Suggestions & learning"),
                tr("تعلّم الكلمات الجديدة وتوقع الكلمة التالية (" + predictionEngine.getLearnedWordCount() + " كلمة محفوظة)",
                        "Learns new words and predicts the next one (" + predictionEngine.getLearnedWordCount() + " words saved)"),
                true,
                this::openCorrectionsAndMemoryScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_GESTURE_SWIPE,
                tr("إيماءات السحب والأنميشن", "Swipe gestures & animation"),
                tr("أنميشن طيران الكلمة الواضح لأعلى الشاشة • اتجاه السحب لمسح كلمة",
                        "Word-flies-up animation • swipe-to-delete direction"),
                true,
                this::openGesturesScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_SOUND_WAVE,
                tr("الصوت والاهتزاز", "Sound & vibration"),
                SoundController.getProfileDisplayName(prefs.getSoundProfile()) + " • " + SoundController.getSwipeProfileDisplayName(prefs.getSwipeSoundProfile()),
                true,
                this::openSoundAndVibrationScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_EMOJI_SMILE,
                tr("الإيموجي والرموز", "Emojis & symbols"),
                tr("حجم الإيموجي • مسح قسم مستخدم حديثاً (أو الضغط مطولاً على رمز لحذفه)",
                        "Emoji size • clear recents (or long-press an emoji to delete it)"),
                true,
                this::openEmojiScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_SLIDERS_PREFS,
                tr("التفضيلات", "Preferences"),
                tr("صف الأرقام • وضع التخفي • اليد الواحدة",
                        "Number row • incognito • one-handed mode"),
                false,
                this::openPreferencesScreen
        ));
        root.addView(group2);

        LinearLayout group3 = createGroupedWhiteCard();
        group3.addView(createMenuRow(
                ModernIconView.ICON_CLIPBOARD_PAD,
                tr("الحافظة الذكية", "Smart clipboard"),
                tr("زر (≡) بأعلى اليسار • زر الضبط والحجم والرجوع داخل الحافظة",
                        "(≡) button top-left • settings, resize and back buttons inside it"),
                true,
                this::openClipboardScreen
        ));
        group3.addView(createMenuRow(
                ModernIconView.ICON_DICTIONARY_BOOK,
                tr("القاموس واختصارات الكلمات", "Dictionary & shortcuts"),
                tr("إضافة كلمة واختصارها (" + prefs.getShortcuts().size() + " اختصار نشط)",
                        "Add a word and its shortcut (" + prefs.getShortcuts().size() + " active)"),
                false,
                this::openShortcutsScreen
        ));
        root.addView(group3);

        LinearLayout group4 = createGroupedWhiteCard();
        group4.addView(createMenuRow(
                ModernIconView.ICON_REFRESH_UPDATE,
                tr("تحديث التطبيق", "Update the app"),
                tr("الإصدار " + BuildConfig.VERSION_NAME
                                + (BuildConfig.OTP_FEATURES_ENABLED ? " • نسخة كاملة (OTP مفعّلة)" : " • نسخة عادية (بدون OTP)")
                                + " • اضغط لتنزيل أي تحديث جديد",
                        "Version " + BuildConfig.VERSION_NAME
                                + (BuildConfig.OTP_FEATURES_ENABLED ? " • Full build (OTP enabled)" : " • Basic build (no OTP)")
                                + " • tap to download the latest update"),
                true,
                this::checkForAppUpdate
        ));
        group4.addView(createMenuRow(
                ModernIconView.ICON_SETTINGS_GEAR,
                tr("سجل الأخطاء", "Error log"),
                tr("لعرض أي خطأ صار بالتطبيق وإرساله مباشرة لإيميل المطوّر",
                        "View any error the app ran into and send it straight to the developer's email"),
                false,
                this::openErrorLogScreen
        ));
        root.addView(group4);

        int copyrightYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        TextView copyrightTv = new TextView(this);
        copyrightTv.setText("© bkey " + copyrightYear + tr(" — جميع الحقوق محفوظة", " — All rights reserved"));
        copyrightTv.setTextColor(Color.parseColor(dm("#9CA3AF")));
        copyrightTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        copyrightTv.setGravity(Gravity.CENTER);
        copyrightTv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams lpCopyright = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpCopyright.topMargin = dp(18);
        copyrightTv.setLayoutParams(lpCopyright);
        root.addView(copyrightTv);

        scroll.addView(root);
        homeMenuView = scroll;
        mainFrame.addView(scroll);
        currentBack = null;
        restoreScroll(scroll);
    }

    private String getThemeAndStyleSummary() {
        String th = prefs.getThemeMode() == KeyboardPreferences.THEME_GBOARD_WHITE ? tr("السمة البيضاء", "White theme")
                : prefs.getThemeMode() == KeyboardPreferences.THEME_MIDNIGHT_SLATE ? tr("السمة الكحلية", "Midnight theme")
                : tr("سمة bkey السوداء", "bkey black theme");
        String[] styleSummariesAr = {"شكل bkey الكلاسيكي", "شكل bkey الحديث", "شكل كبسولات عصرية",
                "شكل bkey ماتيريال", "شكل bkey بدون إطار", "شكل كبسولات عصرية", "شكل بإطار خفيف", "شكل أزرار ثلاثية الأبعاد",
                "شكل بارز ناعم", "شكل دوائر", "شكل زجاجي"};
        String[] styleSummariesEn = {"Classic bkey", "bkey modern", "Modern pill",
                "bkey material", "bkey borderless", "Modern pill", "Light outline", "3D keycaps",
                "Soft raised", "Circles", "Glass"};
        String[] styleSummaries = isDeviceArabic() ? styleSummariesAr : styleSummariesEn;
        int styleIdx = prefs.getKeyboardStyle();
        String st = (styleIdx >= 0 && styleIdx < styleSummaries.length) ? styleSummaries[styleIdx] : styleSummaries[0];
        return th + " • " + st;
    }

    private View createSetupStepRow(int iconType, String colorHex, String title, String subtitle, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundedCard(colorHex, 14));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(onClick);

        ModernIconView icon = new ModernIconView(this, iconType, Color.WHITE, Color.parseColor(dm("#22FFFFFF")));
        icon.setStyle(0.85f, 2.0f);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(dp(38), dp(38));
        lpIcon.rightMargin = dp(12);
        lpIcon.leftMargin = dp(12);
        icon.setLayoutParams(lpIcon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);

        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextColor(Color.parseColor(dm("#E5F0EA")));
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        subTv.setPadding(0, dp(2), 0, 0);

        textCol.addView(titleTv);
        textCol.addView(subTv);

        row.addView(icon);
        row.addView(textCol);
        return row;
    }

    private LinearLayout createGroupedWhiteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedCard(dm("#FFFFFF"), 20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(16);
        card.setLayoutParams(lp);
        return card;
    }

    private View createMenuRow(int iconType, String title, String subtitle, boolean showBottomDivider, final Runnable onClick) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(16), dp(18), dp(16));

        ModernIconView icon = new ModernIconView(this, iconType, Color.parseColor(dm("#5F6368")), Color.TRANSPARENT);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(dp(32), dp(32));
        lpIcon.setMarginStart(0);
        lpIcon.setMarginEnd(dp(10));
        icon.setLayoutParams(lpIcon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.parseColor(dm("#1F2937")));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);

        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextColor(Color.parseColor(dm("#6B7280")));
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        subTv.setPadding(0, dp(3), 0, 0);

        textCol.addView(titleTv);
        textCol.addView(subTv);

        row.addView(icon);
        row.addView(textCol);
        row.setOnClickListener(v -> onClick.run());
        wrapper.addView(row);

        if (showBottomDivider) {
            View div = new View(this);
            div.setBackgroundColor(Color.parseColor(dm("#F1F3F5")));
            div.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            wrapper.addView(div);
        }

        return wrapper;
    }

    private LinearLayout createSubScreenTemplate(String screenTitle) {
        return createSubScreenTemplate(screenTitle, this::showHomeGboardSettingsMenu);
    }

    private LinearLayout createSubScreenTemplate(String screenTitle, final Runnable onBack) {
        if (!returningBack) scrollStack.push(currentScrollY());
        currentBack = onBack;
        mainFrame.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor(dm("#F2F4F8")));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(18), dp(16), dp(36));
        root.setLayoutDirection(isDeviceArabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(dp(4), dp(4), dp(4), dp(16));

        TextView titleTv = new TextView(this);
        titleTv.setText(screenTitle);
        titleTv.setTextColor(Color.parseColor(dm("#111827")));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lpTitle = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpTitle.leftMargin = dp(12);
        titleTv.setLayoutParams(lpTitle);

        topBar.addView(titleTv);
        root.addView(topBar);

        scroll.addView(root);
        mainFrame.addView(scroll);
        restoreScroll(scroll);
        return root;
    }

    private void openErrorLogScreen() {
        LinearLayout root = createSubScreenTemplate(tr("سجل الأخطاء", "Error log"));

        String logText = AppLogger.readLog(this);
        boolean empty = logText == null || logText.trim().isEmpty();

        TextView bodyTv = new TextView(this);
        bodyTv.setText(empty
                ? tr("ولا خطأ مسجّل لحد الآن", "No errors logged yet")
                : logText);
        bodyTv.setTextIsSelectable(true);
        bodyTv.setTextColor(Color.parseColor(dm("#1F2937")));
        bodyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        bodyTv.setTypeface(Typeface.MONOSPACE);
        bodyTv.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        bodyTv.setBackground(roundedCard(dm("#FFFFFF"), 16));
        bodyTv.setPadding(dp(14), dp(14), dp(14), dp(14));
        LinearLayout.LayoutParams lpBody = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lpBody.bottomMargin = dp(14);
        bodyTv.setLayoutParams(lpBody);
        root.addView(bodyTv);

        if (!empty) {
            LinearLayout btnRow = new LinearLayout(this);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView shareBtn = new TextView(this);
            shareBtn.setText(tr("📧 إرسال للمطوّر", "📧 Email developer"));
            shareBtn.setTextColor(Color.WHITE);
            shareBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            shareBtn.setGravity(Gravity.CENTER);
            shareBtn.setBackground(roundedCard(dm("#2563EB"), 12));
            shareBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lpShare = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lpShare.setMarginEnd(dp(8));
            shareBtn.setLayoutParams(lpShare);
            shareBtn.setOnClickListener(v -> {
                try {
                    Intent send = new Intent(Intent.ACTION_SENDTO);
                    send.setData(android.net.Uri.parse("mailto:"));
                    send.putExtra(Intent.EXTRA_EMAIL, new String[]{"lvl60me@gmail.com"});
                    send.putExtra(Intent.EXTRA_SUBJECT, "bkey - " + tr("تقرير خطأ", "Error report"));
                    send.putExtra(Intent.EXTRA_TEXT, AppLogger.readLog(this));
                    startActivity(Intent.createChooser(send, tr("إرسال سجل الأخطاء", "Send error log")));
                } catch (Exception e) {
                    Intent send = new Intent(Intent.ACTION_SEND);
                    send.setType("text/plain");
                    send.putExtra(Intent.EXTRA_TEXT, AppLogger.readLog(this));
                    startActivity(Intent.createChooser(send, tr("مشاركة سجل الأخطاء", "Share error log")));
                }
            });
            btnRow.addView(shareBtn);

            TextView clearBtn = new TextView(this);
            clearBtn.setText(tr("🗑️ مسح", "🗑️ Clear"));
            clearBtn.setTextColor(Color.parseColor(dm("#B91C1C")));
            clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            clearBtn.setGravity(Gravity.CENTER);
            clearBtn.setBackground(roundedCard(dm("#FEE2E2"), 12));
            clearBtn.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lpClear = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lpClear.setMarginStart(dp(8));
            clearBtn.setLayoutParams(lpClear);
            clearBtn.setOnClickListener(v -> {
                AppLogger.clearLog(this);
                openErrorLogScreen();
            });
            btnRow.addView(clearBtn);

            root.addView(btnRow);
        }
    }

    private interface SwitchListener {
        void onToggle(boolean checked);
    }

    private View createClipRetentionTile() {
        final int[] values = {1, 4, 24, 72, 168, 336, 720, 0};
        final String[] labels = {
                tr("ساعة واحدة", "1 hour"), tr("4 ساعات", "4 hours"), tr("يوم واحد", "1 day"), tr("3 أيام", "3 days"), tr("أسبوع", "1 week"),
                tr("أسبوعين", "2 weeks"), tr("30 يوماً", "30 days"), tr("بدون حذف تلقائي", "Never")};
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedCard(dm("#FFFFFF"), 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(tr("حذف نصوص الحافظة ولقطة الشاشة تلقائياً", "Auto-delete old clipboard items & screenshot"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        title.setTextColor(Color.parseColor(dm("#1F2937")));
        card.addView(title);

        final TextView value = new TextView(this);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        value.setTextColor(Color.parseColor(dm("#6B7280")));
        card.addView(value);
        final Runnable refresh = () -> {
            int cur = prefs.getClipRetentionHours();
            String label = labels[4];
            for (int i = 0; i < values.length; i++) if (values[i] == cur) label = labels[i];
            value.setText(tr("المدة: ", "Keep for: ") + label
                    + tr("  (المثبّت لا يُحذف)", "  (pinned items are kept)"));
        };
        refresh.run();

        card.setOnClickListener(v -> {
            int cur = prefs.getClipRetentionHours();
            int sel = 4;
            for (int i = 0; i < values.length; i++) if (values[i] == cur) sel = i;
            new android.app.AlertDialog.Builder(this)
                    .setTitle(tr("حذف النصوص ولقطة الشاشة القديمة", "Auto-delete old items & screenshot"))
                    .setSingleChoiceItems(labels, sel, (d, which) -> {
                        prefs.setClipRetentionHours(values[which]);
                        refresh.run();
                        d.dismiss();
                    })
                    .show();
        });
        return card;
    }

    private View createSwitchTile(String title, String subtitle, boolean checked, final SwitchListener listener) {
        return createSwitchTile(title, subtitle, checked, true, listener);
    }

    private View createSwitchTile(String title, String subtitle, boolean checked, boolean enabled, final SwitchListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundedCard(dm("#FFFFFF"), 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.parseColor(dm("#1F2937")));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        textCol.addView(titleTv);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextColor(Color.parseColor(dm("#6B7280")));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            sub.setPadding(0, dp(4), 0, 0);
            textCol.addView(sub);
        }

        ModernToggleSwitch sw = new ModernToggleSwitch(this);
        sw.setColors(
                Color.parseColor(dm("#2563EB")), Color.parseColor(dm("#D1D5DB")),
                Color.parseColor(dm("#2563EB")), Color.parseColor(dm("#9CA3AF")));
        sw.setChecked(checked);
        sw.setEnabled(enabled);
        sw.setOnToggleListener(listener::onToggle);
        LinearLayout.LayoutParams lpSw = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSw.leftMargin = dp(10);
        sw.setLayoutParams(lpSw);

        card.addView(textCol);
        card.addView(sw);
        if (!enabled) card.setAlpha(0.5f);
        return card;
    }

    public interface SingleChoiceCallback {
        void onSelected(int index);
    }

    /** Modern selectable-card list (highlighted card + check-circle) — replaces plain radio buttons everywhere they were used. */
    private LinearLayout createExclusiveRadioList(String[] options, int selectedIndex, final SingleChoiceCallback callback) {
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        final List<LinearLayout> rows = new ArrayList<>();
        final List<TextView> labels = new ArrayList<>();
        final List<TextView> checks = new ArrayList<>();

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            boolean sel = (i == selectedIndex);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(roundedCard(sel ? dm("#EFF6FF") : dm("#FFFFFF"), 12));
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpRow.bottomMargin = dp(8);
            row.setLayoutParams(lpRow);

            TextView label = new TextView(this);
            label.setText(options[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            label.setTextColor(Color.parseColor(sel ? dm("#2563EB") : dm("#1F2937")));
            label.setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(label);

            TextView check = new TextView(this);
            check.setText(sel ? "✓" : "");
            check.setTextColor(Color.WHITE);
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            check.setGravity(Gravity.CENTER);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setBackground(circleIndicator(sel));
            LinearLayout.LayoutParams lpCheck = new LinearLayout.LayoutParams(dp(22), dp(22));
            lpCheck.leftMargin = dp(10);
            check.setLayoutParams(lpCheck);
            row.addView(check);

            row.setOnClickListener(v -> {
                for (int j = 0; j < rows.size(); j++) {
                    boolean s = (j == idx);
                    rows.get(j).setBackground(roundedCard(s ? dm("#EFF6FF") : dm("#FFFFFF"), 12));
                    labels.get(j).setTextColor(Color.parseColor(s ? dm("#2563EB") : dm("#1F2937")));
                    labels.get(j).setTypeface(s ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                    checks.get(j).setText(s ? "✓" : "");
                    checks.get(j).setBackground(circleIndicator(s));
                }
                callback.onSelected(idx);
            });

            rows.add(row);
            labels.add(label);
            checks.add(check);
            listLayout.addView(row);
        }
        return listLayout;
    }

    private GradientDrawable circleIndicator(boolean selected) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        if (selected) {
            gd.setColor(Color.parseColor(dm("#2563EB")));
        } else {
            gd.setColor(Color.TRANSPARENT);
            gd.setStroke(dp(1), Color.parseColor(dm("#D1D5DB")));
        }
        return gd;
    }

    private void setLanguagesEnabled(boolean arabicOn, boolean englishOn) {
        if (!arabicOn && !englishOn) return;
        int mode = arabicOn && englishOn ? KeyboardPreferences.LANGUAGE_MODE_BOTH
                : arabicOn ? KeyboardPreferences.LANGUAGE_MODE_ARABIC_ONLY
                : KeyboardPreferences.LANGUAGE_MODE_ENGLISH_ONLY;
        prefs.setLanguageMode(mode);
    }

    private void openLanguagesScreen() {
        LinearLayout root = createSubScreenTemplate(tr("اللغة", "Language"));
        fillLanguagesScreen(root);
    }

    private void refreshLanguagesScreen(LinearLayout root) {
        root.removeViews(1, root.getChildCount() - 1);
        fillLanguagesScreen(root);
    }

    private void fillLanguagesScreen(final LinearLayout root) {

        TextView langModeNote = new TextView(this);
        langModeNote.setText(tr(
                "لو فعّلت اللغتين سوا، تقدر تختار طريقة التبديل بينهم (سحب المسافة أو زر 🌐). "
                        + "لو فعّلت لغة وحدة بس، ما يحتاج تبديل أصلاً فيختفي زر 🌐 ويتعطّل خيار السحب تلقائياً.",
                "Enable both languages together to choose how you switch between them (space-bar swipe or the 🌐 button). "
                        + "Enable just one language and there's nothing to switch, so the 🌐 button disappears and the swipe option turns off automatically."));
        langModeNote.setTextColor(Color.parseColor(dm("#6B7280")));
        langModeNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        langModeNote.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(langModeNote);

        boolean arabicOn = prefs.getLanguageMode() != KeyboardPreferences.LANGUAGE_MODE_ENGLISH_ONLY;
        boolean englishOn = prefs.getLanguageMode() != KeyboardPreferences.LANGUAGE_MODE_ARABIC_ONLY;

        root.addView(createSwitchTile(
                tr("العربية", "Arabic"),
                tr("", ""),
                arabicOn,
                checked -> {
                    setLanguagesEnabled(checked, prefs.getLanguageMode() != KeyboardPreferences.LANGUAGE_MODE_ARABIC_ONLY);
                    refreshLanguagesScreen(root);
                }));

        root.addView(createSwitchTile(
                tr("الإنجليزية", "English"),
                tr("", ""),
                englishOn,
                checked -> {
                    setLanguagesEnabled(prefs.getLanguageMode() != KeyboardPreferences.LANGUAGE_MODE_ENGLISH_ONLY, checked);
                    refreshLanguagesScreen(root);
                }));

        boolean bothLangsOn = arabicOn && englishOn;
        root.addView(createSwitchTile(
                tr("التبديل بسحب زر المسافة", "Switch by swiping the space bar"),
                bothLangsOn
                        ? tr("فعّاله: اسحب زر المسافة يميناً أو يساراً للتبديل. مطفيه: يظهر زر 🌐 مخصص للتبديل بدل ذلك",
                                "On: swipe the space bar left or right to switch. Off: a dedicated 🌐 button appears instead")
                        : tr("يحتاج تفعيل اللغتين (عربي + إنجليزي) حتى يشتغل", "Needs both languages enabled (Arabic + English) to work"),
                prefs.isSpaceSwipeLangEnabled(),
                bothLangsOn,
                checked -> prefs.setSpaceSwipeLangEnabled(checked)
        ));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 77) {
            if (!ScreenshotClip.hasPermission(this)) {
                prefs.setScreenshotClipEnabled(false);
                Toast.makeText(this, tr("لم يُمنح إذن الصور، فلا يمكن حفظ لقطات الشاشة", "Photos permission was not granted, so screenshots can't be saved"), Toast.LENGTH_LONG).show();
            }
            openClipboardScreen();
        }
        if (requestCode == 78) {
            boolean granted = checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            prefs.setOtpSuggestEnabled(granted);
            if (!granted) {
                Toast.makeText(this, tr("لم يُمنح إذن الرسائل، فلن يُقترح رمز التحقق تلقائياً", "SMS permission wasn't granted, so the code won't be suggested automatically"), Toast.LENGTH_LONG).show();
            }
            openOtpSettingsScreen();
        }
    }

    private void openPreferencesScreen() {
        LinearLayout root = createSubScreenTemplate(tr("التفضيلات", "Preferences"));

        root.addView(createSwitchTile(
                tr("أحرف كبيرة تلقائياً بداية كل جملة", "Auto-capitalize sentences"),
                tr("يكتب أول حرف إنجليزي كابيتال تلقائياً في بداية النص أو بعد النقطة", "Automatically capitalizes the first letter at the start of text or after a period"),
                prefs.isAutoCapitalizeSentences(),
                checked -> prefs.setAutoCapitalizeSentences(checked)
        ));

        root.addView(createSwitchTile(
                tr("عرض الحروف الإنجليزية كابيتال دائماً", "Always show uppercase English keycaps"),
                tr("يجعل شكل الحروف على الكيبورد الإنجليزي (Q W E R T Y) واضحاً وكبيراً كما في صورة bkey", "Shows the English keys (Q W E R T Y) in bold capitals, bkey-style"),
                prefs.isAlwaysShowUppercaseKeycapsEn(),
                checked -> prefs.setAlwaysShowUppercaseKeycapsEn(checked)
        ));

        root.addView(createSwitchTile(
                tr("نقطة ومسافة تلقائياً عند ضغط المسافة مرتين", "Double-tap space for period"),
                tr("ضغطتان سريعتان على المسافة تضع نقطة وتتبعها بمسافة", "Two quick taps on space insert a period followed by a space"),
                prefs.isDoubleSpacePeriod(),
                checked -> prefs.setDoubleSpacePeriod(checked)
        ));

        root.addView(createSwitchTile(
                tr("بوب أب معاينة الحرف عند اللمس", "Popup on key press"),
                tr("يظهر الحرف مكبّراً فوق الزر فوراً عند الضغط عليه، ويختفي تلقائياً عند بدء السحب", "Shows the letter enlarged above the key the instant you press it, and disappears as soon as you start swiping"),
                prefs.isKeyPreviewPopupEnabled(),
                checked -> prefs.setKeyPreviewPopupEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("صف الأرقام أعلى الحروف", "Number row"),
                tr("يضيف صفاً دائماً من الأرقام 1-0 أعلى الكيبورد بدون الحاجة لفتح الرموز", "Adds a permanent 1-0 row above the keyboard, no need to open symbols"),
                prefs.isNumberRowEnabled(),
                checked -> prefs.setNumberRowEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("صف الأرقام لكلمات المرور", "Number row for passwords"),
                tr("يظهر صف الأرقام دائماً عند إدخال كلمة مرور، حتى لو كان صف الأرقام العام معطلاً", "Always shows the number row in password fields, even if it's off elsewhere"),
                prefs.isNumberRowForPasswords(),
                checked -> prefs.setNumberRowForPasswords(checked)
        ));

        root.addView(createSwitchTile(
                tr("أرقام عربية هندية (١٢٣) في صف الأرقام", "Arabic-Indic digits (١٢٣)"),
                tr("يكتب ١٢٣ بدل 123 عند استخدام الكيبورد العربي (يعمل مع صف الأرقام)", "Types ١٢٣ instead of 123 on the Arabic keyboard (works with the number row)"),
                prefs.isArabicIndicDigits(),
                checked -> prefs.setArabicIndicDigits(checked)
        ));

        root.addView(createSwitchTile(
                tr("وضع التخفي", "Incognito mode"),
                tr("لا يتعلم الكلمات ولا يحفظ النصوص المنسوخة في الحافظة أثناء تفعيله. كلمات المرور والبريد والروابط لا يُتعلَّم منها دائماً", "No new words are learned and nothing is saved to the clipboard while it's on. Passwords, emails and links are never learned, on or off"),
                prefs.isIncognitoMode(),
                checked -> prefs.setIncognitoMode(checked)
        ));

        LinearLayout oneHandCard = createGroupedWhiteCard();
        oneHandCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView ohHeader = new TextView(this);
        ohHeader.setText(tr("وضع اليد الواحدة:", "One-handed mode:"));
        ohHeader.setTextColor(Color.parseColor(dm("#111827")));
        ohHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        ohHeader.setTypeface(Typeface.DEFAULT_BOLD);
        ohHeader.setPadding(0, 0, 0, dp(8));
        oneHandCard.addView(ohHeader);
        oneHandCard.addView(createExclusiveRadioList(
                tr(new String[]{"معطّل (عرض كامل)", "مصغّر جهة اليسار", "مصغّر جهة اليمين"},
                        new String[]{"Off (full width)", "Compact, left side", "Compact, right side"}),
                prefs.getOneHandedMode(),
                idx -> prefs.setOneHandedMode(idx)));
        root.addView(oneHandCard);
    }

    private void openThemeAndStyleScreen() {
        LinearLayout root = createSubScreenTemplate(tr("المظهر وشكل الكيبورد والحجم", "Theme, key style & size"));
        fillThemeAndStyleScreen(root);
    }

    private void refreshThemeScreen(LinearLayout root) {
        root.removeViews(1, root.getChildCount() - 1);
        fillThemeAndStyleScreen(root);
    }

    private void fillThemeAndStyleScreen(final LinearLayout root) {

        LinearLayout fontCard = createGroupedWhiteCard();
        fontCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView fHeader = new TextView(this);
        fHeader.setText(tr("1. خط الحروف", "1. Letter font"));
        fHeader.setTextColor(Color.parseColor(dm("#111827")));
        fHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        fHeader.setTypeface(Typeface.DEFAULT_BOLD);
        fHeader.setPadding(0, 0, 0, dp(10));
        fontCard.addView(fHeader);

        int[] fontIds = {
                KeyboardPreferences.FONT_SYSTEM_DEFAULT, KeyboardPreferences.FONT_SYSTEM_DEFAULT_BOLD,
                KeyboardPreferences.FONT_TAJAWAL_REGULAR, KeyboardPreferences.FONT_TAJAWAL};
        String[] fontNames = tr(
                new String[]{"افتراضي", "افتراضي بولد", "Tajawal", "Tajawal بولد"},
                new String[]{"Default", "Default Bold", "Tajawal", "Tajawal Bold"});
        String[] fontAssets = {
                null, null,
                "fonts/tajawal_regular.ttf", "fonts/tajawal_bold.ttf"};
        List<View> fontTiles = new ArrayList<>();
        for (int i = 0; i < fontIds.length; i++) {
            final int id = fontIds[i];
            Typeface tf;
            if (fontAssets[i] == null) {
                tf = id == KeyboardPreferences.FONT_SYSTEM_DEFAULT_BOLD ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
            } else {
                tf = Typeface.createFromAsset(getAssets(), fontAssets[i]);
            }
            fontTiles.add(createFontPreviewTile(fontNames[i], tf, prefs.getKeyFont() == id,
                    v -> { prefs.setKeyFont(id); refreshThemeScreen(root); }));
        }
        addTileGrid(fontCard, fontTiles, 4);
        root.addView(fontCard);

        LinearLayout themeCard = createGroupedWhiteCard();
        themeCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView tHeader = new TextView(this);
        tHeader.setText(tr("2. لون السمة", "2. Theme color"));
        tHeader.setTextColor(Color.parseColor(dm("#111827")));
        tHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        tHeader.setTypeface(Typeface.DEFAULT_BOLD);
        tHeader.setPadding(0, 0, 0, dp(10));
        themeCard.addView(tHeader);

        String[] themeNames = tr(
                new String[]{"أسود مطفي", "أبيض ساطع", "كحلي داكن"},
                new String[]{"Matte black", "Clean white", "Midnight slate"});
        String[] themeSwatches = new String[]{"#1C1C1E", "#FFFFFF", "#1E2A47"};
        List<View> themeTiles = new ArrayList<>();
        for (int i = 0; i < themeNames.length; i++) {
            final int idx = i;
            themeTiles.add(createPreviewTile(themeNames[i], themeSwatches[i], prefs.getThemeMode() == i,
                    v -> { prefs.setThemeMode(idx); refreshThemeScreen(root); }));
        }
        addTileGrid(themeCard, themeTiles, 3);
        root.addView(themeCard);

        LinearLayout styleCard = createGroupedWhiteCard();
        styleCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView sHeader = new TextView(this);
        sHeader.setText(tr("3. شكل أزرار الكيبورد", "3. Key shape"));
        sHeader.setTextColor(Color.parseColor(dm("#111827")));
        sHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        sHeader.setTypeface(Typeface.DEFAULT_BOLD);
        sHeader.setPadding(0, 0, 0, dp(10));
        styleCard.addView(sHeader);

        String[] styleNames = tr(
                new String[]{"bkey", "bkey حديث", "كبسولات", "bkey ماتيريال", "بدون إطار", "إطار خفيف", "بارز ناعم", "دوائر", "زجاجي"},
                new String[]{"bkey", "bkey modern", "Pill", "bkey material", "Borderless", "Outline", "Soft raised", "Circles", "Glass"});
        int[] styleIds = {0, 1, 2, 3, 4, 6, 8, 9, 10};
        int[] styleRadii = {2, 10, 20, 14, 8, 10, 12, 24, 10};
        List<View> styleTiles = new ArrayList<>();
        for (int i = 0; i < styleNames.length; i++) {
            final int id = styleIds[i];
            styleTiles.add(createKeyStylePreviewTile(styleNames[i], id, prefs.getKeyboardStyle() == id,
                    v -> { prefs.setKeyboardStyle(id); refreshThemeScreen(root); }));
        }
        addTileGrid(styleCard, styleTiles, 3);
        root.addView(styleCard);

        LinearLayout enterCard = createGroupedWhiteCard();
        enterCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView eHeader = new TextView(this);
        eHeader.setText(tr("4. لون زر الإدخال", "4. Enter key color"));
        eHeader.setTextColor(Color.parseColor(dm("#111827")));
        eHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        eHeader.setTypeface(Typeface.DEFAULT_BOLD);
        eHeader.setPadding(0, 0, 0, dp(10));
        enterCard.addView(eHeader);
        String[] enterNames = tr(
                new String[]{"افتراضي", "أزرق", "أخضر", "أحمر", "عنابي"},
                new String[]{"Default", "Blue", "Green", "Red", "Burgundy"});
        String[] enterHex = {"#4B5563", "#8AB4F8", "#16A34A", "#DC2626", "#7F1D3F"};
        List<View> enterTiles = new ArrayList<>();
        for (int i = 0; i < enterNames.length; i++) {
            final int color = i == 0 ? 0 : Color.parseColor(enterHex[i]);
            enterTiles.add(createPreviewTile(enterNames[i], enterHex[i], prefs.getEnterColor() == color,
                    v -> { prefs.setEnterColor(color); refreshThemeScreen(root); }));
        }
        addTileGrid(enterCard, enterTiles, 5);
        root.addView(enterCard);

        root.addView(buildSizerCard(
                tr("5. حجم الحروف داخل المربعات", "5. Key letter size"),
                Math.round(prefs.getKeyFontScale() * 100), 118, 80, 150,
                pct -> prefs.setKeyFontScale(pct / 100f),
                pct -> pct + "%"));

        root.addView(buildSizerCard(
                tr("6. ارتفاع الكيبورد العام", "6. Overall keyboard height"),
                Math.round(prefs.getKeyboardScale() * 100), 105, 70, 145,
                pct -> prefs.setKeyboardScale(pct / 100f),
                pct -> pct + "%"));

        root.addView(buildSizerCard(
                tr("7. ارتفاع صف الاقتراحات العلوي", "7. Suggestion bar height"),
                Math.round(prefs.getSuggestionBarScale() * 100), 115, 70, 125,
                pct -> prefs.setSuggestionBarScale(pct / 100f),
                pct -> pct + "%"));

        root.addView(buildSizerCard(
                tr("8. حجم خط الاقتراحات", "8. Suggestion text size"),
                prefs.getSuggestionTextLevel(), 3, 0, 5,
                lv -> prefs.setSuggestionTextLevel(lv),
                lv -> lv == 0 ? tr("بدون تكبير", "No enlargement") : "+" + lv));
    }

    private interface IntSetter { void set(int value); }
    private interface IntLabelFormatter { String format(int value); }

    private LinearLayout buildSizerCard(String title, int currentValue, int defaultValue,
                                         int min, int max, IntSetter setter, IntLabelFormatter formatter) {
        LinearLayout card = createGroupedWhiteCard();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView header = new TextView(this);
        header.setText(title);
        header.setTextColor(Color.parseColor(dm("#111827")));
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setPadding(0, 0, 0, dp(10));
        card.addView(header);

        boolean isCustom = currentValue != defaultValue;

        final TextView valueLabel = new TextView(this);
        valueLabel.setTextColor(Color.parseColor(dm("#6B7280")));
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        valueLabel.setPadding(dp(2), dp(8), 0, dp(2));
        valueLabel.setText(formatter.format(currentValue));
        valueLabel.setVisibility(isCustom ? View.VISIBLE : View.GONE);

        final SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, currentValue - min)));
        seek.setPadding(dp(14), dp(2), dp(14), dp(8));
        seek.setVisibility(isCustom ? View.VISIBLE : View.GONE);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = min + progress;
                setter.set(value);
                valueLabel.setText(formatter.format(value));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        final TextView[] toggleBtns = new TextView[2];
        View.OnClickListener onDefaultClick = v -> {
            setter.set(defaultValue);
            seek.setProgress(Math.max(0, Math.min(max - min, defaultValue - min)));
            valueLabel.setText(formatter.format(defaultValue));
            valueLabel.setVisibility(View.GONE);
            seek.setVisibility(View.GONE);
            setSizerToggleSelected(toggleBtns, 0);
        };
        View.OnClickListener onCustomClick = v -> {
            valueLabel.setVisibility(View.VISIBLE);
            seek.setVisibility(View.VISIBLE);
            setSizerToggleSelected(toggleBtns, 1);
        };
        card.addView(buildDefaultCustomToggle(isCustom, onDefaultClick, onCustomClick, toggleBtns));
        card.addView(valueLabel);
        card.addView(seek);
        return card;
    }

    private LinearLayout buildDefaultCustomToggle(boolean customSelected, View.OnClickListener onDefault,
                                                   View.OnClickListener onCustom, TextView[] outButtons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView defBtn = sizerToggleButton(tr("افتراضي", "Default"), !customSelected);
        TextView curBtn = sizerToggleButton(tr("مخصص", "Custom"), customSelected);
        defBtn.setOnClickListener(onDefault);
        curBtn.setOnClickListener(onCustom);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp1.rightMargin = dp(8);
        defBtn.setLayoutParams(lp1);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        curBtn.setLayoutParams(lp2);
        row.addView(defBtn);
        row.addView(curBtn);
        outButtons[0] = defBtn;
        outButtons[1] = curBtn;
        return row;
    }

    private void setSizerToggleSelected(TextView[] btns, int selectedIdx) {
        for (int i = 0; i < btns.length; i++) {
            boolean sel = i == selectedIdx;
            btns[i].setTypeface(sel ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            btns[i].setTextColor(Color.parseColor(sel ? dm("#FFFFFF") : dm("#374151")));
            btns[i].setBackground(roundedCard(sel ? dm("#2563EB") : dm("#F3F4F6"), 10));
        }
    }

    private TextView sizerToggleButton(String text, boolean selected) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(10), dp(8), dp(10), dp(8));
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        btn.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        btn.setTextColor(Color.parseColor(selected ? dm("#FFFFFF") : dm("#374151")));
        btn.setBackground(roundedCard(selected ? dm("#2563EB") : dm("#F3F4F6"), 10));
        return btn;
    }

    private View createPreviewTile(String label, String colorHex, boolean selected, View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setOnClickListener(onClick);

        int holderSize = dp(58);
        int circleSize = dp(46);
        FrameLayout swatchHolder = new FrameLayout(this);
        swatchHolder.setLayoutParams(new LinearLayout.LayoutParams(holderSize, holderSize));

        if (selected) {

            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(dp(2), Color.parseColor(dm("#2563EB")));
            View ringView = new View(this);
            ringView.setBackground(ring);
            FrameLayout.LayoutParams lpRing = new FrameLayout.LayoutParams(holderSize, holderSize);
            lpRing.gravity = Gravity.CENTER;
            ringView.setLayoutParams(lpRing);
            swatchHolder.addView(ringView);
        }

        View circle = new View(this);
        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(Color.parseColor(colorHex));
        swatch.setStroke(dp(1), Color.parseColor(dm("#E5E7EB")));
        circle.setBackground(swatch);
        circle.setElevation(selected ? dp(3) : dp(1));
        FrameLayout.LayoutParams lpCircle = new FrameLayout.LayoutParams(circleSize, circleSize);
        lpCircle.gravity = Gravity.CENTER;
        circle.setLayoutParams(lpCircle);
        swatchHolder.addView(circle);

        if (selected) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(colorHex.equals("#FFFFFF") ? Color.parseColor("#2563EB") : Color.WHITE);
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            FrameLayout.LayoutParams lpCheck = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCheck.gravity = Gravity.CENTER;
            check.setLayoutParams(lpCheck);
            swatchHolder.addView(check);
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(Color.parseColor(selected ? dm("#2563EB") : dm("#374151")));
        labelTv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, dp(5), 0, 0);
        labelTv.setMaxLines(2);

        tile.addView(swatchHolder);
        tile.addView(labelTv);
        return tile;
    }

    private View createFontPreviewTile(String label, Typeface typeface, boolean selected, View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setOnClickListener(onClick);

        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(dp(62), dp(54)));
        holder.setBackground(roundedCard(selected ? dm("#EFF6FF") : dm("#F3F4F6"), 13));
        holder.setElevation(selected ? dp(3) : dp(1));

        TextView sample = new TextView(this);
        sample.setText("أب Ab");
        sample.setTypeface(typeface);
        sample.setTextColor(Color.parseColor(dm("#111827")));
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        sample.setGravity(Gravity.CENTER);
        holder.addView(sample, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(13));
        frame.setColor(Color.TRANSPARENT);
        frame.setStroke(dp(selected ? 2 : 1), Color.parseColor(selected ? dm("#2563EB") : dm("#E5E7EB")));
        holder.setForeground(frame);
        if (selected) {
            TextView badge = new TextView(this);
            badge.setText("✓");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundedCard(dm("#2563EB"), 7));
            FrameLayout.LayoutParams lpB = new FrameLayout.LayoutParams(dp(14), dp(14));
            lpB.gravity = Gravity.TOP | Gravity.END;
            lpB.topMargin = dp(2);
            lpB.rightMargin = dp(2);
            holder.addView(badge, lpB);
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(Color.parseColor(selected ? dm("#2563EB") : dm("#374151")));
        labelTv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, dp(5), 0, 0);
        labelTv.setMaxLines(2);

        tile.addView(holder);
        tile.addView(labelTv);
        return tile;
    }

    private View createKeyStylePreviewTile(String label, int styleId, boolean selected, View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setOnClickListener(onClick);

        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(60)));
        holder.setBackground(roundedCard(selected ? dm("#EFF6FF") : dm("#F3F4F6"), 13));
        holder.setElevation(selected ? dp(3) : dp(1));
        holder.addView(new KeyStylePreviewView(this, styleId, prefs.getThemeMode(), prefs.getEnterColor()),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(13));
        frame.setColor(Color.TRANSPARENT);
        frame.setStroke(dp(selected ? 2 : 1), Color.parseColor(selected ? dm("#2563EB") : dm("#E5E7EB")));
        holder.setForeground(frame);
        if (selected) {
            TextView badge = new TextView(this);
            badge.setText("✓");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundedCard(dm("#2563EB"), 7));
            FrameLayout.LayoutParams lpB = new FrameLayout.LayoutParams(dp(14), dp(14));
            lpB.gravity = Gravity.TOP | Gravity.END;
            lpB.topMargin = dp(2);
            lpB.rightMargin = dp(2);
            holder.addView(badge, lpB);
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(Color.parseColor(selected ? dm("#2563EB") : dm("#374151")));
        labelTv.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, dp(5), 0, 0);
        labelTv.setMaxLines(2);

        tile.addView(holder);
        tile.addView(labelTv);
        return tile;
    }

    private void addTileGrid(LinearLayout container, List<View> tiles, int perRow) {
        for (int i = 0; i < tiles.size(); i += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpRow.topMargin = dp(10);
            row.setLayoutParams(lpRow);
            int rowCount = Math.min(i + perRow, tiles.size()) - i;
            for (int j = i; j < Math.min(i + perRow, tiles.size()); j++) {
                LinearLayout.LayoutParams lpTile = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                int posInRow = j - i;
                lpTile.leftMargin = posInRow > 0 ? dp(3) : 0;
                lpTile.rightMargin = posInRow < rowCount - 1 ? dp(3) : 0;
                tiles.get(j).setLayoutParams(lpTile);
                row.addView(tiles.get(j));
            }
            container.addView(row);
        }
    }

    private void openCorrectionsAndMemoryScreen() {
        LinearLayout root = createSubScreenTemplate(tr("التصحيح والاقتراحات والتعلّم", "Corrections, Suggestions & Learning"));

        root.addView(createSwitchTile(
                tr("حفظ الكلمات الجديدة تلقائياً أثناء الكتابة", "Save new words automatically while typing"),
                tr("يتعلم الكيبورد أي كلمة جديدة تكتبها ويحفظها في ذاكرته", "The keyboard learns any new word you type and remembers it"),
                prefs.isAutoLearnEnabled(),
                checked -> prefs.setAutoLearnEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("توقع الكلمة التالية تلقائياً بعد المسافة", "Predict the next word automatically after space"),
                tr("يعرض أكثر 3 كلمات متوقعة بناءً على سياق جملتك السابقة", "Shows the top 3 predicted words based on your previous sentence"),
                prefs.isNextWordPredictionEnabled(),
                checked -> prefs.setNextWordPredictionEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("تصحيح تلقائي صامت عند المسافة", "Silent auto-correct on space"),
                tr("يستبدل الكلمة تلقائياً بأقرب كلمة صحيحة معروفة بمجرد الضغط على مسافة، بدون ما تلمس شيء. اضغط باكسبيس فوراً للتراجع وإرجاع ما كتبته بالضبط. عند الإيقاف يرجع للوضع الحالي: الاقتراح يظهر لك فقط وأنت تختاره يدوياً",
                        "Instantly replaces the word with the closest known correct word the moment you press space — no tap needed. Press backspace right away to undo and restore exactly what you typed. When off, it goes back to the current behavior: the suggestion just shows and you pick it yourself"),
                prefs.isAutoCorrectEnabled(),
                checked -> prefs.setAutoCorrectEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("زر الكتابة بالصوت (المايك)", "Voice typing button (mic)"),
                tr("يظهر على يمين صف الاقتراحات؛ اضغطه وتكلم ويكتب الكلام. يحتاج إذن الميكروفون واتصالاً بالإنترنت غالباً", "Shows on the right of the suggestion row; tap it and speak to type. Needs mic permission and usually an internet connection"),
                prefs.isVoiceButtonEnabled(),
                checked -> prefs.setVoiceButtonEnabled(checked)
        ));

        LinearLayout otpEntryCard = createGroupedWhiteCard();
        otpEntryCard.addView(createMenuRow(
                ModernIconView.ICON_TEXT_CORRECTION,
                tr("اقتراح رمز التحقق (OTP)", "Verification code (OTP) suggestions"),
                BuildConfig.OTP_FEATURES_ENABLED
                        ? tr("من الرسائل وواتساب", "From SMS & WhatsApp")
                        : tr("ميزة إضافية — تحتاج تحديث", "Extra feature — needs an update"),
                false,
                this::openOtpSettingsScreen
        ));
        root.addView(otpEntryCard);

        LinearLayout trainCard = createGroupedWhiteCard();
        trainCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        final EditText trainBox = new EditText(this);
        trainBox.setHint(tr("أدخل جملة هنا لتدريب الكيبورد عليها فوراً...", "Type a sentence here to train the keyboard instantly..."));
        trainBox.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        trainBox.setTextColor(Color.parseColor(dm("#111827")));
        trainBox.setBackground(borderedBox(dm("#F9FAFB"), dm("#D1D5DB"), 12));
        trainBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        trainCard.addView(trainBox);

        Button btnTrain = new Button(this);
        btnTrain.setText(tr("حفظ وتدريب التوقعات على هذه الجملة", "Save & train predictions on this sentence"));
        styleModernButton(btnTrain, dm("#2563EB"));
        LinearLayout.LayoutParams lpTr = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lpTr.topMargin = dp(8);
        btnTrain.setLayoutParams(lpTr);
        btnTrain.setOnClickListener(v -> {
            String s = trainBox.getText().toString().trim();
            if (!s.isEmpty()) {
                predictionEngine.learnFullSentence(s);
                trainBox.setText("");
                Toast.makeText(MainActivity.this, tr("تم حفظ الكلمات وتوقعات الكلمة التالية!", "Words and next-word predictions saved!"), Toast.LENGTH_SHORT).show();
            }
        });
        trainCard.addView(btnTrain);

        Button btnClear = new Button(this);
        btnClear.setText(tr("مسح جميع الكلمات المتعلمة", "Clear all learned words"));
        styleModernButton(btnClear, dm("#DC2626"));
        LinearLayout.LayoutParams lpCl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        lpCl.topMargin = dp(8);
        btnClear.setLayoutParams(lpCl);
        btnClear.setOnClickListener(v -> {
            predictionEngine.clearAllLearnedData();
        });
        trainCard.addView(btnClear);

        root.addView(trainCard);
        root.addView(createMenuRow(
                ModernIconView.ICON_SLIDERS_PREFS,
                tr("الكلمات المتعلمة", "Learned words"),
                tr(predictionEngine.getLearnedWordCount() + " كلمة محفوظة • بحث وحذف", predictionEngine.getLearnedWordCount() + " saved words • search & delete"),
                false,
                this::openLearnedWordsScreen));
    }

    private void checkForAppUpdate() {
        Toast.makeText(this, tr("جاري التحقق من وجود تحديث...", "Checking for an update..."), Toast.LENGTH_SHORT).show();
        AppUpdater.fetchRemoteVersionCode(remoteVersionCode -> {
            if (remoteVersionCode != null && remoteVersionCode <= BuildConfig.VERSION_CODE) {
                Toast.makeText(MainActivity.this,
                        tr("التطبيق محدّث لآخر إصدار بالفعل", "The app is already up to date"),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, tr("جاري تنزيل التحديث...", "Downloading the update..."), Toast.LENGTH_SHORT).show();
            AppUpdater.downloadAndInstall(getApplicationContext(), success -> {
                if (!success) {
                    Toast.makeText(MainActivity.this,
                            tr("تعذّر التنزيل التلقائي، سيفتح الرابط بالمتصفح بدلاً منه",
                                    "Automatic download failed, opening the link in your browser instead"),
                            Toast.LENGTH_LONG).show();
                    try {
                        String url = BuildConfig.OTP_FEATURES_ENABLED
                                ? "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-full-latest.apk"
                                : "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-latest.apk";
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                    } catch (Exception e) {
                        AppLogger.log("MainActivity", "checkForAppUpdate() download fallback", e);
                    }
                }
            });
        });
    }

    private void openOtpSettingsScreen() {
        LinearLayout root = createSubScreenTemplate(tr("اقتراح رمز التحقق (OTP)", "Verification code (OTP) suggestions"), this::openCorrectionsAndMemoryScreen);

        TextView introTv = new TextView(this);
        introTv.setText(tr(
                "يفحص الكيبورد رسالة SMS أو إشعار واتساب الجديد فقط عند وصوله، ويعرض رمز التحقق في منتصف الاقتراحات لمدة 5 دقائق. كل المعالجة محلية على جهازك فقط ولا تُرسل لأي سيرفر",
                "The keyboard checks only a newly arrived SMS or WhatsApp notification and shows the verification code in the middle suggestion slot for 5 minutes. Everything is processed on your device only — nothing is sent to any server"));
        introTv.setTextColor(Color.parseColor(dm("#4B5563")));
        introTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        LinearLayout.LayoutParams lpIntro = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpIntro.bottomMargin = dp(14);
        introTv.setLayoutParams(lpIntro);
        root.addView(introTv);

        if (BuildConfig.OTP_FEATURES_ENABLED) {
            prefs.setOtpFullBuildEverInstalled(true);

            root.addView(createSwitchTile(
                    tr("اقتراح رمز التحقق تلقائياً من الرسائل", "Auto-suggest verification code from SMS"),
                    tr("يفحص الرسالة الجديدة فقط عند وصولها (بدون الوصول لصندوق الرسائل القديم)، ويعرض الرمز في منتصف الاقتراحات لمدة 5 دقائق فقط. يتطلب إذن قراءة الرسائل", "Checks only a newly arrived message (never the old inbox) and shows the code in the middle suggestion slot for 5 minutes. Needs SMS permission"),
                    prefs.isOtpSuggestEnabled() && checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED,
                    checked -> {
                        if (checked) {
                            if (checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                prefs.setOtpSuggestEnabled(true);
                            } else {
                                requestPermissions(new String[]{android.Manifest.permission.RECEIVE_SMS}, 78);
                            }
                        } else {
                            prefs.setOtpSuggestEnabled(false);
                        }
                    }
            ));

            root.addView(createSwitchTile(
                    tr("اقتراح رمز التحقق تلقائياً من واتساب", "Auto-suggest verification code from WhatsApp"),
                    tr("يقرأ إشعارات واتساب فقط (بدون أي تطبيق آخر) بحثاً عن رمز تحقق، ويعرضه في منتصف الاقتراحات لمدة 5 دقائق. كل المعالجة محلية بالجهاز ولا تُرسل لأي سيرفر. يحتاج صلاحية «الوصول للإشعارات» تفعّلها بنفسك من شاشة أندرويد اللي تفتح", "Reads only WhatsApp notifications (no other app) looking for a verification code, and shows it in the middle suggestion slot for 5 minutes. Fully on-device, nothing sent to any server. Needs \"Notification access\", which you enable yourself on the Android screen that opens"),
                    prefs.isWhatsAppOtpSuggestEnabled() && isNotificationAccessGranted(),
                    checked -> {
                        prefs.setWhatsAppOtpSuggestEnabled(checked);
                        if (checked && !isNotificationAccessGranted()) {
                            Toast.makeText(this, tr("فعّل الوصول للإشعارات لتطبيق bkey من الشاشة اللي بتفتح", "Enable notification access for bkey on the screen that opens"), Toast.LENGTH_LONG).show();
                            try {
                                prefs.setPendingReturnToOtpScreen(true);
                                startActivity(new android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                            } catch (Exception e) {
                                AppLogger.log("MainActivity", "openOtpSettingsScreen()", e);
                            }
                        }
                    }
            ));
        } else {
            boolean reverted = prefs.wasOtpFullBuildEverInstalled();

            LinearLayout warnCard = createGroupedWhiteCard();
            warnCard.setPadding(dp(16), dp(16), dp(16), dp(16));

            TextView warnTitle = new TextView(this);
            warnTitle.setText(reverted
                    ? tr("⚠️ رجعت للنسخة الأساسية بدون هذي الميزة", "⚠️ You're back on the basic build without this feature")
                    : tr("⚠️ هذي الميزة تحتاج تحديث التطبيق", "⚠️ This feature needs an app update"));
            warnTitle.setTextColor(Color.parseColor(dm("#B45309")));
            warnTitle.setTypeface(Typeface.DEFAULT_BOLD);
            warnTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            warnCard.addView(warnTitle);

            TextView warnBody = new TextView(this);
            warnBody.setText(reverted
                    ? tr(
                        "كنت مفعّلها من قبل، وسبب رجوعها هو أنك حمّلت تحديث من رابط صفحة المشروع على GitHub (مباشرة أو من المتصفح) بدل زر «تحديث التطبيق» الموجود بالإعدادات الرئيسية. رابط الصفحة ينزّل دايماً النسخة الأساسية بدون OTP، فتثبيته فوق نسختك يستبدلها ويسحب الصلاحيتين بدون أي تنبيه — ذاك سبب رجوعها «كأنها ماتفعلت».\n\nمن الآن: حدّث التطبيق فقط من زر «تحديث التطبيق» بالقائمة الرئيسية للإعدادات — هو زر واحد يحفظ هذي الميزة تلقائياً كل مرة. فعّلها الآن من جديد من هنا:",
                        "You had it on before. It came back off because you installed an update from the project's GitHub page link (directly or via the browser) instead of the \"Update the app\" button in the main Settings menu. The page link always gives the basic build without OTP, so installing it over your current app silently replaces it and drops both permissions — that's why it reverted \"as if it was never turned on\".\n\nFrom now on: only update the app from the \"Update the app\" button in the main Settings menu — that single button always keeps this feature on automatically. Turn it back on now from here:")
                    : tr(
                        "النسخة المثبّتة عندك الآن لا تطلب صلاحية قراءة الرسائل أو الوصول للإشعارات، فهي تثبّت وتحدّث بدون أي تحذير. تفعيل هذي الميزة يتطلب تحديثاً يضيف هاتين الصلاحيتين، وبسببهما نظام حماية Google Play Protect يوقف التحديث تلقائياً برسالة تحذيرية — هذا سلوك قياسي من قوقل لأي تطبيق خارج المتجر يطلب هذي الصلاحيات بالذات، وليس عيباً ولا فيروساً.\n\nإذا تبي الميزة، عطّل فحص الحماية مؤقتاً بهذي الخطوات ثم حمّل التحديث:",
                        "The version you have installed now doesn't request SMS or notification-access permission, so it installs and updates with zero warning. Turning this feature on needs an update that adds those two permissions, and because of them Google Play Protect blocks the update automatically with a warning screen — this is standard Google behavior for any app outside the Play Store that requests these specific permissions, not a bug or malware flag.\n\nIf you want the feature, turn off Play Protect scanning with these steps, then download the update:"));
            warnBody.setTextColor(Color.parseColor(dm("#78350F")));
            warnBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams lpWarnBody = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpWarnBody.topMargin = dp(8);
            warnBody.setLayoutParams(lpWarnBody);
            warnCard.addView(warnBody);

            TextView steps = new TextView(this);
            steps.setText(tr(
                    "1. افتح تطبيق متجر Google Play.\n2. اضغط صورة حسابك بأعلى يمين الشاشة.\n3. اختر Play Protect.\n4. اضغط أيقونة الإعدادات ⚙️ بأعلى يمين الشاشة.\n5. أوقف خيار «Scan apps with Play Protect».\n6. حمّل وثبّت التحديث من الزر تحت.\n7. رجّع تشغيل الخيار بعدها (يفضّل يضل شغّال دايماً لباقي تطبيقاتك).",
                    "1. Open the Google Play Store app.\n2. Tap your profile picture, top right.\n3. Tap Play Protect.\n4. Tap the ⚙️ settings icon, top right.\n5. Turn off \"Scan apps with Play Protect\".\n6. Download and install the update from the button below.\n7. Turn the setting back on afterward (recommended to keep it on for your other apps)."));
            steps.setTextColor(Color.parseColor(dm("#78350F")));
            steps.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            LinearLayout.LayoutParams lpSteps = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpSteps.topMargin = dp(10);
            steps.setLayoutParams(lpSteps);
            warnCard.addView(steps);

            root.addView(warnCard);

            Button downloadBtn = new Button(this);
            downloadBtn.setText(tr("⬇️ تنزيل التحديث (النسخة الكاملة)", "⬇️ Download the update (full build)"));
            styleModernButton(downloadBtn, dm("#2563EB"));
            LinearLayout.LayoutParams lpDl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46));
            lpDl.topMargin = dp(14);
            downloadBtn.setLayoutParams(lpDl);
            downloadBtn.setOnClickListener(v -> {
                Toast.makeText(this, tr("جاري تنزيل التحديث...", "Downloading the update..."), Toast.LENGTH_SHORT).show();
                AppUpdater.downloadAndInstall(getApplicationContext(), true, success -> {
                    if (!success) {
                        Toast.makeText(MainActivity.this,
                                tr("تعذّر التنزيل التلقائي، سيفتح الرابط بالمتصفح بدلاً منه",
                                        "Automatic download failed, opening the link in your browser instead"),
                                Toast.LENGTH_LONG).show();
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                                    "https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-full-latest.apk")));
                        } catch (Exception e) {
                            AppLogger.log("MainActivity", "openOtpSettingsScreen() download fallback", e);
                        }
                    }
                });
            });
            root.addView(downloadBtn);
        }
    }

    private void openLearnedWordsScreen() {
        LinearLayout root = createSubScreenTemplate(tr("الكلمات المتعلمة", "Learned words"), this::openCorrectionsAndMemoryScreen);
        final LinearLayout learnedListMount = new LinearLayout(this);
        learnedListMount.setOrientation(LinearLayout.VERTICAL);
        LinearLayout card = createGroupedWhiteCard();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        learnedFilter = "";
        final EditText searchBox = new EditText(this);
        searchBox.setHint(tr("ابحث عن كلمة في القائمة...", "Search for a word in the list..."));
        searchBox.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        searchBox.setTextColor(Color.parseColor(dm("#111827")));
        searchBox.setSingleLine(true);
        searchBox.setBackground(borderedBox(dm("#F9FAFB"), dm("#D1D5DB"), 12));
        searchBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSearch.topMargin = 0;
        searchBox.setLayoutParams(lpSearch);
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override
            public void afterTextChanged(android.text.Editable e) {
                learnedFilter = e.toString().trim();
                populateLearnedWordsView(learnedListMount);
            }
        });
        card.addView(searchBox);

        populateLearnedWordsView(learnedListMount);
        card.addView(learnedListMount);
        root.addView(card);
    }

    private String learnedFilter = "";

    private void populateLearnedWordsView(final LinearLayout container) {
        container.removeAllViews();
        List<PredictionEngine.LearnedEntry> allEntries = predictionEngine.getLearnedEntries();
        List<PredictionEngine.LearnedEntry> entries = new java.util.ArrayList<>();
        String q = PredictionEngine.fold(learnedFilter);
        for (PredictionEngine.LearnedEntry en : allEntries) {
            if (q.isEmpty() || PredictionEngine.fold(en.word).contains(q)) entries.add(en);
        }

        TextView header = new TextView(this);
        header.setText(q.isEmpty()
                ? tr("الكلمات المحفوظة (" + entries.size() + ")", "Saved words (" + entries.size() + ")")
                : tr("نتائج البحث (" + entries.size() + " من " + allEntries.size() + ")", "Search results (" + entries.size() + " of " + allEntries.size() + ")"));
        header.setTextColor(Color.parseColor(dm("#047857")));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        header.setPadding(0, dp(4), 0, dp(6));
        container.addView(header);

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(q.isEmpty()
                    ? tr("لا توجد كلمات محفوظة بعد. اكتب جملاً أو درّب الكيبورد من الأعلى لتظهر هنا.", "No saved words yet. Type sentences or train the keyboard above to see them here.")
                    : tr("لا توجد كلمة تطابق بحثك.", "No word matches your search."));
            empty.setTextColor(Color.parseColor(dm("#6B7280")));
            container.addView(empty);
            return;
        }

        int shown = 0;
        for (final PredictionEngine.LearnedEntry entry : entries) {
            if (shown++ >= 250) break;

            LinearLayout wRow = new LinearLayout(this);
            wRow.setOrientation(LinearLayout.HORIZONTAL);
            wRow.setGravity(Gravity.CENTER_VERTICAL);
            wRow.setBackground(borderedBox(dm("#F8FAFC"), dm("#E2E8F0"), 10));
            wRow.setPadding(dp(12), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(5);
            wRow.setLayoutParams(lp);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tv = new TextView(this);
            tv.setText(entry.word + "  (×" + entry.count + ")");
            tv.setTextColor(Color.parseColor(dm("#111827")));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            textBox.addView(tv);

            if (!entry.nextSummary.isEmpty()) {
                TextView nx = new TextView(this);
                nx.setText("← " + entry.nextSummary);
                nx.setTextColor(Color.parseColor(dm("#6B7280")));
                nx.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                nx.setPadding(0, dp(2), 0, 0);
                textBox.addView(nx);
            }

            ModernIconView del = new ModernIconView(this, ModernIconView.ICON_CLOSE,
                    Color.parseColor(dm("#DC2626")), Color.TRANSPARENT);
            del.setStyle(0.8f, 1.9f);
            del.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
            del.setOnClickListener(v -> {
                predictionEngine.removeLearnedWord(entry.word);
                populateLearnedWordsView(container);
            });

            wRow.addView(textBox);
            wRow.addView(del);
            container.addView(wRow);
        }
    }

    private void openEmojiScreen() {
        LinearLayout root = createSubScreenTemplate(tr("الإيموجي والرموز", "Emoji & Symbols"));
        fillEmojiScreen(root);
    }

    private void refreshEmojiScreen(LinearLayout root) {
        root.removeViews(1, root.getChildCount() - 1);
        fillEmojiScreen(root);
    }

    private void fillEmojiScreen(final LinearLayout root) {
        LinearLayout sizeCard = createGroupedWhiteCard();
        sizeCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView sizeLabel = new TextView(this);
        sizeLabel.setText(tr("حجم الإيموجي في القائمة: ", "Emoji size in the list: ") + prefs.getEmojiSizeSp());
        sizeLabel.setTextColor(Color.parseColor(dm("#111827")));
        sizeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        sizeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        sizeCard.addView(sizeLabel);

        final TextView preview = new TextView(this);
        preview.setText("😀 😍 👍 🔥 🌙");
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getEmojiSizeSp());
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(0, dp(10), 0, dp(4));
        sizeCard.addView(preview);

        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(18);
        sizeSeek.setProgress(prefs.getEmojiSizeSp() - 16);
        sizeSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sp = 16 + progress;
                prefs.setEmojiSizeSp(sp);
                sizeLabel.setText(tr("حجم الإيموجي في القائمة: ", "Emoji size in the list: ") + sp);
                preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sizeCard.addView(sizeSeek);
        root.addView(sizeCard);

        LinearLayout capsuleCard = createGroupedWhiteCard();
        capsuleCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView capsuleHeader = new TextView(this);
        capsuleHeader.setText(tr("لون الكبسولات العائمة", "Floating capsules color"));
        capsuleHeader.setTextColor(Color.parseColor(dm("#111827")));
        capsuleHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        capsuleHeader.setTypeface(Typeface.DEFAULT_BOLD);
        capsuleHeader.setPadding(0, 0, 0, dp(10));
        capsuleCard.addView(capsuleHeader);

        FrameLayout previewHolder = new FrameLayout(this);
        previewHolder.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setColor(Color.parseColor(dm("#1F2937")));
        previewBg.setCornerRadius(dp(14));
        previewHolder.setBackground(previewBg);
        final TextView previewCapsule = new TextView(this);
        previewCapsule.setText(tr("أبجد", "ABC"));
        previewCapsule.setTextColor(Color.WHITE);
        previewCapsule.setTypeface(Typeface.DEFAULT_BOLD);
        previewCapsule.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        previewCapsule.setGravity(Gravity.CENTER);
        GradientDrawable previewPill = new GradientDrawable();
        previewPill.setCornerRadius(dp(40));
        previewCapsule.setBackground(previewPill);
        FrameLayout.LayoutParams pillLp = new FrameLayout.LayoutParams(dp(90), dp(42));
        pillLp.gravity = Gravity.CENTER;
        previewCapsule.setLayoutParams(pillLp);
        previewHolder.addView(previewCapsule);
        capsuleCard.addView(previewHolder);

        boolean darkThemeNow = prefs.getThemeMode() != KeyboardPreferences.THEME_GBOARD_WHITE;
        final String followBackHex = darkThemeNow ? dm("#F8FAFC") : dm("#1E293B");
        String[] capsuleNames = tr(
                new String[]{"أسود", "أبيض", "رمادي فاتح"},
                new String[]{"Black", "White", "Light gray"});

        String[] capsuleHex = {"#000000", "#FFFFFF", "#9CA3AF"};
        final int FOLLOW_BACK = KeyboardPreferences.FLOATING_CAPSULE_FOLLOW_LABEL;
        List<View> capsuleTiles = new ArrayList<>();
        final Runnable[] updatePreviewRef = new Runnable[1];
        for (int i = 0; i < capsuleNames.length; i++) {
            final int color = Color.parseColor(capsuleHex[i]) & 0x00FFFFFF;
            boolean selected = (prefs.getFloatingCapsuleColor() & 0x00FFFFFF) == color
                    && prefs.getFloatingCapsuleColor() != FOLLOW_BACK;
            capsuleTiles.add(createPreviewTile(capsuleNames[i], capsuleHex[i], selected,
                    v -> { prefs.setFloatingCapsuleColor(color); refreshEmojiScreen(root); }));
        }
        addTileGrid(capsuleCard, capsuleTiles, 3);

        final TextView opacityLabel = new TextView(this);
        opacityLabel.setText(tr("مستوى الشفافية: ", "Opacity: ") + prefs.getFloatingCapsuleOpacity() + "%");
        opacityLabel.setTextColor(Color.parseColor(dm("#111827")));
        opacityLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        opacityLabel.setTypeface(Typeface.DEFAULT_BOLD);
        opacityLabel.setPadding(0, dp(12), 0, 0);
        capsuleCard.addView(opacityLabel);

        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(90);
        opacitySeek.setProgress(prefs.getFloatingCapsuleOpacity() - 10);
        opacitySeek.setPadding(dp(14), dp(10), dp(14), dp(4));
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int percent = 10 + progress;
                prefs.setFloatingCapsuleOpacity(percent);
                opacityLabel.setText(tr("مستوى الشفافية: ", "Opacity: ") + percent + "%");
                updatePreviewRef[0].run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        capsuleCard.addView(opacitySeek);
        root.addView(capsuleCard);

        updatePreviewRef[0] = () -> {
            int storedColor = prefs.getFloatingCapsuleColor();
            int rgb = storedColor == FOLLOW_BACK
                    ? (Color.parseColor(followBackHex) & 0x00FFFFFF)
                    : (storedColor & 0x00FFFFFF);
            int alpha = Math.round(255 * prefs.getFloatingCapsuleOpacity() / 100f);
            previewPill.setColor((rgb) | (alpha << 24));
        };
        updatePreviewRef[0].run();

        LinearLayout clearCard = createGroupedWhiteCard();
        clearCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView clearBtn = new TextView(this);
        clearBtn.setText(tr("مسح قسم «مستخدم حديثاً»", "Clear \"Recently used\" section"));
        clearBtn.setTextColor(Color.parseColor(dm("#DC2626")));
        clearBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        clearBtn.setTypeface(Typeface.DEFAULT_BOLD);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(0, dp(8), 0, dp(8));
        clearBtn.setOnClickListener(v -> {
            prefs.clearRecentEmojis();
            android.widget.Toast.makeText(this, tr("تم مسح الإيموجي الأخيرة", "Recent emojis cleared"), android.widget.Toast.LENGTH_SHORT).show();
        });
        clearCard.addView(clearBtn);
        root.addView(clearCard);
    }

    private void openGesturesScreen() {
        LinearLayout root = createSubScreenTemplate(tr("إيماءات السحب والأنميشن", "Swipe Gestures & Animation"));

        root.addView(createSwitchTile(
                tr("أنميشن طيران الكلمة الواضح لأعلى الشاشة خارج الكيبورد", "Clear flying-word animation above the keyboard"),
                tr("عند السحب للأعلى تنطلق الكلمة المختارة من صندوق الاقتراحات لأعلى الشاشة بسرعة متزنة ووضوح عالٍ ثم تتلاشى", "On swipe-up, the chosen word launches smoothly and clearly above the screen, then fades"),
                prefs.isSwipeUpAnimationEnabled(),
                checked -> prefs.setSwipeUpAnimationEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("أنميشن ارتداد الزر عند الضغط", "Key bounce animation on press"),
                tr("الزر نفسه ينضغط شوي ثم يرتد لحجمه الطبيعي مع كل نقرة، بدل تغيير لون الخلفية فقط",
                        "The key itself compresses slightly then springs back to its normal size with every tap, instead of just a background color change"),
                prefs.isKeyBounceAnimationEnabled(),
                checked -> prefs.setKeyBounceAnimationEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("السحب الأفقي لمسح كلمة كاملة", "Horizontal swipe to delete a whole word"),
                tr("اسحب أفقياً فوق الحروف لحذف آخر كلمة دفعة واحدة (سحبة أوضح الآن، أقل حساسية حتى ما تنمسح كلمة بالغلط أثناء الكتابة)",
                        "Swipe sideways over the keys to delete the last word at once (now needs a clearer swipe, less sensitive so it won't delete by accident while typing)"),
                prefs.isSwipeDeleteWordEnabled(),
                checked -> prefs.setSwipeDeleteWordEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("السحب الأفقي لتحديد كل النص", "Horizontal swipe to select all text"),
                tr("اسحب بالاتجاه المعاكس تماماً لسحبة المسح فوق الحروف ليتحدد كل النص المكتوب دفعة واحدة",
                        "Swipe over the keys in the exact opposite direction of swipe-to-delete to select all the typed text at once"),
                prefs.isSwipeSelectAllEnabled(),
                checked -> prefs.setSwipeSelectAllEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("السحب للأسفل للنسخ واللصق", "Swipe down to copy/paste"),
                tr("اسحب للأسفل فوق الحروف لنسخ النص المحدد، وبعد النسخ تتحول السحبة للأسفل إلى لصق لمدة دقيقة ثم ترجع نسخ",
                        "Swipe down over the keys to copy selected text; after copying, swipe-down turns into paste for one minute, then reverts to copy"),
                prefs.isSwipeDownCopyPasteEnabled(),
                checked -> prefs.setSwipeDownCopyPasteEnabled(checked)
        ));

        LinearLayout dirCard = createGroupedWhiteCard();
        dirCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView dirHeader = new TextView(this);
        dirHeader.setText(tr("اتجاه السحب للمسح:", "Swipe-to-delete direction:"));
        dirHeader.setTextColor(Color.parseColor(dm("#111827")));
        dirHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        dirHeader.setTypeface(Typeface.DEFAULT_BOLD);
        dirHeader.setPadding(0, 0, 0, dp(8));
        dirCard.addView(dirHeader);

        dirCard.addView(createExclusiveRadioList(
                tr(new String[]{"من اليسار لليمين  ▶", "من اليمين لليسار  ◀"},
                   new String[]{"Left to right  ▶", "Right to left  ◀"}),
                prefs.getSwipeDeleteDirection(),
                idx -> prefs.setSwipeDeleteDirection(idx)));
        TextView dirNote = new TextView(this);
        dirNote.setText(tr("سحبة تحديد الكل تاخذ تلقائياً الاتجاه المعاكس لهذا الاختيار",
                "The select-all swipe automatically takes the opposite direction of this choice"));
        dirNote.setTextColor(Color.parseColor(dm("#6B7280")));
        dirNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        dirNote.setPadding(0, dp(8), 0, 0);
        dirCard.addView(dirNote);
        root.addView(dirCard);

        root.addView(createSwitchTile(
                tr("تحريك المؤشر بالضغط المستمر على زر المسافة", "Move the cursor by holding and dragging the space bar"),
                tr("اضغط مطولاً على المسافة ثم حرّك إصبعك يميناً أو يساراً لتحريك المؤشر حرفاً حرفاً. سحبة سريعة بدون ضغط مستمر تبقى تبدّل اللغة كالمعتاد، بدون أي تعارض بين الحركتين",
                        "Press and hold the space bar, then drag to move the cursor character by character. A quick swipe without holding still switches the language as usual — the two gestures never conflict"),
                prefs.isSpaceCursorDragEnabled(),
                checked -> prefs.setSpaceCursorDragEnabled(checked)
        ));
    }

    private void openSoundAndVibrationScreen() {
        LinearLayout root = createSubScreenTemplate(tr("الصوت والاهتزاز", "Sound & Vibration"));

        root.addView(createSwitchTile(
                tr("تفعيل صوت الكتابة الفوري (Zero-Latency SoundPool)", "Enable instant typing sound (Zero-Latency SoundPool)"),
                tr("استجابة صوتية فورية مع 5 خامات صوتية مختلفة تماماً", "Instant sound feedback with 5 completely different tone profiles"),
                prefs.isSoundEnabled(),
                checked -> {
                    prefs.setSoundEnabled(checked);
                    if (checked) soundController.playKeyClick();
                }
        ));

        root.addView(createSwitchTile(
                tr("تفعيل صوت السحب والمسح الواضح", "Enable clear swipe/delete sound"),
                tr("نغمة سحب مسموعة بوضوح عند اختيار الكلمة المقترحة أو السحب للمسح", "A clearly audible swipe tone when picking a suggested word or swiping to delete"),
                prefs.isSwipeSoundEnabled(),
                checked -> {
                    prefs.setSwipeSoundEnabled(checked);
                    if (checked) soundController.playSwipeUpSound();
                }
        ));

        root.addView(createSwitchTile(
                tr("تفعيل الاهتزاز عند اللمس", "Enable vibration on touch"),
                tr("اهتزاز لمسي خفيف مع الضغط والسحب", "A light haptic buzz on key press and swipe"),
                prefs.isVibrationEnabled(),
                checked -> prefs.setVibrationEnabled(checked)
        ));

        LinearLayout volCard = createGroupedWhiteCard();
        volCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView volLabel = new TextView(this);
        volLabel.setText(tr("قوة ومستوى الصوت: ", "Sound volume: ") + prefs.getSoundVolume() + "%");
        volLabel.setTextColor(Color.parseColor(dm("#111827")));
        volLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        volLabel.setTypeface(Typeface.DEFAULT_BOLD);
        volCard.addView(volLabel);

        SeekBar volSeek = new SeekBar(this);
        volSeek.setMax(100);
        volSeek.setProgress(prefs.getSoundVolume());
        volSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        volSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.setSoundVolume(progress);
                volLabel.setText(tr("قوة ومستوى الصوت: ", "Sound volume: ") + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                soundController.playKeyClick();
            }
        });
        volCard.addView(volSeek);
        root.addView(volCard);

        LinearLayout swipeVolCard = createGroupedWhiteCard();
        swipeVolCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView swipeVolLabel = new TextView(this);
        swipeVolLabel.setText(tr("مستوى صوت السحب والمسح (مستقل): ", "Swipe/delete sound level (independent): ") + prefs.getSwipeSoundVolume() + "%");
        swipeVolLabel.setTextColor(Color.parseColor(dm("#111827")));
        swipeVolLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        swipeVolLabel.setTypeface(Typeface.DEFAULT_BOLD);
        swipeVolCard.addView(swipeVolLabel);

        SeekBar swipeVolSeek = new SeekBar(this);
        swipeVolSeek.setMax(100);
        swipeVolSeek.setProgress(prefs.getSwipeSoundVolume());
        swipeVolSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        swipeVolSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.setSwipeSoundVolume(progress);
                swipeVolLabel.setText(tr("مستوى صوت السحب والمسح (مستقل): ", "Swipe/delete sound level (independent): ") + progress + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                soundController.playSwipeUpSound();
            }
        });
        swipeVolCard.addView(swipeVolSeek);
        root.addView(swipeVolCard);

        LinearLayout keySoundCard = createGroupedWhiteCard();
        keySoundCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView pHeader = new TextView(this);
        pHeader.setText(tr("1. اختر نغمة صوت الكتابة على الحروف (اضغط للتجربة):", "1. Choose the typing-tone profile (tap to preview):"));
        pHeader.setTextColor(Color.parseColor(dm("#111827")));
        pHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        pHeader.setTypeface(Typeface.DEFAULT_BOLD);
        pHeader.setPadding(0, 0, 0, dp(8));
        keySoundCard.addView(pHeader);

        final int[] soundOrder = {10, 0, 1, 2, 3, 4, 5};
        String[] keySoundOptions = new String[soundOrder.length];
        int keySel = 0;
        for (int i = 0; i < soundOrder.length; i++) {
            keySoundOptions[i] = SoundController.getProfileDisplayName(soundOrder[i]);
            if (soundOrder[i] == prefs.getSoundProfile()) keySel = i;
        }
        keySoundCard.addView(createExclusiveRadioList(keySoundOptions, keySel, idx -> {
            prefs.setSoundProfile(soundOrder[idx]);
            soundController.playKeyClick();
        }));
        root.addView(keySoundCard);

        LinearLayout swipeSoundCard = createGroupedWhiteCard();
        swipeSoundCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView swHeader = new TextView(this);
        swHeader.setText(tr("2. اختر نغمة صوت السحب والمسح (اضغط للتجربة):", "2. Choose the swipe/delete-tone profile (tap to preview):"));
        swHeader.setTextColor(Color.parseColor(dm("#111827")));
        swHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        swHeader.setTypeface(Typeface.DEFAULT_BOLD);
        swHeader.setPadding(0, 0, 0, dp(8));
        swipeSoundCard.addView(swHeader);

        final int[] swipeSoundOrder = {10, 0, 1, 4, 7, 8, 9, 5};
        String[] swipeSoundOptions = new String[swipeSoundOrder.length];
        int swSel = 0;
        for (int i = 0; i < swipeSoundOrder.length; i++) {
            swipeSoundOptions[i] = SoundController.getSwipeProfileDisplayName(swipeSoundOrder[i]);
            if (swipeSoundOrder[i] == prefs.getSwipeSoundProfile()) swSel = i;
        }
        swipeSoundCard.addView(createExclusiveRadioList(swipeSoundOptions, swSel, idx -> {
            prefs.setSwipeSoundProfile(swipeSoundOrder[idx]);
            soundController.playSwipeUpSound();
        }));
        root.addView(swipeSoundCard);
    }

    private void openClipboardScreen() {
        LinearLayout root = createSubScreenTemplate(tr("الحافظة الذكية (Clipboard)", "Smart Clipboard"));

        root.addView(createSwitchTile(
                tr("حفظ آخر لقطة شاشة في الحافظة", "Save last screenshot to clipboard"),
                tr("تظهر مصغّرة واحدة فقط في قائمة الحافظة، وكل لقطة جديدة تحل محل السابقة. تحتاج إذن الوصول للصور", "Shows a single thumbnail in the clipboard list; each new screenshot replaces the last. Needs photos permission"),
                prefs.isScreenshotClipEnabled(),
                checked -> {
                    prefs.setScreenshotClipEnabled(checked);
                    if (!checked) {
                        ScreenshotClip.clear(this);
                    } else if (!ScreenshotClip.hasPermission(this)) {
                        requestPermissions(new String[]{ScreenshotClip.neededPermission()}, 77);
                    }
                }
        ));

        root.addView(createSwitchTile(
                tr("إظهار آخر نص منسوخ في الاقتراحات", "Show last copied text in suggestions"),
                tr("بعد النسخ يظهر النص أو الرقم في الخانة الوسطى لمدة دقيقة، واسحب أو اضغط عليه للصقه. لا يظهر في حقول كلمات المرور ولا في وضع التخفي", "After copying, the text or number shows in the middle slot for a minute; tap or swipe up to paste. Never shown in password fields or incognito"),
                prefs.isClipChipEnabled(),
                checked -> prefs.setClipChipEnabled(checked)
        ));

        root.addView(createClipRetentionTile());
    }

    private void openShortcutsScreen() {
        LinearLayout root = createSubScreenTemplate(tr("القاموس واختصارات الكلمات", "Dictionary & Text Shortcuts"));

        root.addView(createSwitchTile(
                tr("توسيع الاختصار تلقائياً فور ضغط المسافة", "Auto-expand shortcut on space"),
                tr("عند كتابة الاختصار (مثل: سع) ثم ضغط المسافة يستبدل بالعبارة الكاملة تلقائياً", "Typing the shortcut then pressing space automatically replaces it with the full phrase"),
                prefs.isAutoExpandShortcuts(),
                checked -> prefs.setAutoExpandShortcuts(checked)
        ));

        LinearLayout card = createGroupedWhiteCard();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        final EditText shortInput = new EditText(this);
        shortInput.setHint(tr("الاختصار (مثال: سع)", "Shortcut (e.g. omw)"));
        shortInput.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        shortInput.setTextColor(Color.parseColor(dm("#111827")));
        shortInput.setBackground(borderedBox(dm("#F9FAFB"), dm("#D1D5DB"), 10));
        shortInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(shortInput);

        final EditText phraseInput = new EditText(this);
        phraseInput.setHint(tr("العبارة الكاملة (مثال: السلام عليكم ورحمة الله وبركاته)", "Full phrase (e.g. on my way)"));
        phraseInput.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        phraseInput.setTextColor(Color.parseColor(dm("#111827")));
        phraseInput.setBackground(borderedBox(dm("#F9FAFB"), dm("#D1D5DB"), 10));
        phraseInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpPh = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPh.topMargin = dp(8);
        phraseInput.setLayoutParams(lpPh);
        card.addView(phraseInput);

        final LinearLayout scListMount = new LinearLayout(this);
        scListMount.setOrientation(LinearLayout.VERTICAL);
        scListMount.setPadding(0, dp(12), 0, 0);

        Button btnSaveSc = new Button(this);
        btnSaveSc.setText(tr("+ حفظ الاختصار", "+ Save shortcut"));
        styleModernButton(btnSaveSc, dm("#059669"));
        LinearLayout.LayoutParams lpBtn = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lpBtn.topMargin = dp(8);
        btnSaveSc.setLayoutParams(lpBtn);
        btnSaveSc.setOnClickListener(v -> {
            String k = shortInput.getText().toString().trim();
            String val = phraseInput.getText().toString().trim();
            if (!k.isEmpty() && !val.isEmpty()) {
                prefs.addShortcut(k, val);
                shortInput.setText("");
                phraseInput.setText("");
                populateShortcutsList(scListMount);
            }
        });
        card.addView(btnSaveSc);
        populateShortcutsList(scListMount);
        card.addView(scListMount);
        root.addView(card);
    }

    private void populateShortcutsList(final LinearLayout container) {
        container.removeAllViews();
        for (final Map.Entry<String, String> e : prefs.getShortcuts().entrySet()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(borderedBox(dm("#F8FAFC"), dm("#E2E8F0"), 10));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);

            TextView tv = new TextView(this);
            SpannableString shortcutSpan = new SpannableString(e.getKey());
            shortcutSpan.setSpan(new ForegroundColorSpan(Color.parseColor(dm("#6B7280"))),
                    0, shortcutSpan.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            SpannableStringBuilder rowText = new SpannableStringBuilder();
            rowText.append(shortcutSpan);
            rowText.append("   ");
            rowText.append(e.getValue());
            tv.setText(rowText);
            tv.setTextColor(Color.parseColor(dm("#1F2937")));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ModernIconView del = new ModernIconView(this, ModernIconView.ICON_TRASH,
                    Color.parseColor(dm("#DC2626")), Color.TRANSPARENT);
            del.setStyle(0.85f, 1.9f);
            LinearLayout.LayoutParams lpDel = new LinearLayout.LayoutParams(dp(20), dp(20));
            lpDel.leftMargin = dp(16);
            del.setLayoutParams(lpDel);
            del.setOnClickListener(v -> {
                prefs.removeShortcut(e.getKey());
                populateShortcutsList(container);
            });

            row.addView(tv);
            row.addView(del);
            container.addView(row);
        }
    }
}
