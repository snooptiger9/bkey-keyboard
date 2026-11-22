<p align="center"><b>English</b> | <a href="README.ar.md">العربية</a> | <a href="README.es.md">Español</a> | <a href="README.hi.md">हिन्दी</a></p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="docs/images/banner-light.png?v=2">
    <img src="docs/images/banner.png?v=2" alt="bkey — a BlackBerry-style Android keyboard, swipe up to accept a suggestion and swipe sideways to delete" width="100%">
  </picture>
</p>

# <img src="docs/icons/keyboard.svg" width="34" height="34" align="absmiddle" alt=""> bkey

**An Android keyboard built on BlackBerry Keyboard - Classic's swipe gestures — swipe up to pick a suggestion, swipe sideways to delete (direction configurable).**

![Build](https://github.com/snooptiger9/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/snooptiger9/bkey-keyboard)
![Downloads](https://img.shields.io/github/downloads/snooptiger9/bkey-keyboard/total)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/Languages-AR%20%7C%20EN%20%7C%20ES%20%7C%20HI-blue)
![License](https://img.shields.io/github/license/snooptiger9/bkey-keyboard)
![Last Commit](https://img.shields.io/github/last-commit/snooptiger9/bkey-keyboard)

If you find this useful, a <img src="docs/icons/star.svg" width="22" height="22" align="absmiddle" alt=""> star would mean a lot.


---


<img src="docs/icons/link.svg" width="22" height="22" align="absmiddle" alt=""> **Quick links:** [Features](#en-features) · [Privacy & security](#en-privacy) · [Download](#en-download) · [Build it yourself](BUILDING.md) · [FAQ](#en-faq) · [Community & bug reports](#en-community) · [Support](#en-support)

### Overview <img src="docs/icons/sparkles.svg" width="22" height="22" align="absmiddle" alt="">

**bkey** is an Android keyboard (IME) built on the swipe gestures of the **BlackBerry Keyboard -
Classic** app, with more customization and extra features: swipe up on any suggestion to accept it
instantly, and swipe sideways across the letter row (direction configurable) for quick deletion
instead of repeated backspace taps. Fully supports Arabic, English, Spanish, and Hindi with a carefully
tailored experience for each.

<br>

<a id="en-features"></a>

### Features <img src="docs/icons/rocket.svg" width="22" height="22" align="absmiddle" alt="">

<img src="docs/icons/gesture.svg" width="22" height="22" align="absmiddle" alt=""> **Gestures**
- Swipe up on any suggestion: accept it instantly.

  ![Swipe up to accept a suggestion](docs/gifs/swipe_up_suggestion.gif?v=2)
- Swipe sideways over the keys: delete the last whole word at once (pick the swipe direction in settings).

  ![Swipe sideways to delete the last word](docs/gifs/swipe_delete_word.gif?v=2)
- Swipe in the exact opposite direction: select all typed text at once.

  ![Swipe the opposite direction to select all](docs/gifs/swipe_select_all.gif?v=2)
- Swipe down over the keys: copy/paste or the symbols layout (your choice in Settings → Swipe Gestures).
- Press-and-hold + drag the space bar: move the cursor character by character.

  ![Press-and-hold + drag the space bar to move the cursor](docs/gifs/cursor_move.gif?v=2)
- Swipe the space bar left/right: cycle through your enabled languages (Arabic/English/Spanish/Hindi) without a separate language button.
- Long-press any key: popup with alternate characters (capitals, accents and symbols that fit the active language).

<br>

<img src="docs/icons/keyboard.svg" width="22" height="22" align="absmiddle" alt=""> **Smart typing & prediction**
- Silent auto-correct on space, and next-word prediction (up to 3 words).
- On-the-fly word learning as you type, plus a manual trainer that saves a whole sentence instantly.
- **The more it learns, the more you swipe:** suggestions get more accurate as it learns your words.
- **Learning:** keeps up to 10,000 learned words, with a typing-stats page (counts only), a screen to search and delete learned words, and backup/restore to a JSON file.
- **Automatic emoji suggestions** based on the word or sentence context you're typing.

  ![Automatic emoji suggestion](docs/gifs/emoji_suggest.gif?v=2)
- Text shortcuts: type a shortcut (e.g. "omw") and it expands to the full phrase on space.
- Double-tap space for period + space, and auto-capitalize sentences (English/Spanish).
- Automatic verification-code (OTP) suggestions from SMS or WhatsApp — all processed on-device. *(bkey Plus only)*

<br>

<img src="docs/icons/clipboard.svg" width="22" height="22" align="absmiddle" alt=""> **Smart clipboard**
- Clipboard drawer: pin, delete, and auto-delete old items after a time you choose.
- Automatically saves the last screenshot (a single thumbnail that replaces the previous one). *(bkey Plus only)*
- The last copied text shows right in the middle suggestion slot for a quick re-paste.
- Copied phone numbers and email addresses get a one-tap call / new-mail button (it never calls or sends for you).

<br>

<img src="docs/icons/smile.svg" width="22" height="22" align="absmiddle" alt=""> **Emoji & GIF**
- Emoji and symbols panel with a "Recently used" section.
- GIF search powered by KLIPY. *(bkey Plus only)*

<br>

<img src="docs/icons/palette.svg" width="22" height="22" align="absmiddle" alt=""> **Full customization**
- 4 color themes plus **Auto** (follows Android's light/dark mode).
- 9 key shapes and multiple fonts.
- Size control for key letters, keyboard and suggestions.
- Enter-key color, key-bounce and flying-word animations (each toggleable).

<br>

<img src="docs/icons/speaker.svg" width="22" height="22" align="absmiddle" alt=""> **Sound & vibration**
- Typing tones and swipe/delete tones with separate volumes.
- Optional haptic vibration with adjustable strength.

<br>

<img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> **More preferences**
- Persistent number row, optional Arabic-Indic digits (١٢٣), one-handed mode, voice typing (optional mic button) and a key-preview popup.
- **Privacy-first:** Incognito mode stops learning and clipboard saving; passwords and links are never learned.

<br>

<a id="en-privacy"></a>

### Privacy & security <img src="docs/icons/lock.svg" width="22" height="22" align="absmiddle" alt="">

- **bkey** (the standard version) has no network access and can't read SMS, notifications, photos or storage — those
  permissions aren't in the app. Voice typing still works, handled entirely by your device's own
  speech-recognition service: it tries on-device (offline) recognition first, and only needs an
  internet connection as a fallback if your device doesn't support that.

- **bkey Plus** adds GIF search, image/sticker pasting, OTP suggestions, and the in-app updater. OTP
  suggestions are off by default and only read a newly-arrived SMS/WhatsApp notification — never
  your message history — all processed on-device, never sent to any server.

> <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> **About the Play Protect warning on bkey Plus**
>
> bkey Plus has internet access (for GIF search and the in-app update checker) and photo
> access (for image-pasting and the screenshot-to-clipboard suggestion), and that's what gets it
> flagged by Google Play Protect as a "Harmful app" — confirmed by our own testing. We've appealed
> the false positive with Google; until resolved, this is expected behavior for a sideloaded app
> with these permissions, not malware. Prefer not to see it? Install the standard bkey version instead.

<details>
<summary><b>Steps to pause Play Protect scanning, if you still want to install bkey Plus (tap for details)</b></summary>

<br>

1. Open the **Google Play Store** app.
2. Tap your profile picture, top right.
3. Tap **Play Protect**.
4. Tap the <img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> settings icon, top right.
5. Turn off **Scan apps with Play Protect** — a prompt will ask "Pause app scanning instead?", pick **Pause**.
6. Install the bkey Plus APK.
7. Pause turns itself back on automatically after a day, but it's better to turn it back on yourself right after installing (recommended to keep it on for your other apps).

</details>

<br>

<a id="en-download"></a>

### Download <img src="docs/icons/download.svg" width="22" height="22" align="absmiddle" alt="">

Two builds — pick the one that fits you:

| | <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> bkey (recommended) | <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> bkey Plus |
|---|:---:|:---:|
| Installs with no Play Protect warning | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> see note below |
| GIF search & image/sticker pasting | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| In-app update checker | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Verification-code (OTP) suggestions | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> (optional, off by default) |
| Screenshot-to-clipboard | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Copied-text suggestion | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-v307.apk"><img src="docs/images/badges/dl_bkey_en.png" alt="Download bkey (APK)" width="300"></a>
&nbsp;
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-plus-v307.apk"><img src="docs/images/badges/dl_plus_en.png" alt="Download bkey Plus (APK)" width="300"></a>
</p>

**bkey** needs only the permissions a keyboard needs to type (no internet, photos/storage or SMS), so the Plus-only features in the table are not included. **bkey Plus** has everything, but Google Play Protect warns when you install it (expected, not a real problem; see [Privacy & security](#en-privacy)).

Want automatic updates? Add the build you use to [Obtainium](https://github.com/ImranR98/Obtainium) with one tap, and add only one of them (both share the same package name).

<p>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_bkey_en.png" alt="Add bkey to Obtainium" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%20Plus%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-plus-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_plus_en.png" alt="Add bkey Plus to Obtainium" width="300"></a>
</p>

> [!NOTE]
> bkey Plus can also update from inside the app (Settings → **Update the app**).

### [Build it yourself](BUILDING.md) <img src="docs/icons/wrench.svg" width="22" height="22" align="absmiddle" alt="">

<br>

<a id="en-faq"></a>

### FAQ <img src="docs/icons/help.svg" width="22" height="22" align="absmiddle" alt="">

<details>
<summary><b>Does the keyboard send anything I type to the internet?</b></summary>
<br>

No, never — all learning, correction and prediction happen locally on your device. See [Privacy & security](#en-privacy) for exactly what bkey and bkey Plus can each access.

</details>
<details>
<summary><b>Why does Google Play Protect warn me when I install bkey Plus?</b></summary>
<br>

Internet and photo access together trigger it for any sideloaded app — expected, not malware. See [Privacy & security](#en-privacy) for why, or to pause the scan.

</details>
<details>
<summary><b>Where are my learned words stored, and how do I wipe them?</b></summary>
<br>

They're stored locally on your device only. In the app's settings, under the predictions-training section, the **"Clear all learned words"** button wipes them instantly.

</details>
<details>
<summary><b>Does it use a lot of battery or memory?</b></summary>
<br>

No — it's lightweight and runs fully on-device, with no background cloud processing.

</details>

<br>

<a id="en-community"></a>

### Community & bug reports <img src="docs/icons/users.svg" width="22" height="22" align="absmiddle" alt="">

Settings → **Error log** → **"Email developer"** sends crash details straight to **lvl60me@gmail.com** — the same address for suggestions or feedback, and the only way to reach the developer.

<br>

<a id="en-support"></a>

### Support <img src="docs/icons/mail.svg" width="22" height="22" align="absmiddle" alt="">

If you like the project and want to support it, you can buy me a coffee on Ko-fi:

[![Support me on Ko-fi](https://img.shields.io/badge/Ko--fi-Support_bkey-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/snooptiger)

---

<p align="center">Made with <img src="docs/icons/heart.svg" width="22" height="22" align="absmiddle" alt=""> — bkey</p>
