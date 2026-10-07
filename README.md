# Deutsch mit Lena · APK gratuita con Gemini

Esta versión de Lena funciona sola en tu móvil Android y es **gratis**: usa la API de Google Gemini con una clave gratuita, sin tarjeta y sin suscripciones.

Incluye:
- **Micrófono de verdad**: pulsas el micrófono, hablas en alemán y tu frase aparece escrita.
- **Modo conversación (manos libres)**: Lena habla, se abre el micrófono, contestas, y así sucesivamente. Se activa en Ajustes → Voz.
- **Voz alemana nativa de Android** para leer las respuestas.
- Tu nivel, tus palabras y la conversación se guardan en el propio móvil.

---

## 1. Consigue tu clave gratuita de Gemini (2 minutos)

1. Entra en https://aistudio.google.com/apikey con tu cuenta de Google.
2. Acepta las condiciones si te las pide y pulsa **Create API key** (Crear clave de API).
3. Copia la clave (empieza por `AIza`). Guárdala en un sitio seguro: es como una contraseña.

No hace falta añadir tarjeta ni activar la facturación.

## 2. Genera el APK en GitHub (gratis, una sola vez, desde el móvil)

1. Crea una cuenta en https://github.com.
2. Crea un repositorio nuevo: botón **+** → **New repository**.
   - Nombre: `deutsch-mit-lena`
   - Marca **Private**.
   - Marca **Add a README file**.
   - Pulsa **Create repository**.
3. En el repositorio: **Add file** → **Upload files** → elige `lena-gemini-proyecto.zip` → **Commit changes**.
4. Otra vez **Add file** → **Create new file**:
   - En el nombre escribe exactamente: `.github/workflows/apk.yml`
   - En el contenido pega todo el texto del archivo `apk-workflow.txt`.
   - Pulsa **Commit changes**.
5. GitHub empieza a fabricar el APK solo. Tarda unos 5 minutos (lo puedes seguir en la pestaña **Actions**).
6. Cuando termine, en la página principal del repositorio entra en **Releases** (en el móvil está más abajo, en la columna de información) y descarga **DeutschMitLena.apk**. Ese enlace es tu descarga directa: guárdalo.

> Desde un ordenador es aún más fácil: descomprime el zip y sube todos los archivos (incluida la carpeta `.github`) en el paso 3, y sáltate el paso 4.

## 3. Instala la app

1. Abre `DeutschMitLena.apk`.
2. Android te pedirá permiso para instalar apps de esta fuente (el navegador o «Archivos»). Actívalo.
3. Si Play Protect avisa de que es una app desconocida, elige **Instalar de todos modos**. Es normal en apps que no vienen de Google Play.

## 4. Primer uso

1. Abre **Lena**, pega tu clave de Gemini y pulsa **Guardar y probar**.
2. Si no oyes a Lena, entra en **Ajustes → Voz → Instalar la voz alemana**.
3. Elige un tema y a practicar.

---

## Límites del plan gratuito

- Google pone un **límite de mensajes por minuto y por día** en el plan gratuito. Para practicar un buen rato cada día suele sobrar. Si lo alcanzas, Lena te avisa; el límite diario se renueva cada día.
- Si un modelo se queda sin cuota o deja de existir, Lena prueba sola con otros modelos gratuitos de Google (Flash y Flash-Lite).
- Google puede cambiar o recortar su plan gratuito en el futuro. Si eso pasa, puedes activar la facturación en AI Studio (cuesta céntimos) o pedir que adapte la app a otro servicio.

## Privacidad

- Tu clave se guarda solo en el móvil y únicamente se envía a `generativelanguage.googleapis.com`.
- Las condiciones de Google dicen que, para usuarios del Espacio Económico Europeo (incluida España), Suiza y Reino Unido, se aplican las condiciones de datos del plan de pago también a la cuota gratuita. Aun así, no escribas a Lena datos sensibles.
- Si algún día compartes la app con otras personas en Europa, las condiciones de Google piden usar el plan de pago.

## Mantenimiento

- **Actualizaciones**: el proyecto lleva una firma fija (`app/lena-release.keystore`) y GitHub numera cada versión. Cada APK nuevo se instala encima del anterior **sin perder tu progreso**. No borres ese archivo.
- **No desinstales la app** si quieres conservar tu progreso: se guarda solo en el móvil. (Si tienes activada la copia de seguridad de Google, Android suele restaurarlo al reinstalar.)
- **Si Google cambia los nombres de sus modelos** y Lena dice que ninguno responde, escribe en **Ajustes → Modelo** el nombre de un modelo Flash actual (aparecen en aistudio.google.com).

## Si algo falla al compilar

En la pestaña **Actions**, abre la ejecución en rojo, toca el paso que falló y copia las últimas líneas del error. Con eso se puede arreglar.

## Estructura del proyecto

- `app/src/main/assets/index.html`: la app (interfaz, lógica de Lena y del nivel, conexión con Gemini)
- `app/src/main/java/com/lenadeutsch/app/MainActivity.java`: puente con Android (voz, micrófono, guardado y conexión segura)
- `.github/workflows/apk.yml`: fabricación automática del APK en GitHub
- Con Android Studio: **Open** → esta carpeta → **Build → Build APK(s)**.
