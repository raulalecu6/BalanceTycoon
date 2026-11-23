# Balance Tycoon — Estado actual (versión 0.3, 24 de noviembre de 2026)

Este documento sirve para continuar el desarrollo en un chat nuevo sin perder nada. El diseño completo está en `balance-tycoon-gdd.md`.

## Qué hay hecho

- **Rediseño 0.2:** la empresa del jugador es una **gestoría** con seis departamentos (una planta cada uno), trabajadores, formación, equipos informáticos, programa de gestión, cinco locales, seis tipos de cliente según la reputación y 49 consultas reales de clientes.
- **Mejoras 0.3 (jugabilidad, aspecto y sonido):** 26 consultas de **asiento completo** (el jugador elige la cuenta de cada línea), **clientes VIP**, seis **temporadas** del calendario, cuatro **imprevistos** (VIP, avería, gripe y networking), **traspaso de la gestoría** con puntos de experiencia, **música y efectos** generados por código con ajustes de música, sonido y vibración, y una torre con noche según la hora real, coches, farolas, clientes en cola, decoración por local, confeti y botones que se hunden al pulsarlos.
- **Motor (`core`)** en Kotlin puro: 35 tests en verde y una simulación de equilibrio con un jugador automático (`AutoPlayer`), que también prepara las partidas de demostración de las capturas.
- **App Android (`app`)** con Jetpack Compose: torre en pixel art dibujada con `Canvas` (pantalla Oficina), pestañas Plantilla, Equipos, Clientes y Cuentas (diario, mayor, PyG, balance e impuestos), tema propio con la fuente Pixelify Sans, textos en español e inglés.
- **Repositorio y compilación automática:** https://github.com/raulalecu6/BalanceTycoon. Cada subida a `main` pasa los tests, compila el APK, lo publica en la release `ultima-version` y después lo prueba en un emulador Android 14 (Pixel 6) y publica capturas en la release `capturas`. El APK se firma con una clave de depuración fija (`app/debug.keystore`) para instalar cada versión encima de la anterior.
- **Prototipo 0.1 probado en un móvil real** por el usuario (instala y funciona). La partida de la 0.1 no es compatible: al actualizar se guarda aparte (`partida-prototipo-0.1.json`) y se empieza de cero con un aviso.

## Cómo retomar en un chat nuevo

1. Pedir a Claude que conecte el repositorio `raulalecu6/BalanceTycoon` (con permiso de escritura) y pasarle este documento.
2. Los cambios se suben a `main`; GitHub Actions compila, publica el APK y saca capturas. Las capturas se descargan desde la release `capturas` (los artefactos de Actions no son accesibles desde la sesión de Claude).
3. Para probar el motor sin red: compilador de Kotlin que trae Gradle (`/opt/gradle-*/lib/kotlin-compiler-embeddable-*.jar`) y JUnit 4 (`junit-4.13.2.jar` y `hamcrest-core-1.3.jar` de la misma carpeta). La simulación acepta `-Dsim.hours=N`.
4. En la versión de pruebas, la actividad acepta tres extras para revisar la interfaz: `--ei tab N` (pestaña), `--ef night F` (0 = día, 1 = noche; si no se pasa, sigue la hora real) y `--ei demo_minutes N` (partida avanzada con el jugador automático; deja consultas pendientes, a ser posible una de asiento). Ejemplo: `adb shell am start -n com.alecu.balancetycoon/.MainActivity --ef night 0 --ei demo_minutes 150`.
5. Para regenerar la música y los efectos: `python3 tools/generar_audio.py` (necesita NumPy y FFmpeg con libvorbis); escribe los OGG en `app/src/main/res/raw`.

## Estructura

```
BalanceTycoon/
├── core/                       Motor (Kotlin puro, sin Android)
│   └── src/main/kotlin/com/alecu/balancetycoon/core/
│       ├── accounting/         Plan de cuentas (Pgc), libro diario y mayor (Ledger), informes (Reports)
│       ├── calendar/           Fecha del juego y plazos (GameDate)
│       ├── format/             Formato de cifras y fechas ES/EN (Fmt)
│       ├── game/               Catalog (departamentos, locales, clientes, equipos, software), Cases (consultas
│       │                       de opción múltiple), EntryCases (consultas de asiento), Seasons (temporadas e
│       │                       imprevistos), GameState, GameEngine y AutoPlayer
│       ├── i18n/               Textos bilingües (L, Lang)
│       ├── money/              Importes en céntimos y redondeo (Money)
│       ├── save/               JSON propio y guardado (Json, SaveCodec, formato 3)
│       └── tax/                IVA, retenciones, Seguridad Social, recargos e IS (TaxRules)
│   └── src/test/kotlin/...     Tests y mediciones de equilibrio (EconomySimulation)
├── app/                        App Android (Compose)
│   └── src/main/java/com/alecu/balancetycoon/
│       ├── MainActivity.kt     Actividad única; extras de depuración «tab», «night» y «demo_minutes»
│       ├── GameViewModel.kt    Bucle del juego (10 veces por segundo), guardado, progreso fuera, demo,
│       │                       sonidos según los sucesos, confeti y traspaso
│       ├── GameAudio.kt        Efectos (SoundPool), música en bucle (MediaPlayer) y ajustes de sonido y vibración
│       ├── SaveStore.kt        Archivo partida.json con escritura atómica; aparta las partidas de la 0.1
│       ├── AdsProvider.kt      Punto de enganche de anuncios (simulado)
│       └── ui/                 App (barras y diálogos), Tower (dibujo de la torre), OfficeScreen, StaffScreen,
│                               EquipmentScreen, ClientsScreen, BooksScreen, TaxScreen, Common y tema
├── tools/                      generar_audio.py (síntesis de la música y los efectos)
├── .github/                    Flujo de compilación y script de capturas en el emulador
└── docs/                       GDD, este documento y la licencia de la fuente
```

## Datos técnicos clave

- **Tiempo:** 1 día contable = 30 s reales (`GameEngine.DAY_SECONDS`). Mes = 15 min; trimestre = 45 min; ejercicio ≈ 3 h. Fuera de la app, máximo 8 h (960 días), por tramos de 10 días.
- **Importes:** céntimos en `Long`; tipos en puntos básicos (2100 = 21%); redondeo comercial con `BigDecimal`.
- **Inicio:** capital de 3.000 € (572 a 100), fianza de 600 € (260), apertura del departamento fiscal (600 € en 627 + IVA) y un ordenador de segunda mano (500 € en 217 + IVA). Banco inicial: 1.069 €.
- **Honorarios:** se acumulan durante el día y se contabilizan en un asiento resumen por departamento (705 + 477), con la parte a plazo según el tipo de cliente (430 y `PendingFlow`).
- **Multiplicadores:** trabajadores (×1 a ×6), equipos (×1 a ×2,5), formación (+15% por nivel), programa de gestión (×1 a ×1,7), tipo de cliente (×1 a ×4,5), racha (+2% por acierto, máx. +20%) y turbo de anuncio (×2). Hitos de velocidad a 25, 50, 100, 200, 300 y 400 clientes.
- **Consultas:** una cada 2 días, máximo 6 (sin contar las VIP); a los 10 días las resuelve el equipo a mitad de precio (+1 de reputación). Bien: +3 + tipo de cliente; mal: −3. Honorarios ≈ 90 s de facturación, con un mínimo por tipo de cliente.
- **Consultas de asiento (0.3):** 35% de las que llegan si hay alguna del departamento y tipo de cliente; honorarios ×1,5 y +2 de reputación. Importes sorteados dentro de un rango; se guarda la base (`PendingCase.base`) y el orden barajado de las cuentas candidatas.
- **VIP (0.3):** honorarios ×3 y reputación ×2; se van a los 3 días sin respuesta (−2).
- **Temporadas e imprevistos (0.3):** multiplicador por departamento (`GameEngine.eventMultiplier`): temporadas ×1,5 o ×2; avería ×0,5 durante 4 días (reparar: 622 + 472, 15% de los equipos de la planta, mínimo 150 €); gripe ×0,75 durante 5 días; networking: 627 + 472 por unos dos días de facturación (mínimo 150 €) y +8 + 4 × tipo de cliente de reputación. Un imprevisto cada 12-30 días (el primero, el día 25).
- **Traspaso (0.3):** desde el local 2 (Ciudad Real) y con 1 punto como mínimo. Puntos = ⌊√(honorarios totales en M€)⌋; ingresos × (1 + 0,10 × puntos). La nueva sociedad empieza el 1 de enero siguiente; el patrimonio neto pasa a `ownerWealth`.
- **Límites por progreso:** puestos por planta según el local; nivel de equipos ≤ local + 1; formación ≤ tipo de cliente + 1; módulos de software según la reputación; locales según el tipo de cliente y su coste de obras.
- **Gastos fijos mensuales:** nóminas prorrateadas por días (SS empresa 32,15%, trabajador 6,50%, IRPF medio 12%), alquiler (621, retención del 19% en los locales de Tomelloso), suministros (628, según el local y la plantilla) y seguro trimestral exento (625).
- **Amortización mensual:** ordenadores en 4 años (681/2817), obras del local en 10 años (681/2815) y software en 3 años (680/2806). Al renovar equipos o mudarse, baja con pérdida en 671.
- **Fiscalidad:** igual que la 0.1 (IVA trimestral con compensación, modelos 111/115/123, seguros sociales domiciliados, IS con los tipos de la Ley 7/2024, reserva legal y límite de dividendos). El departamento fiscal de la propia gestoría presenta los modelos el último día sin recargo.
- **Guardado:** `partida.json`, formato 3 (`SaveCodec.FORMAT_VERSION`), que lee las partidas del formato 2. Partida de unos 100 KB tras 10 h de juego.
- **Sonido:** preferencias en `SharedPreferences` «ajustes» (`sonido`, `musica`, `vibracion`). La música suena a 0,35 de volumen y solo en primer plano.
- **Versiones:** AGP 9.5.0, Gradle 9.6.0, Kotlin 2.4.0, Compose BOM 2026.08.00, compileSdk 37, targetSdk 36, minSdk 26.

## Mediciones (versión 0.3; jugador automático activo 10 h con un 85% de aciertos)

| Minuto | Fecha de juego | Banco | Facturación por minuto | Local | Reputación |
|---|---|---|---|---|---|
| 5 | 11/01/2026 | 853 € | 245 € | Bajo | 3 |
| 15 | 31/01/2026 | 7.334 € | 3.896 € | Bajo | 43 |
| 30 | 02/03/2026 | 105.722 € | 57.871 € | Plaza | 100 |
| 60 | 01/05/2026 | 381.207 € | 154.156 € | Plaza | 254 |
| 120 | 29/08/2026 | 2,16 M€ | 456.418 € | Ciudad Real | 582 |
| 180 | 27/12/2026 | 3,40 M€ | 742.883 € | Ciudad Real | 898 |
| 240 | 26/04/2027 | 10,92 M€ | 2,90 M€ | Madrid | 1.229 |
| 360 | 22/12/2027 | 8,96 M€ | 3,08 M€ | Madrid | 2.038 |
| 600 | 15/04/2029 | 330,78 M€ | 12,19 M€ | Torre | 3.787 |

- **Aperturas:** Laboral 11 min; Contabilidad 27,5 min; Mercantil 69 min; Consultoría 183,5 min; Patrimonios 481 min.
- **Tipos de cliente:** Comercios 26 min; Pymes 69 min; Medianas 180 min; Grandes 425 min. **Locales:** 27; 69; 181,5; 432 min.
- **Ejercicios:** 2026, honorarios 169,76 M€ y resultado 11,90 M€ (IS 2,10 M€); 2027, 1,28 mil M€ y 66,83 M€; 2028, 3,50 mil M€ y 375,04 M€.
- **Consultas:** 539 bien y 78 mal (racha máxima 37). **Noche fuera (8 h) al final:** 960 días, +634 de reputación.
- **Comparación con la 0.2:** entre un 10% y un 20% más rápida (asientos, VIP y temporadas). No se ha reequilibrado a propósito: el usuario aún no ha jugado la 0.2, y conviene ajustar el ritmo con su impresión.
- El balance cuadra y el banco nunca queda en negativo en ningún punto de control (`partidaLargaSiempreCuadra`).

## Simplificaciones conocidas (deben citarse en la memoria)

- La ampliación de cartera se registra como publicidad (627), lo que deja un margen neto bajo (≈ 6% en el primer ejercicio).
- Amortización lineal por grupo de cada departamento, con mes completo aunque se compre a final de mes; las obras se amortizan en 10 años aunque el contrato de alquiler pudiera ser más corto.
- Retención de nóminas media del 12% y cotización media del 32,15%; sin pagas extra.
- Sin pagos fraccionados del IS (modelo 202) ni modelo 347 de la propia gestoría.
- Los asientos de las consultas son de los clientes y no pasan al diario de la gestoría; las bajas por gripe no generan coste de incapacidad temporal.
- El traspaso no calcula la ganancia patrimonial del socio en su IRPF: el precio de venta se suma sin más a su patrimonio personal.
- Para el art. 273 LSC se usa el patrimonio neto actual, que incluye el resultado provisional del ejercicio en curso.

## Pendiente

**Decisiones del usuario**
- Nombre definitivo, identificador de la app (`com.alecu.balancetycoon` es provisional) y marca.
- Si el jugador elige el nombre de la gestoría y su avatar al empezar.
- Recompensas de los anuncios y precio del Premium.
- Ritmo de la partida, una vez jugada la 0.3.

**Técnico (0.4)**
- Ajustar la interfaz y el ritmo con lo que el usuario vea en su móvil.
- Tutorial de bienvenida (primeros pasos: tocar la planta, contratar, primera consulta, primer asiento).
- Más consultas de los dos tipos y modo de práctica por módulo del ciclo.
- Logros y mejoras permanentes que se compren con la experiencia.
- Guardado en la nube o copia de seguridad.

**Publicación (1.0)**
- AdMob + UMP, Play Billing, política de privacidad, ficha de Google Play, capturas e icono definitivo.
