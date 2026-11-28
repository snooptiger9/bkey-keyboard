# Third-party notices

This file records third-party material used by bkey's Bangla support.

## Transliteration engine (MIT)

Ported to Java from `BornoPhonetic.kt` by Wasi Master (wmkeyboard,
https://github.com/wasi-master/wmkeyboard), licensed MIT:
Copyright (c) 2026 Wasi Master. Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: the above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Adapted for bkey: Java port, same package, no Android dependencies, no behaviour change.

## BSTI key mapping (CC-BY-4.0)

The opt-in fixed layout in `BstiLayout.java` follows Ekushey's **Bangla BSTI Mobile** touch layout
(https://github.com/ekushey-org/bangla-bsti-mobile), Copyright (c) 2026 Ekushey, licensed
CC-BY-4.0 (https://creativecommons.org/licenses/by/4.0/), based on the revised National Keyboard
Standard BDS 1738:2018 (BSTI). Full independent vowels and common conjuncts are exposed as
long-press overlays, so no hasanta deadkey or reordering rules are needed in this mode. The key
tables were retyped from the project's published mapping tables; no source code was copied.

## Smart-correction data (GPL-3.0)

The roman-to-roman spelling table in `app/src/main/assets/bn_phonetic_fixes.txt` is derived from `PhoneticAutoCorrectData.kt` in BornoTSF / Kiebord by Jayed Ahsan Saad (Codepotro), https://github.com/JayedAhsan/BornoTSF, SPDX-License-Identifier: GPL-3.0-or-later. bkey is distributed under GPL-3.0. The pairs were extracted mechanically; no Kotlin source was copied. The table rewrites roman input before transliteration (for example `ekta=ekoTa`), matching Borno's smart-correction behaviour.

## Bangla word list (CC-BY-SA-4.0)

`app/src/main/assets/dict_bn.txt` (45,757 words) is derived from the Bengali (`bn`) list of
hermitdave/FrequencyWords (https://github.com/hermitdave/FrequencyWords), licensed
CC-BY-SA-4.0. Only words made entirely of Bengali-script characters (U+0980–U+09FF) were
kept, in original frequency order, and the list was re-ranked by bkey's dictionary loader.
See `DICTIONARY_CREDITS.md` for the full credit.
