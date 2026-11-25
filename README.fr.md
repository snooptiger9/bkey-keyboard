<p align="center"><a href="README.md">English</a> | <a href="README.ar.md">العربية</a> | <a href="README.es.md">Español</a> | <b>Français</b> | <a href="README.hi.md">हिन्दी</a></p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="docs/images/banner-light.png?v=2">
    <img src="docs/images/banner.png?v=2" alt="bkey — un clavier Android façon BlackBerry : glissez vers le haut pour accepter une suggestion, sur le côté pour effacer" width="100%">
  </picture>
</p>

# <img src="docs/icons/keyboard.svg" width="36" height="36" align="absmiddle" alt=""> bkey

**Un clavier Android basé sur les gestes de BlackBerry Keyboard - Classic : glissez vers le haut pour choisir une suggestion, sur le côté pour effacer (direction configurable).**

![Build](https://github.com/snooptiger9/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/snooptiger9/bkey-keyboard)
![Downloads](https://img.shields.io/github/downloads/snooptiger9/bkey-keyboard/total)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/Languages-AR%20%7C%20EN%20%7C%20ES%20%7C%20HI%20%7C%20FR-blue)
![License](https://img.shields.io/github/license/snooptiger9/bkey-keyboard)
![Last Commit](https://img.shields.io/github/last-commit/snooptiger9/bkey-keyboard)

Si ce projet vous est utile, une <img src="docs/icons/star.svg" width="22" height="22" align="absmiddle" alt=""> étoile nous ferait très plaisir.


---


<img src="docs/icons/link.svg" width="22" height="22" align="absmiddle" alt=""> **Liens rapides :** [Fonctionnalités](#fr-features) · [Confidentialité et sécurité](#fr-privacy) · [Téléchargement](#fr-download) · [Compiler soi-même](BUILDING.md) · [FAQ](#fr-faq) · [Communauté et bugs](#fr-community) · [Soutien](#fr-support)

### Présentation <img src="docs/icons/sparkles.svg" width="24" height="24" align="absmiddle" alt="">

**bkey** est un clavier Android (IME) basé sur les gestes de l'application **BlackBerry Keyboard -
Classic**, avec plus de personnalisation et des fonctions en plus : glissez vers le haut sur une
suggestion pour l'accepter aussitôt, et glissez sur le côté le long des lettres (direction
configurable) pour effacer vite, au lieu de taper plusieurs fois sur retour arrière. Il prend en
charge l'arabe, l'anglais, l'espagnol, l'hindi et le français, avec une expérience soignée pour chaque langue.

<br>

<a id="fr-features"></a>

### Fonctionnalités <img src="docs/icons/rocket.svg" width="24" height="24" align="absmiddle" alt="">

<img src="docs/icons/gesture.svg" width="22" height="22" align="absmiddle" alt=""> **Gestes**
- Glisser vers le haut sur une suggestion : l'accepter aussitôt.

  ![Glisser vers le haut pour accepter une suggestion](docs/gifs/swipe_up_suggestion.gif?v=2)
- Glisser sur le côté au-dessus des touches : supprimer d'un coup le dernier mot entier (la direction se choisit dans les réglages).

  ![Glisser sur le côté pour supprimer le dernier mot](docs/gifs/swipe_delete_word.gif?v=2)
- Glisser dans la direction exactement opposée : sélectionner tout le texte saisi.

  ![Glisser dans la direction opposée pour tout sélectionner](docs/gifs/swipe_select_all.gif?v=2)
- Glisser vers le bas au-dessus des touches : copier/coller ou disposition des symboles (au choix dans Réglages → Gestes de balayage).
- Appui long + glisser sur la barre d'espace : déplacer le curseur caractère par caractère.

  ![Appui long + glisser sur la barre d'espace pour déplacer le curseur](docs/gifs/cursor_move.gif?v=2)
- Glisser la barre d'espace à gauche/droite : passer d'une langue activée à l'autre (arabe/anglais/espagnol/hindi/français) sans bouton de langue séparé.
- Appui long sur une touche : fenêtre avec des caractères alternatifs (majuscules, accents et symboles adaptés à la langue active).

<br>

<img src="docs/icons/keyboard.svg" width="22" height="22" align="absmiddle" alt=""> **Saisie intelligente et prédiction**
- Correction automatique discrète à l'espace, et prédiction du mot suivant (jusqu'à 3 mots).
- Apprentissage des mots au fil de la frappe, plus un entraîneur manuel qui enregistre une phrase entière aussitôt.
- **Plus il apprend, plus vous glissez :** les suggestions gagnent en précision à mesure qu'il apprend vos mots.
- **Apprentissage :** conserve jusqu'à 10 000 mots appris, avec une page de statistiques de frappe (comptages uniquement), un écran pour rechercher et supprimer les mots appris, et la sauvegarde/restauration dans un fichier JSON.
- **Suggestions d'emojis automatiques** selon le mot ou le contexte de la phrase en cours.

  ![Suggestion d'emoji automatique](docs/gifs/emoji_suggest.gif?v=2)
- Raccourcis de texte : tapez un raccourci (p. ex. « omw ») et il se développe en phrase complète à l'espace.
- Double appui sur espace pour point + espace, et majuscule automatique en début de phrase (anglais/espagnol/français).
- Suggestions automatiques de codes de vérification (OTP) depuis les SMS ou WhatsApp, traitées entièrement sur l'appareil. *(bkey Plus uniquement)*

<br>

<img src="docs/icons/clipboard.svg" width="22" height="22" align="absmiddle" alt=""> **Presse-papiers intelligent**
- Tiroir du presse-papiers : épingler, supprimer, et supprimer automatiquement les anciens éléments après le délai de votre choix.
- Enregistre automatiquement la dernière capture d'écran (une seule miniature qui remplace la précédente). *(bkey Plus uniquement)*
- Le dernier texte copié s'affiche dans l'emplacement central des suggestions pour le recoller vite.
- Les numéros de téléphone et adresses e-mail copiés ont un bouton d'appel / de nouveau message en un appui (il n'appelle ni n'envoie jamais à votre place).

<br>

<img src="docs/icons/smile.svg" width="22" height="22" align="absmiddle" alt=""> **Emojis et GIF**
- Panneau d'emojis et de symboles avec une section « Récemment utilisés ».
- Recherche de GIF avec KLIPY. *(bkey Plus uniquement)*

<br>

<img src="docs/icons/palette.svg" width="22" height="22" align="absmiddle" alt=""> **Personnalisation complète**
- 4 thèmes de couleur plus **Auto** (suit le mode clair/sombre d'Android).
- 9 formes de touches et plusieurs polices.
- Réglage de la taille des lettres, du clavier et des suggestions.
- Couleur de la touche Entrée, animations de rebond des touches et de mots volants (chacune activable).

<br>

<img src="docs/icons/speaker.svg" width="22" height="22" align="absmiddle" alt=""> **Son et vibration**
- Sons de frappe et sons de balayage/suppression avec des volumes séparés.
- Vibration haptique facultative à intensité réglable.

<br>

<img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> **Autres préférences**
- Rangée de chiffres permanente, chiffres arabo-indiens facultatifs (١٢٣), mode une main, saisie vocale (bouton micro facultatif) et aperçu des touches.
- **La confidentialité d'abord :** le mode navigation privée arrête l'apprentissage et l'enregistrement du presse-papiers ; les mots de passe et les liens ne sont jamais appris.

<br>

<a id="fr-privacy"></a>

### Confidentialité et sécurité <img src="docs/icons/lock.svg" width="24" height="24" align="absmiddle" alt="">

- **bkey** (la version standard) n'a aucun accès au réseau et ne peut lire ni les SMS, ni les notifications, ni les photos, ni le stockage : ces
  autorisations ne figurent pas dans l'application. La saisie vocale fonctionne quand même, gérée
  entièrement par le service de reconnaissance vocale de votre appareil : il essaie d'abord la
  reconnaissance sur l'appareil (hors ligne), et n'a besoin d'internet qu'en secours si votre appareil ne la prend pas en charge.

- **bkey Plus** ajoute la recherche de GIF, le collage d'images/stickers, les suggestions OTP et la mise à jour intégrée. Les
  suggestions OTP sont désactivées par défaut et ne lisent qu'une notification SMS/WhatsApp tout juste
  arrivée, jamais l'historique de vos messages, le tout traité sur l'appareil et jamais envoyé à un serveur.

> <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> **À propos de l'avertissement Play Protect sur bkey Plus**
>
> bkey Plus a accès à internet (pour la recherche de GIF et la vérification des mises à jour) et aux photos
> (pour le collage d'images et la suggestion de capture d'écran), et c'est ce qui le fait
> signaler par Google Play Protect comme « application dangereuse », ce que nos propres tests ont confirmé. Nous avons fait appel
> de ce faux positif auprès de Google ; en attendant, c'est le comportement attendu pour une application installée manuellement
> avec ces autorisations, pas un logiciel malveillant. Vous préférez ne pas le voir ? Installez plutôt la version standard de bkey.

<details>
<summary><b>Étapes pour suspendre l'analyse Play Protect, si vous voulez quand même installer bkey Plus (appuyez pour voir)</b></summary>

<br>

1. Ouvrez l'application **Google Play Store**.
2. Appuyez sur votre photo de profil, en haut à droite.
3. Appuyez sur **Play Protect**.
4. Appuyez sur l'icône <img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> de réglages, en haut à droite.
5. Désactivez **Analyser les applications avec Play Protect** ; un message demande « Suspendre plutôt l'analyse des applications ? », choisissez **Suspendre**.
6. Installez l'APK de bkey Plus.
7. La suspension se réactive d'elle-même après un jour, mais il vaut mieux la réactiver vous-même juste après l'installation (il est recommandé de la garder active pour vos autres applications).

</details>

<br>

<a id="fr-download"></a>

### Téléchargement <img src="docs/icons/download.svg" width="24" height="24" align="absmiddle" alt="">

Deux versions, choisissez celle qui vous convient :

| | <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> bkey (recommandé) | <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> bkey Plus |
|---|:---:|:---:|
| S'installe sans avertissement Play Protect | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> [voir Confidentialité et sécurité](#fr-privacy) |
| Recherche de GIF et collage d'images/stickers | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Vérification des mises à jour intégrée | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Suggestions de codes de vérification (OTP) | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> (facultatif, désactivé par défaut) |
| Capture d'écran vers le presse-papiers | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Suggestion du texte copié | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |

#### <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> bkey (recommandé)

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-v312.apk"><img src="docs/images/badges/dl_bkey_fr.png" alt="Télécharger bkey (APK)" width="300"></a>
&nbsp;
<a href="https://apkpure.com/p/com.snooptiger9.bkey"><img src="docs/images/badges/ap_bkey_fr.png" alt="Obtenir bkey sur APKPure" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_bkey_fr.png" alt="Ajouter bkey à Obtainium" width="300"></a>
</p>

#### <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> bkey Plus

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-plus-v312.apk"><img src="docs/images/badges/dl_plus_fr.png" alt="Télécharger bkey Plus (APK)" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%20Plus%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-plus-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_plus_fr.png" alt="Ajouter bkey Plus à Obtainium" width="300"></a>
</p>

> [!NOTE]
> bkey Plus peut aussi se mettre à jour depuis l'application (Réglages → **À propos et maintenance** → **Mettre à jour l'application**). N'ajoutez qu'une seule version à Obtainium (les deux ont le même nom de paquet).

### [Compiler soi-même](BUILDING.md) <img src="docs/icons/wrench.svg" width="24" height="24" align="absmiddle" alt="">

<br>

<a id="fr-faq"></a>

### FAQ <img src="docs/icons/help.svg" width="24" height="24" align="absmiddle" alt="">

<details>
<summary><b>Le clavier envoie-t-il ce que je tape sur internet ?</b></summary>
<br>

Non, jamais : tout l'apprentissage, la correction et la prédiction se font localement sur votre appareil. Voir [Confidentialité et sécurité](#fr-privacy) pour savoir exactement à quoi bkey et bkey Plus peuvent accéder.

</details>
<details>
<summary><b>Pourquoi Google Play Protect m'avertit-il quand j'installe bkey Plus ?</b></summary>
<br>

L'accès à internet et aux photos réunis le déclenche pour toute application installée manuellement : c'est attendu, ce n'est pas un logiciel malveillant. Voir [Confidentialité et sécurité](#fr-privacy) pour comprendre pourquoi, ou pour suspendre l'analyse.

</details>
<details>
<summary><b>Où sont stockés mes mots appris, et comment les effacer ?</b></summary>
<br>

Ils sont stockés uniquement en local sur votre appareil. Dans les réglages de l'application, dans la section d'entraînement des prédictions, le bouton **« Effacer tous les mots appris »** les supprime aussitôt.

</details>
<details>
<summary><b>Consomme-t-il beaucoup de batterie ou de mémoire ?</b></summary>
<br>

Non : il est léger et fonctionne entièrement sur l'appareil, sans traitement en arrière-plan dans le cloud.

</details>

<br>

<a id="fr-community"></a>

### Communauté et rapports de bugs <img src="docs/icons/users.svg" width="24" height="24" align="absmiddle" alt="">

Réglages → **À propos et maintenance** → **Journal d'erreurs** → **« Écrire au développeur »** envoie les détails du plantage directement à **lvl60me@gmail.com**, la même adresse pour les suggestions ou les retours, et le seul moyen de joindre le développeur.

<br>

<a id="fr-support"></a>

### Soutien <img src="docs/icons/mail.svg" width="24" height="24" align="absmiddle" alt="">

Si vous aimez le projet et voulez le soutenir, vous pouvez m'offrir un café sur Ko-fi :

[![Support me on Ko-fi](https://img.shields.io/badge/Ko--fi-Support_bkey-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/snooptiger)

---

<p align="center">Fait avec <img src="docs/icons/heart.svg" width="22" height="22" align="absmiddle" alt=""> — bkey</p>
