# Third-party notices

This file records third-party material used by bkey's Bangla support.

## Transliteration engine (MIT)

Ported to Java from `BornoPhonetic.kt` by Wasi Master (wmkeyboard,
https://github.com/wasi-master/wmkeyboard), licensed MIT:
Copyright (c) 2026 Wasi Master. Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions: the above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

Adapted for bkey: Java port, same package, no Android dependencies, no behaviour change.

## Jatiyo key mapping (CC-BY-4.0)

_(to be filled in Phase 3)_

## Smart-correction data (GPL-3.0)

The roman-to-roman spelling table in `app/src/main/assets/bn_phonetic_fixes.txt` is derived from `PhoneticAutoCorrectData.kt` in BornoTSF / Kiebord by Jayed Ahsan Saad (Codepotro), https://github.com/JayedAhsan/BornoTSF, SPDX-License-Identifier: GPL-3.0-or-later. bkey is distributed under GPL-3.0. The pairs were extracted mechanically; no Kotlin source was copied. The table rewrites roman input before transliteration (for example `ekta=ekoTa`), matching Borno's smart-correction behaviour.

## Bangla word list (CC-BY-SA-4.0)

_(to be filled in Phase 2)_
