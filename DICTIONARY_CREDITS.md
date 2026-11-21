# Dictionary credits
app/src/main/assets/dict_ar.txt, dict_en.txt, dict_es.txt and dict_hi.txt are derived from the
OpenSubtitles-based frequency lists of hermitdave/FrequencyWords
(https://github.com/hermitdave/FrequencyWords), licensed CC-BY-SA-4.0. Lists were filtered
(length, characters) and re-ranked. dict_es.txt has not been filtered for profanity the way
dict_ar.txt and dict_en.txt were.

dict_hi.txt (Hindi, Devanagari) combines two sources: the Hindi list of the same FrequencyWords
project (CC-BY-SA-4.0), keeping only Devanagari words that appear at least three times or are also
in the spell-checker list below, and stems from the GNU Aspell Hindi dictionary by the
Janabhaaratii team (GPL), as converted to Hunspell by Laszlo Nemeth
(https://github.com/LibreOffice/dictionaries/tree/master/hi_IN), limited to 5-10 character
words. Obvious vulgar words were removed. Because this list is smaller than the English and
Spanish ones, Hindi is only auto-corrected for a short list of certain misspellings.
