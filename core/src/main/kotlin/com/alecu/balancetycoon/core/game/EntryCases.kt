package com.alecu.balancetycoon.core.game

import com.alecu.balancetycoon.core.format.Fmt
import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.i18n.Lang
import com.alecu.balancetycoon.core.money.Money

/**
 * Línea de una consulta de asiento: lado, cuenta correcta e importe en puntos básicos de la
 * base (10000 = la base). La línea [REMAINDER] es la que cuadra el asiento.
 */
class EntryLineSpec(val debit: Boolean, val account: String, val bp: Int) {
    companion object {
        const val REMAINDER = -1
    }
}

/** Línea de un asiento ya calculado. */
data class EntryLine(val debit: Boolean, val account: String, val amount: Long)

/**
 * Consulta de asiento completo: el cliente describe una operación y el jugador asigna la cuenta
 * del PGC a cada línea del debe y del haber. Los importes se generan al azar dentro de un margen
 * acorde con el tamaño del cliente. Es la contabilidad del cliente, no la de la gestoría:
 * resolverla solo genera los honorarios de la consulta.
 */
class EntryCaseTemplate(
    override val id: String,
    override val dept: String,
    override val minTier: Int,
    override val emoji: String,
    override val client: L,
    /** Enunciado; {L0}, {L1}… se sustituyen por el importe de cada línea. */
    val statement: L,
    val lines: List<EntryLineSpec>,
    val distractors: List<String>,
    val explanation: L,
    val minEuros: Long,
    val maxEuros: Long,
    val stepEuros: Long,
) : ClientCase {
    init {
        require(lines.count { it.bp == EntryLineSpec.REMAINDER } == 1) { "El asiento $id necesita una única línea de cuadre" }
        require(lines.map { it.account }.toSet().size == lines.size) { "El asiento $id repite una cuenta" }
        require(distractors.none { d -> lines.any { it.account == d } }) { "Un distractor de $id es una cuenta correcta" }
    }

    /** Cuentas entre las que elige el jugador: las correctas y las de distracción. */
    val candidates: List<String> = (lines.map { it.account } + distractors).distinct()

    fun build(base: Long): List<EntryLine> {
        val fixed = lines.map { if (it.bp == EntryLineSpec.REMAINDER) 0L else Money.pct(base, it.bp) }
        var debits = 0L
        var credits = 0L
        lines.forEachIndexed { i, l ->
            if (l.bp != EntryLineSpec.REMAINDER) {
                if (l.debit) debits += fixed[i] else credits += fixed[i]
            }
        }
        return lines.mapIndexed { i, l ->
            val amount = when {
                l.bp != EntryLineSpec.REMAINDER -> fixed[i]
                l.debit -> credits - debits
                else -> debits - credits
            }
            EntryLine(l.debit, l.account, amount)
        }
    }

    fun statementText(base: Long, lang: Lang): String {
        var text = statement.get(lang)
        build(base).forEachIndexed { i, l -> text = text.replace("{L$i}", Fmt.money(l.amount, lang, compact = false)) }
        return text
    }

    /** Base aleatoria redondeada al paso de la plantilla, a partir de un número en [0, 1). */
    fun pickBase(random: Double): Long {
        val steps = (maxEuros - minEuros) / stepEuros
        val k = (random * (steps + 1)).toLong().coerceIn(0, steps)
        return Money.euros(minEuros + k * stepEuros)
    }
}

object EntryCases {
    private const val R = EntryLineSpec.REMAINDER
    private fun d(account: String, bp: Int = 10_000) = EntryLineSpec(true, account, bp)
    private fun h(account: String, bp: Int = 10_000) = EntryLineSpec(false, account, bp)

    private fun e(
        id: String, dept: String, tier: Int, emoji: String, client: String, clientEn: String,
        st: String, stEn: String, lines: List<EntryLineSpec>, distractors: List<String>,
        ex: String, exEn: String, min: Long, max: Long, step: Long,
    ) = EntryCaseTemplate(id, dept, tier, emoji, L(client, clientEn), L(st, stEn), lines, distractors, L(ex, exEn), min, max, step)

    val all: List<EntryCaseTemplate> = listOf(
        // ───────────── Fiscal ─────────────
        e(
            "ef_venta_credito", "fiscal", 0, "📦", "Distribuciones La Veguilla (autónomo)", "Distribuciones La Veguilla (self-employed)",
            "He vendido mercaderías a una tienda por {L1} más {L2} de IVA. Me pagará a 30 días.",
            "I sold goods to a shop for {L1} plus {L2} VAT. They will pay me in 30 days.",
            listOf(d("430", R), h("700"), h("477", 2100)), listOf("572", "705", "472", "400"),
            "La venta de mercaderías se abona en la 700 y el IVA repercutido en la 477. Como el cliente pagará más adelante, el total de la factura queda como derecho de cobro en la 430 Clientes.",
            "The sale of goods is credited to 700 and the output VAT to 477. As the customer will pay later, the invoice total is a receivable in 430 Customers.",
            500, 4_000, 50,
        ),
        e(
            "ef_compra_fruta", "fiscal", 0, "🍎", "Frutas Hermanos Lara (autónomos)", "Frutas Hermanos Lara (self-employed)",
            "Hemos comprado fruta al mayorista por {L0} más {L1} de IVA (4 %). La pagaremos en 30 días.",
            "We bought fruit from the wholesaler for {L0} plus {L1} VAT (4%). We will pay in 30 days.",
            listOf(d("600"), d("472", 400), h("400", R)), listOf("700", "477", "410", "572"),
            "Las compras de mercaderías van a la 600 y el IVA soportado a la 472 (la fruta fresca tributa al 4 %). La deuda con el mayorista, que es un proveedor de mercaderías, se abona en la 400.",
            "Purchases of goods go to 600 and input VAT to 472 (fresh fruit is taxed at 4%). The debt to the wholesaler, a supplier of goods, is credited to 400.",
            400, 3_000, 10,
        ),
        e(
            "ef_profesional", "fiscal", 1, "⚖️", "Cafetería Los Arcos, S.L.", "Cafetería Los Arcos, S.L.",
            "Un abogado nos factura {L0} más {L1} de IVA, con una retención del 15 % de IRPF ({L2}). Le pagamos el resto por banco.",
            "A lawyer invoices us {L0} plus {L1} VAT, with 15% income tax withholding ({L2}). We pay the rest by bank transfer.",
            listOf(d("623"), d("472", 2100), h("4751", 1500), h("572", R)), listOf("629", "473", "477", "410"),
            "Los servicios de un profesional independiente van a la 623. La retención que practicamos no es un gasto ni un descuento: es una deuda con Hacienda (4751) que ingresaremos con el modelo 111.",
            "Services from an independent professional go to 623. The withholding we apply is neither an expense nor a discount: it is a debt to the Tax Agency (4751) paid with form 111.",
            200, 1_500, 10,
        ),
        e(
            "ef_liquidacion_iva", "fiscal", 1, "🧮", "Panadería La Espiga, S.L.", "Panadería La Espiga, S.L.",
            "Cerramos el trimestre con {L0} de IVA repercutido y {L1} de IVA soportado. Registra la liquidación (modelo 303).",
            "We close the quarter with {L0} of output VAT and {L1} of input VAT. Record the VAT return (form 303).",
            listOf(d("477"), h("472", 6_000), h("4750", R)), listOf("4700", "473", "572", "6300"),
            "Se cancelan las dos cuentas de IVA: se carga la 477 por lo repercutido y se abona la 472 por lo soportado. Como la diferencia sale a ingresar, queda como deuda en la 4750 hasta que se paga a Hacienda.",
            "Both VAT accounts are cleared: 477 is debited for output VAT and 472 credited for input VAT. As the difference is payable, it stays as a debt in 4750 until it is paid.",
            1_000, 8_000, 10,
        ),
        e(
            "ef_venta_caja", "fiscal", 1, "🛒", "Supermercados La Despensa, S.L.", "Supermercados La Despensa, S.L.",
            "Hoy la tienda ha vendido alimentos por {L1} más {L2} de IVA (10 %), cobrados en efectivo.",
            "Today the shop sold food for {L1} plus {L2} VAT (10%), paid in cash.",
            listOf(d("570", R), h("700"), h("477", 1_000)), listOf("572", "705", "472", "430"),
            "El cobro en efectivo entra en la caja (570), no en el banco. La venta de mercaderías va a la 700 y el IVA repercutido, a la 477.",
            "Cash takings go to 570 Cash on hand, not to the bank. The sale of goods goes to 700 and the output VAT to 477.",
            300, 2_500, 10,
        ),
        e(
            "ef_pago_202", "fiscal", 3, "🏗️", "Construcciones Alto Guadiana, S.A.", "Construcciones Alto Guadiana, S.A.",
            "Pagamos por banco el pago fraccionado del Impuesto sobre Sociedades de octubre (modelo 202): {L0}.",
            "We pay by bank the October corporate tax instalment (form 202): {L0}.",
            listOf(d("473"), h("572", R)), listOf("4752", "6300", "631", "4751"),
            "Los pagos fraccionados son anticipos del impuesto: se registran como un derecho frente a Hacienda (473) que se descontará al calcular el impuesto del ejercicio.",
            "Instalments are advance payments of the tax: they are recorded as a receivable from the Tax Agency (473) to be deducted when the year's tax is calculated.",
            20_000, 150_000, 100,
        ),

        // ───────────── Laboral ─────────────
        e(
            "el_nomina", "laboral", 0, "💇", "Peluquería Rizos (autónoma con una empleada)", "Peluquería Rizos (self-employed, one employee)",
            "Nómina de la empleada: sueldo bruto de {L0}. La empresa cotiza {L1}; a la trabajadora se le descuenta el 6,50 % de Seguridad Social y {L3} de IRPF. Se pagará el día 5.",
            "Employee payslip: gross salary {L0}. The employer contributes {L1}; the employee has 6.50% social security and {L3} income tax deducted. It will be paid on the 5th.",
            listOf(d("640"), d("642", 3_215), h("476", 3_865), h("4751", 1_200), h("465", R)), listOf("649", "4750", "572", "410"),
            "El sueldo bruto va a la 640 y la cuota de la empresa a la 642. A la 476 se lleva todo lo que se debe a la Seguridad Social (cuota de la empresa y de la trabajadora), a la 4751 la retención de IRPF y a la 465 el líquido que cobrará la empleada.",
            "Gross salary goes to 640 and the employer's contribution to 642. Account 476 holds everything owed to Social Security (employer and employee shares), 4751 the income tax withheld and 465 the net pay owed to the employee.",
            1_200, 2_400, 10,
        ),
        e(
            "el_pago_ss", "laboral", 0, "⚡", "Electricidad Chispa (autónomo)", "Electricidad Chispa (self-employed)",
            "Pagamos por banco los seguros sociales del mes pasado: {L0}.",
            "We pay last month's social security contributions by bank: {L0}.",
            listOf(d("476"), h("572", R)), listOf("642", "640", "4751", "465"),
            "Al contabilizar la nómina, la deuda con la Seguridad Social quedó en la 476. Al pagarla se carga esa cuenta y se abona el banco; el gasto ya se registró en la 642.",
            "When payroll was booked, the debt to Social Security went to 476. Paying it debits that account and credits the bank; the expense was already recorded in 642.",
            300, 1_500, 1,
        ),
        e(
            "el_pago_nomina", "laboral", 1, "🍺", "Bar El Pozo, S.L.", "Bar El Pozo, S.L.",
            "Pagamos por transferencia la nómina del camarero, que quedó pendiente de pago: {L0}.",
            "We pay the waiter's outstanding net salary by transfer: {L0}.",
            listOf(d("465"), h("572", R)), listOf("640", "476", "4751", "570"),
            "El líquido a percibir quedó en la 465 Remuneraciones pendientes de pago. Al pagarlo se cancela esa deuda; el gasto (640) ya se registró con la nómina.",
            "The net pay was left in 465 Salaries payable. Paying it clears that debt; the expense (640) was recorded with the payroll.",
            900, 1_800, 1,
        ),
        e(
            "el_111", "laboral", 2, "🔩", "Talleres Molino Nuevo, S.L.", "Talleres Molino Nuevo, S.L.",
            "Ingresamos en Hacienda las retenciones del trimestre (modelo 111): {L0}.",
            "We pay the quarter's withholdings to the Tax Agency (form 111): {L0}.",
            listOf(d("4751"), h("572", R)), listOf("4750", "473", "476", "642"),
            "Las retenciones practicadas en nóminas y facturas de profesionales se acumulan en la 4751. Al ingresarlas con el modelo 111 se cancela esa deuda.",
            "Withholdings on payslips and professional invoices accumulate in 4751. Paying them with form 111 clears that debt.",
            1_500, 12_000, 1,
        ),

        // ───────────── Contabilidad ─────────────
        e(
            "ec_ordenador", "contable", 0, "🖥️", "Academia Saber Más (autónomo)", "Academia Saber Más (self-employed)",
            "He comprado un ordenador para la academia por {L0} más {L1} de IVA y lo he pagado por transferencia.",
            "I bought a computer for the academy for {L0} plus {L1} VAT and paid by bank transfer.",
            listOf(d("217"), d("472", 2_100), h("572", R)), listOf("629", "602", "216", "477"),
            "Un ordenador que se usará varios años es inmovilizado material: equipos para procesos de información (217). El IVA soportado deducible va a la 472 y el pago por banco se abona en la 572.",
            "A computer used for several years is a fixed asset: IT equipment (217). Deductible input VAT goes to 472 and the bank payment is credited to 572.",
            600, 1_500, 10,
        ),
        e(
            "ec_alquiler", "contable", 1, "🏬", "Floristería Azahar, S.L.", "Floristería Azahar, S.L.",
            "Pagamos por banco el alquiler del local: {L0} más {L1} de IVA. La inmobiliaria nos ha acreditado que no procede retención.",
            "We pay the shop rent by bank: {L0} plus {L1} VAT. The property company has shown that no withholding applies.",
            listOf(d("621"), d("472", 2_100), h("572", R)), listOf("211", "625", "477", "4751"),
            "El alquiler es un gasto de la 621 y el IVA soportado deducible va a la 472. Normalmente se retendría el 19 % (modelo 115), pero hay excepciones, como la de las inmobiliarias que tributan por el epígrafe 861 del IAE.",
            "Rent is an expense in 621 and deductible input VAT goes to 472. Normally 19% would be withheld (form 115), but there are exceptions, such as property companies registered under heading 861 of the business tax.",
            500, 2_500, 10,
        ),
        e(
            "ec_seguro", "contable", 1, "🖨️", "Copistería Rápida, S.L.", "Copistería Rápida, S.L.",
            "Pagamos por banco la prima anual del seguro del local: {L0}. Los seguros están exentos de IVA.",
            "We pay the shop's annual insurance premium by bank: {L0}. Insurance is VAT-exempt.",
            listOf(d("625"), h("572", R)), listOf("472", "621", "629", "626"),
            "Las primas de seguros van a la 625. Como las operaciones de seguro están exentas de IVA (art. 20.Uno.16.º de la Ley del IVA), no hay IVA soportado.",
            "Insurance premiums go to 625. As insurance is VAT-exempt (art. 20.One.16 of the VAT Act), there is no input VAT.",
            200, 1_200, 1,
        ),
        e(
            "ec_alquiler_particular", "contable", 2, "👓", "Óptica Visión Clara, S.L.", "Óptica Visión Clara, S.L.",
            "Pagamos el alquiler del local a su dueña, una particular: {L0} más {L1} de IVA, con una retención del 19 % ({L2}). El resto va por banco.",
            "We pay the shop rent to its owner, a private individual: {L0} plus {L1} VAT, with 19% withholding ({L2}). The rest goes by bank.",
            listOf(d("621"), d("472", 2_100), h("4751", 1_900), h("572", R)), listOf("473", "211", "410", "477"),
            "El gasto de alquiler (621) se registra por el importe íntegro. La retención del 19 % es una deuda con Hacienda (4751) que se ingresa con el modelo 115.",
            "The rent expense (621) is recorded in full. The 19% withholding is a debt to the Tax Agency (4751) paid with form 115.",
            600, 3_000, 10,
        ),
        e(
            "ec_cobro_cliente", "contable", 2, "🧀", "Quesería Campo de Montiel, S.L.", "Quesería Campo de Montiel, S.L.",
            "Un distribuidor nos paga por transferencia una factura que teníamos pendiente de cobro: {L0}.",
            "A distributor pays by transfer an invoice we had outstanding: {L0}.",
            listOf(d("572"), h("430", R)), listOf("400", "700", "570", "477"),
            "La venta y su IVA se registraron al emitir la factura. Ahora solo se cancela el derecho de cobro (430) y entra el dinero en el banco (572).",
            "The sale and its VAT were recorded when the invoice was issued. Now the receivable (430) is cleared and the money reaches the bank (572).",
            2_000, 30_000, 10,
        ),
        e(
            "ec_amortizacion", "contable", 2, "🚐", "Reparto Rápido Mancha, S.L.", "Reparto Rápido Mancha, S.L.",
            "Registra la amortización anual de la furgoneta de reparto: {L0}.",
            "Record the annual depreciation of the delivery van: {L0}.",
            listOf(d("681"), h("2818", R)), listOf("218", "2817", "629", "6300"),
            "La amortización es un gasto (681) y su contrapartida es la amortización acumulada (2818), que resta del valor de la furgoneta en el balance sin modificar la cuenta 218.",
            "Depreciation is an expense (681) and its counterpart is accumulated depreciation (2818), which is deducted from the van's value in the balance sheet without changing account 218.",
            3_000, 9_000, 10,
        ),

        // ───────────── Mercantil ─────────────
        e(
            "em_constitucion", "mercantil", 2, "🧑‍💼", "Emprendedores Nueva Ruta, S.L.", "Emprendedores Nueva Ruta, S.L.",
            "Constituimos la sociedad con un capital de {L0}, que los socios ingresan en la cuenta del banco.",
            "We incorporate the company with share capital of {L0}, which the partners pay into the bank account.",
            listOf(d("572"), h("100", R)), listOf("113", "170", "129", "570"),
            "Las aportaciones de los socios forman el capital social (100). Como el dinero se ingresa en el banco, se carga la 572.",
            "The partners' contributions form the share capital (100). As the money is paid into the bank, 572 is debited.",
            3_000, 60_000, 1_000,
        ),
        e(
            "em_reparto", "mercantil", 3, "🍇", "Bodegas Llano Dorado, S.L.", "Bodegas Llano Dorado, S.L.",
            "La junta acuerda repartir el beneficio de {L0}: {L1} a reserva legal, {L2} a reservas voluntarias y {L3} en dividendos.",
            "The general meeting appropriates the {L0} profit: {L1} to the legal reserve, {L2} to voluntary reserves and {L3} as dividends.",
            listOf(d("129"), h("112", 1_000), h("113", 4_000), h("526", R)), listOf("100", "121", "572", "6300"),
            "El resultado del ejercicio (129) se cancela y se reparte: reserva legal (112), reservas voluntarias (113) y el dividendo, que hasta su pago es una deuda con los socios (526).",
            "The profit for the year (129) is cleared and appropriated: legal reserve (112), voluntary reserves (113) and the dividend, which is a debt to the partners until paid (526).",
            50_000, 400_000, 1_000,
        ),
        e(
            "em_pago_dividendo", "mercantil", 3, "💶", "Distribuciones Mancha Centro, S.L.", "Distribuciones Mancha Centro, S.L.",
            "Pagamos a los socios el dividendo acordado de {L0}, con una retención del 19 % ({L1}).",
            "We pay the partners the agreed dividend of {L0}, with 19% withholding ({L1}).",
            listOf(d("526"), h("4751", 1_900), h("572", R)), listOf("473", "129", "113", "760"),
            "Se cancela la deuda con los socios (526) por el importe bruto. La retención del 19 % es una deuda con Hacienda (4751, modelo 123) y el banco paga el neto.",
            "The debt to the partners (526) is cleared at the gross amount. The 19% withholding is a debt to the Tax Agency (4751, form 123) and the bank pays the net amount.",
            10_000, 200_000, 100,
        ),
        e(
            "em_perdidas", "mercantil", 4, "📉", "Muebles Ribera del Záncara, S.L.", "Muebles Ribera del Záncara, S.L.",
            "Este ejercicio hemos perdido {L0}. La junta acuerda dejar la pérdida pendiente de compensar con beneficios futuros.",
            "We made a loss of {L0} this year. The general meeting decides to carry it forward against future profits.",
            listOf(d("121"), h("129", R)), listOf("113", "112", "678", "100"),
            "La pérdida está en la 129 con saldo deudor. Al aplicarla se lleva a resultados negativos de ejercicios anteriores (121), que se compensarán con beneficios futuros.",
            "The loss sits in 129 with a debit balance. Appropriating it moves it to prior years' losses (121), to be offset against future profits.",
            30_000, 500_000, 1_000,
        ),

        // ───────────── Consultoría ─────────────
        e(
            "ek_prestamo", "consultoria", 3, "🚜", "Agroservicios Llanura, S.L.", "Agroservicios Llanura, S.L.",
            "El banco nos concede un préstamo de {L0} que devolveremos de una sola vez dentro de cinco años. Nos lo ingresa en la cuenta.",
            "The bank grants us a {L0} loan to be repaid in a single payment in five years. It is paid into our account.",
            listOf(d("572"), h("170", R)), listOf("520", "100", "769", "662"),
            "Como se devuelve en más de un año, es una deuda a largo plazo con entidades de crédito (170). Cuando falte menos de un año para el vencimiento, habrá que reclasificarla a la 520.",
            "As it is repaid after more than a year, it is a long-term bank loan (170). When less than a year remains, it must be reclassified to 520.",
            50_000, 600_000, 1_000,
        ),
        e(
            "ek_intereses", "consultoria", 3, "🏦", "Agroservicios Llanura, S.L.", "Agroservicios Llanura, S.L.",
            "El banco nos cobra en la cuenta {L0} de intereses del préstamo.",
            "The bank charges {L0} of loan interest to our account.",
            listOf(d("662"), h("572", R)), listOf("626", "669", "769", "170"),
            "Los intereses de un préstamo son un gasto financiero de la 662. La 626 recoge las comisiones y otros servicios bancarios, que no son intereses.",
            "Loan interest is a finance cost in 662. Account 626 holds bank fees and other banking services, which are not interest.",
            1_000, 30_000, 1,
        ),
        e(
            "ek_is", "consultoria", 4, "🏢", "Constructora Alto Guadiana, S.A.", "Constructora Alto Guadiana, S.A.",
            "El Impuesto sobre Sociedades del ejercicio es de {L0}. Durante el año ya hemos pagado {L1} a cuenta (modelo 202). Registra el impuesto.",
            "Corporate tax for the year is {L0}. We have already paid {L1} on account during the year (form 202). Record the tax.",
            listOf(d("6300"), h("473", 4_000), h("4752", R)), listOf("4750", "631", "129", "472"),
            "El gasto por el impuesto va a la 6300. Se descuentan los pagos a cuenta ya hechos (473) y lo que queda por pagar es una deuda con Hacienda (4752) que se ingresará con el modelo 200.",
            "The tax expense goes to 6300. Payments on account already made (473) are deducted, and the rest is a debt to the Tax Agency (4752) paid with form 200.",
            100_000, 2_000_000, 1_000,
        ),
        e(
            "ek_reclasificacion", "consultoria", 4, "📆", "Energías del Záncara, S.A.", "Energías del Záncara, S.A.",
            "Queda menos de un año para devolver un préstamo de {L0} que teníamos a largo plazo. Reclasifícalo.",
            "Less than a year remains on a {L0} loan we had as long-term. Reclassify it.",
            listOf(d("170"), h("520", R)), listOf("572", "662", "100", "5201"),
            "Las deudas se clasifican por su vencimiento. Cuando falta menos de un año, la deuda pasa de la 170 (largo plazo) a la 520 (corto plazo); no hay movimiento de dinero.",
            "Debts are classified by maturity. When less than a year remains, the debt moves from 170 (long-term) to 520 (short-term); no money changes hands.",
            200_000, 3_000_000, 1_000,
        ),

        // ───────────── Patrimonios ─────────────
        e(
            "ep_acciones", "patrimonios", 4, "📈", "Inversiones Familiares Montalvo, S.L.", "Inversiones Familiares Montalvo, S.L.",
            "Compramos acciones de una empresa cotizada por {L0} para venderlas dentro de unos meses. Pagamos por banco.",
            "We buy shares in a listed company for {L0}, planning to sell them within a few months. We pay by bank.",
            listOf(d("540"), h("572", R)), listOf("260", "100", "760", "520"),
            "Como se compran para venderlas a corto plazo, son inversiones financieras a corto plazo en instrumentos de patrimonio (540).",
            "As they are bought to be sold in the short term, they are short-term equity investments (540).",
            100_000, 5_000_000, 1_000,
        ),
        e(
            "ep_dividendos", "patrimonios", 4, "💰", "Inversiones Familiares Montalvo, S.L.", "Inversiones Familiares Montalvo, S.L.",
            "Cobramos dividendos de esas acciones: {L2} brutos, con una retención del 19 % ({L1}).",
            "We receive dividends on those shares: {L2} gross, with 19% withholding ({L1}).",
            listOf(d("572", R), d("473", 1_900), h("760")), listOf("4751", "769", "700", "540"),
            "El dividendo bruto es un ingreso financiero (760). La retención que nos han practicado es un pago a cuenta de nuestro impuesto (473), no una deuda, y al banco llega el neto.",
            "The gross dividend is finance income (760). The withholding applied to us is a payment on account of our own tax (473), not a debt, and the bank receives the net amount.",
            5_000, 400_000, 100,
        ),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): EntryCaseTemplate = byId[id] ?: throw IllegalArgumentException("Consulta de asiento desconocida: $id")
    fun find(id: String): EntryCaseTemplate? = byId[id]
}
