# Balance Tycoon — Documento de diseño (versión 0.1, 10 de noviembre de 2026)

> **Título provisional.** El nombre definitivo, el identificador de la aplicación en Google Play y la marca quedan pendientes de decidir y de comprobar en la OEPM[^oepm] y en la propia tienda (ver apartado 15).

**Autor:** Raúl Marian Alecu · **Proyecto:** juego para Android, portafolio y trabajo final del Ciclo Formativo de Grado Superior de Administración y Finanzas (curso 2026-27).

---

## 1. Ficha del proyecto

| Campo | Valor |
|---|---|
| Género | Idle / tycoon de gestión empresarial con contabilidad real |
| Plataforma | Android (teléfonos y tabletas), API 26 (Android 8.0) o superior |
| Tecnología | Kotlin nativo, Jetpack Compose, motor contable en Kotlin puro |
| Idiomas | Español e inglés desde la primera versión (como HeadBallerz) |
| Público | Jugadores de idle y tycoon (casual) y estudiantes de FP, bachillerato y grados de empresa |
| Clasificación prevista | PEGI 3 (sin violencia ni compras aleatorias) |
| Modelo de negocio | Gratuito con anuncios recompensados opcionales y compra única «Premium» |
| Normativa de referencia | PGC PYMES (RD 1515/2007), Ley 37/1992 del IVA, Ley 27/2014 del IS, Código de Comercio y LSC |

---

## 2. Concepto y propuesta de valor

Los juegos idle de empresas, como *AdVenture Capitalist* o *Idle Miner Tycoon*, han demostrado que el público disfruta viendo crecer un negocio mientras está fuera, pero en todos ellos el dinero es un único número que sube, sin impuestos, sin cobros aplazados y sin que exista diferencia alguna entre beneficio y liquidez. Esto, sumado a que la contabilidad se percibe como una materia árida y difícil de practicar fuera del aula, prepara el terreno para un juego que no simule la contabilidad sino que la lleve por debajo de verdad, de forma que cada venta, cada nómina y cada inversión genere su asiento en un libro diario real.

- **Problema identificado:** los idle tycoon ignoran la realidad económica de una empresa y las herramientas para practicar contabilidad son poco atractivas.
- **Propuesta:** un idle en el que el jugador funda una sociedad limitada en un pueblo manchego y la hace crecer mientras el juego lleva, de forma visible y fiel a la normativa española, el libro diario, el mayor, el balance y la cuenta de pérdidas y ganancias.
- **Diferenciación:**
  - Beneficio frente a tesorería: se puede ganar dinero y quedarse sin liquidez por cobrar a 60 días.
  - El IVA no es del jugador: se cobra, se guarda y se liquida cada trimestre.
  - Las ampliaciones son inversiones que se amortizan, no gastos.
  - El personal es un coste mensual con Seguridad Social y retenciones, no una compra única.
- **Doble público:** se juega como cualquier idle, pero quien quiera aprender puede abrir los libros y ver exactamente qué ha pasado y por qué.

---

## 3. Bucle de juego

El bucle combina una parte activa, pensada para los primeros minutos de cada sesión, con una parte pasiva que permite que la empresa siga funcionando cuando el jugador cierra la aplicación.

- **Bucle activo (segundos):** pulsar «Vender» en cada línea de negocio que aún no tiene encargado, clasificar los documentos de la bandeja y pagar a tiempo los impuestos para ahorrarse la comisión de la gestoría.
- **Bucle de crecimiento (minutos):** ampliar líneas de negocio (inversión en inmovilizado), contratar encargados para automatizarlas y desbloquear nuevas actividades.
- **Bucle contable (horas):** cierres mensuales (nóminas y amortizaciones), trimestrales (IVA y retenciones) y anuales (Impuesto sobre Sociedades y reparto del resultado).
- **Metajuego (días):** vender la empresa para fundar otra con ventaja (prestigio), previsto para versiones posteriores al prototipo.

---

## 4. Líneas de negocio

Las actividades se inspiran en el tejido empresarial de Tomelloso y de La Mancha, lo cual da identidad al juego y permite enseñar los tres tipos de IVA y los distintos plazos de cobro.

| Línea | IVA ventas | Cuenta de ventas | Compras (cuenta e IVA) | Cobro | Pago a proveedores | Inmovilizado |
|---|---|---|---|---|---|---|
| 🥖 Panadería | 4% (pan común) | 701 | 601 Materias primas, 4% (harina) | Contado | 30 días | 213 Maquinaria (horno) |
| ☕ Cafetería | 10% (hostelería) | 700 | 600 Mercaderías, 10% | Contado | Contado | 216 Mobiliario |
| 🔧 Taller mecánico | 21% | 705 | 602 Otros aprovisionamientos, 21% | 70% contado, 30% a 30 días | 30 días | 213 Maquinaria (elevadores) |
| 🧀 Quesería artesanal | 4% (quesos) | 701 | 601 Materias primas, 4% (leche) | 40% contado, 60% a 30 días | 30 días | 212 Instalaciones técnicas (cámaras) |
| 🍷 Bodega | 21% | 701 | 602 Otros aprovisionamientos, 21% (envases) | 100% a 60 días | 60 días | 213 Maquinaria (prensa y embotelladora) |
| 🚚 Distribuidora (futura) | 21% | 705 | — | 60 días | 60 días | 218 Elementos de transporte |
| 💼 Asesoría (futura) | 21% | 705 | — | 30 días | — | 217 Equipos informáticos |

Los plazos de cobro de la bodega y de la quesería no son arbitrarios, ya que la Ley 3/2004 de lucha contra la morosidad fija un máximo de 60 días entre empresas y la Ley 12/2013 de la cadena alimentaria reduce ese plazo a 30 días para los productos frescos y perecederos[^plazos].

**Economía de cada línea.** Cada línea tiene un coste de apertura, un coste por nivel que crece de forma exponencial, una duración de ciclo y un importe de venta por ciclo y nivel. Al alcanzar los niveles 10, 25, 50, 100 y 200 el ciclo se acelera al doble (economías de escala). Los valores se han ajustado con las simulaciones del motor (ver «Mediciones» en el documento de estado), buscando que el primer ejercicio cierre con una cifra de negocios de entre 1 y 3 M€, lo cual mantiene unas magnitudes creíbles para una pyme manchega:

| Línea | Apertura | Coste del nivel 2 | Crecimiento | Ciclo | Venta por ciclo y nivel | Coste de ventas | Salario encargado |
|---|---|---|---|---|---|---|---|
| Panadería | En propiedad (horno de 600 €) | 60 € | ×1,12 | 1,5 s | 0,60 € | 35% | 1.350 €/mes |
| Cafetería | 1.500 € | 1.500 € | ×1,13 | 4 s | 6 € | 30% | 1.400 €/mes |
| Taller mecánico | 15.000 € | 15.000 € | ×1,14 | 10 s | 70 € | 25% | 1.700 €/mes |
| Quesería | 90.000 € | 90.000 € | ×1,15 | 24 s | 550 € | 40% | 2.000 €/mes |
| Bodega | 1.200.000 € | 1.200.000 € | ×1,16 | 60 s | 3.500 € | 35% | 2.400 €/mes |

Con un jugador automático que juega de forma razonable, la cafetería se abre hacia el minuto 13, el taller hacia el 29, la quesería hacia el 107 y la bodega tras la primera noche fuera de la aplicación. Se trata de una medición interna que deberá contrastarse con jugadores reales, ya que una persona puede priorizar de otra forma.

---

## 5. Calendario y ritmo

El juego lleva un calendario propio que avanza aunque la aplicación esté cerrada. Se ha fijado **un día contable cada 30 segundos reales**, de forma que un mes dura 15 minutos, un trimestre 45 minutos y un ejercicio completo unas 3 horas. Con este ritmo, en una sesión normal el jugador vive al menos un cierre mensual y, con frecuencia, una liquidación de IVA, mientras que el cierre del ejercicio se convierte en un acontecimiento de cada día de juego.

- **Primer ejercicio:** comienza el 1 de enero del año real en que se crea la partida, de modo que se aplican los tipos fiscales de ese año y el calendario transitorio del Impuesto sobre Sociedades se nota al avanzar.
- **Progreso fuera de la aplicación:** se calcula al volver, con un máximo de 8 horas (unos 960 días contables). Solo producen las líneas con encargado y la gestoría presenta los impuestos que venzan.

---

## 6. Motor contable

El motor está escrito en Kotlin puro, sin dependencias de Android, lo cual permite probarlo con tests automáticos y garantizar que la contabilidad cuadra antes de llevarla a las pantallas. Todo importe se guarda en céntimos con números enteros y los porcentajes se redondean al céntimo con redondeo comercial, ya que los decimales en coma flotante producen descuadres.

### 6.1 Plan de cuentas

Se usa un subconjunto del PGC PYMES[^pgc] con las cuentas que el juego necesita. Las principales son:

- **Patrimonio neto:** 100 Capital social, 112 Reserva legal, 113 Reservas voluntarias, 121 Resultados negativos de ejercicios anteriores, 129 Resultado del ejercicio.
- **Inmovilizado:** 212, 213 y 216, con sus amortizaciones acumuladas (2812, 2813 y 2816).
- **Acreedores y deudores:** 400 Proveedores, 410 Acreedores por prestaciones de servicios, 430 Clientes, 465 Remuneraciones pendientes de pago, 476 Organismos de la Seguridad Social acreedores.
- **Hacienda Pública:** 472 IVA soportado, 477 IVA repercutido, 4700 HP deudora por IVA, 4750 HP acreedora por IVA, 4751 HP acreedora por retenciones practicadas, 4752 HP acreedora por IS.
- **Financiación y tesorería:** 5201 Deudas a corto plazo por crédito dispuesto, 526 Dividendo activo a pagar, 572 Bancos.
- **Gastos:** 600-602, 621-629, 631, 640, 642, 662, 678, 681 y 6300.
- **Ingresos:** 700, 701 y 705.

### 6.2 Asientos que genera el juego

| Hecho económico | Asiento |
|---|---|
| Constitución de la sociedad (3.000 €) | (572) Bancos a (100) Capital social |
| Venta al contado | (572) a (70x) Ventas y (477) IVA repercutido |
| Venta a crédito | (430) Clientes a (70x) y (477); al vencimiento, (572) a (430) |
| Compra de aprovisionamientos | (60x) y (472) a (400) Proveedores; al vencimiento, (400) a (572) |
| Ampliación de una línea | (21x) Inmovilizado y (472) a (572) |
| Amortización mensual | (681) a (281x) |
| Factura de un servicio (bandeja) | (62x) y (472) a (4751) si hay retención y (410); pago domiciliado (410) a (572) |
| Nómina mensual | (640) y (642) a (476), (4751) y (465); pago (465) a (572) |
| Liquidación trimestral del IVA | (477) a (472) y (4750), o (4700) si sale a compensar |
| Impuesto sobre Sociedades | (6300) a (4752) |
| Regularización | Grupos 6 y 7 contra (129) |
| Reparto del resultado | (129) a (112), (113) y (526) |

### 6.3 Asientos resumen

Para que el libro diario no crezca sin límite, las ventas y compras de cada línea se anotan en un único asiento por día. Esta práctica no es una licencia del juego, sino que el artículo 28.2 del Código de Comercio permite la anotación conjunta de los totales de las operaciones por periodos no superiores al mes, siempre que su detalle conste en otros registros. El juego conserva en pantalla los últimos 400 asientos y el saldo completo de todas las cuentas del ejercicio.

### 6.4 IVA

- **Tipos:** 4%, 10% y 21% según la línea (artículos 90 y 91 de la Ley 37/1992). Los seguros y las comisiones bancarias están exentos (artículo 20).
- **Liquidación (modelo 303):** el último día de cada trimestre se compensa el IVA repercutido con el soportado. Si sale a ingresar, se paga hasta el día 20 del mes siguiente (30 de enero en el cuarto trimestre); si sale a compensar, se arrastra a los trimestres siguientes en la cuenta 4700.
- **Aprendizaje clave:** la tesorería incluye un IVA que no es de la empresa, y la pantalla de Hacienda lo muestra aparte.

### 6.5 Nóminas y Seguridad Social

Cada encargado y el auxiliar contable cobran un salario mensual. Los porcentajes son una simplificación de los tipos de cotización de 2026, que deberá revisarse con la orden de cotización de cada año:

| Concepto | Empresa | Trabajador |
|---|---|---|
| Contingencias comunes | 23,60% | 4,70% |
| Desempleo (contrato indefinido) | 5,50% | 1,55% |
| FOGASA | 0,20% | — |
| Formación profesional | 0,60% | 0,10% |
| MEI[^mei] (2026) | 0,75% | 0,15% |
| AT y EP (media estimada según actividad) | 1,50% | — |
| **Total** | **32,15%** | **6,50%** |

La retención de IRPF se fija en un 12% medio, lo cual es una estimación, ya que en la realidad depende del salario y de la situación personal de cada trabajador. El neto se paga el último día del mes y los seguros sociales se cargan en cuenta el último día del mes siguiente.

### 6.6 Inversiones y amortización

Cada nivel comprado es una inversión en inmovilizado material que se amortiza linealmente en 10 años, dentro del coeficiente máximo de las tablas fiscales (12% para maquinaria y 10% para mobiliario e instalaciones)[^amort]. Por sencillez, el prototipo amortiza cada línea como un grupo homogéneo de bienes, hasta que la amortización acumulada iguala al coste.

### 6.7 Tesorería y póliza de crédito

Para que el juego nunca se bloquee, la empresa dispone de una póliza de crédito con un interés del 7% anual. Si un pago obligatorio (nómina, impuesto, proveedor) deja el banco en negativo, se dispone de la póliza (572 a 5201) y cada día se devuelve con el saldo disponible. Las ampliaciones voluntarias, en cambio, solo se pueden pagar con saldo propio, lo cual obliga a vigilar los cobros pendientes.

### 6.8 Cierre del ejercicio, IS y reparto del resultado

El 31 de diciembre de cada año de juego se ejecuta el cierre completo:

1. **Impuesto sobre Sociedades:** base imponible igual al resultado contable antes de impuestos, con un único ajuste extracontable (los recargos de Hacienda no son deducibles, artículo 15 LIS) y compensación de bases negativas (artículo 26 LIS).
2. **Tipo aplicable** según el artículo 29 y la disposición transitoria 44.ª de la LIS, modificados por la Ley 7/2024:

| Tipo de entidad | 2026 | 2027 | 2028 | 2029 en adelante |
|---|---|---|---|---|
| Nueva creación (primer periodo con base positiva y el siguiente) | 15% | 15% | 15% | 15% |
| Microempresa (INCN[^incn] < 1 M€): primeros 50.000 € | 19% | 17% | 17% | 17% |
| Microempresa: resto de la base | 21% | 20% | 20% | 20% |
| Reducida dimensión (INCN < 10 M€) | 23% | 22% | 21% | 20% |
| General | 25% | 25% | 25% | 25% |

3. **Regularización** de los grupos 6 y 7 contra la cuenta 129, asiento de cierre y, el 1 de enero, asiento de apertura.
4. **Reparto del resultado:** el jugador decide entre el 1 de enero y el 30 de junio, igual que la junta general ordinaria (artículo 164 LSC). Primero se dota la reserva legal con el 10% del beneficio hasta llegar al 20% del capital (artículo 274 LSC), y solo se pueden repartir dividendos si el patrimonio neto no queda por debajo del capital (artículo 273 LSC). Si no decide, el beneficio pasa a reservas voluntarias.
5. **Dividendos:** se pagan 30 días después con una retención del 19% y el neto pasa al **patrimonio personal del socio**, que será la puntuación global del jugador y la base del prestigio.

---

## 7. Bandeja de documentos (minijuego educativo)

Cada dos días contables llega a la bandeja un documento (una factura o un recibo) que no forma parte del flujo automático de ventas y compras. El jugador debe elegir la cuenta de gasto correcta entre cuatro opciones; si acierta, la racha de rigor contable sube y cada acierto consecutivo añade un 2% a las ventas, hasta un máximo del 20%. Si falla, se muestra la explicación, el documento se contabiliza correctamente igualmente y la racha vuelve a cero. Los importes se escalan con las ventas del último mes, de modo que los gastos generales rondan el 7-8% de la cifra de negocios durante toda la partida.

| Documento | Cuenta correcta | IVA | Retención | Distractores (error habitual) |
|---|---|---|---|---|
| Factura de la notaría (constitución) | 623 | 21% | 15% | 629, 631, 100 |
| Factura de electricidad | 628 | 21% | — | 622, 621, 602 |
| Alquiler del local (propietario particular) | 621 | 21% | 19% | 211, 622, 623 |
| Prima del seguro multirriesgo | 625 | Exento | — | 623, 626, 629 |
| Comisión bancaria de mantenimiento | 626 | Exento | — | 662, 669, 625 |
| Anuncio en la radio local | 627 | 21% | — | 629, 623, 622 |
| Reparación de una avería | 622 | 21% | — | 213, 628, 629 |
| Honorarios de un abogado | 623 | 21% | 15% | 640, 629, 627 |
| Transporte de mercancías | 624 | 21% | — | 600, 628, 629 |
| Material de oficina | 629 | 21% | — | 602, 217, 628 |
| Recibo del IBI | 631 | — | — | 6300, 621, 678 |

Si un documento pasa 10 días sin clasificarse, lo contabiliza la gestoría externa y cobra una pequeña comisión. Contratar un **auxiliar contable** automatiza la bandeja a cambio de su nómina, aunque solo conserva la racha y no la aumenta.

---

## 8. Personal y automatización

- **Encargado de línea:** produce de forma continua sin que el jugador pulse «Vender». A diferencia de otros idle, no es una compra única sino un salario mensual con su coste de Seguridad Social, lo cual obliga a valorar si la línea genera margen suficiente.
- **Auxiliar contable:** clasifica la bandeja cada día.
- **Gestoría externa:** presenta y paga los impuestos el último día del plazo a cambio de 45 € más IVA por modelo. Si el jugador paga antes desde la pantalla de Hacienda, se ahorra la comisión. Se puede desactivar en los ajustes, y en ese caso los pagos fuera de plazo llevan el recargo del artículo 27 de la LGT (1% más un 1% por cada mes completo de retraso), que se contabiliza en la cuenta 678.

---

## 9. Hacienda y plazos

| Obligación | Periodicidad | Vencimiento en el juego | Cuenta |
|---|---|---|---|
| Modelo 303 (IVA) | Trimestral | Día 20 del mes siguiente (30 de enero en el 4T) | 4750 |
| Modelos 111, 115 y 123 (retenciones) | Trimestral | Igual que el 303 | 4751 |
| Seguros sociales | Mensual | Último día del mes siguiente (domiciliado) | 476 |
| Modelo 200 (Impuesto sobre Sociedades) | Anual | 25 de julio del año siguiente | 4752 |

Los pagos fraccionados del modelo 202 y la declaración informativa de operaciones con terceros (modelo 347) quedan fuera del prototipo y se valorarán para la versión 1.0.

---

## 10. Progresión y metajuego

- **Hitos de nivel:** ciclo al doble en los niveles 10, 25, 50, 100 y 200 de cada línea.
- **Cambio de tamaño fiscal:** al superar 1 y 10 millones de cifra de negocios, la empresa deja de ser microempresa y después de reducida dimensión, lo cual sube el tipo del IS y se convierte en un hito visible.
- **Prestigio (versión 1.0):** «Vender la empresa». La valoración se calculará a partir del patrimonio neto y de un múltiplo del resultado de explotación; la venta suma al patrimonio personal y concede puntos de reputación que multiplican las ventas de la siguiente sociedad y desbloquean sectores nuevos.
- **Logros:** primer cierre cuadrado, primer trimestre sin comisiones de gestoría, diez aciertos seguidos en la bandeja, etc.

---

## 11. Monetización

Se busca un modelo que no penalice a quien no paga ni entre en conflicto con el uso educativo:

- **Anuncios recompensados (opcionales):** turbo de ventas ×2 durante 2 horas reales, duplicar las ganancias obtenidas fuera de la aplicación y una presentación de impuestos sin comisión.
- **Compra única «Premium» (estimación: 3,99 €):** elimina los anuncios, deja el turbo ×2 permanente y desbloquea temas visuales.
- **Sin anuncios intersticiales** que interrumpan, sin cajas de botín y sin moneda premium.
- **Línea futura:** licencia para centros educativos con modo profesor y ejercicios guiados.

A nivel técnico se usará Google AdMob con el SDK de consentimiento UMP, obligatorio en la Unión Europea por el RGPD, y Google Play Billing. Igual que en HeadBallerz, el código ya tiene un punto de enganche (`AdsProvider`) que en el prototipo concede la recompensa sin mostrar ningún anuncio.

---

## 12. Arquitectura técnica

- **Módulo `core` (Kotlin puro):** plan de cuentas, libro diario y mayor, informes, reglas fiscales, motor del juego, guardado y formato de cifras. Se prueba con JUnit sin emulador.
- **Módulo `app` (Android):** Jetpack Compose con Material 3, un `ViewModel` que hace avanzar el motor unas diez veces por segundo y lo guarda cada 30 segundos y al salir.
- **Guardado:** archivo JSON propio en el almacenamiento interno de la aplicación, sin librerías externas.
- **Versiones:** Android Gradle Plugin 9.5, Gradle 9.6, Kotlin 2.4, Compose BOM 2026.08.00, compileSdk 37 y targetSdk 36, que es el mínimo que exige Google Play desde el 31 de agosto de 2026.

---

## 13. Alcance del prototipo (versión 0.1) y hoja de ruta

| Bloque | Prototipo 0.1 | Versión 1.0 (publicación) |
|---|---|---|
| Líneas de negocio | 5 | 7 u 8, con prestigio |
| Contabilidad | Diario, mayor, PyG, balance, cierre y apertura | Libro de facturas, sumas y saldos, cuentas anuales exportables en PDF |
| Fiscalidad | IVA, retenciones, seguros sociales, IS 2026-2029 | Pagos fraccionados (202), modelo 347 |
| Minijuego | Elegir la cuenta de gasto | Asiento completo (debe y haber) y niveles de dificultad |
| Personal | Encargados y auxiliar | Plantilla creciente con la empresa |
| Monetización | Punto de enganche simulado | AdMob, UMP y Play Billing |
| Estética | Material 3 con paleta propia y emojis | Ilustraciones en Canva, tipografía Unbounded, sonido y música propios |

---

## 14. Legal, marca y riesgos

- **Nombre y marca:** comprobar «Balance Tycoon» o el nombre definitivo en la OEPM, en la EUIPO y en Google Play antes de fijar el identificador de la aplicación, que no se puede cambiar una vez publicada.
- **Datos ficticios:** todas las empresas, proveedores y personas que aparecen en los documentos son inventados.
- **Aviso educativo:** el juego simplifica la normativa (retención media, cotización media, amortización por grupos) y debe indicar que no constituye asesoramiento fiscal ni contable.
- **Privacidad:** formulario de seguridad de los datos de Google Play y consentimiento de anuncios. El prototipo no recoge ningún dato.
- **Riesgo de equilibrio:** la economía idle crece de forma exponencial y los importes se guardan en céntimos con enteros de 64 bits, lo cual limita una partida a unos 92.000 billones de euros. Se trata de un margen amplio, pero el prestigio deberá reiniciar antes de alcanzarlo.

---

## 15. Decisiones pendientes del usuario

- Nombre definitivo, identificador de la aplicación y marca (¿ALECU como estudio, igual que en las vallas de HeadBallerz?).
- Precio del paquete Premium y qué recompensas ofrecen los anuncios.
- Estilo visual: ilustraciones en Canva, como en HeadBallerz, o estilo plano con iconos.
- Si el juego debe incluir un «modo aprendizaje» con explicaciones de cada cuenta desde el principio o como opción.
- Música y sonido: canciones propias, como en HeadBallerz, o librería con licencia.

---

## Referencias

*Normativa*

- España. (1885). *Real Decreto de 22 de agosto de 1885 por el que se publica el Código de Comercio*. https://www.boe.es/buscar/act.php?id=BOE-A-1885-6627
- España. (1992). *Ley 37/1992, de 28 de diciembre, del Impuesto sobre el Valor Añadido*. https://www.boe.es/buscar/act.php?id=BOE-A-1992-28740
- España. (2003). *Ley 58/2003, de 17 de diciembre, General Tributaria*. https://www.boe.es/buscar/act.php?id=BOE-A-2003-23186
- España. (2004). *Ley 3/2004, de 29 de diciembre, por la que se establecen medidas de lucha contra la morosidad en las operaciones comerciales*. https://www.boe.es/buscar/act.php?id=BOE-A-2004-21998
- España. (2007). *Real Decreto 1515/2007, de 16 de noviembre, por el que se aprueba el Plan General de Contabilidad de Pequeñas y Medianas Empresas y los criterios contables específicos para microempresas*. https://www.boe.es/buscar/act.php?id=BOE-A-2007-19966
- España. (2010). *Real Decreto Legislativo 1/2010, de 2 de julio, por el que se aprueba el texto refundido de la Ley de Sociedades de Capital*. https://www.boe.es/buscar/act.php?id=BOE-A-2010-10544
- España. (2013). *Ley 12/2013, de 2 de agosto, de medidas para mejorar el funcionamiento de la cadena alimentaria*. https://www.boe.es/buscar/act.php?id=BOE-A-2013-8554
- España. (2014). *Ley 27/2014, de 27 de noviembre, del Impuesto sobre Sociedades*. https://www.boe.es/buscar/act.php?id=BOE-A-2014-12328
- España. (2024). *Ley 7/2024, de 20 de diciembre, por la que se establecen un Impuesto Complementario […] y se modifican otras normas tributarias*. https://www.boe.es/buscar/act.php?id=BOE-A-2024-26694

*Referencias web*

- Android Developers. (2026, 5 de noviembre). *Android Gradle plugin 9.5.0 release notes*. https://developer.android.com/build/releases/agp-9-5-0-release-notes
- Android Developers Blog. (2026, agosto). *Jetpack Compose August 2026 release*. https://android-developers.googleblog.com/2026/08/jetpack-compose-august-2026-release.html
- Interforo Abogados. (2026). *Tipos reducidos del impuesto de sociedades para pymes en 2026*. https://interforoabogados.com/contenido-legal/tipos-reducidos-impuesto-sociedades-pymes-2026/

---

[^oepm]: **OEPM (Oficina Española de Patentes y Marcas):** organismo donde se registran y consultan las marcas en España. La EUIPO es su equivalente para toda la Unión Europea.
[^plazos]: Los plazos de la tabla son los máximos legales y el juego los usa como referencia de realismo. En la práctica cada empresa pacta los suyos dentro de ese límite.
[^pgc]: **PGC PYMES:** versión simplificada del Plan General de Contabilidad que pueden aplicar las empresas que no superan dos de estos límites durante dos ejercicios: 4 M€ de activo, 8 M€ de cifra de negocios y 50 trabajadores.
[^mei]: **MEI (Mecanismo de Equidad Intergeneracional):** cotización finalista destinada al Fondo de Reserva de la Seguridad Social, que sube cada año hasta 2029 (0,90% en 2026).
[^amort]: Tabla de coeficientes de amortización lineal del artículo 12.1.a) de la LIS. El coeficiente máximo limita el gasto deducible, y la vida útil contable puede ser mayor.
[^incn]: **INCN (importe neto de la cifra de negocios):** ventas y prestaciones de servicios menos descuentos y devoluciones. Para el IS se toma la del periodo impositivo anterior.
