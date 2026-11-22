<p align="center"><a href="README.md">English</a> | <b>العربية</b> | <a href="README.es.md">Español</a> | <a href="README.hi.md">हिन्दी</a></p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="docs/images/banner-light.png?v=2">
    <img src="docs/images/banner.png?v=2" alt="bkey — a BlackBerry-style Android keyboard, swipe up to accept a suggestion and swipe sideways to delete" width="100%">
  </picture>
</p>

# <img src="docs/icons/keyboard.svg" width="36" height="36" align="absmiddle" alt=""> bkey

**كيبورد أندرويد مبني على إيماءات السحب في BlackBerry Keyboard - Classic — اسحب للأعلى لاختيار اقتراح، واسحب جانبياً للحذف (الاتجاه قابل للتخصيص).**

![Build](https://github.com/snooptiger9/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/snooptiger9/bkey-keyboard)
![Downloads](https://img.shields.io/github/downloads/snooptiger9/bkey-keyboard/total)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/Languages-AR%20%7C%20EN%20%7C%20ES%20%7C%20HI-blue)
![License](https://img.shields.io/github/license/snooptiger9/bkey-keyboard)
![Last Commit](https://img.shields.io/github/last-commit/snooptiger9/bkey-keyboard)

إذا أفادك المشروع، نجمة <img src="docs/icons/star.svg" width="22" height="22" align="absmiddle" alt=""> تعني لنا الكثير.


---


<img src="docs/icons/link.svg" width="22" height="22" align="absmiddle" alt=""> **اختصارات سريعة:** [المميزات](#ar-features) · [الخصوصية والأمان](#ar-privacy) · [التحميل](#ar-download) · [ابنِه بنفسك](BUILDING.md) · [الأسئلة الشائعة](#ar-faq) · [المجتمع والإبلاغ عن الأخطاء](#ar-community) · [الدعم](#ar-support)

### الوصف <img src="docs/icons/sparkles.svg" width="24" height="24" align="absmiddle" alt="">

**bkey** كيبورد أندرويد (IME) مبني على إيماءات السحب في تطبيق **BlackBerry Keyboard - Classic**،
بتخصيص ومميزات أكثر: اسحب للأعلى من أي اقتراح لاختياره فوراً، واسحب جانبياً على صف الحروف (الاتجاه
قابل للتخصيص) للحذف بدل الضغط المتكرر على الباكسبيس. يدعم العربية والإنجليزية والإسبانية والهندية بالكامل
مع تجربة مخصصة بعناية لكل لغة.

<br>

<a id="ar-features"></a>

### المميزات <img src="docs/icons/rocket.svg" width="24" height="24" align="absmiddle" alt="">

<div dir="rtl">

<img src="docs/icons/gesture.svg" width="22" height="22" align="absmiddle" alt=""> **الإيماءات (Gestures)**  
‏• سحب للأعلى من أي اقتراح: قبوله فوراً‏.

![سحب للأعلى لقبول الاقتراح](docs/gifs/swipe_up_suggestion.gif?v=2)

‏• سحب أفقي فوق الحروف: حذف آخر كلمة كاملة دفعة واحدة (تقدر تختار اتجاه السحب من الإعدادات)‏.

![سحب جانبي لحذف آخر كلمة](docs/gifs/swipe_delete_word.gif?v=2)

‏• سحب بالاتجاه المعاكس تماماً: تحديد كل النص المكتوب دفعة واحدة‏.

![سحب بالاتجاه المعاكس لتحديد كل النص](docs/gifs/swipe_select_all.gif?v=2)

‏• سحب للأسفل فوق الحروف: نسخ/لصق أو قائمة الرموز (تختار من الإعدادات ← إيماءات السحب).

‏• ضغط مطوّل + سحب على زر المسافة: تحريك المؤشر حرفاً حرفاً‏.

![ضغط مطوّل وسحب زر المسافة لتحريك المؤشر](docs/gifs/cursor_move.gif?v=2)

‏• سحب زر المسافة يميناً/يساراً: التبديل بين اللغات المفعّلة (عربي/إنجليزي/إسباني/هندي) بدون زر لغة منفصل‏.  
‏• ضغط مطوّل على أي حرف: بوب أب الحروف البديلة (الكبيرة والمشكّلة والرموز المناسبة للغة المفعّلة).

<br>

<img src="docs/icons/keyboard.svg" width="22" height="22" align="absmiddle" alt=""> **الكتابة والتوقع الذكي**  
‏• تصحيح تلقائي صامت عند المسافة، وتوقع الكلمة التالية (حتى 3 كلمات)‏.  
‏• تعلّم الكلمات الجديدة أثناء الكتابة تلقائياً + مدرّب يدوي يحفظ جملة كاملة فوراً‏.  
‏• **كل ما تعلّم الكيبورد كلماتك أكثر، سحبت أكثر:** تصير الاقتراحات أدق مع تعلّم كلماتك.
‏• **التعلّم:** يحفظ حتى 10000 كلمة متعلَّمة، مع صفحة إحصائيات كتابة (أرقام فقط)، وشاشة للبحث في الكلمات وحذفها، ونسخ احتياطي واستعادة بملف JSON.
‏• **اقتراح إيموجي تلقائي** حسب الكلمة أو سياق الجملة المكتوبة‏.

![اقتراح إيموجي تلقائي](docs/gifs/emoji_suggest.gif?v=2)

‏• اختصارات نصية: اكتب اختصار (مثل "سع") ويتحول تلقائياً لعبارة كاملة عند المسافة‏.  
‏• نقطة ومسافة تلقائية بضغطتين متتاليتين على المسافة، وأحرف كبيرة تلقائياً في بداية الجملة (إنجليزي/إسباني).
‏• اقتراح أكواد التحقق (OTP) تلقائياً من الرسائل أو واتساب — كل المعالجة محلية على الجهاز *(نسخة bkey Plus فقط)*‏.

<br>

<img src="docs/icons/clipboard.svg" width="22" height="22" align="absmiddle" alt=""> **الحافظة الذكية**  
‏• درج حافظة: تثبيت، حذف، وحذف تلقائي للعناصر القديمة بعد المدة التي تختارها.
‏• حفظ آخر لقطة شاشة تلقائياً (مصغّرة واحدة تحل محل القديمة) *(نسخة bkey Plus فقط)*‏.  
‏• آخر نص منسوخ يظهر مباشرة بمنتصف شريط الاقتراحات لإعادة لصقه بسرعة‏.  
‏• أرقام الجوال والإيميلات المنسوخة تظهر معها شارة بضغطة واحدة للاتصال / كتابة رسالة جديدة (لا يتصل ولا يرسل نيابةً عنك).

<br>

<img src="docs/icons/smile.svg" width="22" height="22" align="absmiddle" alt=""> **إيموجي و GIF**  
‏• لوحة إيموجي ورموز مع قسم "مستخدم حديثاً".
‏• بحث GIF مدعوم بـ KLIPY. *(bkey Plus فقط)*

<br>

<img src="docs/icons/palette.svg" width="22" height="22" align="absmiddle" alt=""> **التخصيص الكامل**  
‏• 4 ثيمات ألوان + **تلقائي** (يتبع وضع أندرويد الفاتح/الداكن).
‏• 9 أشكال لأزرار الكيبورد وخطوط متعددة.
‏• تحكم بحجم الحروف والكيبورد والاقتراحات.
‏• لون زر الإدخال وأنميشن ارتداد المفاتيح وطيران الكلمة (كل واحد يُفعَّل لوحده).

<br>

<img src="docs/icons/speaker.svg" width="22" height="22" align="absmiddle" alt=""> **الصوت والاهتزاز**  
‏• نغمات كتابة ونغمات سحب/حذف بمستوى صوت مستقل.
‏• اهتزاز لمسي اختياري بقوة قابلة للتعديل.

<br>

<img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> **تفضيلات إضافية**  
‏• صف أرقام دائم، وأرقام عربية هندية (١٢٣) اختيارية، ووضع اليد الواحدة، وكتابة بالصوت (زر مايكروفون اختياري)، وبوب أب معاينة الحرف.
‏• **خصوصية أولاً**: وضع تخفي (Incognito) يوقف التعلّم وحفظ الحافظة، ولا يُتعلَّم من كلمات المرور والروابط أبداً.

</div>

<br>

<a id="ar-privacy"></a>

### الخصوصية والأمان <img src="docs/icons/lock.svg" width="24" height="24" align="absmiddle" alt="">

<div dir="rtl">

‏• **نسخة bkey** ما فيها اتصال إنترنت، وما تقدر تقرأ رسائل ولا إشعارات ولا صور ولا تخزين — هذي
  الصلاحيات غير موجودة بالتطبيق. الكتابة بالصوت تشتغل برضه، عبر خدمة التعرف على الصوت بجهازك
  مباشرة: تجرب التعرف على الصوت داخل الجهاز (بدون نت) أول شي، وما تحتاج اتصال إنترنت إلا إذا
  جهازك ما يدعم هذي الميزة‏.

‏• **نسخة bkey Plus** فيها بحث GIF، لصق الصور/الستيكرات، اقتراح رمز التحقق (OTP)، والتحديث التلقائي من داخل
  التطبيق. اقتراح OTP معطّل افتراضياً ويفحص بس إشعار الرسالة أو واتساب الجديد اللحظي — أبداً
  الرسائل القديمة — وكل المعالجة على الجهاز فقط‏.

> <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> **ملاحظة عن تحذير Play Protect بنسخة bkey Plus**
>
> نسخة bkey Plus فيها صلاحية الإنترنت (لبحث GIF والتحديث من داخل التطبيق) وصلاحية الوصول للصور (للصق
> الصور وآخر لقطة شاشة)، وهذا اللي يخلي Google Play Protect يصنفها "Harmful app"، تأكدنا بالتجربة.
> قدّمنا استئناف لجوجل بهذا التصنيف؛ لحد الحين هذا سلوك متوقع لأي تطبيق مُثبّت من خارج المتجر بهذي
> الصلاحيات، مو فيروس. ما تبي تشوفه؟ ثبّت نسخة bkey الأساسية بدالها‏.

</div>

<details>
<summary dir="rtl"><b>‏خطوات تعليق فحص Play Protect، لو تبي تثبت نسخة bkey Plus برضه (اضغط للتفصيل)</b></summary>

<br>

<div dir="rtl">

‏1. افتح تطبيق **متجر Google Play**.  
‏2. اضغط صورة حسابك بأعلى يمين الشاشة.  
‏3. اختر **Play Protect**.  
‏4. اضغط أيقونة الإعدادات <img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> بأعلى يمين الشاشة.  
‏5. أوقف خيار **Scan apps with Play Protect** — رح تطلع لك رسالة تسألك «Pause app scanning instead?»، اختر **Pause**.  
‏6. ثبّت ملف نسخة bkey Plus.  
‏7. خيار Pause يرجع يشتغل لوحده تلقائياً بعد يوم، لكن الأفضل ترجّعه بنفسك فوراً بعد التثبيت (يفضّل يضل شغّال دايماً لباقي تطبيقاتك).

</div>

</details>

<br>

<a id="ar-download"></a>

### التحميل <img src="docs/icons/download.svg" width="24" height="24" align="absmiddle" alt="">

نسختين — اختر اللي تناسبك:

<div dir="rtl">

| | <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> نسخة bkey (موصى بها) | <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> نسخة bkey Plus |
|---|:---:|:---:|
| تتثبت بدون أي تحذير من Play Protect | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> شوف الملاحظة تحت |
| بحث GIF ولصق الصور/الستيكرات | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| تحديث تلقائي من داخل التطبيق | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| اقتراح رمز التحقق (OTP) | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> (اختياري، معطّل افتراضياً) |
| حفظ لقطة الشاشة بالحافظة | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| اقتراح آخر نص منسوخ | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |

</div>

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-v308.apk"><img src="docs/images/badges/dl_bkey_ar.png" alt="تحميل bkey (APK)" width="300"></a>
&nbsp;
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-plus-v308.apk"><img src="docs/images/badges/dl_plus_ar.png" alt="تحميل bkey Plus (APK)" width="300"></a>
</p>

**نسخة bkey** ما تطلب إلا صلاحيات الكتابة فقط (بدون إنترنت أو صور/تخزين أو رسائل)، فميزات Plus في الجدول غير موجودة فيها. **نسخة bkey Plus** فيها كل شي، بس تثبيتها يسبب تحذير من Google Play Protect (متوقع، مو مشكلة حقيقية — شوف [الخصوصية والأمان](#ar-privacy)).

تبي تحديثات تلقائية؟ أضف النسخة اللي تستخدمها إلى [Obtainium](https://github.com/ImranR98/Obtainium) بضغطة وحدة، وأضف وحدة فقط (النسختان لهما نفس اسم الحزمة).

<p>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_bkey_ar.png" alt="أضف bkey إلى Obtainium" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%20Plus%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-plus-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_plus_ar.png" alt="أضف bkey Plus إلى Obtainium" width="300"></a>
</p>

> [!NOTE]
> نسخة bkey Plus تتحدث أيضاً من داخل التطبيق (الإعدادات ← **تحديث التطبيق**).

### [ابنِه بنفسك](BUILDING.md) <img src="docs/icons/wrench.svg" width="24" height="24" align="absmiddle" alt="">

<br>

<a id="ar-faq"></a>

### الأسئلة الشائعة <img src="docs/icons/help.svg" width="24" height="24" align="absmiddle" alt="">

<div dir="rtl">

<details>
<summary><b>هل الكيبورد يرسل أي بيانات أو كلمات أكتبها للإنترنت؟</b></summary>
<br>

لا إطلاقاً — كل التعلّم والتصحيح والتوقع يصير محلياً على جهازك. شوف [الخصوصية والأمان](#ar-privacy) لتفاصيل اللي تقدر نسخة bkey ونسخة bkey Plus يوصلون له بالضبط.

</details>
<details>
<summary><b>ليش قوقل بروتكت يحذّرني عند تثبيت نسخة bkey Plus؟</b></summary>
<br>

لأن الإنترنت وصلاحية الصور مع بعض يفعّلون التحذير لأي تطبيق مُثبّت من خارج المتجر — سلوك متوقع، مو فيروس. شوف [الخصوصية والأمان](#ar-privacy) للتفاصيل أو لتعليق الفحص.

</details>
<details>
<summary><b>وين تتحفظ الكلمات اللي يتعلمها الكيبورد، وكيف أمسحها؟</b></summary>
<br>

تنحفظ محلياً على جهازك فقط. من إعدادات التطبيق قسم تدريب التوقعات، زر **"مسح جميع الكلمات المتعلمة"** يمسحها كلها فوراً.

</details>
<details>
<summary><b>هل يستهلك الكيبورد بطارية أو ذاكرة كثير؟</b></summary>
<br>

لا — خفيف ويشتغل بالكامل محلياً بدون أي معالجة بالسحابة.

</details>

</div>

<br>

<a id="ar-community"></a>

### المجتمع والإبلاغ عن الأخطاء <img src="docs/icons/users.svg" width="24" height="24" align="absmiddle" alt="">

<div dir="rtl">

من الإعدادات ← **سجل الأخطاء** ← **"إرسال للمطوّر"** يرسل تفاصيل أي خطأ مباشرة لـ **lvl60me@gmail.com** — نفس الإيميل للاقتراحات والملاحظات، وهو الطريقة الوحيدة للتواصل مع المطوّر.

</div>

<br>

<a id="ar-support"></a>

### الدعم <img src="docs/icons/mail.svg" width="24" height="24" align="absmiddle" alt="">

إذا عجبك المشروع وتبي تدعمه، تقدر تدعمني عبر Ko-fi:

[![ادعمني عبر Ko-fi](https://img.shields.io/badge/Ko--fi-ادعم_bkey-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/snooptiger)

---

<p align="center">Made with <img src="docs/icons/heart.svg" width="22" height="22" align="absmiddle" alt=""> — bkey</p>
