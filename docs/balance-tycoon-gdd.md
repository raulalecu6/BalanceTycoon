# Balance Tycoon — Documento de diseño (versión 0.3, 24 de noviembre de 2026)

> **Título provisional.** El nombre definitivo, el identificador de la aplicación en Google Play y la marca quedan pendientes de decidir y de comprobar en la OEPM[^oepm] y en la propia tienda (ver apartado 17).

**Autor:** Raúl Marian Alecu · **Proyecto:** juego para Android, portafolio y trabajo final del Ciclo Formativo de Grado Superior de Administración y Finanzas (curso 2026-27).

> **Cambio respecto a la versión 0.1.** El prototipo 0.1 era un conglomerado de tiendas (panadería, cafetería, taller, quesería y bodega). Tras probarlo, se replantea el juego: la empresa del jugador pasa a ser una **gestoría** que resuelve las consultas de clientes cada vez más importantes, crece por plantas dentro de un edificio y se muda a locales más grandes. El motor contable y fiscal de la 0.1 se conserva íntegro.

> **Novedades de la versión 0.3.** Más jugabilidad, aspecto y sonido: consultas de **asiento completo** (debe y haber), **clientes VIP**, **temporadas** del calendario fiscal, **imprevistos** (averías, gripe y jornadas de networking), **traspaso de la gestoría** con puntos de experiencia (prestigio), **música y efectos de sonido** propios, vibración y una torre con más vida (noche según la hora real, coches, clientes haciendo cola y decoración que mejora con cada local).

---

## 1. Ficha del proyecto

| Campo | Valor |
|---|---|
| Género | Idle / tycoon de gestión con contabilidad real y preguntas de asesoría |
| Plataforma | Android (teléfonos y tabletas), API 26 (Android 8.0) o superior |
| Tecnología | Kotlin nativo, Jetpack Compose, motor contable en Kotlin puro |
| Idiomas | Español e inglés desde la primera versión (como HeadBallerz) |
| Público | Jugadores de idle y tycoon y estudiantes de FP, bachillerato y grados de empresa |
| Clasificación prevista | PEGI 3 (sin violencia ni compras aleatorias) |
| Modelo de negocio | Gratuito con anuncios recompensados opcionales y compra única «Premium» |
| Normativa de referencia | PGC PYMES (RD 1515/2007), Ley del IVA, Ley del IS, Ley del IRPF, Código de Comercio, LSC y Estatuto de los Trabajadores |

---

## 2. Concepto y propuesta de valor

Los juegos idle de empresas, como *AdVenture Capitalist* o *Tiny Tower*, han demostrado que el público disfruta viendo crecer un negocio mientras está fuera, pero en todos ellos el dinero es un único número que sube, sin impuestos, sin cobros aplazados y sin diferencia entre beneficio y liquidez. Esto, sumado a que la contabilidad y la fiscalidad se perciben como materias áridas, prepara el terreno para un juego en el que el jugador dirige justamente el tipo de empresa que vive de ellas: una gestoría.

- **Problema identificado:** los idle tycoon ignoran la realidad económica de una empresa y las herramientas para practicar contabilidad y fiscalidad son poco atractivas.
- **Propuesta:** un idle en el que el jugador abre una gestoría en un bajo de Tomelloso y la convierte en una firma con sede en una torre de Madrid, mientras resuelve consultas reales de sus clientes y el juego lleva, de forma visible, la contabilidad de la propia gestoría.
- **Diferenciación:**
  - **Aprender jugando:** cada consulta de un cliente es una pregunta real de IVA, IRPF, nóminas, contabilidad, derecho mercantil, análisis financiero o patrimonios, con una explicación y su artículo.
  - **Contabilidad de verdad por debajo:** cada honorario, nómina, ordenador, curso o mudanza genera su asiento en el libro diario según el PGC PYMES.
  - **Beneficio frente a tesorería:** las empresas grandes pagan a 30 o 60 días, el IVA cobrado no es de la gestoría y se liquida cada trimestre.
- **Doble público:** se juega como cualquier idle, pero quien quiera aprender puede abrir los libros y ver exactamente qué ha pasado y por qué.

---

## 3. Bucle de juego

- **Bucle activo (segundos):** tocar las plantas sin plantilla para tramitar expedientes a mano, responder las consultas de los clientes que llegan a recepción (de opción múltiple o de asiento completo) y atender los imprevistos.
- **Bucle de crecimiento (minutos):** ampliar la cartera de cada departamento, contratar trabajadores, formarlos y renovar sus ordenadores.
- **Bucle de reputación (horas):** las consultas bien resueltas suben la reputación, que trae clientes más importantes, con más patrimonio y mayores honorarios, y abre locales más grandes.
- **Bucle contable (horas):** cierres mensuales (nóminas, alquiler, suministros y amortizaciones), trimestrales (IVA, retenciones y seguro) y anuales (Impuesto sobre Sociedades y reparto del resultado), con temporadas en las que cada departamento tiene más trabajo.
- **Bucle de prestigio (días):** cuando la gestoría ya es grande, se puede traspasar y fundar otra desde cero con puntos de experiencia que aceleran toda la partida siguiente.

---

## 4. Pantalla principal: la torre

La pantalla principal es un edificio en corte dibujado en pixel art, inspirado en los tycoons de torres:

- **Tejado:** rótulo luminoso con el nombre de la gestoría y el local actual.
- **Una planta por departamento**, cada una con su color de pared, mesas, sillas, lámparas y un archivador. En cada puesto ocupado aparece un trabajador tecleando, y el ordenador cambia según el nivel de equipos (monitor de tubo, PC, doble monitor, estación de trabajo, servidor con LED y holograma de IA).
- **Planta baja:** recepción con el jefe o la jefa, que muestra un bocadillo cuando hay consultas pendientes. Los clientes entran por la puerta y **hacen cola** ante el mostrador, vestidos según su tipo (los VIP, con traje morado, gafas de sol y un destello). El suelo y la decoración mejoran con cada local: baldosas, mármol, molduras doradas y cuadros.
- **Fondo:** cambia con la sede: casas bajas y molinos en Tomelloso, bloques de oficinas en Ciudad Real y rascacielos en Madrid. Por la calle pasan coches (y taxis en Madrid) y hay farolas.
- **Día y noche:** el cielo sigue la **hora real del móvil**: amanecer, día, atardecer y noche con estrellas, luna y ventanas encendidas.
- **Avisos en la torre:** cada planta muestra su temporada (×1,5 o ×2), su avería (pantallas rotas y humo) o su gripe (un puesto vacío), y un reloj de pared marca el avance del día contable.
- **Interacción:** tocar una planta sin trabajadores tramita un expediente; tocar una planta cerrada la abre; tocar la recepción lleva a las consultas. Al terminar expedientes salen monedas flotantes con los honorarios.
- **Panel inferior:** lista de departamentos con nivel, facturación por segundo, barra de progreso y botón de ampliar (×1, ×10 o máximo).

Navegación inferior: **Oficina**, **Plantilla**, **Equipos**, **Clientes** y **Cuentas** (diario, mayor, PyG, balance e impuestos). Sobre la lista de departamentos aparece una fila de avisos (temporadas, imprevistos y clientes VIP) que se pueden tocar para ver el detalle y actuar.

---

## 5. Departamentos

Cada departamento factura honorarios por los expedientes que tramita (705 Prestaciones de servicios, con IVA del 21%). El nivel representa la cartera de clientes del departamento y se amplía con campañas de captación (627 Publicidad).

| Planta | Departamento | Apertura | Expediente | Honorarios por nivel | Salario bruto |
|---|---|---|---|---|---|
| 1 | 🧾 Fiscal | 600 € | 1,5 s | 0,50 € | 1.350 €/mes |
| 2 | 👥 Laboral | 1.500 € | 4 s | 1,20 € | 1.400 €/mes |
| 3 | 📒 Contabilidad | 30.000 € | 10 s | 6 € | 1.600 €/mes |
| 4 | ⚖️ Mercantil | 400.000 € | 24 s | 40 € | 2.000 €/mes |
| 5 | 📈 Consultoría | 6 M€ | 60 s | 250 € | 2.600 €/mes |
| 6 | 💎 Patrimonios | 120 M€ | 150 s | 2.000 € | 3.400 €/mes |

- **Hitos:** al llegar a 25, 50, 100, 200, 300 y 400 clientes, los expedientes van el doble de rápido y la gestoría gana 10 puntos de reputación.
- **Honorarios por expediente** = honorarios por nivel × nivel × trabajadores × equipos × formación × programa de gestión × tipo de cliente × racha × experiencia × temporada o imprevisto × turbo.

---

## 6. Plantilla, formación y equipos

- **Trabajadores:** el primero automatiza el departamento y cada uno más suma lo mismo que uno de los actuales. El número de puestos por planta depende del local (de 2 a 6). Cada contratación compra el ordenador del nuevo puesto (217) y el salario se paga cada mes con su Seguridad Social y su retención de IRPF.
- **Formación:** cinco niveles por departamento, +15% de honorarios cada uno. Se contabiliza en 649 Otros gastos sociales y está exenta de IVA por impartirla un centro autorizado. El nivel máximo depende de la reputación.
- **Equipos informáticos:** seis niveles por departamento (multiplicadores ×1 a ×2,5). El nivel máximo depende del local. Al renovar, los ordenadores antiguos **se dan de baja**: se cancela su amortización acumulada (2817) y el valor pendiente es una pérdida (671).
- **Programa de gestión:** cinco módulos comunes a toda la gestoría (×1,1 a ×1,7), registrados como aplicaciones informáticas (206) y amortizados en tres años (680).

---

## 7. Clientes, reputación y consultas

| Tipo de cliente | Reputación | Honorarios | Cobro |
|---|---|---|---|
| 🧑‍🔧 Autónomos | 0 | ×1 | Al contado |
| 🏪 Comercios y hostelería | 75 | ×1,3 | Al contado |
| 🏭 Pymes | 300 | ×1,7 | 40% a 30 días |
| 🏢 Medianas empresas | 900 | ×2,3 | 70% a 30 días |
| 🏦 Grandes empresas | 2.500 | ×3,2 | 100% a 60 días |
| 💎 Grupos y grandes patrimonios | 6.500 | ×4,5 | 100% a 60 días |

- **Consultas:** cada dos días contables llega una consulta de un cliente ficticio (máximo seis pendientes). Hay **49 consultas** repartidas por departamento y tipo de cliente: tipos de IVA, modelos 130, 111, 303, 347, 349, 202 y 190, recargo de equivalencia, SII, alta en la Seguridad Social, indemnizaciones, registro de jornada, cuentas del PGC, plazos de las cuentas anuales, capital mínimo de la S.L. y la S.A., reserva legal, causas de disolución, tipos del IS, ratios de liquidez, fondo de maniobra, leasing, punto muerto, VAN, Impuesto sobre el Patrimonio, base del ahorro, traspasos de fondos, donaciones y exención de dividendos.
- **Bien resuelta:** se factura la consulta (unos 90 segundos de facturación), suma 3 puntos de reputación más el nivel del cliente y alarga la racha (+2% de honorarios por acierto, hasta +20%).
- **Mal resuelta:** la rectificación es gratuita y se pierden 3 puntos.
- **Sin responder:** a los diez días la resuelve el equipo a mitad de precio y suma 1 punto, de forma que la partida también avanza mientras el jugador está fuera.
- **Patrimonio gestionado:** indicador que multiplica el número de clientes por el patrimonio medio de su tipo.

### 7.1 Consultas de asiento completo

Un 35% de las consultas son **asientos completos**: el cliente describe una operación con sus importes («He vendido mercaderías a crédito por 2.400 € más IVA») y el jugador ve el asiento con las cantidades ya puestas en el debe y el haber; solo tiene que tocar cada línea y elegir su cuenta entre varias candidatas, que incluyen cuentas parecidas como distractores. Al entregarlo, el juego marca cada línea como correcta o incorrecta y muestra la explicación.

- **Recompensa:** honorarios ×1,5 y 2 puntos más de reputación que una consulta de opción múltiple.
- **Importes variables:** cada vez que llega una consulta se sortea una base distinta dentro de un rango, y las líneas se calculan con su porcentaje (IVA, retenciones, Seguridad Social…), de modo que el asiento siempre cuadra.
- **Catálogo (26 asientos):**

| Departamento | Operaciones | Asientos |
|---|---|---|
| 🧾 Fiscal | Venta a crédito, compra de fruta al 4%, factura de un profesional con retención, liquidación del IVA, venta al contado en caja y pago fraccionado del IS | 430 a 700 + 477 · 600 + 472 a 400 · 623 + 472 a 4751 + 572 · 477 a 472 + 4750 · 570 a 700 + 477 · 473 a 572 |
| 👥 Laboral | Nómina, pago de los seguros sociales, pago de la nómina y modelo 111 | 640 + 642 a 476 + 4751 + 465 · 476 a 572 · 465 a 572 · 4751 a 572 |
| 📒 Contabilidad | Ordenador, alquiler a una empresa y a un particular, seguro, cobro de un cliente y amortización de una furgoneta | 217 + 472 a 572 · 621 + 472 a 572 · 621 + 472 a 4751 + 572 · 625 a 572 · 572 a 430 · 681 a 2818 |
| ⚖️ Mercantil | Constitución, reparto del resultado, pago del dividendo y aplicación de pérdidas | 572 a 100 · 129 a 112 + 113 + 526 · 526 a 4751 + 572 · 121 a 129 |
| 📈 Consultoría | Préstamo, intereses, Impuesto sobre Sociedades y reclasificación a corto plazo | 572 a 170 · 662 a 572 · 6300 a 473 + 4752 · 170 a 520 |
| 💎 Patrimonios | Compra de acciones y cobro de dividendos con retención | 540 a 572 · 572 + 473 a 760 |

### 7.2 Clientes VIP

De vez en cuando llega un **cliente VIP**: paga el triple y da el doble de reputación, pero se va a otra gestoría si no se le responde en **3 días contables** (−2 de reputación). Su consulta aparece la primera, con un fondo morado y una cuenta atrás, y en la torre se le ve en la cola de recepción.

---

## 8. Locales

| Local | Ciudad | Plantas | Puestos por planta | Alquiler | Obras | Requisito |
|---|---|---|---|---|---|---|
| Bajo comercial | Tomelloso | 2 | 2 | 300 €/mes | — | — |
| Oficina en la plaza | Tomelloso | 3 | 3 | 900 €/mes | 20.000 € | Comercios y hostelería |
| Edificio de oficinas | Ciudad Real | 4 | 4 | 3.200 €/mes | 300.000 € | Pymes |
| Oficinas en el distrito financiero | Madrid | 5 | 5 | 18.000 €/mes | 5 M€ | Medianas empresas |
| Torre de oficinas | Madrid | 6 | 6 | 80.000 €/mes | 80 M€ | Grandes empresas |

- **Fianza** de dos mensualidades (art. 36 de la Ley de Arrendamientos Urbanos) en la cuenta 260; al mudarse se recupera.
- **Obras de acondicionamiento** en 215 Otras instalaciones, amortizadas en diez años; al mudarse se dan de baja (671), porque quedan en el inmueble.
- **Alquiler mensual** en 621 con IVA y, si el arrendador es persona física (los dos locales de Tomelloso), retención del 19% que se ingresa con el modelo 115.

---

## 9. Temporadas e imprevistos

El calendario del juego reproduce los momentos de más trabajo de una gestoría real. Durante cada **temporada**, el departamento afectado factura más y su planta lo indica con una etiqueta dorada:

| Temporada | Fechas | Departamento | Efecto | Motivo real |
|---|---|---|---|---|
| 📚 Cierres contables | 1 de enero – 31 de marzo | Contabilidad | ×1,5 | Formulación de las cuentas anuales en tres meses (art. 253 LSC) y legalización de libros |
| 🧾 Campaña de la renta | 1 de abril – 30 de junio | Fiscal | ×2 | Declaraciones del IRPF del año anterior |
| 💎 Renta y Patrimonio | 1 de abril – 30 de junio | Patrimonios | ×1,5 | Impuesto sobre el Patrimonio de los clientes con más bienes |
| 🗳️ Juntas y depósito | 1 de junio – 31 de julio | Mercantil | ×1,5 | Aprobación de cuentas antes del 30 de junio (art. 164 LSC) y depósito en el mes siguiente (art. 279 LSC) |
| 🍇 Vendimia | 1 de septiembre – 31 de octubre | Laboral | ×1,5 | Contratación de temporeros en las bodegas y cooperativas de La Mancha |
| 📈 Planificación de fin de año | 1 de noviembre – 31 de diciembre | Consultoría | ×1,5 | Planificación del Impuesto sobre Sociedades y de las inversiones |

Además, cada 12 a 30 días contables se sortea un **imprevisto** entre los posibles en ese momento:

- **⭐ Cliente VIP** (35%): llega una consulta VIP (apartado 7.2).
- **💥 Avería informática** (25%): los ordenadores de un departamento fallan y factura la mitad durante 4 días, hasta que viene el servicio técnico de la garantía. Se puede **reparar ya** pagando a un técnico (622 Reparaciones y conservación + 472, con un coste del 15% del valor de los equipos de esa planta y un mínimo de 150 €).
- **🤧 Gripe** (15%, solo con dos o más trabajadores): el departamento factura un 25% menos durante 5 días. La explicación recuerda quién paga la incapacidad temporal por enfermedad común (días 1-3 sin prestación salvo convenio, 4-15 la empresa y desde el 16 la Seguridad Social o la mutua).
- **🤝 Jornada de networking** (25%): la Cámara de Comercio invita a la gestoría. Ir cuesta unos dos días de facturación (627 Publicidad, propaganda y relaciones públicas + 472) y da 8 puntos de reputación más 4 por cada tipo de cliente alcanzado; la invitación caduca a los 5 días.

---

## 10. Traspaso de la gestoría (prestigio)

Desde el edificio de oficinas de Ciudad Real, el jugador puede **vender las participaciones** de su gestoría y fundar otra desde cero, que empieza el 1 de enero siguiente en el bajo de Tomelloso.

- **Puntos de experiencia:** raíz cuadrada de los honorarios totales facturados, en millones de euros (1 punto con 1 M€, 2 con 4 M€, 3 con 9 M€…). Se acumulan de una gestoría a la siguiente.
- **Efecto:** cada punto suma un **10% a todos los ingresos** para siempre (×1,1, ×1,2…).
- **Precio de venta:** el patrimonio neto de la sociedad, que pasa al patrimonio personal del jugador.
- **Se pierde:** el local, la plantilla, los equipos, los clientes y la reputación. Se conserva el turbo de anuncio que quede.
- **Nombre:** se propone «Mi Gestoría II, S.L.», «III»…, editable antes de confirmar.

---

## 11. Sonido y vibración

- **Música:** un bucle propio de 34 segundos (112 pulsaciones por minuto, acordes Do–La menor–Fa–Sol) que solo suena con la aplicación en primer plano.
- **Efectos:** toque, moneda, compra, contratación, acierto, error, subida de nivel, mudanza, alerta y aviso.
- **Generación:** tanto la música como los efectos se sintetizan con código (`tools/generar_audio.py`, con NumPy y FFmpeg), de modo que no hay derechos de terceros.
- **Vibración:** al pulsar botones y al acertar o fallar una consulta.
- **Ajustes:** interruptores de música, efectos y vibración, que se recuerdan entre sesiones.

---

## 12. Motor contable

### 12.1 Asientos que genera el juego

| Hecho | Asiento |
|---|---|
| Constitución | 572 a 100 |
| Fianza del local | 260 a 572 |
| Apertura o ampliación de cartera | 627 + 472 a 572 |
| Compra de ordenadores | 217 + 472 a 572 |
| Baja de ordenadores sustituidos | 2817 + 671 a 217 |
| Curso de formación | 649 a 572 |
| Licencia de software | 206 + 472 a 572 |
| Obras del local | 215 + 472 a 572 |
| Honorarios del día (asiento resumen) | 572 / 430 a 705 + 477 |
| Honorarios de una consulta | 572 a 705 + 477 |
| Nóminas | 640 + 642 a 476 + 4751 + 465; después 465 a 572 |
| Alquiler | 621 + 472 a 4751 + 410; después 410 a 572 |
| Suministros | 628 + 472 a 410; después 410 a 572 |
| Seguro trimestral (exento) | 625 a 572 |
| Reparación urgente de una avería | 622 + 472 a 572 |
| Jornada de networking | 627 + 472 a 572 |
| Amortización mensual | 681 a 2817 + 2815; 680 a 2806 |
| Liquidación del IVA | 477 a 472 + 4750 (o 4700 a compensar) |
| Impuesto sobre Sociedades | 6300 a 4752 |
| Regularización, cierre y apertura | Grupos 6 y 7 contra 129; cierre y reapertura de todas las cuentas |
| Reparto del resultado | 129 a 112 + 121 + 113 + 526 |

Los asientos de las consultas del apartado 7.1 son de los clientes y no se registran en el diario de la gestoría; solo se factura la consulta.

### 12.2 Fiscalidad y plazos

Se conserva todo lo del prototipo 0.1: IVA trimestral con compensación, retenciones de los modelos 111, 115 y 123, seguros sociales domiciliados, Impuesto sobre Sociedades con los tipos de 2026 a 2029 de la Ley 7/2024 (nueva creación, microempresa, reducida dimensión y general), compensación de bases negativas, reserva legal (art. 274 LSC) y límite de dividendos (art. 273 LSC). Como la empresa es una gestoría, su propio **departamento fiscal presenta los modelos el último día de plazo** sin recargo, y el jugador puede pagarlos antes desde la pestaña de impuestos.

---

## 13. Ritmo y equilibrio

Un día contable dura 30 segundos reales (un mes, 15 minutos; un ejercicio, unas 3 horas) y el progreso fuera de la aplicación llega hasta 8 horas. Mediciones con un jugador automático que acierta el 85% de las consultas:

| Hito | Minuto de juego activo (0.3) | Minuto en la 0.2 |
|---|---|---|
| Primer trabajador (Fiscal) | 4 | 4 |
| Apertura de Laboral | 11 | 12 |
| Clientes «Comercios y hostelería» y mudanza a la plaza | 26-27 | 27-30 |
| Apertura de Contabilidad | 27,5 | 30 |
| Clientes «Pymes», apertura de Mercantil y mudanza a Ciudad Real (desde aquí se puede traspasar) | 69 | 73-92 |
| Clientes «Medianas empresas» y mudanza a Madrid | 180-182 | 210-240 |
| Clientes «Grandes empresas» y torre | 425-432 | 500-530 |
| Apertura de Patrimonios | 481 | ≈ 540 |

Primer ejercicio: unos 170 M€ de honorarios y 11,9 M€ de resultado (90 M€ y 5,5 M€ en la 0.2). La 0.3 va entre un 10% y un 20% más rápida por las consultas de asiento, los VIP y las temporadas; no se ha reequilibrado a propósito hasta que el juego se pruebe con personas. Se trata de una medición con un jugador automático que no se detiene; un jugador humano irá más despacio.

---

## 14. Monetización

- **Anuncios recompensados (opcionales):** honorarios ×2 durante 2 horas reales y, más adelante, duplicar lo ganado fuera de la aplicación.
- **Compra única «Premium» (estimación: 3,99 €):** elimina los anuncios, deja el turbo ×2 permanente y desbloquea decoraciones de la torre.
- **Sin anuncios intersticiales**, sin cajas de botín y sin moneda premium.
- **Línea futura:** licencia para centros educativos con modo profesor y baterías de consultas por módulo.

Se usará Google AdMob con el SDK de consentimiento UMP (obligatorio en la UE) y Google Play Billing. El código ya tiene el punto de enganche `AdsProvider`, que en el prototipo concede la recompensa sin mostrar anuncios.

---

## 15. Arquitectura técnica

- **Módulo `core` (Kotlin puro):** plan de cuentas, libro diario y mayor, informes, reglas fiscales, catálogo (departamentos, locales, tipos de cliente, equipos, software), consultas, motor del juego, jugador automático y guardado. Se prueba con JUnit sin emulador (35 tests y una simulación de equilibrio).
- **Módulo `app` (Android):** Jetpack Compose con un tema propio (paneles verde petróleo, botones menta, fuente Pixelify Sans). La torre se dibuja con `Canvas` en «píxeles de juego», sin imágenes externas.
- **Sonido:** `SoundPool` para los efectos y `MediaPlayer` para la música, con los archivos OGG en `res/raw`.
- **Guardado:** JSON propio en el almacenamiento interno. La versión 3 del formato carga sin problemas las partidas de la 0.2 (los datos nuevos empiezan vacíos); la del prototipo 0.1 no es compatible y se guarda aparte.
- **Integración continua:** GitHub Actions ejecuta los tests, compila el APK, lo publica en la release «ultima-version» y lo prueba en un emulador Android 14, del que publica capturas en la release «capturas».

---

## 16. Hoja de ruta

| Bloque | Prototipo 0.2 | Versión 0.3 (actual) | Versión 1.0 (publicación) |
|---|---|---|---|
| Edificio | 6 departamentos, 5 locales | Noche real, coches, cola de clientes, decoración por local, confeti | Decoración comprable y mascotas |
| Consultas | 49, de opción múltiple | 49 de opción múltiple y 26 de asiento completo, clientes VIP | 150 o más y modo profesor |
| Eventos | — | 6 temporadas y 4 imprevistos | Más imprevistos (inspección, subvenciones) |
| Plantilla | Contratar y formar | Igual | Perfiles con nombre, vacaciones y despidos con indemnización |
| Progresión | Reputación y mudanzas | Traspaso con puntos de experiencia | Logros y mejoras permanentes con la experiencia |
| Monetización | Punto de enganche simulado | Igual | AdMob, UMP y Play Billing |
| Sonido | — | Música, efectos y vibración propios | Más pistas (una por local) |

---

## 17. Legal, marca y decisiones pendientes

- **Nombre y marca:** comprobar el nombre definitivo en la OEPM, la EUIPO y Google Play antes de fijar el identificador, que no se puede cambiar una vez publicada la aplicación.
- **Datos ficticios:** todos los clientes, arrendadores y empresas son inventados.
- **Aviso educativo:** el juego simplifica la normativa (retención media, cotización media, amortización por grupos) y no constituye asesoramiento.
- **Fuente:** Pixelify Sans, con licencia SIL Open Font License 1.1 (copia en `docs/licencias`).
- **Música y efectos:** generados por código para el proyecto, sin licencias de terceros.
- **Pendiente de decidir:** nombre definitivo, precio del Premium y si el jugador elige su avatar y el nombre de la gestoría.

---

## Referencias

*Normativa*

- España. (1885). *Real Decreto de 22 de agosto de 1885 por el que se publica el Código de Comercio*. https://www.boe.es/buscar/act.php?id=BOE-A-1885-6627
- España. (1991). *Ley 19/1991, de 6 de junio, del Impuesto sobre el Patrimonio*. https://www.boe.es/buscar/act.php?id=BOE-A-1991-14392
- España. (1992). *Ley 37/1992, de 28 de diciembre, del Impuesto sobre el Valor Añadido*. https://www.boe.es/buscar/act.php?id=BOE-A-1992-28740
- España. (1994). *Ley 29/1994, de 24 de noviembre, de Arrendamientos Urbanos*. https://www.boe.es/buscar/act.php?id=BOE-A-1994-26003
- España. (2006). *Ley 35/2006, de 28 de noviembre, del Impuesto sobre la Renta de las Personas Físicas*. https://www.boe.es/buscar/act.php?id=BOE-A-2006-20764
- España. (2007). *Real Decreto 1515/2007, de 16 de noviembre, por el que se aprueba el Plan General de Contabilidad de Pequeñas y Medianas Empresas*. https://www.boe.es/buscar/act.php?id=BOE-A-2007-19966
- España. (2010). *Real Decreto Legislativo 1/2010, de 2 de julio, por el que se aprueba el texto refundido de la Ley de Sociedades de Capital*. https://www.boe.es/buscar/act.php?id=BOE-A-2010-10544
- España. (2013). *Ley 14/2013, de 27 de septiembre, de apoyo a los emprendedores y su internacionalización*. https://www.boe.es/buscar/act.php?id=BOE-A-2013-10074
- España. (2014). *Ley 27/2014, de 27 de noviembre, del Impuesto sobre Sociedades*. https://www.boe.es/buscar/act.php?id=BOE-A-2014-12328
- España. (2015). *Real Decreto Legislativo 2/2015, de 23 de octubre, por el que se aprueba el texto refundido de la Ley del Estatuto de los Trabajadores*. https://www.boe.es/buscar/act.php?id=BOE-A-2015-11430
- España. (2022). *Ley 18/2022, de 28 de septiembre, de creación y crecimiento de empresas*. https://www.boe.es/buscar/act.php?id=BOE-A-2022-15818
- España. (2023). *Real Decreto-ley 5/2023, de 28 de junio* (modificaciones estructurales de sociedades mercantiles, libro primero). https://www.boe.es/buscar/act.php?id=BOE-A-2023-15135
- España. (2024). *Ley 7/2024, de 20 de diciembre, por la que se establecen un Impuesto Complementario […] y se modifican otras normas tributarias*. https://www.boe.es/buscar/act.php?id=BOE-A-2024-26694

*Herramientas y recursos*

- Google Fonts. (s. f.). *Pixelify Sans*. https://fonts.google.com/specimen/Pixelify+Sans
- ReactiveCircus. (s. f.). *Android Emulator Runner* (GitHub Action). https://github.com/ReactiveCircus/android-emulator-runner

---

[^oepm]: **OEPM (Oficina Española de Patentes y Marcas):** organismo donde se registran y consultan las marcas en España. La EUIPO es su equivalente para toda la Unión Europea.
