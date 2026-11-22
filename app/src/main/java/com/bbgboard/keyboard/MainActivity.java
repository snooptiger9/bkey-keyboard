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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
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
        ScrollView sv = v != null ? findScrollViewIn(v) : null;
        return sv != null ? sv.getScrollY() : 0;
    }

    // The home menu wraps its ScrollView inside a container alongside a fixed
    // (non-scrolling) header, unlike sub-screens where the ScrollView is the
    // direct child of mainFrame -- so this looks one level deeper when needed.
    private ScrollView findScrollViewIn(View v) {
        if (v instanceof ScrollView) return (ScrollView) v;
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++) {
                ScrollView sv = findScrollViewIn(vg.getChildAt(i));
                if (sv != null) return sv;
            }
        }
        return null;
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
            scroll.animate().alpha(1f).translationX(0f).setDuration(340)
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
            case "#DBEAFE": return "#24406B";
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
        int color = Color.parseColor(dm("#F2F4F8"));
        getWindow().setBackgroundDrawable(new ColorDrawable(color));

        // The system navigation bar (gesture/button bar) at the very bottom of the screen is
        // drawn by the Window itself, outside our view hierarchy, so it stays its default light
        // color unless we color it to match -- otherwise it shows as a bright strip under dark
        // mode. Same fix already applied to the keyboard's own window in applyNavigationBarColor().
        getWindow().setNavigationBarColor(color);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (prefs.isAppDarkMode()) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
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
        checkFlavorSwitch();
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

    /** bkey and Plus share one applicationId, so sideloading one over the other replaces the
     *  same installed app rather than adding a second one -- settings/learned words/clipboard
     *  are kept across that replace, but a user doing it might not realize that happened on
     *  purpose. If the build just changed since last launch, say so once. */
    private void checkFlavorSwitch() {
        String currentFlavor = BuildConfig.OTP_FEATURES_ENABLED ? "full" : "lite";
        String lastFlavor = prefs.getLastKnownFlavor();
        if (lastFlavor != null && !lastFlavor.equals(currentFlavor)) {
            String switchedToName = BuildConfig.OTP_FEATURES_ENABLED
                    ? "bkey Plus"
                    : "bkey";
            // Kept short on purpose so it fits on one line on the toast instead of
            // wrapping to two.
            android.widget.Toast.makeText(this,
                    tr("تحوّلت إلى " + switchedToName + " — إعداداتك وكلماتك محفوظة",
                            "Switched to " + switchedToName + " — settings and words kept"),
                    android.widget.Toast.LENGTH_LONG).show();
        }
        prefs.setLastKnownFlavor(currentFlavor);
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // The system's "choose input method" picker is a dialog window, not a
        // separate Activity, so dismissing it regains focus here without ever
        // calling onResume(). Without this, the enabled/selected state on the
        // home screen stayed stale until the page was closed and reopened.
        if (hasFocus && mainFrame != null && mainFrame.getChildCount() > 0
                && mainFrame.getChildAt(0) == homeMenuView) {
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

        // The title row is a fixed header outside the ScrollView (like Gboard's own
        // "Gboard settings" header), so it stays in place while the list below scrolls.
        LinearLayout pageContainer = new LinearLayout(this);
        pageContainer.setOrientation(LinearLayout.VERTICAL);
        pageContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        pageContainer.setLayoutDirection(isDeviceArabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor(dm("#F2F4F8")));
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(6), dp(16), dp(36));

        root.setLayoutDirection(isDeviceArabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        LinearLayout titleRowTop = new LinearLayout(this);
        titleRowTop.setOrientation(LinearLayout.HORIZONTAL);
        titleRowTop.setGravity(Gravity.CENTER_VERTICAL);
        titleRowTop.setBackgroundColor(Color.parseColor(dm("#F2F4F8")));
        titleRowTop.setPadding(dp(16), dp(22), dp(16), dp(0));
        titleRowTop.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pageTitle = new TextView(this);
        pageTitle.setText(BuildConfig.OTP_FEATURES_ENABLED
                ? tr("إعدادات bkey Plus", "bkey Plus settings")
                : tr("إعدادات bkey", "bkey settings"));
        pageTitle.setTextColor(Color.parseColor(dm("#111827")));
        pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        pageTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pageTitle.setPadding(dp(6), dp(6), dp(6), dp(16));
        pageTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRowTop.addView(pageTitle);

        boolean darkNow = prefs.isAppDarkMode();
        // A more saturated chip background than the very-pale "#EFF6FF" used for selection
        // highlights elsewhere -- that tint barely differed from the page's own light-gray
        // background, so this button didn't read as a tappable chip at all in light mode.
        ModernIconView themeToggleBtn = new ModernIconView(this,
                ModernIconView.ICON_CRESCENT_MOON,
                Color.parseColor(dm("#2563EB")), Color.parseColor(dm("#DBEAFE")));
        themeToggleBtn.setFilled(darkNow);
        themeToggleBtn.setStyle(0.78f, 2.0f);
        LinearLayout.LayoutParams lpToggle = new LinearLayout.LayoutParams(dp(42), dp(42));
        lpToggle.leftMargin = dp(6);
        lpToggle.rightMargin = dp(6);
        themeToggleBtn.setLayoutParams(lpToggle);
        themeToggleBtn.setTranslationY(-dp(8));
        themeToggleBtn.setOnClickListener(v -> {
            soundController.playKeyClick();
            prefs.setAppDarkMode(!prefs.isAppDarkMode());
            applyWindowBackground();
            mainFrame.setBackgroundColor(Color.parseColor(dm("#F2F4F8")));
            showHomeGboardSettingsMenu();
        });
        titleRowTop.addView(themeToggleBtn);

        pageContainer.addView(titleRowTop);

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
                    ModernIconView.ICON_POWER, dm("#059669"),
                    tr("1. تفعيل الكيبورد", "1. Enable the keyboard"),
                    tr("يفتح إعدادات لوحات المفاتيح في النظام", "Opens the system's keyboard list"),
                    v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
            ));
            LinearLayout.LayoutParams lpGap = (LinearLayout.LayoutParams) quickSetupCard.getChildAt(0).getLayoutParams();
            lpGap.bottomMargin = dp(10);
        }

        if (imeSelected) {
            quickSetupCard.addView(createSetupStepRow(
                    ModernIconView.ICON_SHIELD_CHECK, dm("#16A34A"),
                    tr("مفعّل ومختار حالياً", "Enabled and currently selected"),
                    "",
                    v -> {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showInputMethodPicker();
                    }
            ));
        } else {
            quickSetupCard.addView(createSetupStepRow(
                    ModernIconView.ICON_SWITCH, dm("#2563EB"),
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
                null,
                true,
                this::openLanguagesScreen
        ));
        group1.addView(createMenuRow(
                ModernIconView.ICON_PALETTE_BRUSH,
                tr("المظهر وشكل الكيبورد والحجم", "Theme, key style & size"),
                null,
                false,
                this::openThemeAndStyleScreen
        ));
        root.addView(group1);

        LinearLayout group2 = createGroupedWhiteCard();
        group2.addView(createMenuRow(
                ModernIconView.ICON_TEXT_CORRECTION,
                tr("التصحيح والاقتراحات والتعلّم", "Suggestions & learning"),
                null,
                true,
                this::openCorrectionsAndMemoryScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_HAND_SWIPE,
                tr("إيماءات السحب والأنميشن", "Swipe gestures & animation"),
                null,
                true,
                this::openGesturesScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_SOUND_WAVE,
                tr("الصوت والاهتزاز", "Sound & vibration"),
                null,
                true,
                this::openSoundAndVibrationScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_EMOJI_SMILE,
                tr("الإيموجي والرموز", "Emojis & symbols"),
                null,
                true,
                this::openEmojiScreen
        ));
        group2.addView(createMenuRow(
                ModernIconView.ICON_SLIDERS_PREFS,
                tr("التفضيلات", "Preferences"),
                null,
                false,
                this::openPreferencesScreen
        ));
        root.addView(group2);

        LinearLayout group3 = createGroupedWhiteCard();
        group3.addView(createMenuRow(
                ModernIconView.ICON_CLIPBOARD_PAD,
                tr("الحافظة الذكية", "Smart clipboard"),
                null,
                true,
                this::openClipboardScreen
        ));
        group3.addView(createMenuRow(
                ModernIconView.ICON_BOLT,
                tr("القاموس واختصارات الكلمات", "Dictionary & shortcuts"),
                null,
                false,
                this::openShortcutsScreen
        ));
        root.addView(group3);

        // Typing stats: its own card, separate from the dictionary and clipboard rows.
        LinearLayout groupStats = createGroupedWhiteCard();
        groupStats.addView(createMenuRow(
                ModernIconView.ICON_STATS,
                tr("إحصائيات الكتابة", "Typing stats"),
                null,
                false,
                this::openStatsScreen
        ));
        root.addView(groupStats);

        LinearLayout group4 = createGroupedWhiteCard();
        if (BuildConfig.OTP_FEATURES_ENABLED) {
            // The in-app updater needs INTERNET, which the bkey (lite) build
            // intentionally doesn't request -- so this row would just be a
            // button that can never work there. Shown in Plus (full) only;
            // bkey is updated manually from GitHub.
            group4.addView(createMenuRow(
                    ModernIconView.ICON_REFRESH_UPDATE,
                    tr("تحديث التطبيق", "Update the app"),
                    tr("الإصدار " + BuildConfig.VERSION_NAME + " • نسخة bkey Plus"
                                    + "\nاضغط لتنزيل أي تحديث جديد",
                            "Version " + BuildConfig.VERSION_NAME + " • bkey Plus build"
                                    + "\ntap to download the latest update"),
                    true,
                    this::checkForAppUpdate
            ));
        }
        group4.addView(createMenuRow(
                ModernIconView.ICON_BACKUP,
                tr("النسخ الاحتياطي والاستعادة", "Backup & restore"),
                tr("احفظ كلماتك واختصاراتك في ملف واستعدها في أي جهاز",
                        "Save your words and shortcuts to a file and restore them on any device"),
                true,
                this::openBackupScreen
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
        pageContainer.addView(scroll);
        homeMenuView = pageContainer;
        mainFrame.addView(pageContainer);
        currentBack = null;
        restoreScroll(scroll);
    }

    private String getThemeAndStyleSummary() {
        String th = prefs.getRawThemeMode() == KeyboardPreferences.THEME_AUTO ? tr("السمة التلقائية", "Auto theme")
                : prefs.getThemeMode() == KeyboardPreferences.THEME_GBOARD_WHITE ? tr("السمة البيضاء", "White theme")
                : prefs.getThemeMode() == KeyboardPreferences.THEME_MIDNIGHT_SLATE ? tr("السمة الكحلية", "Midnight theme")
                : prefs.getThemeMode() == KeyboardPreferences.THEME_FOREST_GREEN ? tr("السمة الخضراء", "Forest theme")
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

    private int shade(int color, float f) {
        return Color.argb(Color.alpha(color),
                Math.min(255, Math.round(Color.red(color) * f)),
                Math.min(255, Math.round(Color.green(color) * f)),
                Math.min(255, Math.round(Color.blue(color) * f)));
    }

    private View createSetupStepRow(int iconType, String colorHex, String title, String subtitle, View.OnClickListener onClick) {
        int base = Color.parseColor(colorHex);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{base, shade(base, 0.78f)});
        bg.setCornerRadius(dp(22));
        row.setBackground(bg);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(onClick);

        ModernIconView icon = new ModernIconView(this, iconType, Color.WHITE, Color.argb(48, 255, 255, 255));
        icon.setStyle(0.7f, 2.0f);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(dp(46), dp(46));
        lpIcon.setMarginEnd(dp(14));
        icon.setLayoutParams(lpIcon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.WHITE);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);

        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextColor(Color.argb(215, 255, 255, 255));
        subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        subTv.setPadding(0, dp(3), 0, 0);

        textCol.addView(titleTv);
        if (subtitle != null && !subtitle.isEmpty()) textCol.addView(subTv);

        row.addView(icon);
        row.addView(textCol);
        return row;
    }

    /** Rounded input pill with a leading icon, shared by the training, shortcut and search fields. */
    private LinearLayout createFieldPill(int iconType, String hint, int accent, EditText[] out) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(borderedBox(dm("#F9FAFB"), dm("#E5E7EB"), 22));
        pill.setPadding(dp(14), dp(2), dp(14), dp(2));
        ModernIconView ic = new ModernIconView(this, iconType, accent, Color.TRANSPARENT);
        ic.setStyle(0.9f, 1.4f);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        pill.addView(ic);
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        et.setTextColor(Color.parseColor(dm("#111827")));
        et.setBackground(null);
        et.setPadding(dp(10), dp(12), dp(4), dp(12));
        et.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pill.addView(et);
        out[0] = et;
        return pill;
    }

    /** Full-width gradient button with a white icon, the same shape language as the action cards. */
    private View createGradientButton(int iconType, String text, int from, int to, View.OnClickListener onClick) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        bg.setCornerRadius(dp(22));
        b.setBackground(bg);
        b.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        b.setLayoutParams(lp);
        ModernIconView ic = new ModernIconView(this, iconType, Color.WHITE, Color.TRANSPARENT);
        ic.setStyle(0.9f, 1.4f);
        LinearLayout.LayoutParams lpi = new LinearLayout.LayoutParams(dp(22), dp(22));
        lpi.setMarginEnd(dp(8));
        ic.setLayoutParams(lpi);
        b.addView(ic);
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Color.WHITE);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        b.addView(t);
        b.setOnClickListener(onClick);
        return b;
    }

    private LinearLayout createGroupedWhiteCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedCard(dm("#FFFFFF"), 20));
        // Clips each row's press ripple to the card's own rounded corners
        // (so the first/last row's highlight doesn't stick out past them).
        card.setClipToOutline(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(16);
        card.setLayoutParams(lp);
        return card;
    }

    private int menuAccent(int iconType) {
        switch (iconType) {
            case ModernIconView.ICON_GLOBE_LANG: return Color.parseColor("#0EA5E9");
            case ModernIconView.ICON_PALETTE_BRUSH:
            case ModernIconView.ICON_THEME_PALETTE: return Color.parseColor("#EC4899");
            case ModernIconView.ICON_TEXT_CORRECTION: return Color.parseColor("#10B981");
            case ModernIconView.ICON_HAND_SWIPE:
            case ModernIconView.ICON_GESTURE_SWIPE: return Color.parseColor("#8B5CF6");
            case ModernIconView.ICON_SOUND_WAVE: return Color.parseColor("#6366F1");
            case ModernIconView.ICON_EMOJI_SMILE: return Color.parseColor("#F59E0B");
            case ModernIconView.ICON_SLIDERS_PREFS: return Color.parseColor("#64748B");
            case ModernIconView.ICON_CLIPBOARD_PAD: return Color.parseColor("#14B8A6");
            case ModernIconView.ICON_BOLT: return Color.parseColor("#F97316");
            case ModernIconView.ICON_STATS: return Color.parseColor("#3B82F6");
            case ModernIconView.ICON_REFRESH_UPDATE: return Color.parseColor("#22C55E");
            case ModernIconView.ICON_BACKUP: return Color.parseColor("#0891B2");
            case ModernIconView.ICON_SETTINGS_GEAR: return Color.parseColor("#EF4444");
            default: return Color.parseColor(dm("#5F6368"));
        }
    }

    private View createMenuRow(int iconType, String title, String subtitle, boolean showBottomDivider, final Runnable onClick) {
        return createMenuRow(iconType, title, subtitle, showBottomDivider, onClick, null);
    }

    /** Same as the 5-arg overload, but with an optional out-param to grab a live reference to
     *  the subtitle TextView (e.g. to update a word count in place after a clear action,
     *  instead of rebuilding the whole row/screen). Pass null to behave exactly like before. */
    private View createMenuRow(int iconType, String title, String subtitle, boolean showBottomDivider, final Runnable onClick, TextView[] subtitleOut) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // No-subtitle rows get extra vertical padding (vs. the subtitle rows'
        // dp(16)) so they read as a comfortably tall row with the title
        // centered in it, not just a cramped single line. Padding stays
        // symmetric top/bottom either way, so the title keeps centering
        // exactly in the middle of the row.
        int vPad = (subtitle != null && !subtitle.isEmpty()) ? dp(16) : dp(24);
        row.setPadding(dp(18), vPad, dp(18), vPad);

        // Soft tinted tile behind each icon: one accent per feature, drawn in that
        // accent over a ~16% tint of it, so rows are easy to tell apart at a glance.
        int accent = menuAccent(iconType);
        ModernIconView iconInner = new ModernIconView(this, iconType, accent, Color.TRANSPARENT);
        iconInner.setStyle(1f, 1.5f);
        iconInner.setLayoutParams(new FrameLayout.LayoutParams(dp(26), dp(26), Gravity.CENTER));
        FrameLayout icon = new FrameLayout(this);
        android.graphics.drawable.GradientDrawable tile = new android.graphics.drawable.GradientDrawable();
        tile.setColor(Color.argb(42, Color.red(accent), Color.green(accent), Color.blue(accent)));
        tile.setCornerRadius(dp(13));
        icon.setBackground(tile);
        icon.addView(iconInner);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(dp(44), dp(44));
        lpIcon.setMarginStart(0);
        lpIcon.setMarginEnd(dp(14));
        icon.setLayoutParams(lpIcon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.parseColor(dm("#1F2937")));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);

        textCol.addView(titleTv);
        if (subtitle != null && !subtitle.isEmpty()) {
            // Omitted entirely (not just left blank) when there's no subtitle text,
            // so the title sits vertically centered in the row instead of leaving
            // an empty gap below it.
            TextView subTv = new TextView(this);
            subTv.setText(subtitle);
            subTv.setTextColor(Color.parseColor(dm("#6B7280")));
            subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            subTv.setPadding(0, dp(3), 0, 0);
            textCol.addView(subTv);
            if (subtitleOut != null) subtitleOut[0] = subTv;
        }

        row.addView(icon);
        row.addView(textCol);
        row.setClickable(true);
        row.setFocusable(true);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        row.setBackgroundResource(ripple.resourceId);
        row.setOnClickListener(v -> {
            captureTitleForTransition(titleTv);
            onClick.run();
        });
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
        playScreenEnter(root, titleTv);
        return root;
    }

    // ---- Screen-enter transition: the row title glides into the new screen's header
    // and the cards rise in one after another. Text only; icons stay put.
    private float[] pendingTitleSrc; // left, right, top (window px), text size px
    private CharSequence pendingTitleText;
    private long pendingTitleTime;

    private void captureTitleForTransition(TextView tv) {
        try {
            int[] loc = new int[2];
            tv.getLocationInWindow(loc);
            pendingTitleSrc = new float[]{loc[0], loc[0] + tv.getWidth(), loc[1], tv.getTextSize()};
            pendingTitleText = tv.getText();
            pendingTitleTime = android.os.SystemClock.uptimeMillis();
        } catch (Exception e) {
            pendingTitleSrc = null;
        }
    }

    private void playScreenEnter(final LinearLayout root, final TextView titleTv) {
        final float[] src = pendingTitleSrc;
        final CharSequence srcText = pendingTitleText;
        final boolean fresh = src != null
                && android.os.SystemClock.uptimeMillis() - pendingTitleTime < 800;
        pendingTitleSrc = null;
        if (returningBack || !fresh) return;

        // Cards rise in, one after another (the first child is the title bar).
        final android.view.animation.Interpolator decel = new android.view.animation.DecelerateInterpolator(1.6f);
        for (int i = 1; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            c.setAlpha(0f);
            c.setTranslationY(dp(26));
            c.animate().alpha(1f).translationY(0f)
                    .setStartDelay(70L + 45L * Math.min(i - 1, 7))
                    .setDuration(300).setInterpolator(decel).start();
        }

        titleTv.setAlpha(0f);
        titleTv.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                titleTv.getViewTreeObserver().removeOnPreDrawListener(this);
                try {
                    final boolean rtl = titleTv.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
                    int[] tl = new int[2];
                    titleTv.getLocationInWindow(tl);
                    int[] ml = new int[2];
                    mainFrame.getLocationInWindow(ml);
                    final int w = Math.max(titleTv.getWidth(), 1);
                    final TextView ghost = new TextView(MainActivity.this);
                    ghost.setText(srcText);
                    ghost.setTextColor(titleTv.getCurrentTextColor());
                    ghost.setTextSize(TypedValue.COMPLEX_UNIT_PX, titleTv.getTextSize());
                    ghost.setTypeface(titleTv.getTypeface());
                    ghost.setLayoutDirection(titleTv.getLayoutDirection());
                    ghost.setSingleLine(true);
                    ghost.setLayoutParams(new FrameLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT));
                    ghost.setPivotX(rtl ? w : 0f);
                    ghost.setPivotY(0f);
                    float tgtStartX = rtl ? tl[0] + w : tl[0];
                    float srcStartX = rtl ? src[1] : src[0];
                    float endX = tgtStartX - (rtl ? w : 0f) - ml[0];
                    float startX = srcStartX - (rtl ? w : 0f) - ml[0];
                    float endY = tl[1] - ml[1];
                    float startY = src[2] - ml[1];
                    float scale0 = Math.max(0.5f, Math.min(1f, src[3] / Math.max(titleTv.getTextSize(), 1f)));
                    ghost.setTranslationX(startX);
                    ghost.setTranslationY(startY);
                    ghost.setScaleX(scale0);
                    ghost.setScaleY(scale0);
                    mainFrame.addView(ghost);
                    ghost.animate().translationX(endX).translationY(endY).scaleX(1f).scaleY(1f)
                            .setDuration(400)
                            .setInterpolator(new android.view.animation.PathInterpolator(0.2f, 0f, 0.4f, 1f))
                            .withEndAction(() -> {
                                titleTv.setAlpha(1f);
                                mainFrame.removeView(ghost);
                            }).start();
                    // Safety net: never leave the real title hidden.
                    titleTv.postDelayed(() -> titleTv.setAlpha(1f), 600);
                } catch (Exception e) {
                    titleTv.setAlpha(1f);
                }
                return true;
            }
        });
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
        // The screenshot-to-clipboard feature doesn't exist at all in the
        // bkey (lite) build (no photos/storage permission there), so its
        // retention-settings title shouldn't mention it either.
        title.setText(BuildConfig.OTP_FEATURES_ENABLED
                ? tr("حذف نصوص الحافظة ولقطة الشاشة تلقائياً", "Auto-delete old clipboard items & screenshot")
                : tr("حذف نصوص الحافظة تلقائياً", "Auto-delete old clipboard items"));
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
                    .setTitle(BuildConfig.OTP_FEATURES_ENABLED
                            ? tr("حذف النصوص ولقطة الشاشة القديمة", "Auto-delete old items & screenshot")
                            : tr("حذف النصوص القديمة", "Auto-delete old items"))
                    .setSingleChoiceItems(labels, sel, (d, which) -> {
                        prefs.setClipRetentionHours(values[which]);
                        refresh.run();
                        d.dismiss();
                    })
                    .show();
        });
        return card;
    }

    private void setAutoCorrectStrongTileEnabled(View tile, boolean enabled) {
        tile.setAlpha(enabled ? 1f : 0.5f);
        if (tile instanceof ViewGroup && ((ViewGroup) tile).getChildCount() > 1) {
            ((ViewGroup) tile).getChildAt(1).setEnabled(enabled);
        }
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
        lp.bottomMargin = dp(6);
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

    private String languageDisplayName(String code) {
        if (KeyboardPreferences.LANG_AR.equals(code)) return tr("العربية", "Arabic");
        if (KeyboardPreferences.LANG_ES.equals(code)) return tr("الإسبانية", "Spanish");
        if (KeyboardPreferences.LANG_HI.equals(code)) return tr("الهندية", "Hindi");
        return tr("الإنجليزية", "English");
    }

    private void setLanguageEnabled(String code, boolean on) {
        List<String> enabled = new ArrayList<>(prefs.getEnabledLanguages());
        if (on) {
            if (!enabled.contains(code)) enabled.add(code);
        } else {
            if (enabled.size() <= 1) return; // always keep at least one language active
            enabled.remove(code);
        }
        prefs.setEnabledLanguages(enabled);
    }

    private void openLanguagesScreen() {
        LinearLayout root = createSubScreenTemplate(tr("اللغة", "Language"));
        fillLanguagesScreen(root);
    }

    private void refreshLanguagesScreen(LinearLayout root) {
        // clearFocus() first works around a known Android framework bug: bulk-removing
        // children with removeViews() while one of them (e.g. a just-touched language
        // card, or one of the enable/disable Switches above it) still holds input focus
        // throws a NullPointerException inside ViewGroup.removeViewsInternal. Left
        // uncaught that bug would re-fire on every redraw frame while this screen stays
        // in its half-rebuilt state, which is exactly what made this freeze for ~3
        // seconds in practice -- so this is also guarded with a one-at-a-time fallback,
        // just in case clearFocus() isn't enough on some device/Android version.
        root.clearFocus();
        try {
            root.removeViews(1, root.getChildCount() - 1);
        } catch (Exception e) {
            AppLogger.log("MainActivity", "refreshLanguagesScreen() removeViews", e);
            while (root.getChildCount() > 1) {
                try {
                    root.removeViewAt(root.getChildCount() - 1);
                } catch (Exception e2) {
                    AppLogger.log("MainActivity", "refreshLanguagesScreen() removeViewAt fallback", e2);
                    break;
                }
            }
        }
        fillLanguagesScreen(root);
    }

    private void fillLanguagesScreen(final LinearLayout root) {

        TextView langModeNote = new TextView(this);
        langModeNote.setText(tr(
                "فعّل لغتين أو أكثر للتبديل بسحب المسافة أو زر 🌐.",
                "Enable two or more languages to switch via space-bar swipe or the 🌐 button."));
        langModeNote.setTextColor(Color.parseColor(dm("#6B7280")));
        langModeNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        langModeNote.setPadding(dp(4), 0, dp(4), dp(12));
        root.addView(langModeNote);

        final List<String> enabled = prefs.getEnabledLanguages();

        for (final String code : KeyboardPreferences.ALL_LANGUAGES) {
            root.addView(createSwitchTile(
                    languageDisplayName(code),
                    tr("", ""),
                    enabled.contains(code),
                    checked -> {
                        setLanguageEnabled(code, checked);
                        refreshLanguagesScreen(root);
                    }));
        }

        boolean multiLangOn = enabled.size() >= 2;

        if (multiLangOn) {
            TextView orderLabel = new TextView(this);
            orderLabel.setText(tr("ترتيب التبديل", "Switch order"));
            orderLabel.setTextColor(Color.parseColor(dm("#1F2937")));
            orderLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
            orderLabel.setPadding(dp(4), dp(10), dp(4), dp(2));
            root.addView(orderLabel);

            TextView orderHint = new TextView(this);
            orderHint.setText(tr("اضغط مطولاً على بطاقة اللغة ثم اسحبها لتغيير ترتيبها",
                    "Press and hold a language card, then drag to reorder it"));
            orderHint.setTextColor(Color.parseColor(dm("#9CA3AF")));
            orderHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            orderHint.setPadding(dp(4), 0, dp(4), dp(8));
            root.addView(orderHint);

            LinearLayout orderContainer = new LinearLayout(this);
            orderContainer.setOrientation(LinearLayout.VERTICAL);
            root.addView(orderContainer);

            for (int i = 0; i < enabled.size(); i++) {
                orderContainer.addView(createLanguageOrderRow(root, orderContainer, enabled.get(i), i));
            }
        }

        root.addView(createSwitchTile(
                tr("التبديل بسحب زر المسافة", "Switch by swiping the space bar"),
                multiLangOn
                        ? tr("فعّاله: اسحب زر المسافة يميناً أو يساراً للتبديل. مطفيه: يظهر زر 🌐 مخصص للتبديل بدل ذلك",
                                "On: swipe the space bar left or right to switch.\nOff: a dedicated 🌐 button appears instead")
                        : tr("يحتاج تفعيل لغتين أو أكثر حتى يشتغل", "Needs two or more languages enabled to work"),
                prefs.isSpaceSwipeLangEnabled(),
                multiLangOn,
                checked -> prefs.setSpaceSwipeLangEnabled(checked)
        ));
    }

    private View createLanguageOrderRow(final LinearLayout root, final LinearLayout container, final String code, final int initialIndex) {
        final LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundedCard(dm("#FFFFFF"), 16));
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        row.setTag(code);
        // Not focusable: the custom OnTouchListener already handles everything this row
        // needs, and an unfocusable view can't pick up a lingering OS-drawn focus/pressed
        // highlight after a rapid rebuild like the one a drop triggers.
        row.setFocusable(false);
        row.setFocusableInTouchMode(false);

        TextView order = new TextView(this);
        order.setText((initialIndex + 1) + "-");
        order.setTextColor(Color.parseColor(dm("#9CA3AF")));
        order.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        order.setPadding(0, 0, dp(10), 0);
        row.addView(order);

        TextView name = new TextView(this);
        name.setText(languageDisplayName(code));
        name.setTextColor(Color.parseColor(dm("#1F2937")));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(name);

        TextView handle = new TextView(this);
        handle.setText("☰");
        handle.setTextColor(Color.parseColor(dm("#9CA3AF")));
        handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        handle.setPadding(dp(10), dp(4), dp(2), dp(4));
        row.addView(handle);

        setupLanguageOrderDrag(root, container, row);

        return row;
    }

    private static void setAncestorsDisallowIntercept(View v, boolean disallow) {
        ViewParent p = v.getParent();
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(disallow);
            p = p.getParent();
        }
    }

    /** Long-press-and-drag reordering for a language card: holding it still for the system's
     *  long-press timeout picks it up (with haptic + elevation feedback), then dragging up or
     *  down moves it to whichever slot the finger is currently over, live-renumbering the "1-,
     *  2-, 3-" labels as it passes other cards. Releasing commits the new order to prefs and
     *  rebuilds the screen. A quick swipe/scroll before the long-press timeout fires is left
     *  alone so it still scrolls the settings page normally instead of starting a drag.
     *
     *  Important: the real view hierarchy (container's children) is never touched while the
     *  gesture is live -- only a virtual order list and each row's translationY. Android cuts
     *  off further touch events to a view that gets removeView()'d/addView()'d back mid-gesture,
     *  which is why an earlier version of this (that physically reordered on every ACTION_MOVE)
     *  only ever worked for the very first card: that's the only row whose desiredIndex never
     *  changes (already at index 0), so it never triggered the live reorder that broke every
     *  other card's touch stream. The actual container is only reordered once, at the end, via
     *  refreshLanguagesScreen -- by which point the gesture is already over. */
    private void setupLanguageOrderDrag(final LinearLayout root, final LinearLayout container, final LinearLayout row) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        final long longPressTimeout = ViewConfiguration.getLongPressTimeout();

        final float[] downRawX = new float[1];
        final float[] downRawY = new float[1];
        final float[] startAbsoluteTop = new float[1];
        final float[] slotHeight = new float[1];
        final int[] homeIndex = new int[1];
        final boolean[] dragging = new boolean[1];
        final boolean[] longPressFired = new boolean[1];
        // Guards against the drop logic (prefs commit + full screen rebuild) ever running
        // more than once per drag session. Diagnostic logging proved that ACTION_UP/CANCEL
        // was re-entering this same case block hundreds of times within a single release
        // (not one slow call -- "refreshLanguagesScreen done" was logged 600+ times in a
        // row, each a few ms apart, together spanning ~2 seconds, which is exactly the
        // "stuck for 2-3 seconds" visual the drop was showing). This flag makes the heavy
        // work idempotent regardless of how many times the event fires.
        final boolean[] dropHandled = new boolean[1];
        final ArrayList<View> virtualOrder = new ArrayList<>();

        final Runnable startDrag = () -> {
            longPressFired[0] = true;
            dragging[0] = true;
            row.setAlpha(0.92f);
            // A real shadow (View.elevation) is deliberately avoided for the "picked up"
            // look: on some devices its GPU shadow layer visibly keeps rendering for a
            // couple of seconds after elevation is reset back to 0 during the view-tree
            // rebuild that follows a drop -- which is exactly what made a just-dropped
            // card look like it was still being held. A background tint gives the same
            // feedback with no shadow layer that can linger.
            row.setBackground(roundedCard(dm("#EAF1FF"), 16));
            row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            setAncestorsDisallowIntercept(row, true);
        };

        row.setOnTouchListener((v, event) -> {
            // Belt-and-suspenders: nothing this listener does should ever be able to crash the
            // whole app. Any unexpected exception bails out of the drag quietly instead.
            try {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: {
                        dragging[0] = false;
                        longPressFired[0] = false;
                        dropHandled[0] = false;
                        downRawX[0] = event.getRawX();
                        downRawY[0] = event.getRawY();
                        slotHeight[0] = row.getHeight() + dp(6);
                        homeIndex[0] = container.indexOfChild(row);
                        startAbsoluteTop[0] = homeIndex[0] * slotHeight[0];
                        virtualOrder.clear();
                        for (int i = 0; i < container.getChildCount(); i++) {
                            virtualOrder.add(container.getChildAt(i));
                        }
                        handler.removeCallbacks(startDrag);
                        handler.postDelayed(startDrag, longPressTimeout);
                        // Claim the gesture from the settings page's ScrollView *now*, not only
                        // once the long-press fires: otherwise the natural finger wobble of a
                        // real hold crosses the ScrollView's own touch slop first and it steals
                        // the gesture to scroll, so the long-press timer never gets to fire.
                        setAncestorsDisallowIntercept(row, true);
                        return true;
                    }

                    case MotionEvent.ACTION_MOVE: {
                        if (!longPressFired[0]) {
                            float dx = event.getRawX() - downRawX[0];
                            float dy = event.getRawY() - downRawY[0];
                            if (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop) {
                                // Moved before the long-press fired -- the user wanted to scroll
                                // the page, not pick up the card. Hand the gesture back.
                                handler.removeCallbacks(startDrag);
                                setAncestorsDisallowIntercept(row, false);
                            }
                            return true;
                        }
                        if (!dragging[0] || slotHeight[0] <= 0f || homeIndex[0] < 0) return true;
                        int childCount = container.getChildCount();
                        if (childCount <= 0) return true;

                        float totalDy = event.getRawY() - downRawY[0];
                        float draggedAbsoluteTop = startAbsoluteTop[0] + totalDy;
                        int lastIndex = childCount - 1;
                        // Clamp to the stack's own top/bottom -- a card already first (or
                        // last) has nowhere to go, so it shouldn't visually float off past
                        // the edge of the list while the finger keeps dragging it that way.
                        float maxTop = lastIndex * slotHeight[0];
                        if (draggedAbsoluteTop < 0f) draggedAbsoluteTop = 0f;
                        if (draggedAbsoluteTop > maxTop) draggedAbsoluteTop = maxTop;
                        int desiredIndex = (int) Math.floor((draggedAbsoluteTop + slotHeight[0] / 2f) / slotHeight[0]);
                        if (desiredIndex < 0) desiredIndex = 0;
                        if (desiredIndex > lastIndex) desiredIndex = lastIndex;

                        int currentVirtualIndex = virtualOrder.indexOf(row);
                        if (currentVirtualIndex < 0) return true;
                        if (desiredIndex != currentVirtualIndex) {
                            virtualOrder.remove(currentVirtualIndex);
                            virtualOrder.add(desiredIndex, row);
                        }

                        // Slide every other card to the slot the virtual reorder puts it in --
                        // this is purely visual (translationY), container's real children and
                        // indices never change until the gesture ends.
                        for (int i = 0; i < childCount; i++) {
                            View sibling = container.getChildAt(i);
                            if (sibling == row) continue;
                            int virtualPos = virtualOrder.indexOf(sibling);
                            if (virtualPos < 0) continue;
                            sibling.setTranslationY((virtualPos - i) * slotHeight[0]);
                            if (sibling instanceof LinearLayout) {
                                LinearLayout siblingRow = (LinearLayout) sibling;
                                if (siblingRow.getChildCount() > 0 && siblingRow.getChildAt(0) instanceof TextView) {
                                    ((TextView) siblingRow.getChildAt(0)).setText((virtualPos + 1) + "-");
                                }
                            }
                        }
                        row.setTranslationY(draggedAbsoluteTop - startAbsoluteTop[0]);
                        int draggedVirtualPos = virtualOrder.indexOf(row);
                        if (row.getChildCount() > 0 && row.getChildAt(0) instanceof TextView) {
                            ((TextView) row.getChildAt(0)).setText((draggedVirtualPos + 1) + "-");
                        }
                        return true;
                    }

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: {
                        handler.removeCallbacks(startDrag);
                        if (dragging[0] && !dropHandled[0]) {
                            dropHandled[0] = true;
                            // Snapshot the new order now, while virtualOrder is still valid.
                            final List<String> order = new ArrayList<>();
                            for (View v2 : virtualOrder) {
                                Object tag = v2.getTag();
                                if (tag instanceof String) order.add((String) tag);
                            }
                            final boolean orderValid = !virtualOrder.isEmpty() && order.size() == virtualOrder.size();
                            // Drop the "picked up" look (tint, alpha, offset) on this row and
                            // every sibling immediately, synchronously, so the card looks
                            // settled right away.
                            row.setTranslationY(0f);
                            row.setAlpha(1f);
                            row.setBackground(roundedCard(dm("#FFFFFF"), 16));
                            for (int i = 0; i < container.getChildCount(); i++) {
                                container.getChildAt(i).setTranslationY(0f);
                            }
                            // Commit the prefs and rebuild the screen on the NEXT frame,
                            // completely off this touch-dispatch call stack. Diagnostic
                            // logging proved that calling refreshLanguagesScreen() (which
                            // tears down and rebuilds the whole row hierarchy) synchronously
                            // from inside this ACTION_UP/CANCEL handler made this exact case
                            // block get re-entered hundreds of times for a single release --
                            // "refreshLanguagesScreen done" was logged 600+ times in a row,
                            // a few ms apart, together spanning roughly two seconds, which is
                            // exactly the "still looks held for 2-3 seconds" symptom. Mutating
                            // the view tree while Android is still mid-dispatch for this
                            // gesture is what triggered the repeated re-entry; posting the
                            // heavy work to run after dispatch has fully finished avoids that,
                            // and dropHandled[0] makes it a no-op even if onTouch does still
                            // fire again afterward.
                            row.post(() -> {
                                if (orderValid) {
                                    prefs.setEnabledLanguages(order);
                                }
                                refreshLanguagesScreen(root);
                            });
                        }
                        dragging[0] = false;
                        longPressFired[0] = false;
                        virtualOrder.clear();
                        return true;
                    }

                    default:
                        return false;
                }
            } catch (Throwable t) {
                AppLogger.log("MainActivity", "setupLanguageOrderDrag()", t);
                handler.removeCallbacks(startDrag);
                dragging[0] = false;
                longPressFired[0] = false;
                virtualOrder.clear();
                try {
                    row.setTranslationY(0f);
                    row.setAlpha(1f);
                    row.setBackground(roundedCard(dm("#FFFFFF"), 16));
                } catch (Throwable ignored) {
                    // row itself may be in a bad state -- nothing more we can safely do.
                }
                return true;
            }
        });
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
                tr(new String[]{"معطّل (عرض كامل)", "جهة اليسار", "جهة اليمين"},
                        new String[]{"Off (full width)", "Left side", "Right side"}),
                prefs.getOneHandedMode(),
                idx -> prefs.setOneHandedMode(idx)));
        root.addView(oneHandCard);
    }

    private void openThemeAndStyleScreen() {
        LinearLayout root = createSubScreenTemplate(tr("المظهر وشكل الكيبورد والحجم", "Theme, key style & size"));
        fillThemeAndStyleScreen(root);
    }

    private void setVibrationSliderEnabled(View card, boolean enabled) {
        card.setAlpha(enabled ? 1f : 0.45f);
        if (card instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) card;
            for (int i = 0; i < g.getChildCount(); i++) g.getChildAt(i).setEnabled(enabled);
        }
    }

    private String vibrationLabel(int ms) {
        return tr("قوة الاهتزاز: ", "Vibration strength: ")
                + (ms == 0 ? tr("افتراضي النظام", "System default") : ms + " ms");
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
                new String[]{"أسود مطفي", "أبيض ساطع", "كحلي داكن", "أخضر غابة", "تلقائي"},
                new String[]{"Matte black", "Clean white", "Midnight slate", "Forest green", "Auto"});
        String[] themeSwatches = new String[]{"#1C1C1E", "#FFFFFF", "#1E2A47", "#1B3126", "#FFFFFF"};
        List<View> themeTiles = new ArrayList<>();
        // Auto first (leftmost), then the fixed themes in their usual order.
        int[] themeOrder = {KeyboardPreferences.THEME_AUTO, 0, 1, 2, 3};
        for (int oi = 0; oi < themeOrder.length; oi++) {
            final int i = themeOrder[oi];
            final int idx = i;
            themeTiles.add(createPreviewTile(themeNames[i], themeSwatches[i], prefs.getRawThemeMode() == i,
                    v -> { prefs.setThemeMode(idx); refreshThemeScreen(root); }, i == KeyboardPreferences.THEME_AUTO));
        }
        addTileGrid(themeCard, themeTiles, 5);
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
                Math.round(prefs.getKeyFontScale() * 100), 100, 50, 150,
                pct -> prefs.setKeyFontScale(pct / 100f),
                pct -> pct + "%"));

        root.addView(buildSizerCard(
                tr("6. ارتفاع الكيبورد العام", "6. Overall keyboard height"),
                Math.round(prefs.getKeyboardScale() * 100), 100, 70, 145,
                pct -> prefs.setKeyboardScale(pct / 100f),
                pct -> pct + "%"));

        root.addView(buildSizerCard(
                tr("7. ارتفاع صف الاقتراحات العلوي", "7. Suggestion bar height"),
                Math.round(prefs.getSuggestionBarScale() * 100), 100, 70, 125,
                pct -> prefs.setSuggestionBarScale(pct / 100f),
                pct -> pct + "%"));

        root.addView(buildSizerCard(
                tr("8. حجم خط الاقتراحات", "8. Suggestion text size"),
                prefs.getSuggestionTextLevel(), 2, 0, 5,
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
        return createPreviewTile(label, colorHex, selected, onClick, false);
    }

    private View createPreviewTile(String label, String colorHex, boolean selected, View.OnClickListener onClick, boolean halfAndHalf) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setOnClickListener(onClick);

        int holderSize = dp(58);
        int circleSize = dp(46);
        FrameLayout swatchHolder = new FrameLayout(this);
        swatchHolder.setLayoutParams(new LinearLayout.LayoutParams(holderSize, holderSize));

        if (selected) {
            // A dark swatch (e.g. the near-black "Matte black" theme) sitting right next to a
            // plain blue ring gave almost no sense of "this one is selected" -- the ring was
            // there, but nothing about it stood out against a dark circle the way it does
            // against a light one. Checking the swatch's own brightness and switching to a
            // lighter, higher-contrast ring color for dark swatches fixes that without changing
            // how light/colorful swatches look.
            int swatchRgb = Color.parseColor(colorHex);
            double luminance = (0.299 * Color.red(swatchRgb) + 0.587 * Color.green(swatchRgb) + 0.114 * Color.blue(swatchRgb)) / 255.0;
            boolean darkSwatch = luminance < 0.35;
            String ringColorHex = darkSwatch ? "#93C5FD" : dm("#2563EB");

            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setColor(Color.TRANSPARENT);
            ring.setStroke(dp(darkSwatch ? 3 : 2), Color.parseColor(ringColorHex));
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
        if (halfAndHalf) {
            // "Auto" swatch: left half white, right half black.
            final int strokeColor = Color.parseColor(dm("#E5E7EB"));
            final float sw = dp(1);
            circle = new View(this) {
                @Override
                protected void onDraw(android.graphics.Canvas c) {
                    float w = getWidth(), h = getHeight(), r = Math.min(w, h) / 2f;
                    android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    android.graphics.Path circlePath = new android.graphics.Path();
                    circlePath.addCircle(w / 2f, h / 2f, r - sw / 2f, android.graphics.Path.Direction.CW);
                    c.save();
                    c.clipPath(circlePath);
                    p.setColor(Color.WHITE);
                    c.drawRect(0, 0, w / 2f, h, p);
                    p.setColor(Color.parseColor("#1C1C1E"));
                    c.drawRect(w / 2f, 0, w, h, p);
                    c.restore();
                    p.setStyle(android.graphics.Paint.Style.STROKE);
                    p.setStrokeWidth(sw);
                    p.setColor(strokeColor);
                    c.drawCircle(w / 2f, h / 2f, r - sw / 2f, p);
                }
            };
        } else {
            circle.setBackground(swatch);
        }
        circle.setElevation(selected ? dp(3) : dp(1));
        FrameLayout.LayoutParams lpCircle = new FrameLayout.LayoutParams(circleSize, circleSize);
        lpCircle.gravity = Gravity.CENTER;
        circle.setLayoutParams(lpCircle);
        swatchHolder.addView(circle);

        if (selected) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(halfAndHalf ? Color.parseColor("#2563EB")
                    : colorHex.equals("#FFFFFF") ? Color.parseColor("#2563EB") : Color.WHITE);
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
        sample.setText("Ab");
        sample.setTypeface(typeface);
        sample.setTextColor(Color.parseColor(dm("#111827")));
        sample.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
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

        // Every tile's mock preview renders in whatever keyboard theme is currently active --
        // against a near-black "Matte black"/"Midnight slate" preview, the usual plain blue
        // selection border and checkmark badge barely stood out. Boosting both to a lighter,
        // higher-contrast blue specifically while a dark theme is selected fixes that without
        // changing how it looks on the light theme.
        boolean darkThemeNow = prefs.getThemeMode() != 1;
        String selectionColorHex = darkThemeNow ? "#93C5FD" : dm("#2563EB");

        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(dp(72), dp(60)));
        holder.setBackground(roundedCard(selected ? dm("#EFF6FF") : dm("#F3F4F6"), 13));
        holder.setElevation(selected ? dp(3) : dp(1));
        holder.addView(new KeyStylePreviewView(this, styleId, prefs.getThemeMode(), prefs.getEnterColor()),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(13));
        frame.setColor(Color.TRANSPARENT);
        frame.setStroke(dp(selected && darkThemeNow ? 3 : selected ? 2 : 1),
                Color.parseColor(selected ? selectionColorHex : dm("#E5E7EB")));
        holder.setForeground(frame);
        if (selected) {
            TextView badge = new TextView(this);
            badge.setText("✓");
            badge.setTextColor(darkThemeNow ? Color.parseColor("#1E293B") : Color.WHITE);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundedCard(selectionColorHex, 7));
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

        final View[] autoCorrectStrongTile = new View[1];
        root.addView(createSwitchTile(
                tr("تصحيح تلقائي صامت عند المسافة", "Silent auto-correct on space"),
                tr("يستبدل الكلمة تلقائياً بأقرب كلمة صحيحة معروفة بمجرد الضغط على مسافة، بدون ما تلمس شيء. اضغط باكسبيس فوراً للتراجع وإرجاع ما كتبته بالضبط. عند الإيقاف يرجع للوضع الحالي: الاقتراح يظهر لك فقط وأنت تختاره يدوياً",
                        "Instantly replaces the word with the closest known correct word the moment you press space — no tap needed. Press backspace right away to undo and restore exactly what you typed. When off, it goes back to the current behavior: the suggestion just shows and you pick it yourself"),
                prefs.isAutoCorrectEnabled(),
                checked -> {
                    prefs.setAutoCorrectEnabled(checked);
                    if (autoCorrectStrongTile[0] != null) setAutoCorrectStrongTileEnabled(autoCorrectStrongTile[0], checked);
                }
        ));

        // Sub-option of the silent auto-correct switch above: only usable while it is on.
        View strongTile = createSwitchTile(
                tr("تصحيح أقوى", "Stronger correction"),
                tr("الوضع الافتراضي يصحح فقط لما يكون التصحيح واضحاً. التشغيل يخلّيه يصحح حتى لما تتقارب الاحتمالات، فيصحح أكثر لكن قد يخطئ أحياناً. اضغط باكسبيس للتراجع",
                        "The default only corrects when the fix is clear. Turning this on also corrects close calls, so it fixes more but may occasionally get it wrong. Press backspace to undo"),
                prefs.isAutoCorrectStrong(),
                prefs.isAutoCorrectEnabled(),
                checked -> prefs.setAutoCorrectStrong(checked)
        );
        ViewGroup.MarginLayoutParams strongLp = (ViewGroup.MarginLayoutParams) strongTile.getLayoutParams();
        strongLp.setMarginStart(dp(18));
        strongTile.setLayoutParams(strongLp);
        autoCorrectStrongTile[0] = strongTile;
        root.addView(strongTile);

        root.addView(createSwitchTile(
                tr("زر الكتابة بالصوت (المايك)", "Voice typing button (mic)"),
                tr("يظهر على يمين صف الاقتراحات؛ اضغطه وتكلم ويكتب الكلام. يحتاج إذن الميكروفون، ويجرب التعرف على الصوت داخل جهازك أول شي، وإذا جهازك ما يدعمه يحتاج اتصالاً بالإنترنت", "Shows on the right of the suggestion row; tap it and speak to type. Needs mic permission; tries on-device speech recognition first, and needs an internet connection if your device doesn't support that"),
                prefs.isVoiceButtonEnabled(),
                checked -> prefs.setVoiceButtonEnabled(checked)
        ));

        if (BuildConfig.OTP_FEATURES_ENABLED) {
            // OTP suggestions need SMS/notification-listener access, which the
            // bkey (lite) build doesn't request at all -- hidden entirely
            // there instead of showing a row that leads nowhere.
            LinearLayout otpEntryCard = createGroupedWhiteCard();
            otpEntryCard.addView(createMenuRow(
                    ModernIconView.ICON_MESSAGE_TEXT,
                    tr("اقتراح رمز التحقق (OTP)", "Verification code (OTP) suggestions"),
                    tr("من الرسائل وواتساب", "From SMS & WhatsApp"),
                    false,
                    this::openOtpSettingsScreen
            ));
            root.addView(otpEntryCard);
        }

        LinearLayout trainCard = createGroupedWhiteCard();
        trainCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        final int indigoA = Color.parseColor("#6366F1"), indigoB = Color.parseColor("#4338CA");
        EditText[] trainOut = new EditText[1];
        trainCard.addView(createFieldPill(ModernIconView.ICON_SPARKLE,
                tr("أدخل جملة هنا لتدريب الكيبورد عليها فوراً...", "Type a sentence here to train the keyboard instantly..."),
                indigoA, trainOut));
        final EditText trainBox = trainOut[0];

        trainCard.addView(createGradientButton(ModernIconView.ICON_SPARKLE,
                tr("حفظ وتدريب التوقعات على هذه الجملة", "Save & train predictions on this sentence"),
                indigoA, indigoB, v -> {
            String s = trainBox.getText().toString().trim();
            if (!s.isEmpty()) {
                predictionEngine.learnFullSentence(s);
                trainBox.setText("");
                Toast.makeText(MainActivity.this, tr("تم حفظ الكلمات وتوقعات الكلمة التالية!", "Words and next-word predictions saved!"), Toast.LENGTH_SHORT).show();
            }
        }));

        final TextView[] learnedCountSubtitleHolder = new TextView[1];

        trainCard.addView(createGradientButton(ModernIconView.ICON_TRASH,
                tr("مسح جميع الكلمات المتعلمة", "Clear all learned words"),
                Color.parseColor("#EF4444"), Color.parseColor("#B91C1C"), v -> {
            predictionEngine.clearAllLearnedData();
            prefs.clearAutoCorrectExceptions();
            if (learnedCountSubtitleHolder[0] != null) {
                learnedCountSubtitleHolder[0].setText(learnedWordsCountSubtitle());
            }
            Toast.makeText(MainActivity.this,
                    tr("تم مسح جميع الكلمات المتعلمة", "All learned words were cleared"),
                    Toast.LENGTH_SHORT).show();
        }));

        root.addView(trainCard);
        root.addView(createMenuRow(
                ModernIconView.ICON_DICTIONARY_BOOK,
                tr("الكلمات المتعلمة", "Learned words"),
                learnedWordsCountSubtitle(),
                false,
                this::openLearnedWordsScreen,
                learnedCountSubtitleHolder));
    }

    private String learnedWordsCountSubtitle() {
        int count = predictionEngine.getLearnedWordCount();
        return tr(count + " كلمة محفوظة • بحث وحذف", count + " saved words • search & delete");
    }

    private Handler downloadToastHandler;
    private Runnable downloadToastRunnable;
    private Toast downloadToast;

    // Toast.LENGTH_SHORT/LENGTH_LONG always auto-dismiss after a couple of
    // seconds, so a single .show() made it look like the download had
    // already stopped while it was still running in the background. This
    // keeps re-showing the same toast every couple of seconds until the
    // download actually finishes (success or failure), so it stays visible
    // the whole time.
    private void startPersistentDownloadToast() {
        stopPersistentDownloadToast();
        downloadToast = Toast.makeText(this, tr("جاري تنزيل التحديث الآن...", "Downloading the update now..."), Toast.LENGTH_LONG);
        downloadToastHandler = new Handler(Looper.getMainLooper());
        downloadToastRunnable = new Runnable() {
            @Override
            public void run() {
                if (downloadToast == null) return;
                downloadToast.show();
                downloadToastHandler.postDelayed(this, 2500);
            }
        };
        downloadToastHandler.post(downloadToastRunnable);
    }

    private void stopPersistentDownloadToast() {
        if (downloadToastHandler != null && downloadToastRunnable != null) {
            downloadToastHandler.removeCallbacks(downloadToastRunnable);
        }
        if (downloadToast != null) {
            downloadToast.cancel();
            downloadToast = null;
        }
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
            startPersistentDownloadToast();
            AppUpdater.downloadAndInstall(getApplicationContext(), success -> {
                stopPersistentDownloadToast();
                if (!success) {
                    Toast.makeText(MainActivity.this,
                            tr("تعذّر التنزيل التلقائي، سيفتح الرابط بالمتصفح بدلاً منه",
                                    "Automatic download failed, opening the link in your browser instead"),
                            Toast.LENGTH_LONG).show();
                    try {
                        String url = "https://github.com/snooptiger9/bkey-keyboard/releases/latest";
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
                "يفحص الكيبورد رسالة SMS أو إشعار واتساب الجديد فقط عند وصوله، ويعرض رمز التحقق في منتصف الاقتراحات لمدة 3 دقائق. كل المعالجة محلية على جهازك فقط ولا تُرسل لأي سيرفر",
                "The keyboard checks only a newly arrived SMS or WhatsApp notification and shows the verification code in the middle suggestion slot for 3 minutes. Everything is processed on your device only — nothing is sent to any server"));
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
                    tr("يفحص الرسالة الجديدة فقط عند وصولها (بدون الوصول لصندوق الرسائل القديم)، ويعرض الرمز في منتصف الاقتراحات لمدة 3 دقائق فقط. يتطلب إذن قراءة الرسائل", "Checks only a newly arrived message (never the old inbox) and shows the code in the middle suggestion slot for 3 minutes. Needs SMS permission"),
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
                    tr("يقرأ إشعارات واتساب فقط (بدون أي تطبيق آخر) بحثاً عن رمز تحقق، ويعرضه في منتصف الاقتراحات لمدة 3 دقائق. كل المعالجة محلية بالجهاز ولا تُرسل لأي سيرفر. يحتاج صلاحية «الوصول للإشعارات» تفعّلها بنفسك من شاشة أندرويد اللي تفتح", "Reads only WhatsApp notifications (no other app) looking for a verification code, and shows it in the middle suggestion slot for 3 minutes. Fully on-device, nothing sent to any server. Needs \"Notification access\", which you enable yourself on the Android screen that opens"),
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
                startPersistentDownloadToast();
                AppUpdater.downloadAndInstall(getApplicationContext(), true, success -> {
                    stopPersistentDownloadToast();
                    if (!success) {
                        Toast.makeText(MainActivity.this,
                                tr("تعذّر التنزيل التلقائي، سيفتح الرابط بالمتصفح بدلاً منه",
                                        "Automatic download failed, opening the link in your browser instead"),
                                Toast.LENGTH_LONG).show();
                        try {
                            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(
                                    "https://github.com/snooptiger9/bkey-keyboard/releases/latest")));
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

        int learned = 0;
        try { learned = predictionEngine.getLearnedWordCount(); } catch (Exception ignored) {}
        root.addView(createStatCard(
                tr("كلمات تعلّمها الكيبورد", "Words learned"),
                tr("اضغط × بجانب أي كلمة لحذفها من القاموس", "Tap × next to any word to remove it from the dictionary"),
                learned, Color.parseColor("#0EA5E9"), Color.parseColor("#2563EB"), 0L));

        final LinearLayout learnedListMount = new LinearLayout(this);
        learnedListMount.setOrientation(LinearLayout.VERTICAL);
        learnedFilter = "";

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);
        searchRow.setBackground(borderedBox(dm("#FFFFFF"), dm("#E5E7EB"), 26));
        searchRow.setPadding(dp(14), dp(2), dp(14), dp(2));
        LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSearch.bottomMargin = dp(12);
        searchRow.setLayoutParams(lpSearch);
        ModernIconView sIc = new ModernIconView(this, ModernIconView.ICON_SEARCH, Color.parseColor(dm("#6B7280")), Color.TRANSPARENT);
        sIc.setStyle(0.9f, 2.0f);
        sIc.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        searchRow.addView(sIc);
        final EditText searchBox = new EditText(this);
        searchBox.setHint(tr("ابحث عن كلمة...", "Search for a word..."));
        searchBox.setHintTextColor(Color.parseColor(dm("#9CA3AF")));
        searchBox.setTextColor(Color.parseColor(dm("#111827")));
        searchBox.setSingleLine(true);
        searchBox.setBackground(null);
        searchBox.setPadding(dp(10), dp(12), dp(4), dp(12));
        searchBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        searchBox.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence cs, int a, int b, int c) {}
            @Override
            public void afterTextChanged(android.text.Editable e) {
                learnedFilter = e.toString().trim();
                populateLearnedWordsView(learnedListMount);
            }
        });
        searchRow.addView(searchBox);
        root.addView(searchRow);

        populateLearnedWordsView(learnedListMount);
        root.addView(learnedListMount);
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
                ? tr("الأكثر استخداماً (" + entries.size() + ")", "Most used (" + entries.size() + ")")
                : tr("نتائج البحث (" + entries.size() + " من " + allEntries.size() + ")", "Search results (" + entries.size() + " of " + allEntries.size() + ")"));
        header.setTextColor(Color.parseColor(dm("#374151")));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        header.setPadding(dp(4), dp(2), dp(4), dp(8));
        container.addView(header);

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(q.isEmpty()
                    ? tr("لا توجد كلمات محفوظة بعد. اكتب جملاً لتظهر هنا.", "No saved words yet. Type some sentences and they'll show up here.")
                    : tr("لا توجد كلمة تطابق بحثك.", "No word matches your search."));
            empty.setTextColor(Color.parseColor(dm("#6B7280")));
            empty.setPadding(dp(4), dp(8), dp(4), dp(8));
            container.addView(empty);
            return;
        }

        int shown = 0;
        for (final PredictionEngine.LearnedEntry entry : entries) {
            if (shown >= 250) break;

            LinearLayout wRow = new LinearLayout(this);
            wRow.setOrientation(LinearLayout.HORIZONTAL);
            wRow.setGravity(Gravity.CENTER_VERTICAL);
            wRow.setBackground(borderedBox(dm("#FFFFFF"), dm("#EEF0F3"), 16));
            wRow.setPadding(dp(14), dp(10), dp(6), dp(10));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            wRow.setLayoutParams(lp);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            TextView tv = new TextView(this);
            tv.setText(entry.word);
            tv.setTextColor(Color.parseColor(dm("#111827")));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            top.addView(tv);
            TextView cnt = new TextView(this);
            cnt.setText("×" + entry.count);
            cnt.setTextColor(Color.parseColor(dm("#1D4ED8")));
            cnt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
            cnt.setTypeface(Typeface.DEFAULT_BOLD);
            cnt.setBackground(roundedCard(dm("#DBEAFE"), 10));
            cnt.setPadding(dp(8), dp(2), dp(8), dp(2));
            LinearLayout.LayoutParams lpCnt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCnt.setMarginStart(dp(8));
            cnt.setLayoutParams(lpCnt);
            top.addView(cnt);
            textBox.addView(top);

            if (!entry.nextSummary.isEmpty()) {
                LinearLayout chips = new LinearLayout(this);
                chips.setOrientation(LinearLayout.HORIZONTAL);
                chips.setPadding(0, dp(6), 0, 0);
                for (String nw : entry.nextSummary.split(" ، ")) {
                    if (nw.trim().isEmpty()) continue;
                    TextView chip = new TextView(this);
                    chip.setText(nw.trim());
                    chip.setSingleLine(true);
                    chip.setTextColor(Color.parseColor(dm("#4B5563")));
                    chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                    chip.setBackground(roundedCard(dm("#F3F4F6"), 10));
                    chip.setPadding(dp(8), dp(2), dp(8), dp(2));
                    LinearLayout.LayoutParams lpChip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    lpChip.setMarginEnd(dp(6));
                    chip.setLayoutParams(lpChip);
                    chips.addView(chip);
                }
                textBox.addView(chips);
            }

            ModernIconView del = new ModernIconView(this, ModernIconView.ICON_CLOSE,
                    Color.parseColor(dm("#DC2626")), Color.TRANSPARENT);
            del.setStyle(0.7f, 1.9f);
            del.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
            del.setOnClickListener(v -> {
                predictionEngine.removeLearnedWord(entry.word);
                populateLearnedWordsView(container);
            });

            wRow.addView(textBox);
            wRow.addView(del);
            container.addView(wRow);

            if (shown < 10) {
                // Soft staggered entrance for the first rows only.
                wRow.setAlpha(0f);
                wRow.setTranslationY(dp(10));
                wRow.animate().alpha(1f).translationY(0f).setStartDelay(60L * shown).setDuration(260).start();
            }
            shown++;
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
        LinearLayout sizeCard = new LinearLayout(this);
        sizeCard.setOrientation(LinearLayout.VERTICAL);
        sizeCard.setPadding(dp(18), dp(14), dp(18), dp(10));
        GradientDrawable sizeBg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#FACC15"), Color.parseColor("#FB923C")});
        sizeBg.setCornerRadius(dp(22));
        sizeCard.setBackground(sizeBg);
        LinearLayout.LayoutParams lpSize = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSize.bottomMargin = dp(10);
        sizeCard.setLayoutParams(lpSize);

        TextView sizeLabel = new TextView(this);
        sizeLabel.setText(tr("حجم الإيموجي في القائمة", "Emoji size in the list"));
        sizeLabel.setTextColor(Color.argb(230, 255, 255, 255));
        sizeLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        sizeLabel.setTypeface(Typeface.DEFAULT_BOLD);
        sizeCard.addView(sizeLabel);

        final TextView sizeNum = new TextView(this);
        sizeNum.setText(String.valueOf(prefs.getEmojiSizeSp()));
        sizeNum.setTextColor(Color.WHITE);
        sizeNum.setTextSize(TypedValue.COMPLEX_UNIT_SP, 38f);
        sizeNum.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        sizeNum.setIncludeFontPadding(false);
        sizeNum.setPadding(0, dp(2), 0, dp(6));
        sizeCard.addView(sizeNum);

        final TextView preview = new TextView(this);
        preview.setText("😀 😍 👍 🔥 🌙");
        preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getEmojiSizeSp());
        preview.setGravity(Gravity.CENTER);
        preview.setPadding(dp(8), dp(6), dp(8), dp(6));
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setColor(Color.argb(46, 255, 255, 255));
        previewBg.setCornerRadius(dp(16));
        preview.setBackground(previewBg);
        sizeCard.addView(preview);

        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(18);
        sizeSeek.setProgress(prefs.getEmojiSizeSp() - 16);
        sizeSeek.setPadding(dp(8), dp(10), dp(8), dp(2));
        android.content.res.ColorStateList white = android.content.res.ColorStateList.valueOf(Color.WHITE);
        sizeSeek.setProgressTintList(white);
        sizeSeek.setThumbTintList(white);
        sizeSeek.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.argb(90, 255, 255, 255)));
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int sp = 16 + progress;
                prefs.setEmojiSizeSp(sp);
                sizeNum.setText(String.valueOf(sp));
                preview.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sizeCard.addView(sizeSeek);
        root.addView(sizeCard);

        View clearCard = createActionCard(ModernIconView.ICON_TRASH,
                tr("مسح قسم «مستخدم حديثاً»", "Clear \"Recently used\" section"),
                tr("يحذف الإيموجي الأخيرة من اللوحة", "Removes the recent emojis from the panel"),
                Color.parseColor("#F43F5E"), Color.parseColor("#DC2626"), 120L, v -> {
                    prefs.clearRecentEmojis();
                    android.widget.Toast.makeText(this, tr("تم مسح الإيموجي الأخيرة", "Recent emojis cleared"), android.widget.Toast.LENGTH_SHORT).show();
                });
        clearCard.setPadding(dp(14), dp(10), dp(14), dp(10));
        root.addView(clearCard);
        // The old "Floating capsules color" picker (Black / Light gray) used to live here.
        // Its default (black) was nearly invisible on the Matte Black and Midnight Slate
        // themes' own dark background, so the floating ABC/Delete capsules now use a fixed
        // color automatically chosen per theme instead of a user-picked one (see
        // floatingPillC() in BlackBerryImeService.java): a fixed light gray on the two dark
        // themes, and the same style as the back-arrow buttons in the clipboard/emoji
        // drawers on the white theme.
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

        // Swipe down can only do one job: copy/paste OR open/close the symbols layout. Turning one
        // on switches the other one off (the preference setters do it, these switches mirror it).
        final ModernToggleSwitch[] downSwitches = new ModernToggleSwitch[2];
        View copyPasteTile = createSwitchTile(
                tr("السحب للأسفل للنسخ واللصق", "Swipe down to copy/paste"),
                tr("اسحب للأسفل فوق الحروف لنسخ النص المحدد، وبعد النسخ تتحول السحبة للأسفل إلى لصق لمدة دقيقة ثم ترجع نسخ. تشغيله يطفي خيار السحب للأسفل للرموز",
                        "Swipe down over the keys to copy selected text; after copying, swipe-down turns into paste for one minute, then reverts to copy. Turning it on turns off swipe down for symbols"),
                prefs.isSwipeDownCopyPasteEnabled(),
                checked -> {
                    prefs.setSwipeDownCopyPasteEnabled(checked);
                    if (checked && downSwitches[1] != null) downSwitches[1].setChecked(false);
                }
        );
        View symbolsTile = createSwitchTile(
                tr("السحب للأسفل لقائمة الرموز", "Swipe down for the symbols layout"),
                tr("اسحب للأسفل فوق الحروف لفتح قائمة الرموز للغة المفتوحة، واسحب للأسفل وأنت في الرموز للرجوع للحروف. يعمل لكل لغة على حدة. تشغيله يطفي خيار السحب للأسفل للنسخ واللصق",
                        "Swipe down over the keys to open the symbols layout of the language you are typing in, and swipe down again on symbols to go back to the letters. Works separately for every language. Turning it on turns off swipe down to copy/paste"),
                prefs.isSwipeDownSymbolsEnabled(),
                checked -> {
                    prefs.setSwipeDownSymbolsEnabled(checked);
                    if (checked && downSwitches[0] != null) downSwitches[0].setChecked(false);
                }
        );
        if (copyPasteTile instanceof ViewGroup && ((ViewGroup) copyPasteTile).getChildCount() > 1
                && ((ViewGroup) copyPasteTile).getChildAt(1) instanceof ModernToggleSwitch) {
            downSwitches[0] = (ModernToggleSwitch) ((ViewGroup) copyPasteTile).getChildAt(1);
        }
        if (symbolsTile instanceof ViewGroup && ((ViewGroup) symbolsTile).getChildCount() > 1
                && ((ViewGroup) symbolsTile).getChildAt(1) instanceof ModernToggleSwitch) {
            downSwitches[1] = (ModernToggleSwitch) ((ViewGroup) symbolsTile).getChildAt(1);
        }
        root.addView(copyPasteTile);
        root.addView(symbolsTile);

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

    /** A big animated number card: count-up, gentle slide-in, and a colored gradient. */
    private View createStatCard(String label, String caption, final int target, int colorFrom, int colorTo, long delayMs) {
        return createStatCard(label, caption, target, colorFrom, colorTo, delayMs, "");
    }

    private View createStatCard(String label, String caption, final int target, int colorFrom, int colorTo, long delayMs, final String suffix) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(22));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{colorFrom, colorTo});
        bg.setCornerRadius(dp(26));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);

        TextView lab = new TextView(this);
        lab.setText(label);
        lab.setTextColor(Color.argb(230, 255, 255, 255));
        lab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(lab);

        final TextView num = new TextView(this);
        num.setText("0");
        num.setTextColor(Color.WHITE);
        num.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f);
        num.setTypeface(Typeface.create("sans-serif-light", Typeface.BOLD));
        num.setIncludeFontPadding(false);
        num.setPadding(0, dp(6), 0, dp(4));
        card.addView(num);

        TextView cap = new TextView(this);
        cap.setText(caption);
        cap.setTextColor(Color.argb(200, 255, 255, 255));
        cap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        card.addView(cap);

        // Entrance: slide up + fade in, then the number counts up. Plain View/ValueAnimator only.
        card.setAlpha(0f);
        card.setTranslationY(dp(24));
        card.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(0, Math.max(0, target));
        va.setStartDelay(delayMs + 120);
        va.setDuration(target == 0 ? 1 : 1000);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        va.addUpdateListener(a -> num.setText(java.text.NumberFormat.getInstance(java.util.Locale.US)
                .format((Integer) a.getAnimatedValue()) + suffix));
        va.start();
        return card;
    }

    private static final int REQ_BACKUP_SAVE = 7101;
    private static final int REQ_BACKUP_OPEN = 7102;
    private static final int BACKUP_MAX_BYTES = 4 * 1024 * 1024;

    private View createActionCard(int iconType, String title, String caption, int colorFrom, int colorTo, long delayMs, View.OnClickListener onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{colorFrom, colorTo});
        bg.setCornerRadius(dp(22));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        card.setLayoutParams(lp);

        ModernIconView ic = new ModernIconView(this, iconType, Color.WHITE, Color.argb(48, 255, 255, 255));
        ic.setStyle(0.7f, 2.0f);
        ic.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        card.addView(ic);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lpT = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lpT.setMarginStart(dp(14));
        texts.setLayoutParams(lpT);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16.5f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        texts.addView(t);
        TextView c = new TextView(this);
        c.setText(caption);
        c.setTextColor(Color.argb(215, 255, 255, 255));
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        c.setPadding(0, dp(3), 0, 0);
        texts.addView(c);
        card.addView(texts);

        card.setOnClickListener(onClick);
        card.setAlpha(0f);
        card.setTranslationY(dp(20));
        card.animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(380)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        return card;
    }

    private void openBackupScreen() {
        LinearLayout root = createSubScreenTemplate(tr("النسخ الاحتياطي والاستعادة", "Backup & restore"));

        int learned = 0;
        try { learned = predictionEngine.getLearnedWordCount(); } catch (Exception ignored) {}
        int shortcuts = prefs.getShortcuts().size();

        root.addView(createStatCard(
                tr("كلمات في النسخة", "Words in your backup"),
                tr("هذه الكلمات ستُحفظ في الملف", "These words will be saved to the file"),
                learned, Color.parseColor("#F59E0B"), Color.parseColor("#EA580C"), 0L));
        root.addView(createStatCard(
                tr("اختصارات الكلمات", "Word shortcuts"),
                tr("تُحفظ معها في نفس الملف", "Saved in the same file"),
                shortcuts, Color.parseColor("#8B5CF6"), Color.parseColor("#6D28D9"), 140L));

        root.addView(createActionCard(ModernIconView.ICON_BACKUP,
                tr("حفظ نسخة احتياطية", "Back up now"),
                tr("ينشئ ملف JSON تختار مكانه", "Creates a JSON file in a place you choose"),
                Color.parseColor("#2563EB"), Color.parseColor("#1D4ED8"), 280L, v -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("application/json");
                        i.putExtra(Intent.EXTRA_TITLE, "bkey-backup-"
                                + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date()) + ".json");
                        startActivityForResult(i, REQ_BACKUP_SAVE);
                    } catch (Exception e) {
                        AppLogger.log("MainActivity", "openBackupScreen() save", e);
                        Toast.makeText(this, tr("تعذّر فتح منتقي الملفات", "Couldn't open the file picker"), Toast.LENGTH_LONG).show();
                    }
                }));
        root.addView(createActionCard(ModernIconView.ICON_REFRESH_UPDATE,
                tr("استعادة من ملف", "Restore from a file"),
                tr("يدمج الكلمات دون حذف ما عندك الآن", "Merges the words without deleting what you have now"),
                Color.parseColor("#10B981"), Color.parseColor("#047857"), 400L, v -> {
                    try {
                        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        i.setType("*/*");
                        startActivityForResult(i, REQ_BACKUP_OPEN);
                    } catch (Exception e) {
                        AppLogger.log("MainActivity", "openBackupScreen() open", e);
                        Toast.makeText(this, tr("تعذّر فتح منتقي الملفات", "Couldn't open the file picker"), Toast.LENGTH_LONG).show();
                    }
                }));

        TextView note = new TextView(this);
        note.setText(tr("الملف نصي ويحوي كلماتك بدون تشفير، فاحفظه في مكان خاص. الاستعادة تضيف فقط ولا تحذف شيئاً.",
                "The file is plain text and holds your words unencrypted, so keep it somewhere private. Restoring only adds; it never deletes anything."));
        note.setTextColor(Color.parseColor(dm("#6B7280")));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        note.setPadding(dp(6), dp(4), dp(6), 0);
        root.addView(note);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        if (requestCode == REQ_BACKUP_SAVE) {
            try {
                org.json.JSONObject out = new org.json.JSONObject();
                out.put("format", "bkey-backup");
                out.put("version", 1);
                out.put("createdAt", System.currentTimeMillis());
                out.put("learned", predictionEngine.exportBackup());
                org.json.JSONObject sc = new org.json.JSONObject();
                for (java.util.Map.Entry<String, String> e : prefs.getShortcuts().entrySet()) sc.put(e.getKey(), e.getValue());
                out.put("shortcuts", sc);
                try (java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt")) {
                    if (os == null) throw new java.io.IOException("no stream");
                    os.write(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                Toast.makeText(this, tr("تم حفظ النسخة الاحتياطية", "Backup saved"), Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                AppLogger.log("MainActivity", "backup save", e);
                Toast.makeText(this, tr("تعذّر حفظ النسخة الاحتياطية", "Couldn't save the backup"), Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_BACKUP_OPEN) {
            try {
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is == null) throw new java.io.IOException("no stream");
                    byte[] buf = new byte[8192];
                    int n, total = 0;
                    while ((n = is.read(buf)) > 0) {
                        total += n;
                        if (total > BACKUP_MAX_BYTES) throw new java.io.IOException("too large");
                        bos.write(buf, 0, n);
                    }
                }
                org.json.JSONObject in = new org.json.JSONObject(new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                if (!"bkey-backup".equals(in.optString("format")) || in.optInt("version", 0) != 1) {
                    Toast.makeText(this, tr("هذا ليس ملف نسخة احتياطية من bkey", "This isn't a bkey backup file"), Toast.LENGTH_LONG).show();
                    return;
                }
                int words = predictionEngine.importBackup(in.optJSONObject("learned"));
                int sc = 0;
                org.json.JSONObject shortcuts = in.optJSONObject("shortcuts");
                if (shortcuts != null) {
                    java.util.Iterator<String> it = shortcuts.keys();
                    while (it.hasNext() && sc < 500) {
                        String k = it.next();
                        String v = shortcuts.optString(k, "");
                        if (k.isEmpty() || k.length() > 40 || v.isEmpty() || v.length() > 500) continue;
                        prefs.addShortcut(k, v);
                        sc++;
                    }
                }
                Toast.makeText(this, tr("تمت الاستعادة: " + words + " كلمة و" + sc + " اختصار",
                        "Restored " + words + " words and " + sc + " shortcuts"), Toast.LENGTH_LONG).show();
                openBackupScreen();
            } catch (Exception e) {
                AppLogger.log("MainActivity", "backup restore", e);
                Toast.makeText(this, tr("تعذّرت الاستعادة: الملف غير صالح", "Couldn't restore: the file isn't valid"), Toast.LENGTH_LONG).show();
            }
        }
    }

    private View createLanguageShareCard(int totalWords) {
        String[] codes = {KeyboardPreferences.LANG_AR, KeyboardPreferences.LANG_EN, KeyboardPreferences.LANG_ES, KeyboardPreferences.LANG_HI};
        String[] names = tr(new String[]{"العربية", "الإنجليزية", "الإسبانية", "الهندية"}, new String[]{"Arabic", "English", "Spanish", "Hindi"});
        int[] colors = {Color.parseColor("#10B981"), Color.parseColor("#3B82F6"), Color.parseColor("#F59E0B"), Color.parseColor("#EC4899")};
        int sum = 0;
        int[] counts = new int[4];
        for (int i = 0; i < 4; i++) { counts[i] = prefs.getStat("stat_lang_" + codes[i]); sum += counts[i]; }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(22), dp(22), dp(22), dp(20));
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{Color.parseColor("#475569"), Color.parseColor("#1E293B")});
        bg.setCornerRadius(dp(26));
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(14);
        card.setLayoutParams(lp);

        TextView lab = new TextView(this);
        lab.setText(tr("الكتابة بكل لغة", "Words by language"));
        lab.setTextColor(Color.argb(230, 255, 255, 255));
        lab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        lab.setTypeface(Typeface.DEFAULT_BOLD);
        lab.setPadding(0, 0, 0, dp(10));
        card.addView(lab);

        if (sum == 0) {
            TextView empty = new TextView(this);
            empty.setText(tr("ابدأ الكتابة لتظهر النسب هنا", "Start typing and the shares will show here"));
            empty.setTextColor(Color.argb(200, 255, 255, 255));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            card.addView(empty);
        }
        for (int i = 0; i < 4 && sum > 0; i++) {
            if (counts[i] == 0) continue;
            final int pct = Math.round(counts[i] * 100f / sum);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpRow.bottomMargin = dp(8);
            row.setLayoutParams(lpRow);

            TextView name = new TextView(this);
            name.setText(names[i]);
            name.setTextColor(Color.WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            name.setLayoutParams(new LinearLayout.LayoutParams(dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(name);

            FrameLayout track = new FrameLayout(this);
            GradientDrawable tb = new GradientDrawable();
            tb.setCornerRadius(dp(6));
            tb.setColor(Color.argb(50, 255, 255, 255));
            track.setBackground(tb);
            track.setLayoutParams(new LinearLayout.LayoutParams(0, dp(12), 1f));
            final View fill = new View(this);
            GradientDrawable fb = new GradientDrawable();
            fb.setCornerRadius(dp(6));
            fb.setColor(colors[i]);
            fill.setBackground(fb);
            fill.setLayoutParams(new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
            track.addView(fill);
            track.post(() -> {
                final int full = Math.max(1, track.getWidth() * pct / 100);
                android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(0, full);
                va.setDuration(900);
                va.setStartDelay(900);
                va.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
                va.addUpdateListener(a -> {
                    ViewGroup.LayoutParams flp = fill.getLayoutParams();
                    flp.width = (Integer) a.getAnimatedValue();
                    fill.setLayoutParams(flp);
                });
                va.start();
            });
            row.addView(track);

            TextView pv = new TextView(this);
            pv.setText(pct + "%");
            pv.setTextColor(Color.WHITE);
            pv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
            pv.setGravity(Gravity.END);
            pv.setLayoutParams(new LinearLayout.LayoutParams(dp(44), ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(pv);
            card.addView(row);
        }

        card.setAlpha(0f);
        card.setTranslationY(dp(24));
        card.animate().alpha(1f).translationY(0f).setStartDelay(900L).setDuration(420)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        return card;
    }

    private void openStatsScreen() {
        LinearLayout root = createSubScreenTemplate(tr("إحصائيات الكتابة", "Typing stats"));

        int learned = 0;
        try { learned = predictionEngine.getLearnedWordCount(); } catch (Exception ignored) {}
        int flicks = prefs.getFlickWordCount();

        root.addView(createStatCard(
                tr("كلمات تعلّمها الكيبورد", "Words learned"),
                tr("تتزايد مع كتابتك، وتبقى على جهازك فقط", "Grows as you type, and stays on your device only"),
                learned, Color.parseColor("#3B6CF6"), Color.parseColor("#6D4BF0"), 0L));
        root.addView(createStatCard(
                tr("كلمات اخترتها بالسحب لأعلى", "Words picked by swiping up"),
                tr("كل كلمة مقترحة سحبتها من شريط الاقتراحات", "Every suggestion you swiped up from the suggestion bar"),
                flicks, Color.parseColor("#12B981"), Color.parseColor("#0E8F8F"), 180L));

        int totalWords = Math.max(prefs.getStat("stat_words_total"), flicks);
        int swipeShare = totalWords == 0 ? 0 : Math.min(100, Math.round(flicks * 100f / totalWords));
        root.addView(createStatCard(
                tr("نسبة كلماتك بالسحب", "Share of words by swiping"),
                tr("من كل الكلمات التي كتبتها منذ هذا التحديث", "Of all the words you wrote since this update"),
                swipeShare, Color.parseColor("#EC4899"), Color.parseColor("#9D174D"), 360L, "%"));
        root.addView(createStatCard(
                tr("ضغطات وفّرها السحب (تقريباً)", "Taps saved by swiping (approx.)"),
                tr("الحروف التي لم تضطر لكتابتها بفضل الاقتراحات", "Letters you did not have to type thanks to suggestions"),
                prefs.getStat("stat_saved_keys"), Color.parseColor("#06B6D4"), Color.parseColor("#0369A1"), 540L));
        root.addView(createStatCard(
                tr("كلمات حذفتها بسحبة واحدة", "Words deleted with one swipe"),
                tr("كل مرة حذفت فيها كلمة كاملة بالسحب الجانبي", "Each time you removed a whole word with the sideways swipe"),
                prefs.getStat("stat_swipe_deletes"), Color.parseColor("#F97316"), Color.parseColor("#B91C1C"), 720L));
        root.addView(createLanguageShareCard(totalWords));

        TextView note = new TextView(this);
        note.setText(tr("هذه أعداد فقط، لا يُحفظ أي نص مما تكتبه في الإحصائيات.", "These are counts only; no text you type is stored in the stats."));
        note.setTextColor(Color.parseColor(dm("#6B7280")));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        note.setPadding(dp(6), dp(4), dp(6), 0);
        root.addView(note);
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

        final View[] vibCardRef = new View[1];
        root.addView(createSwitchTile(
                tr("تفعيل الاهتزاز عند اللمس", "Enable vibration on touch"),
                tr("اهتزاز لمسي خفيف مع الضغط والسحب", "A light haptic buzz on key press and swipe"),
                prefs.isVibrationEnabled(),
                checked -> {
                    prefs.setVibrationEnabled(checked);
                    if (vibCardRef[0] != null) setVibrationSliderEnabled(vibCardRef[0], checked);
                }
        ));

        LinearLayout vibCard = createGroupedWhiteCard();
        vibCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView vibLabel = new TextView(this);
        vibLabel.setTextColor(Color.parseColor(dm("#111827")));
        vibLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        vibLabel.setTypeface(Typeface.DEFAULT_BOLD);
        vibLabel.setText(vibrationLabel(prefs.getVibrationMs()));
        vibCard.addView(vibLabel);
        SeekBar vibSeek = new SeekBar(this);
        vibSeek.setMax(46); // 0 = system default, 1..46 = 5..50 ms
        vibSeek.setProgress(prefs.getVibrationMs() == 0 ? 0 : prefs.getVibrationMs() - 4);
        vibSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        vibSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int ms = progress == 0 ? 0 : progress + 4;
                prefs.setVibrationMs(ms);
                vibLabel.setText(vibrationLabel(ms));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int ms = prefs.getVibrationMs();
                try {
                    android.os.Vibrator vib = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);
                    if (vib != null && vib.hasVibrator() && ms > 0) {
                        if (android.os.Build.VERSION.SDK_INT >= 26) vib.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                        else vib.vibrate(ms);
                    }
                } catch (Exception ignored) {}
            }
        });
        vibCard.addView(vibSeek);
        root.addView(vibCard);
        vibCardRef[0] = vibCard;
        setVibrationSliderEnabled(vibCard, prefs.isVibrationEnabled());

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
        // Closer to the swipe-tone card right below it than the default card spacing --
        // the two belong together (steps 1 and 2 of the same "pick your tones" task).
        ((LinearLayout.LayoutParams) keySoundCard.getLayoutParams()).bottomMargin = dp(6);
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

        if (BuildConfig.OTP_FEATURES_ENABLED) {
            // Screenshot-to-clipboard needs photos/storage permission, which
            // the bkey (lite) build intentionally doesn't request -- so this
            // one stays Plus-only.
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
        }

        // The copied-text suggestion chip only reads the system clipboard
        // (ClipboardManager), which needs no special permission -- so unlike
        // the screenshot one above, it's available in bkey too.
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

        final int tealA = Color.parseColor("#14B8A6"), tealB = Color.parseColor("#0F766E");
        EditText[] o1 = new EditText[1], o2 = new EditText[1];
        card.addView(createFieldPill(ModernIconView.ICON_BOLT,
                tr("الاختصار (مثال: سع)", "Shortcut (e.g. omw)"), tealA, o1));
        final EditText shortInput = o1[0];
        LinearLayout phrasePill = createFieldPill(ModernIconView.ICON_MESSAGE_TEXT,
                tr("العبارة الكاملة (مثال: السلام عليكم ورحمة الله وبركاته)", "Full phrase (e.g. on my way)"), tealA, o2);
        LinearLayout.LayoutParams lpPh = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpPh.topMargin = dp(8);
        phrasePill.setLayoutParams(lpPh);
        card.addView(phrasePill);
        final EditText phraseInput = o2[0];

        final LinearLayout scListMount = new LinearLayout(this);
        scListMount.setOrientation(LinearLayout.VERTICAL);
        scListMount.setPadding(0, dp(12), 0, 0);

        card.addView(createGradientButton(ModernIconView.ICON_BOLT,
                tr("حفظ الاختصار", "Save shortcut"), tealA, tealB, v -> {
            String k = shortInput.getText().toString().trim();
            String val = phraseInput.getText().toString().trim();
            if (!k.isEmpty() && !val.isEmpty()) {
                prefs.addShortcut(k, val);
                shortInput.setText("");
                phraseInput.setText("");
                populateShortcutsList(scListMount);
            }
        }));
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
                    Color.parseColor(dm("#9CA3AF")), Color.TRANSPARENT);
            del.setStyle(0.85f, 1.5f);
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
