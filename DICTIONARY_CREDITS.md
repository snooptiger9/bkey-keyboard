# Dictionary credits
app/src/main/assets/dict_ar.txt, dict_en.txt, dict_es.txt, dict_fr.txt and dict_hi.txt are derived from the
OpenSubtitles-based frequency lists of hermitdave/FrequencyWords
(https://github.com/hermitdave/FrequencyWords), licensed CC-BY-SA-4.0. Lists were filtered
(length, characters) and re-ranked. dict_es.txt and dict_fr.txt (letters only, a short list of vulgar words removed) have not been fully filtered for profanity the way
dict_ar.txt and dict_en.txt were.

dict_hi.txt (Hindi, Devanagari) combines two sources: the Hindi list of the same FrequencyWords
project (CC-BY-SA-4.0), keeping only Devanagari words that appear at least three times or are also
in the spell-checker list below, and stems from the GNU Aspell Hindi dictionary by the
Janabhaaratii team (GPL), as converted to Hunspell by Laszlo Nemeth
(https://github.com/LibreOffice/dictionaries/tree/master/hi_IN), limited to 5-10 character
words. Obvious vulgar words were removed. Because this list is smaller than the English and
Spanish ones, Hindi is only auto-corrected for a short list of certain misspellings.

dict_es.txt and dict_fr.txt were later checked against the Hunspell spell-checker dictionaries of
LibreOffice (https://github.com/LibreOffice/dictionaries, es_ES/es_MX and fr_FR): words they do not
accept (English names and words from subtitles, misspellings without accents, French words glued
without their apostrophe) were removed and replaced by the next most frequent accepted words of the
same FrequencyWords lists, so each list keeps its size. The spell-checker dictionaries were only used
as a filter and are not included. Common French elisions (c'est, j'ai, d'accord...) were added.

dict_ur.txt (Urdu) combines the word frequencies of the Makhzan corpus
(https://github.com/zeerakahmed/makhzan, statistics under the MIT license, Copyright (c) Zeerak
Ahmed), a curated corpus of edited Urdu text, with the Urdu list of the same FrequencyWords project
(CC-BY-SA-4.0) for everyday conversational words. Vowel marks were removed, known word-breaking
errors of the corpus were dropped, only Urdu letters were kept, and obvious vulgar words were removed.
The most common word pairs of Makhzan seed the next-word suggestions. Because this list is smaller,
Urdu, like Hindi, is not auto-corrected; its words show as suggestions.

dict_id.txt (Indonesian) is derived from the Indonesian (id) list of the same FrequencyWords project
(CC-BY-SA-4.0), in frequency order, and kept only words that passed three rules: (1) lowercase
letters a-z only, 2 to 20 letters, seen at least 5 times; (2) accepted by the Hunspell Indonesian
dictionary of LibreOffice (hunspell-id, LGPL-3.0) and also found, alone or with a -ku/-mu/-nya/-lah/
-kah/-pun ending, in the public-domain Indonesian Wordlist (https://github.com/za/id-wordlist);
(3) not a vulgar, offensive or sexual word (checked against the MIT-licensed indonesian-badwords list,
https://github.com/drizki/indonesian-badwords, plus a hand-made list), and not an English word that
is more common in English than in Indonesian subtitles. Names and two-letter abbreviations were
removed. Words the rules were unsure about were dropped, so the list is smaller but clean. The
spell-checker and word lists were only used as filters and are not included. Like Hindi and Urdu,
Indonesian is not auto-corrected; its words show as suggestions.
dict_bn.txt (Bangla) is derived from the Bengali (bn) list of the same FrequencyWords project
(CC-BY-SA-4.0), filtered to words written only in Bengali script and re-ranked. Non-word entries
(Bengali digits, bare vowel signs, hasanta and anusvara) were removed, and vulgar or offensive
words were filtered against the MIT-licensed Bengali list of
https://github.com/4troDev/profanity.csv and the Apache-2.0 Bengali list of
https://github.com/readme-SVG/Banned-words, with a manual pass so ordinary words that appear in
those lists (for example বাচ্চা and বাংলাদেশ) are kept. The filter lists were used only to decide
which entries to drop and are not included. As with Hindi and Urdu, Bangla is not auto-corrected:
its words show as suggestions.