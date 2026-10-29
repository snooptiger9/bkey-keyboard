package com.bbgboard.keyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
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

    // ===== تذكّر مكان السكرول: الرجوع يعيدك لنفس الموضع في الشاشة السابقة =====
    private final java.util.ArrayDeque<Integer> scrollStack = new java.util.ArrayDeque<>();
    private boolean returningBack = false;
    private int pendingScrollY = 0;
    private Runnable currentBack = null;

    private int currentScrollY() {
        View v = mainFrame != null && mainFrame.getChildCount() > 0 ? mainFrame.getChildAt(0) : null;
        return v instanceof ScrollView ? v.getScrollY() : 0;
    }

    /** إعادة فتح نفس الشاشة بعد تغيير إعداد، بنفس موضع السكرول وبدون إضافة مدخل جديد لسجل الرجوع. */
    private void reopenKeepingScroll(Runnable open) {
        pendingScrollY = currentScrollY();
        returningBack = true;
        open.run();
    }

    private void goBack(Runnable parent) {
        pendingScrollY = scrollStack.isEmpty() ? 0 : scrollStack.pop();
        returningBack = true;
        parent.run();
    }

    private void restoreScroll(final ScrollView scroll) {
        final int y = pendingScrollY;
        pendingScrollY = 0;
        returningBack = false;
        if (y > 0) scroll.post(() -> scroll.scrollTo(0, y));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** true إذا كانت لغة الجهاز الحالية عربية، لعرض كل نص الإعدادات بلغة واحدة فقط بدل الاثنتين معاً. */
    private boolean isDeviceArabic() {
        String lang = java.util.Locale.getDefault().getLanguage();
        return lang != null && lang.toLowerCase(java.util.Locale.ROOT).startsWith("ar");
    }

    /** يرجع النص العربي أو الإنجليزي حسب لغة الجهاز فقط. */
    private String tr(String ar, String en) {
        return isDeviceArabic() ? ar : en;
    }

    private String[] tr(String[] ar, String[] en) {
        return isDeviceArabic() ? ar : en;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new KeyboardPreferences(this);
        soundController = new SoundController(this, prefs);
        predictionEngine = new PredictionEngine(this, prefs);
        clipboardRepo = new ClipboardRepository(this);

        mainFrame = new FrameLayout(this);
        mainFrame.setBackgroundColor(Color.parseColor("#F2F4F8"));
        mainFrame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        showHomeGboardSettingsMenu();
        setContentView(mainFrame);
    }

    @Override
    public void onBackPressed() {
        if (mainFrame != null && mainFrame.getChildCount() > 0 && mainFrame.getChildAt(0) != homeMenuView) {
            goBack(currentBack != null ? currentBack : this::showHomeGboardSettingsMenu);
            return;
        }
        super.onBackPressed();
    }

    private void showHomeGboardSettingsMenu() {
        mainFrame.removeAllViews();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#F2F4F8"));
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(22), dp(16), dp(36));

        root.setLayoutDirection(isDeviceArabic() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        TextView pageTitle = new TextView(this);
        pageTitle.setText(tr("إعدادات الكيبورد", "Keyboard settings"));
        pageTitle.setTextColor(Color.parseColor("#1F2937"));
        pageTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f);
        pageTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        pageTitle.setPadding(dp(6), dp(6), dp(6), dp(16));
        root.addView(pageTitle);

        LinearLayout quickSetupCard = new LinearLayout(this);
        quickSetupCard.setOrientation(LinearLayout.VERTICAL);
        quickSetupCard.setBackground(roundedCard("#FFFFFF", 20));
        quickSetupCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lpSetup = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpSetup.bottomMargin = dp(16);
        quickSetupCard.setLayoutParams(lpSetup);

        // الخطوتان مفصولتان كل واحدة بصفها الكامل مع أيقونة حديثة، بدل زرين متلاصقين جنباً إلى جنب
        quickSetupCard.addView(createSetupStepRow(
                ModernIconView.ICON_BACK_KEYBOARD, "#059669",
                tr("1. تفعيل الكيبورد", "1. Enable the keyboard"),
                tr("يفتح إعدادات لوحات المفاتيح في النظام", "Opens the system's keyboard list"),
                v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        ));
        LinearLayout.LayoutParams lpGap = (LinearLayout.LayoutParams) quickSetupCard.getChildAt(0).getLayoutParams();
        lpGap.bottomMargin = dp(10);
        quickSetupCard.addView(createSetupStepRow(
                ModernIconView.ICON_SETTINGS_GEAR, "#2563EB",
                tr("2. اختيار الكيبورد", "2. Switch to it"),
                tr("يفتح قائمة اختيار لوحة المفاتيح الحالية", "Opens the input method picker"),
                v -> {
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showInputMethodPicker();
                }
        ));

        EditText quickTestEdit = new EditText(this);
        quickTestEdit.setHint(tr("اضغط هنا لتجربة الكيبورد والبوب أب وأصوات السحب...", "Tap here to try the keyboard, popups and swipe sounds..."));
        quickTestEdit.setHintTextColor(Color.parseColor("#9CA3AF"));
        quickTestEdit.setTextColor(Color.parseColor("#111827"));
        quickTestEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        quickTestEdit.setBackground(borderedBox("#F9FAFB", "#E5E7EB", 12));
        quickTestEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        LinearLayout.LayoutParams lpQt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lpQt.topMargin = dp(10);
        quickTestEdit.setLayoutParams(lpQt);
        quickSetupCard.addView(quickTestEdit);
        root.addView(quickSetupCard);

        // المجموعة 1
        LinearLayout group1 = createGroupedWhiteCard();
        group1.addView(createMenuRow(
                ModernIconView.ICON_GLOBE_LANG,
                tr("اللغات وتوزيعة الحروف", "Languages & layout"),
                tr("العربية والإنجليزية • بوب أب الضغط المطول", "Arabic & English • long-press popup"),
                true,
                this::openLanguagesScreen
        ));
        group1.addView(createMenuRow(
                ModernIconView.ICON_THEME_PALETTE,
                tr("المظهر وشكل الكيبورد", "Theme & key style"),
                getThemeAndStyleSummary(),
                false,
                this::openThemeAndStyleScreen
        ));
        root.addView(group1);

        // المجموعة 2
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
                tr("التفضيلات وأحجام الكيبورد", "Preferences & sizes"),
                tr("حجم الحروف • ارتفاع الكيبورد • صف الأرقام • وضع التخفي • اليد الواحدة",
                        "Font size • keyboard height • number row • incognito • one-handed mode"),
                false,
                this::openPreferencesScreen
        ));
        root.addView(group2);

        // المجموعة 3
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

        scroll.addView(root);
        homeMenuView = scroll;
        mainFrame.addView(scroll);
        currentBack = null;
        restoreScroll(scroll);
    }

    private String getThemeAndStyleSummary() {
        String th = prefs.getThemeMode() == KeyboardPreferences.THEME_GBOARD_WHITE ? tr("السمة البيضاء", "White theme")
                : prefs.getThemeMode() == KeyboardPreferences.THEME_MIDNIGHT_SLATE ? tr("السمة الكحلية", "Midnight theme")
                : tr("سمة بلاك بيري السوداء", "BlackBerry black theme");
        String[] styleSummariesAr = {"شكل بلاك بيري الكلاسيكي", "شكل Gboard الحديث", "شكل كبسولات عصرية",
                "شكل Gboard ماتيريال", "شكل Gboard بدون إطار", "شكل كبسولات عصرية", "شكل بإطار خفيف", "شكل أزرار ثلاثية الأبعاد",
                "شكل بارز ناعم", "شكل دوائر", "شكل زجاجي"};
        String[] styleSummariesEn = {"Classic BlackBerry", "Gboard modern", "Modern pill",
                "Gboard material", "Gboard borderless", "Modern pill", "Light outline", "3D keycaps",
                "Soft raised", "Circles", "Glass"};
        String[] styleSummaries = isDeviceArabic() ? styleSummariesAr : styleSummariesEn;
        int styleIdx = prefs.getKeyboardStyle();
        String st = (styleIdx >= 0 && styleIdx < styleSummaries.length) ? styleSummaries[styleIdx] : styleSummaries[0];
        return th + " • " + st;
    }

    /** صف كامل العرض لخطوة إعداد واحدة (تفعيل أو اختيار الكيبورد)، بلون مميز وأيقونة حديثة. */
    private View createSetupStepRow(int iconType, String colorHex, String title, String subtitle, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(roundedCard(colorHex, 14));
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(onClick);

        ModernIconView icon = new ModernIconView(this, iconType, Color.WHITE, Color.parseColor("#22FFFFFF"));
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
        subTv.setTextColor(Color.parseColor("#E5F0EA"));
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
        card.setBackground(roundedCard("#FFFFFF", 20));
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

        ModernIconView icon = new ModernIconView(this, iconType, Color.parseColor("#5F6368"), Color.TRANSPARENT);
        LinearLayout.LayoutParams lpIcon = new LinearLayout.LayoutParams(dp(28), dp(28));
        lpIcon.leftMargin = dp(14);
        lpIcon.rightMargin = dp(6);
        icon.setLayoutParams(lpIcon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextColor(Color.parseColor("#1F2937"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);

        TextView subTv = new TextView(this);
        subTv.setText(subtitle);
        subTv.setTextColor(Color.parseColor("#6B7280"));
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
            div.setBackgroundColor(Color.parseColor("#F1F3F5"));
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
        scroll.setBackgroundColor(Color.parseColor("#F2F4F8"));
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

        ModernIconView backBtn = new ModernIconView(this, ModernIconView.ICON_BACK_ARROW, Color.parseColor("#1F2937"), Color.parseColor("#E2E8F0"));
        LinearLayout.LayoutParams lpBack = new LinearLayout.LayoutParams(dp(40), dp(40));
        lpBack.leftMargin = dp(12);
        backBtn.setLayoutParams(lpBack);
        backBtn.setOnClickListener(v -> goBack(onBack));

        TextView titleTv = new TextView(this);
        titleTv.setText(screenTitle);
        titleTv.setTextColor(Color.parseColor("#111827"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        titleTv.setTypeface(Typeface.DEFAULT_BOLD);
        titleTv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        topBar.addView(backBtn);
        topBar.addView(titleTv);
        root.addView(topBar);

        scroll.addView(root);
        mainFrame.addView(scroll);
        restoreScroll(scroll);
        return root;
    }

    private interface SwitchListener {
        void onToggle(boolean checked);
    }

    /** صف تبديل بأسلوب قوائم Gboard: العنوان والوصف على جهة، ومفتاح التبديل في الطرف الآخر، داخل بطاقة بيضاء مستقلة. */
    /** بطاقة اختيار مدة الحذف التلقائي لنصوص الحافظة القديمة (المثبّت مستثنى). */
    private View createClipRetentionTile() {
        final int[] values = {4, 24, 72, 168, 336, 720, 0}; // بالساعات
        final String[] labels = {
                tr("4 ساعات", "4 hours"), tr("يوم واحد", "1 day"), tr("3 أيام", "3 days"), tr("أسبوع", "1 week"),
                tr("أسبوعين", "2 weeks"), tr("30 يوماً", "30 days"), tr("بدون حذف تلقائي", "Never")};
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundedCard("#FFFFFF", 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        TextView title = new TextView(this);
        title.setText(tr("حذف نصوص الحافظة ولقطة الشاشة تلقائياً", "Auto-delete old clipboard items & screenshot"));
        title.setTextSize(16);
        title.setTextColor(Color.parseColor("#202124"));
        card.addView(title);

        final TextView value = new TextView(this);
        value.setTextSize(13);
        value.setTextColor(Color.parseColor("#5F6368"));
        card.addView(value);
        final Runnable refresh = () -> {
            int cur = prefs.getClipRetentionHours();
            String label = labels[3];
            for (int i = 0; i < values.length; i++) if (values[i] == cur) label = labels[i];
            value.setText(tr("المدة: ", "Keep for: ") + label
                    + tr("  (المثبّت لا يُحذف)", "  (pinned items are kept)"));
        };
        refresh.run();

        card.setOnClickListener(v -> {
            int cur = prefs.getClipRetentionHours();
            int sel = 3;
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
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(roundedCard("#FFFFFF", 16));
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
        titleTv.setTextColor(Color.parseColor("#1F2937"));
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        textCol.addView(titleTv);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextColor(Color.parseColor("#6B7280"));
            sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            sub.setPadding(0, dp(4), 0, 0);
            textCol.addView(sub);
        }

        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onToggle(isChecked));
        LinearLayout.LayoutParams lpSw = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lpSw.leftMargin = dp(10);
        sw.setLayoutParams(lpSw);

        card.addView(textCol);
        card.addView(sw);
        return card;
    }

    public interface SingleChoiceCallback {
        void onSelected(int index);
    }

    private LinearLayout createExclusiveRadioList(String[] options, int selectedIndex, final SingleChoiceCallback callback) {
        LinearLayout listLayout = new LinearLayout(this);
        listLayout.setOrientation(LinearLayout.VERTICAL);
        final List<RadioButton> buttons = new ArrayList<>();

        for (int i = 0; i < options.length; i++) {
            final int idx = i;
            final RadioButton rb = new RadioButton(this);
            rb.setId(View.generateViewId());
            rb.setText(options[i]);
            rb.setTextColor(Color.parseColor("#1F2937"));
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
            rb.setPadding(dp(8), dp(10), dp(8), dp(10));
            rb.setChecked(i == selectedIndex);

            rb.setOnClickListener(v -> {
                for (int j = 0; j < buttons.size(); j++) {
                    buttons.get(j).setChecked(j == idx);
                }
                callback.onSelected(idx);
            });

            buttons.add(rb);
            listLayout.addView(rb);
        }
        return listLayout;
    }

    private void openLanguagesScreen() {
        LinearLayout root = createSubScreenTemplate(tr("اللغات وتوزيعة الحروف", "Languages & layout"));

        // اللغات المفعّلة: الاثنتان (تبديل بالسحب) أو لغة واحدة فقط بدون تبديل
        LinearLayout langModeCard = createGroupedWhiteCard();
        langModeCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView langModeHeader = new TextView(this);
        langModeHeader.setText(tr("اللغات المفعّلة:", "Enabled languages:"));
        langModeHeader.setTextColor(Color.parseColor("#111827"));
        langModeHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        langModeHeader.setTypeface(Typeface.DEFAULT_BOLD);
        langModeHeader.setPadding(0, 0, 0, dp(8));
        langModeCard.addView(langModeHeader);
        langModeCard.addView(createExclusiveRadioList(
                tr(new String[]{"العربية والإنجليزية (تبديل بسحب المسافة)", "العربية فقط (بدون تبديل)", "الإنجليزية فقط (بدون تبديل)"},
                   new String[]{"Arabic & English (swipe the space bar to switch)", "Arabic only (no switching)", "English only (no switching)"}),
                prefs.getLanguageMode(),
                idx -> prefs.setLanguageMode(idx)));
        root.addView(langModeCard);

        root.addView(createSwitchTile(
                tr("تغيير اللغة بسحب زر المسافة يميناً أو يساراً", "Swipe the space bar to switch language"),
                tr("اسحب زر المسافة (‹ العربية ›) للتبديل الفوري بين العربية والإنجليزية بدون زر لغة منفصل",
                        "Swipe the space bar left or right to switch instantly between Arabic and English, no separate key needed"),
                prefs.isSpaceSwipeLangEnabled(),
                checked -> prefs.setSpaceSwipeLangEnabled(checked)
        ));

        LinearLayout infoCard = createGroupedWhiteCard();
        infoCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView infoTv = new TextView(this);
        infoTv.setText(tr(
                "• اللغة العربية: توزيعة Gboard الصافية بدون أرقام على الحروف. اضغط مطولاً على أي حرف عربي ليظهر بوب أب الهمزات والحروف البديلة وتختار منها.\n\n• اللغة الإنجليزية: توزيعة QWERTY القياسية مع دعم الكابيتال التلقائي وتثبيت Caps Lock.",
                "• Arabic: a clean Gboard-style layout with no numbers printed on the letter keys. Long-press any Arabic letter to pick a hamza or alternate form.\n\n• English: standard QWERTY with auto-capitalize and a Caps Lock toggle."
        ));
        infoTv.setTextColor(Color.parseColor("#374151"));
        infoTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        infoCard.addView(infoTv);
        root.addView(infoCard);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 77 && !ScreenshotClip.hasPermission(this)) {
            prefs.setScreenshotClipEnabled(false);
            Toast.makeText(this, tr("لم يُمنح إذن الصور، فلا يمكن حفظ لقطات الشاشة", "Photos permission was not granted, so screenshots can't be saved"), Toast.LENGTH_LONG).show();
            openPreferencesScreen();
        }
        if (requestCode == 78) {
            boolean granted = checkSelfPermission(android.Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
            prefs.setOtpSuggestEnabled(granted);
            if (!granted) {
                Toast.makeText(this, tr("لم يُمنح إذن الرسائل، فلن يُقترح رمز التحقق تلقائياً", "SMS permission wasn't granted, so the code won't be suggested automatically"), Toast.LENGTH_LONG).show();
            }
            openPreferencesScreen();
        }
    }

    private void openPreferencesScreen() {
        LinearLayout root = createSubScreenTemplate(tr("التفضيلات وأحجام الكيبورد", "Preferences & sizes"));

        LinearLayout fontCard = createGroupedWhiteCard();
        fontCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView fontLabel = new TextView(this);
        int fontPct = Math.round(prefs.getKeyFontScale() * 100);
        fontLabel.setText(tr("حجم الحروف داخل المربعات: ", "Key letter size: ") + fontPct + "%");
        fontLabel.setTextColor(Color.parseColor("#111827"));
        fontLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        fontLabel.setTypeface(Typeface.DEFAULT_BOLD);
        fontCard.addView(fontLabel);

        SeekBar fontSeek = new SeekBar(this);
        fontSeek.setMax(70);
        fontSeek.setProgress(Math.max(0, Math.min(70, fontPct - 80)));
        fontSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        fontSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = 80 + progress;
                prefs.setKeyFontScale(pct / 100f);
                fontLabel.setText(tr("حجم الحروف داخل المربعات: ", "Key letter size: ") + pct + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        fontCard.addView(fontSeek);
        root.addView(fontCard);

        LinearLayout heightCard = createGroupedWhiteCard();
        heightCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView heightLabel = new TextView(this);
        int heightPct = Math.round(prefs.getKeyboardScale() * 100);
        heightLabel.setText(tr("ارتفاع الكيبورد العام: ", "Overall keyboard height: ") + heightPct + "%");
        heightLabel.setTextColor(Color.parseColor("#111827"));
        heightLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        heightLabel.setTypeface(Typeface.DEFAULT_BOLD);
        heightCard.addView(heightLabel);

        SeekBar heightSeek = new SeekBar(this);
        heightSeek.setMax(75);
        heightSeek.setProgress(Math.max(0, Math.min(75, heightPct - 70)));
        heightSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        heightSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = 70 + progress;
                prefs.setKeyboardScale(pct / 100f);
                heightLabel.setText(tr("ارتفاع الكيبورد العام: ", "Overall keyboard height: ") + pct + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        heightCard.addView(heightSeek);
        root.addView(heightCard);

        LinearLayout sugSizeCard = createGroupedWhiteCard();
        sugSizeCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView sugLabel = new TextView(this);
        int sugPct = Math.round(prefs.getSuggestionBarScale() * 100);
        sugLabel.setText(tr("ارتفاع صف الاقتراحات العلوي: ", "Suggestion bar height: ") + sugPct + "%");
        sugLabel.setTextColor(Color.parseColor("#111827"));
        sugLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        sugLabel.setTypeface(Typeface.DEFAULT_BOLD);
        sugSizeCard.addView(sugLabel);

        SeekBar sugSeek = new SeekBar(this);
        sugSeek.setMax(55);
        sugSeek.setProgress(Math.max(0, Math.min(55, sugPct - 70)));
        sugSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        sugSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int pct = 70 + progress;
                prefs.setSuggestionBarScale(pct / 100f);
                sugLabel.setText(tr("ارتفاع صف الاقتراحات العلوي: ", "Suggestion bar height: ") + pct + "%");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sugSizeCard.addView(sugSeek);
        root.addView(sugSizeCard);

        // حجم خط الاقتراحات: افتراضي + 5 درجات أكبر
        LinearLayout sugTextCard = createGroupedWhiteCard();
        sugTextCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView sugTextLabel = new TextView(this);
        sugTextLabel.setTextColor(Color.parseColor("#111827"));
        sugTextLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        sugTextLabel.setTypeface(Typeface.DEFAULT_BOLD);
        final Runnable updateSugTextLabel = () -> {
            int lv = prefs.getSuggestionTextLevel();
            sugTextLabel.setText(tr("حجم خط الاقتراحات: ", "Suggestion text size: ")
                    + (lv == 0 ? tr("افتراضي", "Default") : "+" + lv));
        };
        updateSugTextLabel.run();
        sugTextCard.addView(sugTextLabel);
        SeekBar sugTextSeek = new SeekBar(this);
        sugTextSeek.setMax(5);
        sugTextSeek.setProgress(prefs.getSuggestionTextLevel());
        sugTextSeek.setPadding(dp(14), dp(12), dp(14), dp(8));
        sugTextSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                prefs.setSuggestionTextLevel(progress);
                updateSugTextLabel.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        sugTextCard.addView(sugTextSeek);
        root.addView(sugTextCard);

        root.addView(createSwitchTile(
                tr("أحرف كبيرة تلقائياً بداية كل جملة", "Auto-capitalize sentences"),
                tr("يكتب أول حرف إنجليزي كابيتال تلقائياً في بداية النص أو بعد النقطة", "Automatically capitalizes the first letter at the start of text or after a period"),
                prefs.isAutoCapitalizeSentences(),
                checked -> prefs.setAutoCapitalizeSentences(checked)
        ));

        root.addView(createSwitchTile(
                tr("عرض الحروف الإنجليزية كابيتال دائماً", "Always show uppercase English keycaps"),
                tr("يجعل شكل الحروف على الكيبورد الإنجليزي (Q W E R T Y) واضحاً وكبيراً كما في صورة بلاك بيري", "Shows the English keys (Q W E R T Y) in bold capitals, BlackBerry-style"),
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
        ohHeader.setTextColor(Color.parseColor("#111827"));
        ohHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        ohHeader.setTypeface(Typeface.DEFAULT_BOLD);
        ohHeader.setPadding(0, 0, 0, dp(8));
        oneHandCard.addView(ohHeader);
        oneHandCard.addView(createExclusiveRadioList(
                tr(new String[]{"معطّل (عرض كامل)", "مصغّر جهة اليسار", "مصغّر جهة اليمين"},
                        new String[]{"Off (full width)", "Compact, left side", "Compact, right side"}),
                prefs.getOneHandedMode(),
                idx -> prefs.setOneHandedMode(idx)));
        root.addView(oneHandCard);

        LinearLayout splitCard = createGroupedWhiteCard();
        splitCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView splitHeader = new TextView(this);
        splitHeader.setText(tr("تقسيم الكيبورد على الشاشات الكبيرة:", "Split keyboard on large screens:"));
        splitHeader.setTextColor(Color.parseColor("#111827"));
        splitHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        splitHeader.setTypeface(Typeface.DEFAULT_BOLD);
        splitHeader.setPadding(0, 0, 0, dp(8));
        splitCard.addView(splitHeader);
        TextView splitDesc = new TextView(this);
        splitDesc.setText(tr("يقسم صفوف الحروف والأرقام لنصفين بفاصل بالمنتصف (الصف السفلي يبقى متصلاً)، لتسهيل الكتابة بالإبهامين على الشاشة الداخلية للجوالات القابلة للطي أو التابلت",
                "Splits the letter and number rows into two halves with a gap in the middle (the bottom row stays connected), for easier two-thumb typing on a foldable's inner screen or a tablet"));
        splitDesc.setTextColor(Color.parseColor("#6B7280"));
        splitDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        splitDesc.setPadding(0, 0, 0, dp(10));
        splitCard.addView(splitDesc);
        splitCard.addView(createExclusiveRadioList(
                tr(new String[]{"تلقائي حسب عرض الشاشة", "مفعّل دائماً", "معطّل دائماً"},
                        new String[]{"Automatic based on screen width", "Always on", "Always off"}),
                prefs.getSplitKeyboardMode(),
                idx -> prefs.setSplitKeyboardMode(idx)));
        root.addView(splitCard);

    }

    private void openThemeAndStyleScreen() {
        LinearLayout root = createSubScreenTemplate(tr("المظهر وشكل الكيبورد", "Theme & key style"));
        fillThemeAndStyleScreen(root);
    }

    /** تحديث الشاشة في مكانها بعد اختيار (بدون إعادة بناء الصفحة كلها: لا وميض ولا قفز بالسكرول). */
    private void refreshThemeScreen(LinearLayout root) {
        root.removeViews(1, root.getChildCount() - 1);
        fillThemeAndStyleScreen(root);
    }

    private void fillThemeAndStyleScreen(final LinearLayout root) {

        LinearLayout themeCard = createGroupedWhiteCard();
        themeCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView tHeader = new TextView(this);
        tHeader.setText(tr("1. لون السمة", "1. Theme color"));
        tHeader.setTextColor(Color.parseColor("#111827"));
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
        sHeader.setText(tr("2. شكل أزرار الكيبورد", "2. Key shape"));
        sHeader.setTextColor(Color.parseColor("#111827"));
        sHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        sHeader.setTypeface(Typeface.DEFAULT_BOLD);
        sHeader.setPadding(0, 0, 0, dp(10));
        styleCard.addView(sHeader);

        String[] styleNames = tr(
                new String[]{"بلاك بيري", "Gboard حديث", "كبسولات", "Gboard ماتيريال", "بدون إطار", "إطار خفيف", "ثلاثي الأبعاد", "بارز ناعم", "دوائر", "زجاجي"},
                new String[]{"BlackBerry", "Gboard modern", "Pill", "Gboard material", "Borderless", "Outline", "3D keycap", "Soft raised", "Circles", "Glass"});
        int[] styleIds = {0, 1, 2, 3, 4, 6, 7, 8, 9, 10};
        int[] styleRadii = {2, 10, 20, 14, 8, 10, 6, 12, 24, 10};
        List<View> styleTiles = new ArrayList<>();
        for (int i = 0; i < styleNames.length; i++) {
            final int id = styleIds[i];
            styleTiles.add(createKeyStylePreviewTile(styleNames[i], id, prefs.getKeyboardStyle() == id,
                    v -> { prefs.setKeyboardStyle(id); refreshThemeScreen(root); }));
        }
        addTileGrid(styleCard, styleTiles, 3);
        root.addView(styleCard);

        // لون كبسولة زر الإدخال (السهم يبقى أبيض)
        LinearLayout enterCard = createGroupedWhiteCard();
        enterCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView eHeader = new TextView(this);
        eHeader.setText(tr("3. لون زر الإدخال", "3. Enter key color"));
        eHeader.setTextColor(Color.parseColor("#111827"));
        eHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        eHeader.setTypeface(Typeface.DEFAULT_BOLD);
        eHeader.setPadding(0, 0, 0, dp(10));
        enterCard.addView(eHeader);
        String[] enterNames = tr(
                new String[]{"افتراضي", "أزرق", "أخضر", "بنفسجي", "برتقالي", "وردي", "أحمر", "عنابي"},
                new String[]{"Default", "Blue", "Green", "Purple", "Orange", "Pink", "Red", "Burgundy"});
        String[] enterHex = {"#4B5563", "#2563EB", "#16A34A", "#7C3AED", "#EA580C", "#DB2777", "#DC2626", "#7F1D3F"};
        List<View> enterTiles = new ArrayList<>();
        for (int i = 0; i < enterNames.length; i++) {
            final int color = i == 0 ? 0 : Color.parseColor(enterHex[i]);
            enterTiles.add(createPreviewTile(enterNames[i], enterHex[i], prefs.getEnterColor() == color,
                    v -> { prefs.setEnterColor(color); refreshThemeScreen(root); }));
        }
        addTileGrid(enterCard, enterTiles, 3);
        root.addView(enterCard);
    }

    /** بطاقة معاينة صغيرة بلون مصمت وعلامة اختيار، بنفس أسلوب شبكة السمات في إعدادات أندرويد. */
    private View createPreviewTile(String label, String colorHex, boolean selected, View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setOnClickListener(onClick);

        FrameLayout swatchHolder = new FrameLayout(this);
        swatchHolder.setLayoutParams(new LinearLayout.LayoutParams(dp(78), dp(64)));
        GradientDrawable swatch = roundedCard(colorHex, 14);
        if (selected) swatch.setStroke(dp(3), Color.parseColor("#2563EB"));
        else swatch.setStroke(dp(1), Color.parseColor("#E2E8F0"));
        swatchHolder.setBackground(swatch);

        if (selected) {
            TextView check = new TextView(this);
            check.setText("✓");
            check.setTextColor(colorHex.equals("#FFFFFF") ? Color.parseColor("#2563EB") : Color.WHITE);
            check.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            FrameLayout.LayoutParams lpCheck = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCheck.gravity = Gravity.CENTER;
            check.setLayoutParams(lpCheck);
            swatchHolder.addView(check);
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(Color.parseColor("#374151"));
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, dp(5), 0, 0);
        labelTv.setMaxLines(2);

        tile.addView(swatchHolder);
        tile.addView(labelTv);
        return tile;
    }

    /** بطاقة معاينة لشكل المفاتيح: مربعان صغيران بنفس استدارة الشكل المختار. */
    private View createKeyStylePreviewTile(String label, int styleId, boolean selected, View.OnClickListener onClick) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setOnClickListener(onClick);

        FrameLayout holder = new FrameLayout(this);
        holder.setLayoutParams(new LinearLayout.LayoutParams(dp(78), dp(64)));
        holder.addView(new KeyStylePreviewView(this, styleId, prefs.getThemeMode(), prefs.getEnterColor()),
                new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // إطار الاختيار فوق المعاينة + شارة ✓ زرقاء
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(14));
        frame.setColor(Color.TRANSPARENT);
        frame.setStroke(dp(selected ? 3 : 1), Color.parseColor(selected ? "#2563EB" : "#E2E8F0"));
        holder.setForeground(frame);
        if (selected) {
            TextView badge = new TextView(this);
            badge.setText("✓");
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            badge.setTypeface(Typeface.DEFAULT_BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundedCard("#2563EB", 9));
            FrameLayout.LayoutParams lpB = new FrameLayout.LayoutParams(dp(18), dp(18));
            lpB.gravity = Gravity.TOP | Gravity.END;
            lpB.topMargin = dp(3);
            lpB.rightMargin = dp(3);
            holder.addView(badge, lpB);
        }

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextColor(Color.parseColor("#374151"));
        labelTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        labelTv.setGravity(Gravity.CENTER);
        labelTv.setPadding(0, dp(5), 0, 0);
        labelTv.setMaxLines(2);

        tile.addView(holder);
        tile.addView(labelTv);
        return tile;
    }

    /** يرتب قائمة بطاقات في شبكة صفوف متساوية العرض، بعدد أعمدة محدد، داخل الحاوية المعطاة. */
    private void addTileGrid(LinearLayout container, List<View> tiles, int perRow) {
        for (int i = 0; i < tiles.size(); i += perRow) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpRow = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpRow.topMargin = dp(10);
            row.setLayoutParams(lpRow);
            for (int j = i; j < Math.min(i + perRow, tiles.size()); j++) {
                LinearLayout.LayoutParams lpTile = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
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

        // إعدادات الاقتراحات (زر المايك، رمز التحقق من الرسائل)
        root.addView(createSwitchTile(
                tr("زر الكتابة بالصوت (المايك)", "Voice typing button (mic)"),
                tr("يظهر على يمين صف الاقتراحات؛ اضغطه وتكلم ويكتب الكلام. يحتاج إذن الميكروفون واتصالاً بالإنترنت غالباً", "Shows on the right of the suggestion row; tap it and speak to type. Needs mic permission and usually an internet connection"),
                prefs.isVoiceButtonEnabled(),
                checked -> prefs.setVoiceButtonEnabled(checked)
        ));

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

        LinearLayout trainCard = createGroupedWhiteCard();
        trainCard.setPadding(dp(16), dp(16), dp(16), dp(16));

        final EditText trainBox = new EditText(this);
        trainBox.setHint(tr("أدخل جملة هنا لتدريب الكيبورد عليها فوراً...", "Type a sentence here to train the keyboard instantly..."));
        trainBox.setHintTextColor(Color.parseColor("#9CA3AF"));
        trainBox.setTextColor(Color.parseColor("#111827"));
        trainBox.setBackground(borderedBox("#F9FAFB", "#D1D5DB", 12));
        trainBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        trainCard.addView(trainBox);

        Button btnTrain = new Button(this);
        btnTrain.setText(tr("حفظ وتدريب التوقعات على هذه الجملة", "Save & train predictions on this sentence"));
        btnTrain.setAllCaps(false);
        btnTrain.setTextColor(Color.WHITE);
        btnTrain.setBackground(roundedCard("#2563EB", 12));
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
        btnClear.setAllCaps(false);
        btnClear.setTextColor(Color.WHITE);
        btnClear.setBackground(roundedCard("#DC2626", 12));
        LinearLayout.LayoutParams lpCl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        lpCl.topMargin = dp(8);
        btnClear.setLayoutParams(lpCl);
        btnClear.setOnClickListener(v -> {
            predictionEngine.clearAllLearnedData();
        });
        trainCard.addView(btnClear);

        // قائمة الكلمات المتعلمة في نافذة مستقلة (فتح/إغلاق + بحث)
        root.addView(trainCard);
        root.addView(createMenuRow(
                ModernIconView.ICON_SLIDERS_PREFS,
                tr("الكلمات المتعلمة", "Learned words"),
                tr(predictionEngine.getLearnedWordCount() + " كلمة محفوظة • بحث وحذف", predictionEngine.getLearnedWordCount() + " saved words • search & delete"),
                false,
                this::openLearnedWordsScreen));
    }

    /** نافذة الكلمات المتعلمة: مربع بحث وقائمة، والرجوع يعيد لشاشة التصحيح. */
    private void openLearnedWordsScreen() {
        LinearLayout root = createSubScreenTemplate(tr("الكلمات المتعلمة", "Learned words"), this::openCorrectionsAndMemoryScreen);
        final LinearLayout learnedListMount = new LinearLayout(this);
        learnedListMount.setOrientation(LinearLayout.VERTICAL);
        LinearLayout card = createGroupedWhiteCard();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        learnedFilter = "";
        final EditText searchBox = new EditText(this);
        searchBox.setHint(tr("ابحث عن كلمة في القائمة...", "Search for a word in the list..."));
        searchBox.setHintTextColor(Color.parseColor("#9CA3AF"));
        searchBox.setTextColor(Color.parseColor("#111827"));
        searchBox.setSingleLine(true);
        searchBox.setBackground(borderedBox("#F9FAFB", "#D1D5DB", 12));
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
        header.setTextColor(Color.parseColor("#047857"));
        header.setTypeface(Typeface.DEFAULT_BOLD);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        header.setPadding(0, dp(4), 0, dp(6));
        container.addView(header);

        if (entries.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(q.isEmpty()
                    ? tr("لا توجد كلمات محفوظة بعد. اكتب جملاً أو درّب الكيبورد من الأعلى لتظهر هنا.", "No saved words yet. Type sentences or train the keyboard above to see them here.")
                    : tr("لا توجد كلمة تطابق بحثك.", "No word matches your search."));
            empty.setTextColor(Color.parseColor("#6B7280"));
            container.addView(empty);
            return;
        }

        int shown = 0;
        for (final PredictionEngine.LearnedEntry entry : entries) {
            if (shown++ >= 250) break;

            LinearLayout wRow = new LinearLayout(this);
            wRow.setOrientation(LinearLayout.HORIZONTAL);
            wRow.setGravity(Gravity.CENTER_VERTICAL);
            wRow.setBackground(borderedBox("#F8FAFC", "#E2E8F0", 10));
            wRow.setPadding(dp(12), dp(4), dp(4), dp(4));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(5);
            wRow.setLayoutParams(lp);

            LinearLayout textBox = new LinearLayout(this);
            textBox.setOrientation(LinearLayout.VERTICAL);
            textBox.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView tv = new TextView(this);
            tv.setText(entry.word + "  (×" + entry.count + ")");
            tv.setTextColor(Color.parseColor("#111827"));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            tv.setTypeface(Typeface.DEFAULT_BOLD);
            textBox.addView(tv);

            if (!entry.nextSummary.isEmpty()) {
                TextView nx = new TextView(this);
                nx.setText("← " + entry.nextSummary);
                nx.setTextColor(Color.parseColor("#6B7280"));
                nx.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
                nx.setPadding(0, dp(2), 0, 0);
                textBox.addView(nx);
            }

            ModernIconView del = new ModernIconView(this, ModernIconView.ICON_CLOSE,
                    Color.parseColor("#DC2626"), Color.TRANSPARENT);
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

        LinearLayout sizeCard = createGroupedWhiteCard();
        sizeCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        final TextView sizeLabel = new TextView(this);
        sizeLabel.setText(tr("حجم الإيموجي في القائمة: ", "Emoji size in the list: ") + prefs.getEmojiSizeSp());
        sizeLabel.setTextColor(Color.parseColor("#111827"));
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

        LinearLayout clearCard = createGroupedWhiteCard();
        clearCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView clearBtn = new TextView(this);
        clearBtn.setText(tr("مسح قسم «مستخدم حديثاً»", "Clear \"Recently used\" section"));
        clearBtn.setTextColor(Color.parseColor("#DC2626"));
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
                tr("السحب الأفقي لمسح كلمة كاملة", "Horizontal swipe to delete a whole word"),
                tr("اسحب أفقياً فوق الحروف لحذف آخر كلمة دفعة واحدة", "Swipe sideways over the keys to delete the last word at once"),
                prefs.isSwipeDeleteWordEnabled(),
                checked -> prefs.setSwipeDeleteWordEnabled(checked)
        ));

        LinearLayout dirCard = createGroupedWhiteCard();
        dirCard.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView dirHeader = new TextView(this);
        dirHeader.setText(tr("اتجاه السحب للمسح:", "Swipe-to-delete direction:"));
        dirHeader.setTextColor(Color.parseColor("#111827"));
        dirHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        dirHeader.setTypeface(Typeface.DEFAULT_BOLD);
        dirHeader.setPadding(0, 0, 0, dp(8));
        dirCard.addView(dirHeader);
        dirCard.addView(createExclusiveRadioList(
                tr(new String[]{"من اليسار لليمين  →", "من اليمين لليسار  ←"},
                   new String[]{"Left to right  →", "Right to left  ←"}),
                prefs.getSwipeDeleteDirection(),
                idx -> prefs.setSwipeDeleteDirection(idx)));
        root.addView(dirCard);

        root.addView(createSwitchTile(
                tr("سحب زر المسافة يميناً أو يساراً لتغيير اللغة", "Swipe the space bar left or right to change language"),
                tr("التبديل السريع بين العربية والإنجليزية عبر تمرير الإصبع على المسافة", "Quickly switch between Arabic and English by swiping the space bar"),
                prefs.isSpaceSwipeLangEnabled(),
                checked -> prefs.setSpaceSwipeLangEnabled(checked)
        ));

        root.addView(createSwitchTile(
                tr("تحريك المؤشر بالضغط المستمر على زر المسافة", "Move the cursor by holding and dragging the space bar"),
                tr("اضغط مطولاً على المسافة ثم حرّك إصبعك يميناً أو يساراً لتحريك المؤشر حرفاً حرفاً. سحبة سريعة بدون ضغط مستمر تبقى تبدّل اللغة كالمعتاد، بدون أي تعارض بين الحركتين",
                        "Press and hold the space bar, then drag to move the cursor character by character. A quick swipe without holding still switches the language as usual — the two gestures never conflict"),
                prefs.isSpaceCursorDragEnabled(),
                checked -> prefs.setSpaceCursorDragEnabled(checked)
        ));
    }

    private void openSoundAndVibrationScreen() {
        LinearLayout root = createSubScreenTemplate(tr("الصوت والاهتزاز (5 نغمات كتابة + 5 نغمات سحب)", "Sound & Vibration (5 typing tones + 5 swipe tones)"));

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
        volLabel.setTextColor(Color.parseColor("#111827"));
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
        swipeVolLabel.setTextColor(Color.parseColor("#111827"));
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
        pHeader.setTextColor(Color.parseColor("#111827"));
        pHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        pHeader.setTypeface(Typeface.DEFAULT_BOLD);
        pHeader.setPadding(0, 0, 0, dp(8));
        keySoundCard.addView(pHeader);

        final int[] soundOrder = {10, 0, 1, 2, 3, 4, 6, 7, 8, 9, 5};
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
        swHeader.setTextColor(Color.parseColor("#111827"));
        swHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f);
        swHeader.setTypeface(Typeface.DEFAULT_BOLD);
        swHeader.setPadding(0, 0, 0, dp(8));
        swipeSoundCard.addView(swHeader);

        String[] swipeSoundOptions = new String[soundOrder.length];
        int swSel = 0;
        for (int i = 0; i < soundOrder.length; i++) {
            swipeSoundOptions[i] = SoundController.getSwipeProfileDisplayName(soundOrder[i]);
            if (soundOrder[i] == prefs.getSwipeSoundProfile()) swSel = i;
        }
        swipeSoundCard.addView(createExclusiveRadioList(swipeSoundOptions, swSel, idx -> {
            prefs.setSwipeSoundProfile(soundOrder[idx]);
            soundController.playSwipeUpSound();
        }));
        root.addView(swipeSoundCard);
    }

    private void openClipboardScreen() {
        LinearLayout root = createSubScreenTemplate(tr("الحافظة الذكية (Clipboard)", "Smart Clipboard"));

        // إعدادات الحافظة (لقطة الشاشة، آخر نص منسوخ، مدة الحذف)
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


        LinearLayout card = createGroupedWhiteCard();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));

        final EditText newClipBox = new EditText(this);
        newClipBox.setHint(tr("أضف نصاً أو عبارة لتثبيتها في الحافظة...", "Add text or a phrase to pin in the clipboard..."));
        newClipBox.setHintTextColor(Color.parseColor("#9CA3AF"));
        newClipBox.setTextColor(Color.parseColor("#111827"));
        newClipBox.setBackground(borderedBox("#F9FAFB", "#D1D5DB", 12));
        newClipBox.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(newClipBox);

        final LinearLayout listMount = new LinearLayout(this);
        listMount.setOrientation(LinearLayout.VERTICAL);
        listMount.setPadding(0, dp(12), 0, 0);

        Button btnAdd = new Button(this);
        btnAdd.setText(tr("+ إضافة وتثبيت في الحافظة", "+ Add & pin to clipboard"));
        btnAdd.setAllCaps(false);
        btnAdd.setTextColor(Color.WHITE);
        btnAdd.setBackground(roundedCard("#059669", 12));
        LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        lpAdd.topMargin = dp(8);
        btnAdd.setLayoutParams(lpAdd);
        btnAdd.setOnClickListener(v -> {
            String t = newClipBox.getText().toString().trim();
            if (!t.isEmpty()) {
                clipboardRepo.addClip(t, true);
                newClipBox.setText("");
                populateClipsSettingsList(listMount);
            }
        });
        card.addView(btnAdd);
        populateClipsSettingsList(listMount);
        card.addView(listMount);
        root.addView(card);
    }

    private void populateClipsSettingsList(final LinearLayout container) {
        container.removeAllViews();
        for (final ClipboardRepository.ClipEntry clip : clipboardRepo.getClips()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackground(borderedBox("#F8FAFC", "#E2E8F0", 10));
            row.setPadding(dp(10), dp(8), dp(10), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);

            TextView pinBtn = new TextView(this);
            pinBtn.setText("📌");
            pinBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            pinBtn.setAlpha(clip.pinned ? 1.0f : 0.35f);
            pinBtn.setPadding(dp(8), dp(4), dp(10), dp(4));
            pinBtn.setOnClickListener(v -> {
                clipboardRepo.togglePin(clip.text);
                populateClipsSettingsList(container);
            });

            TextView tv = new TextView(this);
            tv.setText(clip.text);
            tv.setTextColor(Color.parseColor("#1F2937"));
            tv.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = new TextView(this);
            del.setText("✕");
            del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
            del.setTextColor(Color.parseColor("#DC2626"));
            del.setPadding(dp(10), dp(4), dp(8), dp(4));
            del.setOnClickListener(v -> {
                clipboardRepo.removeClip(clip.text);
                populateClipsSettingsList(container);
            });

            row.addView(pinBtn);
            row.addView(tv);
            row.addView(del);
            container.addView(row);
        }
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
        shortInput.setHintTextColor(Color.parseColor("#9CA3AF"));
        shortInput.setTextColor(Color.parseColor("#111827"));
        shortInput.setBackground(borderedBox("#F9FAFB", "#D1D5DB", 10));
        shortInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(shortInput);

        final EditText phraseInput = new EditText(this);
        phraseInput.setHint(tr("العبارة الكاملة (مثال: السلام عليكم ورحمة الله وبركاته)", "Full phrase (e.g. on my way)"));
        phraseInput.setHintTextColor(Color.parseColor("#9CA3AF"));
        phraseInput.setTextColor(Color.parseColor("#111827"));
        phraseInput.setBackground(borderedBox("#F9FAFB", "#D1D5DB", 10));
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
        btnSaveSc.setAllCaps(false);
        btnSaveSc.setTextColor(Color.WHITE);
        btnSaveSc.setBackground(roundedCard("#059669", 10));
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
            row.setBackground(borderedBox("#F8FAFC", "#E2E8F0", 10));
            row.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(6);
            row.setLayoutParams(lp);

            TextView tv = new TextView(this);
            tv.setText("«" + e.getKey() + "»  ⬅  " + e.getValue());
            tv.setTextColor(Color.parseColor("#1F2937"));
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView del = new TextView(this);
            del.setText(tr("حذف ✕", "Delete ✕"));
            del.setTextColor(Color.parseColor("#DC2626"));
            del.setPadding(dp(8), dp(4), dp(8), dp(4));
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
