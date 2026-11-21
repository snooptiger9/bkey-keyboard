<p align="center"><a href="README.md">English</a> | <a href="README.ar.md">العربية</a> | <a href="README.es.md">Español</a> | <b>हिन्दी</b></p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="docs/images/banner-light.png?v=2">
    <img src="docs/images/banner.png?v=2" alt="bkey — BlackBerry शैली का Android कीबोर्ड: सुझाव स्वीकारने के लिए ऊपर स्वाइप करें और मिटाने के लिए बग़ल में स्वाइप करें" width="100%">
  </picture>
</p>

# <img src="docs/icons/keyboard.svg" width="34" height="34" align="absmiddle" alt=""> bkey

**BlackBerry Keyboard - Classic के जेस्चर पर आधारित एक Android कीबोर्ड: सुझाव चुनने के लिए ऊपर स्वाइप करें और मिटाने के लिए बग़ल में स्वाइप करें (दिशा बदली जा सकती है)।**

![Build](https://github.com/snooptiger9/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/snooptiger9/bkey-keyboard)
![Downloads](https://img.shields.io/github/downloads/snooptiger9/bkey-keyboard/total)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/Languages-AR%20%7C%20EN%20%7C%20ES%20%7C%20HI-blue)
![License](https://img.shields.io/github/license/snooptiger9/bkey-keyboard)
![Last Commit](https://img.shields.io/github/last-commit/snooptiger9/bkey-keyboard)

अगर यह आपके काम आए, तो एक <img src="docs/icons/star.svg" width="22" height="22" align="absmiddle" alt=""> स्टार से बहुत हौसला मिलेगा।



---


<img src="docs/icons/link.svg" width="22" height="22" align="absmiddle" alt=""> **त्वरित लिंक:** [सुविधाएँ](#hi-features) · [गोपनीयता और सुरक्षा](#hi-privacy) · [डाउनलोड](#hi-download) · [खुद बनाएँ](BUILDING.md) · [अक्सर पूछे जाने वाले प्रश्न](#hi-faq) · [समुदाय और बग](#hi-community) · [सहयोग](#hi-support)

### परिचय <img src="docs/icons/sparkles.svg" width="22" height="22" align="absmiddle" alt="">

**bkey** एक Android कीबोर्ड (IME) है जो **BlackBerry Keyboard - Classic** ऐप के स्वाइप जेस्चर पर आधारित है, और
इसमें ज़्यादा कस्टमाइज़ेशन और अतिरिक्त सुविधाएँ हैं: किसी भी सुझाव पर ऊपर स्वाइप करके उसे तुरंत स्वीकार करें,
और अक्षरों की पंक्ति पर बग़ल में स्वाइप करके (दिशा आप चुनते हैं) बार-बार बैकस्पेस दबाने के बजाय तेज़ी से मिटाएँ।
यह अरबी, अंग्रेज़ी, स्पेनिश और हिन्दी को पूरी तरह सपोर्ट करता है, और हर भाषा के लिए अलग से सोच-समझकर बनाया गया अनुभव देता है।

<br>

<a id="hi-features"></a>

### सुविधाएँ <img src="docs/icons/rocket.svg" width="22" height="22" align="absmiddle" alt="">

<img src="docs/icons/gesture.svg" width="22" height="22" align="absmiddle" alt=""> **जेस्चर**
- किसी भी सुझाव पर ऊपर स्वाइप करें: वह तुरंत स्वीकार हो जाता है।

  ![सुझाव स्वीकारने के लिए ऊपर स्वाइप करें](docs/gifs/swipe_up_suggestion.gif?v=2)
- कुंजियों पर बग़ल में स्वाइप करें: आख़िरी पूरा शब्द एक बार में मिट जाता है (दिशा सेटिंग्स में चुनें)।

  ![आख़िरी शब्द मिटाने के लिए बग़ल में स्वाइप करें](docs/gifs/swipe_delete_word.gif?v=2)
- ठीक विपरीत दिशा में स्वाइप करें: लिखा हुआ पूरा टेक्स्ट एक साथ चुना जाता है।

  ![सब कुछ चुनने के लिए विपरीत दिशा में स्वाइप करें](docs/gifs/swipe_select_all.gif?v=2)
- कुंजियों पर नीचे स्वाइप करें: क्या हो, यह आप तय करते हैं (सेटिंग्स → स्वाइप जेस्चर; दोनों
    विकल्प एक साथ चालू नहीं हो सकते, एक चालू करने पर दूसरा बंद हो जाता है):
  - **कॉपी / पेस्ट:** चुना हुआ टेक्स्ट कॉपी करता है, और वही जेस्चर एक मिनट तक पेस्ट बन जाता है।
  - **अंक और चिह्न:** आप जिस भाषा में लिख रहे हैं उसका चिह्न-लेआउट खोलता है; चिह्नों में दोबारा नीचे
    स्वाइप करने पर अक्षरों पर लौट आते हैं। यह हर भाषा में अलग-अलग काम करता है।
- स्पेस बार को दबाए रखकर खींचें: कर्सर एक-एक अक्षर आगे-पीछे होता है।

  ![कर्सर हिलाने के लिए स्पेस बार दबाए रखें और खींचें](docs/gifs/cursor_move.gif?v=2)
- स्पेस बार को बाएँ/दाएँ स्वाइप करें: अलग भाषा-बटन के बिना, चालू की गई भाषाओं (अरबी/अंग्रेज़ी/स्पेनिश/हिन्दी) के बीच बदलें।
- किसी भी कुंजी को दबाए रखें: वैकल्पिक अक्षरों वाली विंडो खुलती है। अंग्रेज़ी कीबोर्ड में छोटा अक्षर अपना बड़ा रूप देता है (BlackBerry शैली), और अंकों की पंक्ति केवल Shift या Caps Lock चालू होने पर दिखती है। जो चिह्न पहले से चिह्न-पृष्ठ पर हैं वे दोहराए नहीं जाते, उच्चारण-चिह्न वाले अक्षर केवल स्पेनिश कीबोर्ड में दिखते हैं, और केवल अरबी के चिह्न (﷼ ، ؛ ؟) तभी दिखते हैं जब अरबी चालू हो।

<br>

<img src="docs/icons/keyboard.svg" width="22" height="22" align="absmiddle" alt=""> **स्मार्ट टाइपिंग और अनुमान**
- स्पेस दबाने पर चुपचाप ऑटो-करेक्ट और अगले शब्द का अनुमान (3 शब्दों तक)।
- टाइप करते समय शब्द सीखना, साथ में एक मैनुअल ट्रेनर जो पूरा वाक्यांश तुरंत सहेज लेता है।
- **जितना ज़्यादा सीखे, उतना ज़्यादा स्वाइप:** जैसे-जैसे कीबोर्ड आपके शब्द सीखता है, उसके सुझाव ज़्यादा सटीक होते जाते हैं, इसलिए आप जो भी लिखते हैं वह लगभग हर बार सही सुझाव पर ऊपर स्वाइप बन जाता है, हर अक्षर टाइप करने की जगह।
- **सीखने की सीमाएँ:** 10,000 तक सीखे हुए शब्द रखता है (सबसे कम इस्तेमाल वाले पहले हटते हैं) और हर शब्द के लिए 32 तक संभावित अगले शब्द (सबसे कमज़ोर पहले हटते हैं)।
- **टाइपिंग आँकड़े:** सेटिंग्स का एक पृष्ठ दिखाता है कि कीबोर्ड ने कितने शब्द सीखे, कितने आपने ऊपर स्वाइप करके चुने, आपके स्वाइप किए शब्दों का अनुपात, बचाए गए कीस्ट्रोक, एक ही स्वाइप से मिटाए गए शब्द, और भाषाओं के बीच आपका बँटवारा (केवल गिनती; कोई टेक्स्ट सहेजा नहीं जाता)।
- **सीखे हुए शब्दों की स्क्रीन:** अपने शब्द खोजें, देखें कि हर शब्द कितनी बार इस्तेमाल हुआ और उसके अगले सुझाव क्या हैं, और जो चाहें हटा दें।
- **बैकअप और रीस्टोर:** सीखे हुए शब्द और टेक्स्ट शॉर्टकट एक JSON फ़ाइल में सहेजें और नए फ़ोन पर बहाल करें। रीस्टोर केवल जोड़ता है; आपके मौजूदा डेटा को कभी नहीं मिटाता।
- आप जो शब्द या वाक्य लिखते हैं उसके संदर्भ के हिसाब से **अपने-आप इमोजी सुझाव**।

  ![अपने-आप इमोजी सुझाव](docs/gifs/emoji_suggest.gif?v=2)
- टेक्स्ट शॉर्टकट: एक शॉर्टकट लिखें (जैसे "omw") और स्पेस दबाते ही वह पूरे वाक्यांश में बदल जाता है।
- स्पेस पर डबल टैप करने से अपने-आप पूर्णविराम + स्पेस (हिन्दी में "।" + स्पेस)।
- हर वाक्य का पहला अक्षर अपने-आप बड़ा (अंग्रेज़ी/स्पेनिश)।
- **हिन्दी (देवनागरी) कीबोर्ड:** मानक InScript लेआउट, Shift परत के साथ, संयुक्ताक्षर (क्ष, त्र, ज्ञ, श्र) और नुक़्ता वाले अक्षर लंबे दबाव पर, देवनागरी अंक, ₹ और दंड (।)। इसका अपना शब्दकोश, अगले शब्द का अनुमान, इमोजी सुझाव और सीखे हुए शब्द हैं, जो दूसरी भाषाओं से अलग रहते हैं, और हिन्दी में ऑटो-करेक्ट केवल तभी होता है जब ग़लती निश्चित हो।
- SMS या WhatsApp से सत्यापन कोड (OTP) के अपने-आप सुझाव — सारा प्रोसेसिंग डिवाइस पर होता है। *(केवल bkey Plus)*

<br>

<img src="docs/icons/clipboard.svg" width="22" height="22" align="absmiddle" alt=""> **स्मार्ट क्लिपबोर्ड**
- पूरा क्लिपबोर्ड दराज़: पिन करें, हटाएँ, और आपके चुने समय (1 घंटे से 30 दिन, या कभी नहीं) के बाद पुराने आइटम अपने-आप हटाएँ।
- आख़िरी स्क्रीनशॉट अपने-आप सहेजता है (एक ही थंबनेल जो पिछले को बदल देता है)। *(केवल bkey Plus)*
- आख़िरी कॉपी किया टेक्स्ट बीच वाले सुझाव खाने में दिखता है ताकि दोबारा तेज़ी से पेस्ट हो सके।
- कॉपी किए गए फ़ोन नंबरों पर एक-टैप कॉल बटन दिखता है जो नंबर भरकर डायलर खोलता है (आपकी ओर से कभी कॉल नहीं करता)।

<br>

<img src="docs/icons/smile.svg" width="22" height="22" align="absmiddle" alt=""> **इमोजी और GIF**
- "हाल में इस्तेमाल किए" अनुभाग के साथ पूरा इमोजी और चिह्न पैनल (पूरा साफ़ करें या एक इमोजी हटाने के लिए उसे दबाए रखें)।
- इमोजी पैनल का GIF टैब KLIPY GIF से चलता है: एनिमेटेड GIF खोजें और पूर्वावलोकन देखें, तैरते खोज बॉक्स के साथ जो ग्रिड को नहीं ढकता। *(केवल bkey Plus)*

<br>

<img src="docs/icons/palette.svg" width="22" height="22" align="absmiddle" alt=""> **पूरा कस्टमाइज़ेशन**
- 4 रंग थीम और **Auto** (आधा सफ़ेद, आधा काला): Android के दिन/रात मोड के साथ चलता है, दिन में सफ़ेद थीम और रात में मैट ब्लैक इस्तेमाल करता है।
- 9 कुंजी आकार और कई फ़ॉन्ट (System/Tajawal)।
- सुझाव पट्टी वही फ़ॉन्ट इस्तेमाल करती है जो आप कुंजियों के लिए चुनते हैं।
- आकार पर पूरा नियंत्रण: अक्षरों का आकार, कीबोर्ड की ऊँचाई, सुझाव पट्टी की ऊँचाई और सुझावों के टेक्स्ट का आकार।
- Enter कुंजी का रंग; तैरते ABC/मिटाएँ कैप्सूल अपने-आप आपकी थीम से मेल खाते हैं।
- कुंजी उछाल एनिमेशन और सुझाव स्वीकारने पर "उड़ता शब्द" एनिमेशन, दोनों अलग-अलग चालू/बंद किए जा सकते हैं।

<br>

<img src="docs/icons/speaker.svg" width="22" height="22" align="absmiddle" alt=""> **ध्वनि और कंपन**
- कई टाइपिंग ध्वनियाँ + स्वाइप/मिटाने की अलग ध्वनियाँ, हर एक की अपनी आवाज़।
- दबाने और स्वाइप करने पर वैकल्पिक हैप्टिक कंपन, तीव्रता बदली जा सकती है (5–50 ms, या सिस्टम की)।

<br>

<img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> **और प्राथमिकताएँ**
- कीबोर्ड के ऊपर स्थायी अंक पंक्ति (साथ ही पासवर्ड फ़ील्ड में अलग पंक्ति, भले ही सामान्य पंक्ति बंद हो)।
- वैकल्पिक इंडो-अरबी अंक (١٢٣)।
- एक हाथ का मोड (बायाँ या दायाँ)।
- आवाज़ से टाइपिंग (वैकल्पिक माइक्रोफ़ोन बटन)।
- कुंजी दबाने पर उसके ऊपर पूर्वावलोकन विंडो।
- **गोपनीयता सबसे पहले**: इन्कॉग्निटो मोड सीखना और क्लिपबोर्ड सहेजना पूरी तरह बंद कर देता है; पासवर्ड और लिंक कभी नहीं सीखे जाते, इन्कॉग्निटो के बाहर भी। ईमेल पते केवल तभी सहेजे जाते हैं जब वे सही लिखे हों।

<br>

<a id="hi-privacy"></a>

### गोपनीयता और सुरक्षा <img src="docs/icons/lock.svg" width="22" height="22" align="absmiddle" alt="">

- **bkey** (मानक संस्करण) के पास नेटवर्क एक्सेस नहीं है और यह SMS, नोटिफ़िकेशन, फ़ोटो या स्टोरेज नहीं पढ़ सकता: ये
  अनुमतियाँ ऐप में हैं ही नहीं। आवाज़ से टाइपिंग फिर भी काम करती है, जिसे पूरी तरह आपके डिवाइस की
  स्पीच-रिकग्निशन सेवा संभालती है: यह पहले डिवाइस पर (ऑफ़लाइन) पहचान आज़माती है और केवल तब इंटरनेट का
  सहारा लेती है जब आपका डिवाइस इसे सपोर्ट न करे।

- **bkey Plus** में GIF खोज, छवि/स्टिकर पेस्ट, OTP सुझाव और इन-ऐप अपडेटर जुड़ते हैं। OTP सुझाव
  डिफ़ॉल्ट रूप से बंद रहते हैं और केवल अभी-अभी आई SMS/WhatsApp नोटिफ़िकेशन पढ़ते हैं, आपका संदेश इतिहास कभी नहीं;
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

### डाउनलोड <img src="docs/icons/download.svg" width="22" height="22" align="absmiddle" alt="">

दो संस्करण: जो आपके लिए बेहतर हो वह चुनें:

| | <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> bkey (अनुशंसित) | <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> bkey Plus |
|---|:---:|:---:|
| Play Protect चेतावनी के बिना इंस्टॉल होता है | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> नीचे नोट देखें |
| GIF खोज और छवि/स्टिकर पेस्ट | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| इन-ऐप अपडेट जाँच | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| सत्यापन कोड (OTP) सुझाव | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> (वैकल्पिक, डिफ़ॉल्ट रूप से बंद) |
| क्लिपबोर्ड में स्क्रीनशॉट | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| कॉपी किए टेक्स्ट का सुझाव | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-v306.apk"><img src="docs/images/badges/dl_bkey_en.png" alt="bkey (APK) डाउनलोड करें" width="300"></a>
&nbsp;
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-plus-v306.apk"><img src="docs/images/badges/dl_plus_en.png" alt="bkey Plus (APK) डाउनलोड करें" width="300"></a>
</p>

**bkey** केवल वही अनुमतियाँ माँगता है जो टाइपिंग के लिए ज़रूरी हैं (इंटरनेट नहीं, फ़ोटो/स्टोरेज नहीं, SMS नहीं); ऊपर की कोई भी सुविधा यहाँ काम नहीं करती सिवाय कॉपी किए टेक्स्ट के सुझाव के, क्योंकि बाक़ी के लिए इंटरनेट या फ़ोटो एक्सेस चाहिए।

**bkey Plus** में सब कुछ है, लेकिन इंस्टॉल करते समय Google Play Protect की चेतावनी आती है (अपेक्षित है, कोई असली समस्या नहीं; कारण जानने के लिए [गोपनीयता और सुरक्षा](#hi-privacy) देखें)।

अपने-आप अपडेट चाहिए? जो संस्करण आप इस्तेमाल करते हैं उसे एक टैप में [Obtainium](https://github.com/ImranR98/Obtainium) में जोड़ें: **bkey** या **bkey Plus**। हर लिंक केवल अपना APK फ़ॉलो करता है, इसलिए एक दूसरे को कभी नहीं बदलता। दोनों का पैकेज नाम एक ही है, इसलिए Obtainium में केवल एक ही रखें।

<p>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_bkey_en.png" alt="bkey को Obtainium में जोड़ें" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%20Plus%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-plus-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_plus_en.png" alt="bkey Plus को Obtainium में जोड़ें" width="300"></a>
</p>

> [!CAUTION]
> - **bkey अपडेट करना:** इसी पृष्ठ से या ऊपर के **bkey** बटन से Obtainium के ज़रिए।
> - **bkey Plus अपडेट करना:** इसी पृष्ठ से, ऐप के अंदर से (सेटिंग्स → **ऐप अपडेट करें**) या ऊपर के **bkey Plus** बटन से Obtainium के ज़रिए। किसी भी तरह यह bkey Plus ही रहता है, कभी मानक bkey नहीं बनता।
> - **Obtainium:** दोनों संस्करणों का पैकेज नाम एक ही है, इसलिए केवल वही जोड़ें जो आप इस्तेमाल करते हैं। हर बटन केवल अपना APK फ़ॉलो करता है; ग़लत वाला जोड़ने से आपका संस्करण दूसरे से बदल जाएगा।

### [खुद बनाएँ](BUILDING.md) <img src="docs/icons/wrench.svg" width="22" height="22" align="absmiddle" alt="">

<br>

<a id="hi-faq"></a>

### अक्सर पूछे जाने वाले प्रश्न <img src="docs/icons/help.svg" width="22" height="22" align="absmiddle" alt="">

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

### समुदाय और बग <img src="docs/icons/users.svg" width="22" height="22" align="absmiddle" alt="">

सेटिंग्स → **Error log** → **"Email developer"** क्रैश का विवरण सीधे **lvl60me@gmail.com** पर भेजता है: सुझाव या फ़ीडबैक के लिए भी यही पता है, और डेवलपर से संपर्क का यही एकमात्र तरीक़ा है।

<br>

<a id="hi-support"></a>

### सहयोग <img src="docs/icons/mail.svg" width="22" height="22" align="absmiddle" alt="">

अगर आपको प्रोजेक्ट पसंद है और आप उसका साथ देना चाहते हैं, तो Ko-fi पर मुझे एक कॉफ़ी पिला सकते हैं:

[![Ko-fi पर सहयोग करें](https://img.shields.io/badge/Ko--fi-Support_bkey-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/snooptiger)

---

<p align="center"><img src="docs/icons/heart.svg" width="22" height="22" align="absmiddle" alt=""> के साथ बनाया — bkey</p>
