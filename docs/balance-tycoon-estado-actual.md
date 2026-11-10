# Balance Tycoon — Estado actual (versión 0.2, 10 de noviembre de 2026)

Este documento sirve para continuar el desarrollo en un chat nuevo sin perder nada. El diseño completo está en `balance-tycoon-gdd.md`.

## Qué hay hecho

- **Rediseño 0.2:** la empresa del jugador es una **gestoría** con seis departamentos (una planta cada uno), trabajadores, formación, equipos informáticos, programa de gestión, cinco locales, seis tipos de cliente según la reputación y 49 consultas reales de clientes.
- **Motor (`core`)** en Kotlin puro: 28 tests en verde y una simulación de equilibrio con un jugador automático (`AutoPlayer`), que también prepara las partidas de demostración de las capturas.
- **App Android (`app`)** con Jetpack Compose: torre en pixel art dibujada con `Canvas` (pantalla Oficina), pestañas Plantilla, Equipos, Clientes y Cuentas (diario, mayor, PyG, balance e impuestos), tema propio con la fuente Pixelify Sans, textos en español e inglés.
- **Repositorio y compilación automática:** https://github.com/raulalecu6/BalanceTycoon. Cada subida a `main` pasa los tests, compila el APK, lo publica en la release `ultima-version` y después lo prueba en un emulador Android 14 (Pixel 6) y publica capturas en la release `capturas`. El APK se firma con una clave de depuración fija (`app/debug.keystore`) para instalar cada versión encima de la anterior.
- **Prototipo 0.1 probado en un móvil real** por el usuario (instala y funciona). La partida de la 0.1 no es compatible: al actualizar se guarda aparte (`partida-prototipo-0.1.json`) y se empieza de cero con un aviso.

## Cómo retomar en un chat nuevo

1. Pedir a Claude que conecte el repositorio `raulalecu6/BalanceTycoon` (con permiso de escritura) y pasarle este documento.
2. Los cambios se suben a `main`; GitHub Actions compila, publica el APK y saca capturas. Las capturas se descargan desde la release `capturas` (los artefactos de Actions no son accesibles desde la sesión de Claude).
3. Para probar el motor sin red: compilador de Kotlin que trae Gradle (`/opt/gradle-*/lib/kotlin-compiler-embeddable-*.jar`) y JUnit 4 (`junit-4.13.2.jar` y `hamcrest-core-1.3.jar` de la misma carpeta). La simulación acepta `-Dsim.hours=N`.
4. En la versión de pruebas, la actividad acepta dos extras para revisar la interfaz: `--ei tab N` (pestaña) y `--ei demo_minutes N` (partida avanzada con el jugador automático). Ejemplo: `adb shell am start -n com.alecu.balancetycoon/.MainActivity --ei demo_minutes 150`.

## Estructura

```
BalanceTycoon/
├── core/                       Motor (Kotlin puro, sin Android)
│   └── src/main/kotlin/com/alecu/balancetycoon/core/
│       ├── accounting/         Plan de cuentas (Pgc), libro diario y mayor (Ledger), informes (Reports)
│       ├── calendar/           Fecha del juego y plazos (GameDate)
│       ├── format/             Formato de cifras y fechas ES/EN (Fmt)
│       ├── game/               Catalog (departamentos, locales, clientes, equipos, software), Cases (consultas),
│       │                       GameState, GameEngine y AutoPlayer
│       ├── i18n/               Textos bilingües (L, Lang)
│       ├── money/              Importes en céntimos y redondeo (Money)
│       ├── save/               JSON propio y guardado (Json, SaveCodec, formato 2)
│       └── tax/                IVA, retenciones, Seguridad Social, recargos e IS (TaxRules)
│   └── src/test/kotlin/...     Tests y mediciones de equilibrio (EconomySimulation)
├── app/                        App Android (Compose)
│   └── src/main/java/com/alecu/balancetycoon/
│       ├── MainActivity.kt     Actividad única; extras de depuración «tab» y «demo_minutes»
│       ├── GameViewModel.kt    Bucle del juego (10 veces por segundo), guardado, progreso fuera y demo
│       ├── SaveStore.kt        Archivo partida.json con escritura atómica; aparta las partidas de la 0.1
│       ├── AdsProvider.kt      Punto de enganche de anuncios (simulado)
│       └── ui/                 App (barras y diálogos), Tower (dibujo de la torre), OfficeScreen, StaffScreen,
│                               EquipmentScreen, ClientsScreen, BooksScreen, TaxScreen, Common y tema
├── .github/                    Flujo de compilación y script de capturas en el emulador
└── docs/                       GDD, este documento y la licencia de la fuente
```

## Datos técnicos clave

- **Tiempo:** 1 día contable = 30 s reales (`GameEngine.DAY_SECONDS`). Mes = 15 min; trimestre = 45 min; ejercicio ≈ 3 h. Fuera de la app, máximo 8 h (960 días), por tramos de 10 días.
- **Importes:** céntimos en `Long`; tipos en puntos básicos (2100 = 21%); redondeo comercial con `BigDecimal`.
- **Inicio:** capital de 3.000 € (572 a 100), fianza de 600 € (260), apertura del departamento fiscal (600 € en 627 + IVA) y un ordenador de segunda mano (500 € en 217 + IVA). Banco inicial: 1.069 €.
- **Honorarios:** se acumulan durante el día y se contabilizan en un asiento resumen por departamento (705 + 477), con la parte a plazo según el tipo de cliente (430 y `PendingFlow`).
- **Multiplicadores:** trabajadores (×1 a ×6), equipos (×1 a ×2,5), formación (+15% por nivel), programa de gestión (×1 a ×1,7), tipo de cliente (×1 a ×4,5), racha (+2% por acierto, máx. +20%) y turbo de anuncio (×2). Hitos de velocidad a 25, 50, 100, 200, 300 y 400 clientes.
- **Consultas:** una cada 2 días, máximo 6; a los 10 días las resuelve el equipo a mitad de precio (+1 de reputación). Bien: +3 + tipo de cliente; mal: −3. Honorarios ≈ 90 s de facturación, con un mínimo por tipo de cliente.
- **Límites por progreso:** puestos por planta según el local; nivel de equipos ≤ local + 1; formación ≤ tipo de cliente + 1; módulos de software según la reputación; locales según el tipo de cliente y su coste de obras.
- **Gastos fijos mensuales:** nóminas prorrateadas por días (SS empresa 32,15%, trabajador 6,50%, IRPF medio 12%), alquiler (621, retención del 19% en los locales de Tomelloso), suministros (628, según el local y la plantilla) y seguro trimestral exento (625).
- **Amortización mensual:** ordenadores en 4 años (681/2817), obras del local en 10 años (681/2815) y software en 3 años (680/2806). Al renovar equipos o mudarse, baja con pérdida en 671.
- **Fiscalidad:** igual que la 0.1 (IVA trimestral con compensación, modelos 111/115/123, seguros sociales domiciliados, IS con los tipos de la Ley 7/2024, reserva legal y límite de dividendos). El departamento fiscal de la propia gestoría presenta los modelos el último día sin recargo.
- **Guardado:** `partida.json`, formato 2 (`SaveCodec.FORMAT_VERSION`). Partida de unos 96 KB tras 10 h de juego.
- **Versiones:** AGP 9.5.0, Gradle 9.6.0, Kotlin 2.4.0, Compose BOM 2026.08.00, compileSdk 37, targetSdk 36, minSdk 26.

## Mediciones (versión 0.2; jugador automático activo 10 h con un 85% de aciertos)

| Minuto | Fecha de juego | Banco | Facturación por minuto | Local | Reputación |
|---|---|---|---|---|---|
| 5 | 11/01/2026 | 833 € | 245 € | Bajo | 3 |
| 15 | 31/01/2026 | 6.192 € | 1.670 € | Bajo | 27 |
| 30 | 02/03/2026 | 60.217 € | 45.669 € | Plaza | 77 |
| 60 | 01/05/2026 | 503.839 € | 89.931 € | Plaza | 198 |
| 120 | 29/08/2026 | 948.955 € | 381.217 € | Ciudad Real | 445 |
| 180 | 27/12/2026 | 3,60 M€ | 457.718 € | Ciudad Real | 697 |
| 240 | 26/04/2027 | 4,51 M€ | 1,49 M€ | Madrid | 943 |
| 360 | 22/12/2027 | 18,33 M€ | 2,11 M€ | Madrid | 1.596 |
| 600 | 15/04/2029 | 150,31 M€ | 7,46 M€ | Torre | 2.937 |

- **Aperturas:** Laboral 12 min; Contabilidad 30 min; Mercantil 92 min; Consultoría 239 min; Patrimonios con la torre (≈ 9 h).
- **Ejercicios:** 2026, honorarios 90,36 M€ y resultado 5,52 M€ (IS 974.433 €, nueva creación); 2027, 660,93 M€ y 31,79 M€; 2028, 1,24 mil M€ y 106,16 M€.
- **Noche fuera (8 h) al final:** 960 días, +636 de reputación.
- El balance cuadra y el banco nunca queda en negativo en ningún punto de control (`partidaLargaSiempreCuadra`).

## Simplificaciones conocidas (deben citarse en la memoria)

- La ampliación de cartera se registra como publicidad (627), lo que deja un margen neto bajo (≈ 6% en el primer ejercicio).
- Amortización lineal por grupo de cada departamento, con mes completo aunque se compre a final de mes; las obras se amortizan en 10 años aunque el contrato de alquiler pudiera ser más corto.
- Retención de nóminas media del 12% y cotización media del 32,15%; sin pagas extra.
- Sin pagos fraccionados del IS (modelo 202) ni modelo 347 de la propia gestoría.
- Para el art. 273 LSC se usa el patrimonio neto actual, que incluye el resultado provisional del ejercicio en curso.

## Pendiente

**Decisiones del usuario**
- Nombre definitivo, identificador de la app (`com.alecu.balancetycoon` es provisional) y marca.
- Si el jugador elige el nombre de la gestoría y su avatar al empezar.
- Recompensas de los anuncios y precio del Premium; música.

**Técnico (0.3)**
- Ajustar la interfaz con lo que el usuario vea en su móvil.
- Tutorial de bienvenida (primeros pasos: tocar la planta, contratar, primera consulta).
- Más consultas y consultas de asiento completo (debe y haber).
- Prestigio («vender la gestoría») y eventos (campaña de la renta, cierre del ejercicio).
- Guardado en la nube o copia de seguridad.

**Publicación (1.0)**
- AdMob + UMP, Play Billing, política de privacidad, ficha de Google Play, capturas e icono definitivo.
