<p align="center"><a href="README.md">English</a> | <a href="README.ar.md">العربية</a> | <a href="README.es.md">Español</a> | <a href="README.fr.md">Français</a> | <b>हिन्दी</b></p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="docs/images/banner-light.png?v=2">
    <img src="docs/images/banner.png?v=2" alt="bkey — BlackBerry शैली का Android कीबोर्ड: सुझाव स्वीकारने के लिए ऊपर स्वाइप करें और मिटाने के लिए बग़ल में स्वाइप करें" width="100%">
  </picture>
</p>

# <img src="docs/icons/keyboard.svg" width="36" height="36" align="absmiddle" alt=""> bkey

**BlackBerry Keyboard - Classic के जेस्चर पर आधारित एक Android कीबोर्ड: सुझाव चुनने के लिए ऊपर स्वाइप करें और मिटाने के लिए बग़ल में स्वाइप करें (दिशा बदली जा सकती है)।**

![Build](https://github.com/snooptiger9/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/snooptiger9/bkey-keyboard)
![Downloads](https://img.shields.io/github/downloads/snooptiger9/bkey-keyboard/total)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/Languages-AR%20%7C%20EN%20%7C%20ES%20%7C%20HI%20%7C%20FR%20%7C%20UR-blue)
![License](https://img.shields.io/github/license/snooptiger9/bkey-keyboard)
![Last Commit](https://img.shields.io/github/last-commit/snooptiger9/bkey-keyboard)
[![APKPure](https://img.shields.io/badge/APKPure-v306.0-blue)](https://apkpure.com/p/com.snooptiger9.bkey)
[![IzzyOnDroid](https://img.shields.io/badge/dynamic/json?url=https%3A%2F%2Fapt.izzysoft.de%2Ffdroid%2Fapi%2Fv1%2Fshield%2Fcom.snooptiger9.bkey&query=%24.message&prefix=v&label=IzzyOnDroid&color=blue)](https://apt.izzysoft.de/packages/com.snooptiger9.bkey)

अगर यह आपके काम आए, तो एक <img src="docs/icons/star.svg" width="22" height="22" align="absmiddle" alt=""> स्टार से बहुत हौसला मिलेगा।


---


<img src="docs/icons/link.svg" width="22" height="22" align="absmiddle" alt=""> **त्वरित लिंक:** [सुविधाएँ](#hi-features) · [गोपनीयता और सुरक्षा](#hi-privacy) · [डाउनलोड](#hi-download) · [खुद बनाएँ](BUILDING.md) · [अक्सर पूछे जाने वाले प्रश्न](#hi-faq) · [समुदाय और बग](#hi-community) · [रोडमैप](#hi-roadmap) · [सहयोग](#hi-support)

### परिचय <img src="docs/icons/sparkles.svg" width="24" height="24" align="absmiddle" alt="">

**bkey** एक Android कीबोर्ड (IME) है जो **BlackBerry Keyboard - Classic** ऐप के स्वाइप जेस्चर पर आधारित है, और
इसमें ज़्यादा कस्टमाइज़ेशन और अतिरिक्त सुविधाएँ हैं: किसी भी सुझाव पर ऊपर स्वाइप करके उसे तुरंत स्वीकार करें,
और अक्षरों की पंक्ति पर बग़ल में स्वाइप करके (दिशा आप चुनते हैं) बार-बार बैकस्पेस दबाने के बजाय तेज़ी से मिटाएँ।
यह अरबी, अंग्रेज़ी, स्पेनिश, हिन्दी, फ़्रेंच और उर्दू को पूरी तरह सपोर्ट करता है, और हर भाषा के लिए अलग से सोच-समझकर बनाया गया अनुभव देता है।

<br>

<a id="hi-features"></a>

### सुविधाएँ <img src="docs/icons/rocket.svg" width="24" height="24" align="absmiddle" alt="">

<img src="docs/icons/gesture.svg" width="22" height="22" align="absmiddle" alt=""> **जेस्चर**
- किसी भी सुझाव पर ऊपर स्वाइप करें: वह तुरंत स्वीकार हो जाता है।

  ![सुझाव स्वीकारने के लिए ऊपर स्वाइप करें](docs/gifs/swipe_up_suggestion.gif?v=2)
- कुंजियों पर बग़ल में स्वाइप करें: आख़िरी पूरा शब्द एक बार में मिट जाता है (दिशा सेटिंग्स में चुनें)।

  ![आख़िरी शब्द मिटाने के लिए बग़ल में स्वाइप करें](docs/gifs/swipe_delete_word.gif?v=2)
- ठीक विपरीत दिशा में स्वाइप करें: लिखा हुआ पूरा टेक्स्ट एक साथ चुना जाता है।

  ![सब कुछ चुनने के लिए विपरीत दिशा में स्वाइप करें](docs/gifs/swipe_select_all.gif?v=2)
- कुंजियों पर नीचे स्वाइप करें: कॉपी/पेस्ट या चिह्न-लेआउट (सेटिंग्स → स्वाइप जेस्चर में चुनें)।
- स्पेस बार को दबाए रखकर खींचें: कर्सर एक-एक अक्षर आगे-पीछे होता है।

  ![कर्सर हिलाने के लिए स्पेस बार दबाए रखें और खींचें](docs/gifs/cursor_move.gif?v=2)
- स्पेस बार को बाएँ/दाएँ स्वाइप करें: अलग भाषा-बटन के बिना, चालू की गई भाषाओं (अरबी/अंग्रेज़ी/स्पेनिश/हिन्दी/फ़्रेंच/उर्दू) के बीच बदलें।
- फ़्रेंच डिफ़ॉल्ट रूप से AZERTY लेआउट इस्तेमाल करता है; QWERTY सेटिंग्स → भाषाएँ में चुना जा सकता है।
- किसी भी कुंजी को दबाए रखें: वैकल्पिक अक्षरों वाली विंडो (सक्रिय भाषा के हिसाब से बड़े अक्षर, उच्चारण-चिह्न और चिह्न)।

<br>

<img src="docs/icons/keyboard.svg" width="22" height="22" align="absmiddle" alt=""> **स्मार्ट टाइपिंग और अनुमान**
- स्पेस दबाने पर चुपचाप ऑटो-करेक्ट और अगले शब्द का अनुमान (3 शब्दों तक)।
- टाइप करते समय शब्द सीखना, साथ में एक मैनुअल ट्रेनर जो पूरा वाक्यांश तुरंत सहेज लेता है।
- **जितना ज़्यादा सीखे, उतना ज़्यादा स्वाइप:** आपके शब्द सीखने के साथ सुझाव और सटीक होते जाते हैं।
- **सीखना:** 10,000 तक सीखे हुए शब्द रखता है, साथ में टाइपिंग आँकड़ों का पृष्ठ (केवल गिनती), सीखे शब्द खोजने और हटाने की स्क्रीन, और JSON फ़ाइल में बैकअप/रीस्टोर।
- आप जो शब्द या वाक्य लिखते हैं उसके संदर्भ के हिसाब से **अपने-आप इमोजी सुझाव**।

  ![अपने-आप इमोजी सुझाव](docs/gifs/emoji_suggest.gif?v=2)
- टेक्स्ट शॉर्टकट: एक शॉर्टकट लिखें (जैसे "omw") और स्पेस दबाते ही वह पूरे वाक्यांश में बदल जाता है।
- स्पेस पर डबल टैप से पूर्णविराम + स्पेस (हिन्दी में "।"), और वाक्य का पहला अक्षर अपने-आप बड़ा (अंग्रेज़ी/स्पेनिश)।
- WhatsApp और मैसेज ऐप की नोटिफ़िकेशन से सत्यापन कोड (OTP) के अपने-आप सुझाव — सारा प्रोसेसिंग डिवाइस पर होता है। *(केवल bkey Plus)*

<br>

<img src="docs/icons/clipboard.svg" width="22" height="22" align="absmiddle" alt=""> **स्मार्ट क्लिपबोर्ड**
- क्लिपबोर्ड दराज़: पिन करें, हटाएँ, और आपके चुने समय के बाद पुराने आइटम अपने-आप हटाएँ।
- आख़िरी स्क्रीनशॉट अपने-आप सहेजता है (एक ही थंबनेल जो पिछले को बदल देता है)। *(केवल bkey Plus)*
- आख़िरी कॉपी किया टेक्स्ट बीच वाले सुझाव खाने में दिखता है ताकि दोबारा तेज़ी से पेस्ट हो सके।
- कॉपी किए फ़ोन नंबर और ईमेल पते पर एक-टैप कॉल / नया-मेल बटन दिखता है (आपकी ओर से कभी कॉल या भेजता नहीं)।

<br>

<img src="docs/icons/smile.svg" width="22" height="22" align="absmiddle" alt=""> **इमोजी और GIF**
- "हाल में इस्तेमाल किए" अनुभाग वाला इमोजी और चिह्न पैनल।
- KLIPY से चलने वाली GIF खोज। *(केवल bkey Plus)*

<br>

<img src="docs/icons/palette.svg" width="22" height="22" align="absmiddle" alt=""> **पूरा कस्टमाइज़ेशन**
- 4 रंग थीम और **Auto** (Android के हल्के/गहरे मोड के साथ चलता है)।
- 9 कुंजी आकार और कई फ़ॉन्ट।
- अक्षरों, कीबोर्ड और सुझावों के आकार पर नियंत्रण।
- Enter कुंजी का रंग, कुंजी उछाल और उड़ते शब्द के एनिमेशन (अलग-अलग चालू/बंद)।

<br>

<img src="docs/icons/speaker.svg" width="22" height="22" align="absmiddle" alt=""> **ध्वनि और कंपन**
- कई टाइपिंग ध्वनियाँ और स्वाइप/मिटाने की ध्वनियाँ, अलग-अलग आवाज़ के साथ।
- वैकल्पिक हैप्टिक कंपन, तीव्रता बदली जा सकती है।

<br>

<img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> **और प्राथमिकताएँ**
- स्थायी अंक पंक्ति, वैकल्पिक इंडो-अरबी अंक (١٢٣), एक हाथ का मोड, आवाज़ से टाइपिंग (वैकल्पिक माइक बटन) और कुंजी पूर्वावलोकन विंडो।
- **गोपनीयता सबसे पहले**: इन्कॉग्निटो मोड सीखना और क्लिपबोर्ड सहेजना बंद कर देता है; पासवर्ड और लिंक कभी नहीं सीखे जाते।

<br>

<a id="hi-privacy"></a>

### गोपनीयता और सुरक्षा <img src="docs/icons/lock.svg" width="24" height="24" align="absmiddle" alt="">

- **bkey** (मानक संस्करण) के पास नेटवर्क एक्सेस नहीं है और यह SMS, नोटिफ़िकेशन, फ़ोटो या स्टोरेज नहीं पढ़ सकता: ये
  अनुमतियाँ ऐप में हैं ही नहीं। आवाज़ से टाइपिंग फिर भी काम करती है, जिसे पूरी तरह आपके डिवाइस की
  स्पीच-रिकग्निशन सेवा संभालती है: यह पहले डिवाइस पर (ऑफ़लाइन) पहचान आज़माती है और केवल तब इंटरनेट का
  सहारा लेती है जब आपका डिवाइस इसे सपोर्ट न करे।

- **bkey Plus** में GIF खोज, छवि/स्टिकर पेस्ट, OTP सुझाव और इन-ऐप अपडेटर जुड़ते हैं। OTP सुझाव
  डिफ़ॉल्ट रूप से बंद रहते हैं और केवल अभी-अभी आई WhatsApp या मैसेज ऐप की नोटिफ़िकेशन पढ़ते हैं, आपका संदेश इतिहास कभी नहीं;
  सारा प्रोसेसिंग डिवाइस पर होता है और किसी सर्वर को कभी नहीं भेजा जाता।

> <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> **bkey Plus पर Play Protect की चेतावनी के बारे में**
>
> bkey Plus के पास इंटरनेट एक्सेस (GIF खोज और अपडेट जाँच के लिए) और फ़ोटो एक्सेस
> (छवि पेस्ट और क्लिपबोर्ड में स्क्रीनशॉट सुझाव के लिए) है, और इसी वजह से Google Play Protect इसे
> "हानिकारक ऐप" बता देता है, जिसे हमने अपने परीक्षणों में भी पुष्ट किया है। हमने इस ग़लत पहचान के ख़िलाफ़
> Google में अपील की है; जब तक यह सुलझती नहीं, इन अनुमतियों वाले हाथ से इंस्टॉल किए गए ऐप के लिए यह अपेक्षित व्यवहार है,
> मैलवेयर नहीं। इसे नहीं देखना चाहते? मानक bkey संस्करण इंस्टॉल करें।

<details>
<summary><b>अगर फिर भी bkey Plus इंस्टॉल करना चाहें तो Play Protect स्कैन रोकने के चरण (देखने के लिए टैप करें)</b></summary>

<br>

1. **Google Play Store** ऐप खोलें।
2. ऊपर दाईं ओर अपनी प्रोफ़ाइल फ़ोटो पर टैप करें।
3. **Play Protect** पर टैप करें।
4. ऊपर दाईं ओर सेटिंग्स आइकन <img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> पर टैप करें।
5. **Play Protect से ऐप्स स्कैन करें** बंद करें; यह पूछेगा "ऐप स्कैनिंग रोकें?", **रोकें** चुनें।
6. bkey Plus का APK इंस्टॉल करें।
7. रोक एक दिन बाद अपने-आप फिर चालू हो जाती है, लेकिन बेहतर है कि इंस्टॉल के तुरंत बाद आप ख़ुद इसे चालू कर दें (आपके दूसरे ऐप्स के लिए इसे चालू रखने की सलाह है)।

</details>

<br>

<a id="hi-download"></a>

### डाउनलोड <img src="docs/icons/download.svg" width="24" height="24" align="absmiddle" alt="">

दो संस्करण: जो आपके लिए बेहतर हो वह चुनें:

| | <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> bkey (अनुशंसित) | <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> bkey Plus |
|---|:---:|:---:|
| Play Protect चेतावनी के बिना इंस्टॉल होता है | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> [देखें गोपनीयता और सुरक्षा](#hi-privacy) |
| GIF खोज और छवि/स्टिकर पेस्ट | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| इन-ऐप अपडेट जाँच | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| सत्यापन कोड (OTP) सुझाव | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> (वैकल्पिक, डिफ़ॉल्ट रूप से बंद) |
| क्लिपबोर्ड में स्क्रीनशॉट | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| कॉपी किए टेक्स्ट का सुझाव | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |

#### <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> bkey (अनुशंसित)

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-v318.apk"><img src="docs/images/badges/dl_bkey_en.png" alt="bkey (APK) डाउनलोड करें" width="300"></a>
&nbsp;
<a href="https://apkpure.com/p/com.snooptiger9.bkey"><img src="docs/images/badges/ap_bkey_en.png" alt="bkey को APKPure से पाएँ" width="300"></a>
&nbsp;
<a href="https://apt.izzysoft.de/packages/com.snooptiger9.bkey"><img src="docs/images/badges/iz_bkey_en.png" alt="bkey को IzzyOnDroid से पाएँ" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_bkey_en.png" alt="bkey को Obtainium में जोड़ें" width="300"></a>
</p>

#### <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> bkey Plus

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-plus-v318.apk"><img src="docs/images/badges/dl_plus_en.png" alt="bkey Plus (APK) डाउनलोड करें" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%20Plus%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-plus-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_plus_en.png" alt="bkey Plus को Obtainium में जोड़ें" width="300"></a>
</p>

> [!NOTE]
> bkey Plus ऐप के अंदर से भी अपडेट हो सकता है (सेटिंग्स → **ऐप के बारे में और रखरखाव** → **ऐप अपडेट करें**)। Obtainium में केवल एक ही संस्करण जोड़ें (दोनों का पैकेज नाम एक ही है)।

<br>

<br>

### [खुद बनाएँ](BUILDING.md) <img src="docs/icons/wrench.svg" width="24" height="24" align="absmiddle" alt="">

<br>

<a id="hi-faq"></a>

### अक्सर पूछे जाने वाले प्रश्न <img src="docs/icons/help.svg" width="24" height="24" align="absmiddle" alt="">

<details>
<summary><b>क्या कीबोर्ड मेरा टाइप किया इंटरनेट पर भेजता है?</b></summary>
<br>

नहीं, कभी नहीं: सारा सीखना, सुधार और अनुमान आपके डिवाइस पर ही होता है। हर संस्करण, bkey और bkey Plus, ठीक किस चीज़ तक पहुँच सकता है यह जानने के लिए [गोपनीयता और सुरक्षा](#hi-privacy) देखें।

</details>
<details>
<summary><b>bkey Plus इंस्टॉल करते समय Google Play Protect चेतावनी क्यों देता है?</b></summary>
<br>

इंटरनेट और फ़ोटो एक्सेस एक साथ होने पर हाथ से इंस्टॉल किए किसी भी ऐप पर यह लगती है: यह अपेक्षित है, मैलवेयर नहीं। कारण जानने या स्कैन रोकने के लिए [गोपनीयता और सुरक्षा](#hi-privacy) देखें।

</details>
<details>
<summary><b>मेरे सीखे हुए शब्द कहाँ सहेजे जाते हैं और उन्हें कैसे मिटाऊँ?</b></summary>
<br>

वे केवल आपके डिवाइस पर सहेजे जाते हैं। ऐप की सेटिंग्स में, अनुमान प्रशिक्षण अनुभाग के अंदर, **"Clear all learned words"** बटन उन्हें तुरंत मिटा देता है।

</details>
<details>
<summary><b>क्या यह बहुत बैटरी या मेमोरी खाता है?</b></summary>
<br>

नहीं: यह हल्का है और पूरी तरह डिवाइस पर चलता है, बैकग्राउंड में कोई क्लाउड प्रोसेसिंग नहीं होती।

</details>

<br>

<a id="hi-community"></a>

### समुदाय और बग <img src="docs/icons/users.svg" width="24" height="24" align="absmiddle" alt="">

सेटिंग्स → **ऐप के बारे में और रखरखाव** → **Error log** → **"Email developer"** क्रैश का विवरण सीधे **lvl60me@gmail.com** पर भेजता है: सुझाव या फ़ीडबैक के लिए भी यही पता है, और डेवलपर से संपर्क का यही एकमात्र तरीक़ा है।
**Google Play पर bkey की टेस्टिंग में मदद करना चाहते हैं?** Google Play पर इस्तेमाल होने वाले अपने Google अकाउंट का पता **lvl60me@gmail.com** पर भेजें, मैं आपको क्लोज़्ड टेस्ट में जोड़ दूँगा।


<br>

<a id="hi-roadmap"></a>

### रोडमैप <img src="docs/icons/map.svg" width="24" height="24" align="absmiddle" alt="">

<a href="ROADMAP.md"><img src="docs/images/roadmap-card.svg" alt="रोडमैप" width="520"></a>

bkey में आगे: **Google Play**, **इंडोनेशियाई भाषा** और **एक साथ कई भाषाओं में टाइपिंग**।

[**पूरा रोडमैप देखें (अंग्रेज़ी में)**](ROADMAP.md)

<br>

<a id="hi-support"></a>

### सहयोग <img src="docs/icons/mail.svg" width="24" height="24" align="absmiddle" alt="">

अगर आपको प्रोजेक्ट पसंद है और आप उसका साथ देना चाहते हैं, तो Ko-fi पर मुझे एक कॉफ़ी पिला सकते हैं:

[![Ko-fi पर सहयोग करें](https://img.shields.io/badge/Ko--fi-Support_bkey-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/snooptiger)

**लाइसेंस:** GPL v3. आप इसे उपयोग और संशोधित कर सकते हैं, बशर्ते कोड उसी लाइसेंस के तहत खुला रहे और इस प्रोजेक्ट का उल्लेख हो। "bkey" नाम और इसका लोगो लाइसेंस में शामिल नहीं हैं।

---

<p align="center"><img src="docs/icons/heart.svg" width="22" height="22" align="absmiddle" alt=""> के साथ बनाया — bkey</p>
