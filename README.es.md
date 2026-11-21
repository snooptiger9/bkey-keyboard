<p align="center"><a href="README.md">English</a> | <a href="README.ar.md">العربية</a> | <b>Español</b></p>

<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="docs/images/banner-light.png?v=2">
    <img src="docs/images/banner.png?v=2" alt="bkey — un teclado Android al estilo BlackBerry: desliza hacia arriba para aceptar una sugerencia y hacia los lados para borrar" width="100%">
  </picture>
</p>

# <img src="docs/icons/keyboard.svg" width="34" height="34" align="absmiddle" alt=""> bkey

**Un teclado Android basado en los gestos de BlackBerry Keyboard - Classic: desliza hacia arriba para elegir una sugerencia y hacia los lados para borrar (la dirección es configurable).**

![Build](https://github.com/snooptiger9/bkey-keyboard/actions/workflows/build.yml/badge.svg)
![Release](https://img.shields.io/github/v/release/snooptiger9/bkey-keyboard)
![Downloads](https://img.shields.io/github/downloads/snooptiger9/bkey-keyboard/total)
![Platform](https://img.shields.io/badge/platform-Android%207.0%2B-3DDC84?logo=android&logoColor=white)
![Languages](https://img.shields.io/badge/Trilingual-AR%20%7C%20EN%20%7C%20ES-blue)
![License](https://img.shields.io/github/license/snooptiger9/bkey-keyboard)
![Last Commit](https://img.shields.io/github/last-commit/snooptiger9/bkey-keyboard)

Si te resulta útil, una <img src="docs/icons/star.svg" width="22" height="22" align="absmiddle" alt=""> estrella significaría mucho.



---


<img src="docs/icons/link.svg" width="22" height="22" align="absmiddle" alt=""> **Enlaces rápidos:** [Funciones](#es-features) · [Privacidad y seguridad](#es-privacy) · [Descarga](#es-download) · [Compílalo tú mismo](BUILDING.md) · [Preguntas frecuentes](#es-faq) · [Comunidad y errores](#es-community) · [Apoyo](#es-support)

### Descripción <img src="docs/icons/sparkles.svg" width="22" height="22" align="absmiddle" alt="">

**bkey** es un teclado Android (IME) basado en los gestos de deslizamiento de la app **BlackBerry
Keyboard - Classic**, con más personalización y funciones extra: desliza hacia arriba sobre cualquier
sugerencia para aceptarla al instante, y desliza hacia los lados sobre la fila de letras (dirección
configurable) para borrar rápido en lugar de pulsar la tecla de retroceso una y otra vez. Es
totalmente compatible con árabe, inglés y español, con una experiencia cuidada para cada idioma.

<br>

<a id="es-features"></a>

### Funciones <img src="docs/icons/rocket.svg" width="22" height="22" align="absmiddle" alt="">

<img src="docs/icons/gesture.svg" width="22" height="22" align="absmiddle" alt=""> **Gestos**
- Desliza hacia arriba sobre cualquier sugerencia: la aceptas al instante.

  ![Desliza hacia arriba para aceptar una sugerencia](docs/gifs/swipe_up_suggestion.gif?v=2)
- Desliza hacia los lados sobre las teclas: borra la última palabra completa de una vez (la dirección se elige en los ajustes).

  ![Desliza hacia los lados para borrar la última palabra](docs/gifs/swipe_delete_word.gif?v=2)
- Desliza en la dirección exactamente opuesta: selecciona todo el texto escrito de una vez.

  ![Desliza en la dirección opuesta para seleccionar todo](docs/gifs/swipe_select_all.gif?v=2)
- Desliza hacia abajo sobre las teclas: tú eliges qué hace (Ajustes → Gestos de deslizamiento; las dos
  opciones son excluyentes, activar una desactiva la otra):
  - **Copiar / pegar:** copia el texto seleccionado, y el mismo gesto se convierte en pegar durante un
    minuto.
  - **Números y símbolos:** abre el diseño de símbolos del idioma en el que escribes; desliza hacia abajo
    otra vez en los símbolos para volver a las letras. Funciona por separado en cada idioma.
- Mantén pulsada la barra espaciadora y arrastra: mueve el cursor carácter a carácter.

  ![Mantén pulsada y arrastra la barra espaciadora para mover el cursor](docs/gifs/cursor_move.gif?v=2)
- Desliza la barra espaciadora a izquierda/derecha: cambia entre tus idiomas activados (árabe/inglés/español) sin un botón de idioma aparte.
- Mantén pulsada cualquier tecla: ventana con caracteres alternativos. En el teclado inglés, una letra minúscula da su mayúscula (al estilo BlackBerry), y la fila de números solo aparece mientras Shift o Bloq Mayús está activo. Los símbolos que ya están en la página de símbolos no se repiten, las letras con acento aparecen solo en el teclado español, y los símbolos exclusivos del árabe (﷼ ، ؛ ؟) aparecen solo cuando el árabe está activado.

<br>

<img src="docs/icons/keyboard.svg" width="22" height="22" align="absmiddle" alt=""> **Escritura inteligente y predicción**
- Autocorrección silenciosa al pulsar espacio y predicción de la palabra siguiente (hasta 3 palabras).
- Aprendizaje de palabras al escribir, más un entrenador manual que guarda una frase entera al instante.
- **Cuanto más aprende, más deslizas:** a medida que el teclado aprende tus palabras, sus sugerencias son más precisas, así que casi todo lo que escribes se vuelve un deslizamiento hacia arriba sobre la sugerencia correcta en vez de teclear cada letra.
- **Límites de aprendizaje:** guarda hasta 8.000 palabras aprendidas (se eliminan primero las menos usadas) y hasta 32 posibles palabras siguientes por cada palabra (se eliminan primero las más débiles).
- **Estadísticas de escritura:** una página en los ajustes muestra cuántas palabras ha aprendido el teclado, cuántas elegiste deslizando hacia arriba, tu proporción de palabras deslizadas, las pulsaciones ahorradas, las palabras borradas con un solo deslizamiento y tu reparto entre idiomas (solo recuentos; no se guarda ningún texto).
- **Pantalla de palabras aprendidas:** busca tus palabras, mira cuántas veces se usa cada una y sus sugerencias siguientes, y elimina las que quieras.
- **Copia de seguridad y restauración:** guarda tus palabras aprendidas y atajos de texto en un archivo JSON y restáuralos en un teléfono nuevo. Restaurar solo añade; nunca borra lo que ya tienes.
- **Sugerencias automáticas de emojis** según el contexto de la palabra o la frase que escribes.

  ![Sugerencia automática de emojis](docs/gifs/emoji_suggest.gif?v=2)
- Atajos de texto: escribe un atajo (p. ej. "omw") y se expande a la frase completa al pulsar espacio.
- Doble toque en espacio para punto + espacio automático.
- Mayúscula automática al inicio de cada frase (inglés/español).
- Sugerencias automáticas de códigos de verificación (OTP) desde SMS o WhatsApp — todo se procesa en el dispositivo. *(solo bkey Plus)*

<br>

<img src="docs/icons/clipboard.svg" width="22" height="22" align="absmiddle" alt=""> **Portapapeles inteligente**
- Un cajón de portapapeles completo: fijar, borrar y eliminar automáticamente los elementos antiguos
  tras el tiempo que elijas (de 1 hora a 30 días, o nunca).
- Guarda automáticamente la última captura de pantalla (una sola miniatura que reemplaza a la anterior). *(solo bkey Plus)*
- El último texto copiado aparece en la ranura central de sugerencias para volver a pegarlo rápido.
- Los números de teléfono copiados muestran un botón de llamada de un toque que abre el marcador con el número ya escrito (nunca llama por ti).

<br>

<img src="docs/icons/smile.svg" width="22" height="22" align="absmiddle" alt=""> **Emojis y GIF**
- Panel completo de emojis y símbolos con una sección de "Usados recientemente" (bórrala entera o
  mantén pulsado para quitar un solo emoji).
- La pestaña GIF del panel de emojis funciona con KLIPY GIF: busca y previsualiza GIF animados, con un campo de búsqueda flotante que no tapa la cuadrícula. *(solo bkey Plus)*

<br>

<img src="docs/icons/palette.svg" width="22" height="22" align="absmiddle" alt=""> **Personalización total**
- 4 temas de color más **Auto** (mitad blanco, mitad negro): sigue el modo día/noche de Android, usando el tema blanco de día y Negro mate de noche.
- 9 formas de tecla y varias fuentes (Sistema/Tajawal).
- La barra de sugerencias usa la misma fuente que elijas para las teclas.
- Control total del tamaño: tamaño de las letras, altura del teclado, altura de la barra de sugerencias y
  tamaño del texto de las sugerencias.
- Color de la tecla Enter; las cápsulas flotantes ABC/Borrar combinan con tu tema automáticamente.
- Animación de rebote de teclas y animación de la palabra voladora al aceptar una sugerencia, cada una activable por separado.

<br>

<img src="docs/icons/speaker.svg" width="22" height="22" align="absmiddle" alt=""> **Sonido y vibración**
- Varios tonos de escritura + tonos independientes para deslizar/borrar, cada uno con su propio volumen.
- Vibración háptica opcional al pulsar y deslizar, con intensidad ajustable (5–50 ms, o la del sistema).

<br>

<img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt=""> **Más preferencias**
- Una fila de números permanente sobre el teclado (más una fila dedicada en los campos de contraseña
  aunque la general esté desactivada).
- Dígitos indo-arábigos opcionales (١٢٣).
- Modo de una mano (izquierda o derecha).
- Dictado por voz (botón de micrófono opcional).
- Ventana de vista previa sobre la tecla al tocarla.
- **Privacidad ante todo**: el modo incógnito desactiva por completo el aprendizaje y el guardado del
  portapapeles; las contraseñas y los enlaces nunca se aprenden, ni siquiera fuera del modo incógnito.
  Las direcciones de correo solo se guardan cuando están bien escritas.

<br>

<a id="es-privacy"></a>

### Privacidad y seguridad <img src="docs/icons/lock.svg" width="22" height="22" align="absmiddle" alt="">

- **bkey** (la versión estándar) no tiene acceso a la red y no puede leer SMS, notificaciones, fotos ni almacenamiento: esos
  permisos no están en la app. El dictado por voz sigue funcionando, gestionado por completo por el servicio de
  reconocimiento de voz de tu dispositivo: primero intenta el reconocimiento en el propio dispositivo (sin conexión) y solo
  necesita internet como alternativa si tu dispositivo no lo admite.

- **bkey Plus** añade búsqueda de GIF, pegado de imágenes/stickers, sugerencias OTP y el actualizador integrado. Las sugerencias
  OTP vienen desactivadas por defecto y solo leen una notificación de SMS/WhatsApp recién llegada, nunca
  tu historial de mensajes; todo se procesa en el dispositivo y nunca se envía a ningún servidor.

> <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> **Sobre la advertencia de Play Protect en bkey Plus**
>
> bkey Plus tiene acceso a internet (para la búsqueda de GIF y el buscador de actualizaciones) y acceso a fotos
> (para pegar imágenes y la sugerencia de captura al portapapeles), y eso hace que Google Play Protect lo marque
> como "aplicación dañina", algo que confirmamos con nuestras propias pruebas. Hemos apelado el falso positivo ante
> Google; hasta que se resuelva, es un comportamiento esperado para una app instalada manualmente con estos permisos,
> no malware. ¿Prefieres no verla? Instala la versión estándar de bkey.

<details>
<summary><b>Pasos para pausar el análisis de Play Protect, si aun así quieres instalar bkey Plus (toca para ver)</b></summary>

<br>

1. Abre la app **Google Play Store**.
2. Toca tu foto de perfil, arriba a la derecha.
3. Toca **Play Protect**.
4. Toca el icono de ajustes <img src="docs/icons/sliders.svg" width="22" height="22" align="absmiddle" alt="">, arriba a la derecha.
5. Desactiva **Analizar apps con Play Protect**; te preguntará "¿Pausar el análisis de apps?", elige **Pausar**.
6. Instala el APK de bkey Plus.
7. La pausa se reactiva sola tras un día, pero es mejor que la reactives tú mismo justo después de instalar (se recomienda mantenerlo activado para tus otras apps).

</details>

<br>

<a id="es-download"></a>

### Descarga <img src="docs/icons/download.svg" width="22" height="22" align="absmiddle" alt="">

Dos versiones: elige la que mejor te venga:

| | <img src="docs/icons/dot-green.svg" width="18" height="18" align="absmiddle" alt=""> bkey (recomendada) | <img src="docs/icons/dot-blue.svg" width="18" height="18" align="absmiddle" alt=""> bkey Plus |
|---|:---:|:---:|
| Se instala sin advertencia de Play Protect | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/alert.svg" width="22" height="22" align="absmiddle" alt=""> ver nota abajo |
| Búsqueda de GIF y pegado de imágenes/stickers | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Buscador de actualizaciones integrado | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Sugerencias de códigos de verificación (OTP) | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> (opcional, desactivado por defecto) |
| Captura de pantalla al portapapeles | — | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |
| Sugerencia del texto copiado | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> | <img src="docs/icons/check.svg" width="18" height="18" align="absmiddle" alt=""> |

<p>
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-v304.apk"><img src="docs/images/badges/dl_bkey_es.png" alt="Descargar bkey (APK)" width="300"></a>
&nbsp;
<a href="https://github.com/snooptiger9/bkey-keyboard/releases/latest/download/bkey-plus-v304.apk"><img src="docs/images/badges/dl_plus_es.png" alt="Descargar bkey Plus (APK)" width="300"></a>
</p>

**bkey** solo pide lo estrictamente necesario para escribir (sin internet, sin fotos/almacenamiento, sin SMS); nada de lo anterior funciona aquí salvo la sugerencia del texto copiado, ya que el resto necesita internet o acceso a fotos.

**bkey Plus** lo tiene todo, pero al instalarlo aparece una advertencia de Google Play Protect (esperada, no es un problema real; mira [Privacidad y seguridad](#es-privacy) para saber por qué).

¿Quieres actualizaciones automáticas? Añade la versión que usas a [Obtainium](https://github.com/ImranR98/Obtainium) con un toque: **bkey** o **bkey Plus**. Cada enlace sigue solo su propio APK, así que una nunca reemplaza a la otra. Ambas comparten el mismo nombre de paquete, por lo que debes tener solo una en Obtainium.

<p>
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_bkey_es.png" alt="Añadir bkey a Obtainium" width="300"></a>
&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.snooptiger9.bkey%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fsnooptiger9%2Fbkey-keyboard%22%2C%22author%22%3A%22snooptiger9%22%2C%22name%22%3A%22bkey%20Plus%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%2C%5C%22apkFilterRegEx%5C%22%3A%5C%22%5Ebkey-plus-v%5B0-9%5D%2B%5C%5C%5C%5C.apk%24%5C%22%7D%22%7D"><img src="docs/images/badges/ob_plus_es.png" alt="Añadir bkey Plus a Obtainium" width="300"></a>
</p>

> [!CAUTION]
> - **Actualizar bkey:** desde esta página o con Obtainium usando el botón **bkey** de arriba.
> - **Actualizar bkey Plus:** desde esta página, desde dentro de la app (Ajustes → **Actualizar la app**) o con Obtainium usando el botón **bkey Plus** de arriba. De cualquier forma se queda en bkey Plus, nunca pasa a la versión estándar de bkey.
> - **Obtainium:** ambas versiones comparten el mismo nombre de paquete, así que añade solo la que usas. Cada botón sigue únicamente su propio APK; añadir el equivocado reemplazaría tu versión por la otra.

### [Compílalo tú mismo](BUILDING.md) <img src="docs/icons/wrench.svg" width="22" height="22" align="absmiddle" alt="">

<br>

<a id="es-faq"></a>

### Preguntas frecuentes <img src="docs/icons/help.svg" width="22" height="22" align="absmiddle" alt="">

<details>
<summary><b>¿El teclado envía a internet lo que escribo?</b></summary>
<br>

No, nunca: todo el aprendizaje, la corrección y la predicción ocurren localmente en tu dispositivo. Consulta [Privacidad y seguridad](#es-privacy) para ver exactamente a qué puede acceder cada versión, bkey y bkey Plus.

</details>
<details>
<summary><b>¿Por qué Google Play Protect me avisa al instalar bkey Plus?</b></summary>
<br>

El acceso a internet y a fotos juntos lo activa en cualquier app instalada manualmente: es esperado, no es malware. Consulta [Privacidad y seguridad](#es-privacy) para saber por qué o para pausar el análisis.

</details>
<details>
<summary><b>¿Dónde se guardan mis palabras aprendidas y cómo las borro?</b></summary>
<br>

Se guardan solo en tu dispositivo. En los ajustes de la app, en la sección de entrenamiento de predicciones, el botón **"Clear all learned words"** (Borrar todas las palabras aprendidas) las elimina al instante.

</details>
<details>
<summary><b>¿Gasta mucha batería o memoria?</b></summary>
<br>

No: es ligero y funciona por completo en el dispositivo, sin procesamiento en la nube en segundo plano.

</details>

<br>

<a id="es-community"></a>

### Comunidad y errores <img src="docs/icons/users.svg" width="22" height="22" align="absmiddle" alt="">

Ajustes → **Error log** → **"Email developer"** envía los detalles del fallo directamente a **lvl60me@gmail.com**: la misma dirección sirve para sugerencias o comentarios, y es la única forma de contactar con el desarrollador.

<br>

<a id="es-support"></a>

### Apoyo <img src="docs/icons/mail.svg" width="22" height="22" align="absmiddle" alt="">

Si te gusta el proyecto y quieres apoyarlo, puedes invitarme a un café en Ko-fi:

[![Apóyame en Ko-fi](https://img.shields.io/badge/Ko--fi-Apoya_bkey-FF5E5B?logo=ko-fi&logoColor=white)](https://ko-fi.com/snooptiger)

---

<p align="center">Hecho con <img src="docs/icons/heart.svg" width="22" height="22" align="absmiddle" alt=""> — bkey</p>
