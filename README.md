# Balance Tycoon (título provisional)

Juego idle/tycoon para Android en el que diriges una gestoría: empiezas en un bajo de Tomelloso con autónomos como clientes y, resolviendo sus consultas de IVA, nóminas, contabilidad, mercantil y patrimonios, ganas reputación, contratas plantilla, renuevas los ordenadores y te mudas a locales cada vez más grandes hasta una torre en Madrid. Mientras tanto, el juego lleva de verdad la contabilidad de la gestoría (libro diario, mayor, PyG, balance, IVA trimestral, nóminas, Impuesto sobre Sociedades y reparto del resultado) según el PGC PYMES y la normativa española de 2026, simplificada.

Autor: Raúl Marian Alecu. Prototipo 0.2 (10 de noviembre de 2026).

## Descargar el APK de prueba

Cada cambio que se sube a `main` se compila automáticamente en GitHub Actions. El APK más reciente está en la release [«Última versión de prueba»](https://github.com/raulalecu6/BalanceTycoon/releases/tag/ultima-version): se descarga desde el móvil y se instala (Android pedirá permitir la instalación desde el navegador). Las versiones siguientes se instalan encima sin perder la partida.

Las capturas de la última compilación, sacadas en un emulador, están en la release [«Capturas automáticas»](https://github.com/raulalecu6/BalanceTycoon/releases/tag/capturas).

## Requisitos para compilar en local (opcional)

- Android Studio actualizado (noviembre de 2026 o posterior), con soporte para Android Gradle Plugin 9.5.
- JDK 17 o superior (Android Studio ya trae uno).
- SDK de Android 37 (Android Studio lo ofrece al sincronizar).

## Abrir y ejecutar

1. En Android Studio: **File > Open** y elegir la carpeta `BalanceTycoon`.
2. Esperar a que termine la sincronización de Gradle (la primera vez descarga Gradle 9.6 y las librerías).
3. Elegir un emulador o un móvil con la depuración USB activada y pulsar **Run ▶**.

Si Android Studio propone actualizar AGP o alguna librería, se puede aceptar.

## Tests del motor

```
./gradlew :core:test
```

Incluye los tests de contabilidad, fiscalidad, cierre del ejercicio, guardado y una simulación de partida con un jugador automático que imprime las mediciones de equilibrio.

## Estructura

- `core/`: motor del juego en Kotlin puro (contabilidad, impuestos, economía, guardado). No depende de Android.
- `app/`: app Android con Jetpack Compose (torre de la gestoría y pestañas Plantilla, Equipos, Clientes y Cuentas).
- `docs/`: documento de diseño (`balance-tycoon-gdd.md`) y estado actual (`balance-tycoon-estado-actual.md`).

## Aviso

Las empresas, clientes y personas que aparecen en el juego son ficticios. Fuente Pixelify Sans con licencia SIL Open Font License 1.1 (`docs/licencias`). El juego simplifica la normativa y no constituye asesoramiento fiscal ni contable.
