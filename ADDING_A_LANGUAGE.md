# Adding a language

A checklist of every place that has to change when a keyboard language is added. It is based on
how French was added (Latin script, same QWERTY layout). Search the code for `LANG_FR` / `LANG_ES`
to find the neighbouring code in each file. Missing one place does not fail the build: the language
just falls back to English text or behaviour, so go through the whole list.

Paths are under `app/src/main/java/com/bbgboard/keyboard/` unless stated otherwise.

## 1. Language code

`KeyboardPreferences.java`
- Add `LANG_XX = "xx"` and append it to `ALL_LANGUAGES` (this is what fills the Languages screen).
- `autoSetupLanguagesOnFirstRun`: Android can report some languages with an old code (`in` for
  Indonesian, `iw` for Hebrew, `ji` for Yiddish). Map it to the new code there and in
  `SettingsText.languageOf`.

## 2. Dictionary (suggestions, near-miss correction)

`app/src/main/assets/dict_xx.txt`: one word per line, most frequent first, lower case, letters only.
- Source: the OpenSubtitles lists of hermitdave/FrequencyWords (CC-BY-SA-4.0), 50k file.
- Filter: letters of that language only, drop words seen fewer than 5 times, drop single letters
  that are not real words, drop obvious vulgar words and triple repeated letters.
- Remove words of the other languages (mostly English in subtitle data): drop a word that exists in
  `dict_en` when its frequency per million there is more than ~12x its frequency in the new list,
  but keep real words of the language that also exist in English (e.g. French "pain").
- Add the source line to `DICTIONARY_CREDITS.md`.

A cleaner method (used for Indonesian, `dict_id.txt`). A word is kept only if it passes all three
rules, and any word the rules are unsure about is dropped, even if the list gets smaller:
1. Form: letters of the language only, 2 to 20 letters, seen at least 5 times.
2. Validity: accepted by the LibreOffice Hunspell dictionary of the language, and also found in a
   second, independent word list (with common endings such as `-nya`, `-ku`, `-mu` removed).
   Both lists are only used as filters and are not shipped.
3. Safety: not a vulgar, sexual or offensive word. Check derived forms too (match the stem inside
   the word, then allow back real words that only contain it by chance). Drop English words that are
   more common in English subtitles than in the new language's, names, and two-letter
   abbreviations. Hunspell and subtitle lists both contain English words and names, so rule 2 alone
   is not enough.

Read through the top few hundred words and a random sample of the result before using it.

`PredictionEngine.java`
- Fields: `baseXxWords`, `baseXxBigrams`, `baseXxByFold`, and a `latinIsXx` flag.
- `setActiveLatinLanguage`, `activeLatinTag`, `currentLatinWords/Bigrams/ByFold`.
- `loadLanguage()`: the `dict_xx.txt` line.
- `knownFolded` is shared by every loaded language, and a word in it is never corrected. A new
  language added to it changes English/Spanish/French correction for everyone who turns it on
  (e.g. Indonesian "cair" stopped "car" from being suggested). Keep the new language in its own set
  that only counts while it is active, like `knownFoldedId`.
- `findAutoCorrection`: the "sure typo" map line, and the generic English-typo fallback condition.
  A Latin language with no typo map must return `null` in the "sure typo" line explicitly, or it
  falls through to the English typo map ("teh" is a real Indonesian word).
- If the dictionary is small or was filtered hard, turn silent correction off (the
  `latinIsHindi || latinIsUrdu || latinIsIndonesian` line) so real words missing from the list are
  never replaced; words still show as suggestions.
- `COMMON_XX_TYPOS` (declaration + the `String[] xx = {...}` block): only spellings that are never
  right. Do not list real words, including real words of other languages that people mix in.
- `activeDefaultWords`: six starter words for an empty bar.
- Non-Latin scripts also need a tokenizer / word-character check review (see Hindi and Arabic).

## 3. Layout and long-press accents

`BlackBerryKeyboardView.java`
- Latin languages reuse `buildEnglishQwertyRows()`. Add the language's accents to `latinAlts`
  (French uses the `FRENCH_ALTS` map). A different letter layout (AZERTY, Cyrillic...) needs its own
  `buildXxRows()` and a branch in `rebuildKeys()`.
- Strings drawn by the keyboard itself: Reset / Done labels in the resize overlay, and the name in
  `languageDisplayName` (shown on the space bar).
- `setCurrentLanguage` only needs changes for a language with its own flags (like `isHindi`).

## 4. Text in the keyboard's own panels

- Create `XxUi.java` like `FrenchUi.java`: a map from the English string to the translation, plus
  the 8 emoji category titles. Anything missing falls back to English.
- `BlackBerryImeService.java`: `emojiCatTitles()`, `uiText()` (one line each), and the voice
  recognition locale in `onVoiceInputRequested`.

## 5. Emoji suggestions

`EmojiSuggester.java`
- Add an `XX_MAP` (same `word|emoji` chunk format) and its branch in `suggestFor`. Keep it separate
  when the words are also ordinary English words.
- Add the language's accented letters to `fold()` if they are not there yet.

## 6. Settings and stats

`MainActivity.java`
- `languageDisplayName` (Arabic and English names).
- `createLanguageChartCard`: the `codes`, `names`, `colors` arrays. Show the new bar only when the
  language is on or has been typed, so the chart of other users stays the same.
- The auto-capitalize description, if the language uses capital letters (Arabic text in
  `MainActivity`, the other translations in `SettingsText`).

`SettingsText.java` (settings pages in the phone's language)
- `languageOf`, `pairsFor`, and a new `XX` array: translate every English key of the `ES` array.
  Keep `%s`, `\n` and `\"` exactly as in the key.
- Add the language's name ("Indonesian") to the other arrays.
- `app/src/main/res/xml/locales_config.xml`: add the code (per-app language list, Android 13+).

`OtpDetector.java`
- The words for "code" / "verification" and for card, amount, account, minute... in the new
  language. These lists are shared by all languages, so check that messages in the other languages
  still give the same result.

## 7. Documentation and store text

- `README.md` and its translations: supported-languages sentence, the space-bar swipe line, the
  auto-capitalize line, and the language switcher row at the top. The language badge reads
  "English +N": raise N by one.
- A new `README.xx.md` (copy `README.fr.md`), plus new `dl_*_xx.png` / `ob_*_xx.png` badges in
  `docs/images/badges/` (copy the Spanish ones and replace the small text line).
- `.github/workflows/build.yml`: add the new README to the two `sed` lines and to `git add` in the
  "Point README download links" step.
- `metadata/en-US/full_description.txt` (store text) and `docs/images/social-preview.png` (language pills).

## 8. Check before pushing

- Build compiles: every `LANG_XX` branch above is a plain `if`, so a typo shows up at compile time.
- Nothing else changed: run the same list of inputs (prefixes of dictionary words, typos, links,
  numbers, learned words) through the old and the new `PredictionEngine` for every existing language,
  with the new language turned on, and compare. The output must be identical.
- Separation: a word learned in the new language must not show in the other languages, and the
  other way round. Links, numbers and symbols must give no suggestions.
- On a device: switch to the language, type, long-press the accented keys, open the clipboard and
  emoji panels (titles must be translated), open the resize bar, and check the Languages screen and
  the "Words by language" card.
- Do not bump `versionCode` unless a release is intended.
