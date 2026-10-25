# Deutsch mit Lena · app Android con Claude

Lena es una tutora de alemán para tu móvil Android. Usa la API de Claude (Anthropic) con tu propia clave: no necesita una suscripción a Claude, solo un poco de saldo en la API, que pagas por uso.

## Qué hace

**Conversación**
- Chateas con Lena en alemán, escribiendo o hablando con el micrófono.
- Habla a tu nivel (desde A2), corrige tus frases y te enseña palabras nuevas muy poco a poco.
- **Modo conversación (manos libres)**: Lena habla, se abre el micrófono, contestas… Se activa en Ajustes → Voz.

**Ejercicios de gramática** (pestaña «Ejercicios»)
- Escribe el tema que quieras («el dativo», «Perfekt con sein», «verbos separables»…) o elige uno de los sugeridos para tu nivel.
- Lena prepara 5, 10 o 15 ejercicios (en lotes pequeños en paralelo, con reintentos automáticos) con la regla explicada en español y ejemplos que puedes escuchar.
- Cuatro tipos: rellenar el hueco, elegir la opción correcta, ordenar palabras y transformar frases.
- Corrección al momento con explicación. Si crees que tu respuesta también vale, pulsa «Creo que mi respuesta también vale» y Lena la revisa.
- Al terminar: tu puntuación, tus fallos y la opción de hacer otra ronda. Si aciertas casi todo, la siguiente es un poco más difícil; si fallas mucho, un poco más fácil.
- **Repasar tus errores**: Lena guarda los errores que te corrige en el chat. Puedes practicarlos con «Repasar mis errores recientes» o con «Practicar este error» en cada corrección.
- También puedes pedirlos en el chat: *«Kannst du mir Übungen zum Dativ geben?»* o *«hazme ejercicios de verbos modales»*.

Tu nivel, tus palabras, la conversación y tus ejercicios se guardan solo en el móvil.

---

## 1. Consigue tu clave de API de Claude (5 minutos)

1. Entra en https://platform.claude.com y crea una cuenta.
2. En **Billing**, añade saldo. Con 5 $ tienes para muchos meses de práctica (ver «Cuánto cuesta»).
3. En **API keys**, pulsa **Create key**, ponle un nombre (por ejemplo «Lena») y copia la clave. Empieza por `sk-ant-`.
4. Guárdala en un sitio seguro: es como una contraseña.

## 2. Instala la app

1. Descarga `DeutschMitLena.apk` desde la sección **Releases** de este repositorio (la versión más reciente).
2. Ábrelo en el móvil. Android te pedirá permiso para instalar apps de esa fuente; actívalo.
3. Si Play Protect avisa de que es una app desconocida, elige **Instalar de todos modos**.
4. Abre **Lena**, pega tu clave y pulsa **Guardar y probar**.
5. Si no oyes a Lena, entra en **Ajustes → Voz → Instalar la voz alemana**.

Si ya tenías instalada una versión anterior, la nueva se instala encima y **conserva tu progreso**. Solo tendrás que pegar la clave de Claude la primera vez.

## Cuánto cuesta

La app usa **Claude Haiku 5.5**, el modelo más rápido y económico: 0,10 $ por millón de tokens de entrada y 0,50 $ por millón de salida.

| Uso | Coste aproximado |
|---|---|
| Un mensaje en el chat | 0,0003 $ |
| Una ronda de 10 ejercicios | 0,001 $ |
| 1.000 mensajes + 100 rondas de ejercicios | unos 0,40 $ |

Puedes ver tu gasto en platform.claude.com → Usage, y poner un límite mensual en Billing. La API funciona aunque no tengas suscripción a Claude.

## Mantenimiento

- **Si Anthropic retira el modelo**: Lena pregunta sola a la API cuál es el modelo Haiku más nuevo y lo empieza a usar. También puedes escribir otro modelo en **Ajustes → Modelo**.
- **Actualizaciones**: cada cambio que se sube a este repositorio genera un APK nuevo en **Releases**. Lleva una firma fija (`app/lena-release.keystore`), así que se instala encima sin perder el progreso. No borres ese archivo.
- **No desinstales la app** si quieres conservar tu progreso: se guarda solo en el móvil.
- **Tu clave** se guarda solo en el móvil y únicamente se envía a `api.anthropic.com`. Si crees que alguien la tiene, bórrala en la consola y crea otra.

## Estructura del proyecto

- `app/src/main/assets/index.html`: la app (interfaz, conversación, ejercicios, nivel y conexión con Claude)
- `app/src/main/java/com/lenadeutsch/app/MainActivity.java`: puente con Android (voz, micrófono, guardado y conexión segura)
- `.github/workflows/apk.yml`: fabricación automática del APK en GitHub
- Con Android Studio: **Open** → esta carpeta → **Build → Build APK(s)**.
