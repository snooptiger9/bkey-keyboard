# bkey ⌨️

**كيبورد أندرويد بطابع BlackBerry الكلاسيكي — سحب للأعلى لاختيار الاقتراح، وسحب لليسار للحذف.**
**A BlackBerry-style Android keyboard — swipe up to pick a suggestion, swipe left to delete.**

![Build](https://github.com/jsbskzbakwu6568/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/jsbskzbakwu6568/bkey-keyboard)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/العربية%20%7C%20English-bilingual-blue)

---

## 📖 العربية

### ✨ الوصف

**bkey** كيبورد أندرويد (IME) مستوحى من أزرار بلاك بيري الفيزيائية الكلاسيكية، لكن بتفاعل حديث:
اسحب للأعلى من أي اقتراح لاختياره فوراً، واسحب لليسار على صف الحروف للحذف بدل الضغط المتكرر
على الباكسبيس. يدعم العربية والإنجليزية بالكامل مع تجربة مخصصة بعناية لكل لغة.

### 🚀 المميزات

- **تفاعل بالسحب**: سحب للأعلى = قبول الاقتراح، سحب لليسار = حذف سريع، بدون أزرار إضافية تزحم الشاشة.
- **تصحيح وتوقع ذكي**: تصحيح تلقائي صامت عند المسافة، توقع الكلمة التالية، وتعلّم الكلمات الجديدة أثناء الكتابة.
- **مظهر قابل للتخصيص بالكامل**: 3 ثيمات ألوان، 9 أشكال لأزرار الكيبورد، خطوط متعددة (افتراضي/Tajawal)،
  وتحكم كامل بحجم الحروف وارتفاع الكيبورد وشريط الاقتراحات.
- **حافظة (Clipboard) مدمجة**: درج مخصص لآخر النصوص المنسوخة، مع تثبيت وحذف كل عنصر على حدة.
- **لوحة إيموجي و GIF**: بحث ومعاينة متحركة لصور GIF عبر GIPHY، بالإضافة للإيموجي والرموز.
- **اقتراح رموز التحقق تلقائياً**: يكتشف أكواد OTP من الرسائل النصية أو واتساب ويعرضها بمنتصف
  شريط الاقتراحات لمدة محدودة — كل المعالجة محلية على الجهاز.
- **خصوصية أولاً**: وضع تخفي (Incognito) يوقف التعلّم والحفظ بالحافظة، وكلمات المرور/الإيميلات/الروابط
  لا يُتعلَّم منها مطلقاً حتى في الوضع العادي.
- **كتابة بالصوت ووضع اليد الواحدة**: زر مايكروفون اختياري، ووضع مصغّر لليد الواحدة (يمين/يسار).
- **أرقام عربية هندية (١٢٣)** اختيارية، وصف أرقام مخصص لحقول كلمات المرور.

### 📥 التحميل

آخر نسخة APK موقّعة جاهزة دائماً من صفحة **[Releases](../../releases)** في هذا المستودع.
كل إصدار جديد يُبنى ويُوقّع تلقائياً عبر GitHub Actions فور كل تحديث في الكود.

### 🛠️ البناء من المصدر

```bash
git clone <repo-url>
cd bkey-keyboard
# أنشئ app/keystore.properties محلياً بنفس صيغة مفاتيح storePassword/keyAlias/keyPassword
./gradlew assembleRelease
```

> ملاحظة: كلمة سر التوقيع ليست داخل المستودع — تُحقن وقت البناء من GitHub Secrets
> (`BKEY_KEYSTORE_PASSWORD`). للبناء محلياً تحتاج تسوي ملف `keystore.properties` بنفسك.

### 🔒 الخصوصية والأمان

- لا يوجد اتصال بالإنترنت إلا لميزتي GIF (GIPHY) والكتابة بالصوت — بقية الكيبورد يعمل بالكامل محلياً.
- أكواد التحقق (OTP) تُقرأ وتُعالج على الجهاز فقط ولا تُرسل لأي سيرفر خارجي.
- مفتاح توقيع التطبيق محمي عبر GitHub Secrets، وليس مكشوفاً في الكود.

---

## 📖 English

### ✨ Overview

**bkey** is an Android keyboard (IME) inspired by the classic BlackBerry physical keyboard, with a
modern gesture twist: swipe up on any suggestion to accept it instantly, and swipe left across the
letter row for quick deletion instead of repeated backspace taps. Fully supports Arabic and English
with a carefully tailored experience for each.

### 🚀 Features

- **Gesture-driven**: swipe up to accept a suggestion, swipe left to delete fast — no extra clutter.
- **Smart correction & prediction**: silent auto-correct on space, next-word prediction, and
  on-the-fly word learning as you type.
- **Fully customizable look**: 3 color themes, 9 key shapes, multiple fonts (System/Tajawal), and
  full control over letter size, keyboard height, and suggestion bar size.
- **Built-in clipboard manager**: a dedicated drawer for recent copied text, with per-item pin/delete.
- **Emoji & GIF panel**: search and preview animated GIFs via GIPHY, plus emoji and symbols.
- **Automatic verification-code suggestions**: detects OTP codes from SMS or WhatsApp and surfaces
  them in the middle suggestion slot for a limited time — all processed on-device.
- **Privacy-first**: Incognito mode disables learning and clipboard saving entirely; passwords,
  emails and links are never learned from, even outside Incognito mode.
- **Voice typing & one-handed mode**: optional mic button, and a compact one-handed layout
  (left or right).
- **Arabic-Indic digits (١٢٣)** toggle, plus a dedicated number row for password fields.

### 📥 Download

The latest signed APK is always available from the **[Releases](../../releases)** page of this
repository. Every new release is built and signed automatically by GitHub Actions on each update.

### 🛠️ Build from source

```bash
git clone <repo-url>
cd bkey-keyboard
# create app/keystore.properties locally with storePassword/keyAlias/keyPassword keys
./gradlew assembleRelease
```

> Note: the signing password is not stored in this repository — it's injected at build time from
> GitHub Secrets (`BKEY_KEYSTORE_PASSWORD`). To build locally you'll need to create your own
> `keystore.properties` file.

### 🔒 Privacy & security

- No network access except for the GIF (GIPHY) and voice-typing features — everything else runs
  fully on-device.
- Verification codes (OTP) are read and processed on-device only, never sent to any server.
- The app's signing key is protected via GitHub Secrets, never exposed in the codebase.

---

<p align="center">Made with ❤️ — bkey</p>
