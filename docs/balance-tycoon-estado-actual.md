# Balance Tycoon — Estado actual (versión 0.1, 10 de noviembre de 2026)

Este documento sirve para continuar el desarrollo en un chat nuevo sin perder nada. El diseño completo está en `balance-tycoon-gdd.md`.

## Qué hay hecho

- **Motor contable y de juego (`core`)** en Kotlin puro, compilado y probado: 24 tests en verde y una simulación de equilibrio.
- **App Android (`app`)** con Jetpack Compose: cuatro pantallas (Negocio, Bandeja, Libros y Hacienda), progreso fuera de la app, guardado y textos en español e inglés. Compila en GitHub Actions desde la primera subida (10/11/2026).
- **Repositorio y compilación automática:** https://github.com/raulalecu6/BalanceTycoon. Cada subida a `main` pasa los tests del motor, genera el APK de prueba y lo publica en la release `ultima-version`. El APK se firma con una clave de depuración fija (`app/debug.keystore`) para poder instalar cada versión encima de la anterior.
- **Documentación:** diseño (GDD), este documento y el README.

## Cómo retomar en un chat nuevo

1. Pedir a Claude que conecte el repositorio `raulalecu6/BalanceTycoon` (con permiso de escritura) y pasarle este documento.
2. Los cambios se suben a `main` y GitHub Actions compila y publica el APK; los errores se leen en el registro de la ejecución.
3. Para probar el motor sin red: compilador de Kotlin que trae Gradle (`/opt/gradle-*/lib/kotlin-compiler-embeddable-*.jar`) y JUnit 4 (`junit-4.13.2.jar` y `hamcrest-core-1.3.jar` de la misma carpeta).

## Estructura

```
BalanceTycoon/
├── core/                       Motor (Kotlin puro, sin Android)
│   └── src/main/kotlin/com/alecu/balancetycoon/core/
│       ├── accounting/         Plan de cuentas (Pgc), libro diario y mayor (Ledger), informes (Reports)
│       ├── calendar/           Fecha del juego y plazos (GameDate)
│       ├── format/             Formato de cifras y fechas ES/EN (Fmt)
│       ├── game/               Catálogo (líneas y documentos), estado y motor (GameEngine)
│       ├── i18n/               Textos bilingües (L, Lang)
│       ├── money/              Importes en céntimos y redondeo (Money)
│       ├── save/               JSON propio y guardado (Json, SaveCodec)
│       └── tax/                IVA, retenciones, Seguridad Social, recargos e IS (TaxRules)
│   └── src/test/kotlin/...     Tests, bot jugador y mediciones de equilibrio
├── app/                        App Android (Compose)
│   └── src/main/java/com/alecu/balancetycoon/
│       ├── MainActivity.kt     Actividad única; avisa al ViewModel al entrar y salir
│       ├── GameViewModel.kt    Bucle del juego (10 veces por segundo), guardado y progreso fuera
│       ├── SaveStore.kt        Archivo partida.json con escritura atómica
│       ├── AdsProvider.kt      Punto de enganche de anuncios (simulado)
│       └── ui/                 Pantallas, tema y componentes
└── docs/                       GDD y este documento
```

## Datos técnicos clave

- **Tiempo:** 1 día contable = 30 s reales (`GameEngine.DAY_SECONDS`). Mes = 15 min; trimestre = 45 min; ejercicio ≈ 3 h. El primer ejercicio es el año real en que se crea la partida.
- **Fuera de la app:** máximo 8 h (`OFFLINE_CAP_SECONDS`), unos 960 días. La app avanza por tramos de 10 días cediendo el hilo para no bloquear la pantalla.
- **Importes:** céntimos en `Long`; tipos en puntos básicos (2100 = 21%); redondeo comercial con `BigDecimal`. Límite práctico: `Money.MAX` ≈ 23.000 billones de euros.
- **Capital inicial:** 3.000 € (572 a 100). Al empezar se compra el primer nivel de la panadería (horno de 600 € + IVA) y llega la factura de la notaría.
- **Ventas:** se acumulan durante el día y se contabilizan al cerrar el día en un asiento resumen por línea (art. 28.2 del Código de Comercio), con su compra de aprovisionamientos.
- **Cobros y pagos:** `PendingFlow` con vencimiento (430 a 572 y 400 a 572).
- **Póliza de crédito:** si un pago obligatorio deja el banco en negativo, se dispone de la 5201; se devuelve cada día con el saldo disponible. Interés del 7% anual sobre el saldo medio diario, liquidado a fin de mes (662). Las ampliaciones no se pueden pagar con la póliza.
- **Bandeja:** un documento cada 2 días (`DOC_INTERVAL_DAYS`), el alquiler el día 1 de cada mes, máximo 12 en la bandeja; a los 10 días los contabiliza la gestoría (15 € + IVA). Racha: +2% de ventas por acierto, máximo +20%. El asiento siempre va a la cuenta correcta.
- **Importe de los documentos:** máximo entre el mínimo de la plantilla y `peso × 0,5% × ventas de los últimos 30 días`, con una variación aleatoria de ±20%. Resultado medido: gastos generales ≈ 7% de las ventas.
- **Nóminas:** prorrateadas por días trabajados en el mes. SS empresa 32,15%, trabajador 6,50%, IRPF medio 12%. Seguros sociales: último día del mes siguiente (domiciliados).
- **IVA:** liquidación el último día del trimestre, con compensación en la 4700; pago hasta el día 20 del mes siguiente (30 de enero en el 4T).
- **Retenciones:** acumuladores por modelo (`wh111`, `wh115`, `wh123`); una obligación trimestral que nombra los modelos con importe.
- **Gestoría:** activa por defecto; paga el último día con 45 € + IVA. Sin gestoría, a los 30 días de retraso se paga con recargo del art. 27 LGT (1% + 1% por mes completo), en la 678 y no deducible.
- **Cierre:** IS (ajuste por gastos no deducibles, bases negativas, nueva creación al 15% en el primer ejercicio con base positiva y el siguiente, microempresa, reducida dimensión o general según la cifra de negocios del año anterior), regularización, cierre y apertura. El beneficio queda pendiente de reparto hasta el 30 de junio; las pérdidas van a la 121 directamente.
- **Reparto:** reserva legal 10% hasta el 20% del capital, compensación de la 121, dividendo máximo limitado por el art. 273 LSC; dividendo pagado a los 30 días con retención del 19%; el neto suma al patrimonio del socio.
- **Azar:** xorshift64 con el estado guardado en la partida (reproducible en tests).
- **Guardado:** `partida.json` en el almacenamiento interno, cada 30 s y al salir. Formato versionado (`SaveCodec.FORMAT_VERSION = 1`).
- **Versiones del proyecto:** AGP 9.5.0, Gradle 9.6.0, Kotlin 2.4.0, Compose BOM 2026.08.00, compileSdk 37, targetSdk 36, minSdk 26. Con AGP 9 no se aplica `org.jetbrains.kotlin.android` (Kotlin integrado).

## Mediciones (versión 0.1; bot activo 4 h con un 85% de aciertos, partida de 2026)

| Minuto | Fecha de juego | Banco | Ventas por minuto | Niveles (pan, café, taller, queso, vino) |
|---|---|---|---|---|
| 5 | 11/01/2026 | 2.459 € | 814 € | 16 · 0 · 0 · 0 · 0 |
| 15 | 31/01/2026 | 3.766 € | 3.597 € | 31 · 2 · 0 · 0 · 0 |
| 30 | 02/03/2026 | 5.788 € | 5.407 € | 36 · 7 · 1 · 0 · 0 |
| 60 | 01/05/2026 | 10.400 € | 8.510 € | 38 · 15 · 4 · 0 · 0 |
| 120 | 29/08/2026 | 24.707 € | 16.606 € | 43 · 20 · 8 · 2 · 0 |
| 180 | 27/12/2026 | 29.377 € | 30.594 € | 46 · 22 · 16 · 4 · 0 |
| 240 | 26/04/2027 | 38.353 € | 40.908 € | 47 · 25 · 17 · 5 · 0 |

- **Desbloqueos:** cafetería 13,5 min; taller 29 min; quesería 107,5 min; bodega después de la primera noche.
- **Ejercicio 2026:** ventas 2,47 M€, resultado 1,21 M€, IS 212.909 € (nueva creación, 15%).
- **Gastos generales** (otros gastos de explotación) sobre ventas: 7,0%.
- **Noche fuera (8 h):** 960 días, ventas 19,64 M€, banco +10,55 M€, 11.735 asientos.
- **Partida guardada:** unos 89 KB. **Asientos** en 4 h: 4.786.
- El balance cuadra y el banco nunca queda en negativo en ningún punto de control (tests `partidaLargaSiempreCuadra` y `cierreDelEjercicioYAperturaDelSiguiente`).

## Simplificaciones conocidas (deben citarse en la memoria)

- Compras justo a tiempo: no hay existencias ni variación de existencias.
- Amortización lineal por grupo homogéneo de cada línea, con mes completo aunque se compre a final de mes.
- Retención de nóminas media del 12% y cotización media del 32,15%.
- Sin pagos fraccionados del IS (modelo 202), sin modelo 347 y sin deducciones del IS.
- Para el art. 273 LSC se usa el patrimonio neto actual, que incluye el resultado provisional del ejercicio en curso.
- Margen neto muy alto (≈ 49%): es una licencia de juego para que la empresa pueda crecer.

## Pendiente

**Decisiones del usuario**
- Nombre definitivo, identificador de la app (`com.alecu.balancetycoon` es provisional) y marca.
- Estilo visual (Canva, como HeadBallerz) y tipografía (Unbounded, de Google Fonts).
- Recompensas de los anuncios y precio del Premium.

**Técnico (0.2)**
- Probar la app en un móvil real y ajustar la interfaz con lo que se vea.
- Pantalla de bienvenida y tutorial con el documento de la notaría.
- Prestigio («vender la empresa») y más líneas (distribuidora y asesoría).
- Guardado en la nube o copia de seguridad (Google Play Games o Auto Backup).
- Tests de interfaz con Compose.

**Publicación (1.0)**
- AdMob + UMP, Play Billing, política de privacidad, ficha de Google Play, capturas e icono definitivo.
