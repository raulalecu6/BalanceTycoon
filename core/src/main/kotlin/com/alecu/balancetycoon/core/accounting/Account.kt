package com.alecu.balancetycoon.core.accounting

import com.alecu.balancetycoon.core.i18n.L

/** Saldo habitual de la cuenta: deudor (activo y gastos) o acreedor (pasivo, patrimonio e ingresos). */
enum class Nature { DEBIT, CREDIT }

/** Masas del balance de situación (modelo abreviado del PGC PYMES). */
enum class BsSection(val title: L) {
    NON_CURRENT_ASSETS(L("A) Activo no corriente", "A) Non-current assets")),
    CURRENT_ASSETS(L("B) Activo corriente", "B) Current assets")),
    EQUITY(L("A) Patrimonio neto", "A) Equity")),
    NON_CURRENT_LIABILITIES(L("B) Pasivo no corriente", "B) Non-current liabilities")),
    CURRENT_LIABILITIES(L("C) Pasivo corriente", "C) Current liabilities"));

    val isAsset: Boolean get() = this == NON_CURRENT_ASSETS || this == CURRENT_ASSETS
}

/** Epígrafes del balance a los que se agrega cada cuenta. */
enum class BsLine(val section: BsSection, val title: L) {
    INTANGIBLE(BsSection.NON_CURRENT_ASSETS, L("Inmovilizado intangible", "Intangible assets")),
    PPE(BsSection.NON_CURRENT_ASSETS, L("Inmovilizado material", "Property, plant and equipment")),
    LT_INVESTMENTS(BsSection.NON_CURRENT_ASSETS, L("Inversiones financieras a largo plazo", "Long-term financial investments")),
    TRADE_RECEIVABLES(BsSection.CURRENT_ASSETS, L("Deudores comerciales y otras cuentas a cobrar", "Trade and other receivables")),
    CASH(BsSection.CURRENT_ASSETS, L("Efectivo y otros activos líquidos equivalentes", "Cash and cash equivalents")),
    CAPITAL(BsSection.EQUITY, L("Capital", "Share capital")),
    RESERVES(BsSection.EQUITY, L("Reservas", "Reserves")),
    PRIOR_RESULTS(BsSection.EQUITY, L("Resultados de ejercicios anteriores", "Prior years' results")),
    YEAR_RESULT(BsSection.EQUITY, L("Resultado del ejercicio", "Profit (loss) for the year")),
    LT_DEBT(BsSection.NON_CURRENT_LIABILITIES, L("Deudas a largo plazo", "Long-term debt")),
    ST_DEBT(BsSection.CURRENT_LIABILITIES, L("Deudas a corto plazo", "Short-term debt")),
    TRADE_PAYABLES(BsSection.CURRENT_LIABILITIES, L("Acreedores comerciales y otras cuentas a pagar", "Trade and other payables")),
}

/** Partidas de la cuenta de pérdidas y ganancias (modelo abreviado del PGC PYMES). */
enum class PygLine(val title: L) {
    REVENUE(L("Importe neto de la cifra de negocios", "Revenue")),
    SUPPLIES(L("Aprovisionamientos", "Cost of supplies")),
    PERSONNEL(L("Gastos de personal", "Personnel expenses")),
    OTHER_OPEX(L("Otros gastos de explotación", "Other operating expenses")),
    DEPRECIATION(L("Amortización del inmovilizado", "Depreciation")),
    DISPOSALS(L("Deterioro y resultado por enajenaciones del inmovilizado", "Impairment and gains (losses) on disposal of fixed assets")),
    OTHER_RESULTS(L("Otros resultados", "Other results")),
    FINANCIAL_INCOME(L("Ingresos financieros", "Finance income")),
    FINANCIAL_EXPENSE(L("Gastos financieros", "Finance costs")),
    INCOME_TAX(L("Impuestos sobre beneficios", "Income tax")),
}

/** Cuenta del plan contable. Debe pertenecer al balance (bsLine) o a la cuenta de PyG (pygLine). */
data class Account(
    val code: String,
    val name: L,
    val nature: Nature,
    val bsLine: BsLine? = null,
    val pygLine: PygLine? = null,
) {
    init {
        require((bsLine == null) != (pygLine == null)) { "La cuenta $code debe ir al balance o a la PyG, no a ambos" }
    }

    val isPyg: Boolean get() = pygLine != null
}

/**
 * Subconjunto del PGC PYMES (RD 1515/2007) que usa el juego, con los nombres oficiales
 * de las cuentas en español y una traducción de referencia en inglés.
 */
object Pgc {
    private fun bs(code: String, es: String, en: String, nature: Nature, line: BsLine) = Account(code, L(es, en), nature, bsLine = line)
    private fun pyg(code: String, es: String, en: String, nature: Nature, line: PygLine) = Account(code, L(es, en), nature, pygLine = line)

    val all: List<Account> = listOf(
        // Grupo 1: Financiación básica
        bs("100", "Capital social", "Share capital", Nature.CREDIT, BsLine.CAPITAL),
        bs("112", "Reserva legal", "Legal reserve", Nature.CREDIT, BsLine.RESERVES),
        bs("113", "Reservas voluntarias", "Voluntary reserves", Nature.CREDIT, BsLine.RESERVES),
        bs("120", "Remanente", "Retained earnings carried forward", Nature.CREDIT, BsLine.PRIOR_RESULTS),
        bs("121", "Resultados negativos de ejercicios anteriores", "Prior years' losses", Nature.DEBIT, BsLine.PRIOR_RESULTS),
        bs("129", "Resultado del ejercicio", "Profit (loss) for the year", Nature.CREDIT, BsLine.YEAR_RESULT),
        bs("170", "Deudas a largo plazo con entidades de crédito", "Long-term bank loans", Nature.CREDIT, BsLine.LT_DEBT),
        // Grupo 2: Activo no corriente
        bs("206", "Aplicaciones informáticas", "Computer software", Nature.DEBIT, BsLine.INTANGIBLE),
        bs("2806", "Amortización acumulada de aplicaciones informáticas", "Accumulated amortisation of computer software", Nature.CREDIT, BsLine.INTANGIBLE),
        bs("211", "Construcciones", "Buildings", Nature.DEBIT, BsLine.PPE),
        bs("212", "Instalaciones técnicas", "Technical installations", Nature.DEBIT, BsLine.PPE),
        bs("213", "Maquinaria", "Machinery", Nature.DEBIT, BsLine.PPE),
        bs("215", "Otras instalaciones", "Other installations", Nature.DEBIT, BsLine.PPE),
        bs("216", "Mobiliario", "Furniture", Nature.DEBIT, BsLine.PPE),
        bs("217", "Equipos para procesos de información", "IT equipment", Nature.DEBIT, BsLine.PPE),
        bs("218", "Elementos de transporte", "Vehicles", Nature.DEBIT, BsLine.PPE),
        bs("2812", "Amortización acumulada de instalaciones técnicas", "Accumulated depreciation of technical installations", Nature.CREDIT, BsLine.PPE),
        bs("2813", "Amortización acumulada de maquinaria", "Accumulated depreciation of machinery", Nature.CREDIT, BsLine.PPE),
        bs("2815", "Amortización acumulada de otras instalaciones", "Accumulated depreciation of other installations", Nature.CREDIT, BsLine.PPE),
        bs("2816", "Amortización acumulada de mobiliario", "Accumulated depreciation of furniture", Nature.CREDIT, BsLine.PPE),
        bs("2817", "Amortización acumulada de equipos para procesos de información", "Accumulated depreciation of IT equipment", Nature.CREDIT, BsLine.PPE),
        bs("260", "Fianzas constituidas a largo plazo", "Long-term deposits given", Nature.DEBIT, BsLine.LT_INVESTMENTS),
        // Grupo 4: Acreedores y deudores por operaciones comerciales
        bs("400", "Proveedores", "Suppliers", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        bs("410", "Acreedores por prestaciones de servicios", "Service creditors", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        bs("430", "Clientes", "Customers", Nature.DEBIT, BsLine.TRADE_RECEIVABLES),
        bs("465", "Remuneraciones pendientes de pago", "Salaries payable", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        bs("4700", "Hacienda Pública, deudora por IVA", "VAT receivable from the Tax Agency", Nature.DEBIT, BsLine.TRADE_RECEIVABLES),
        bs("472", "Hacienda Pública, IVA soportado", "Input VAT", Nature.DEBIT, BsLine.TRADE_RECEIVABLES),
        bs("4750", "Hacienda Pública, acreedora por IVA", "VAT payable", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        bs("4751", "Hacienda Pública, acreedora por retenciones practicadas", "Withholdings payable", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        bs("4752", "Hacienda Pública, acreedora por impuesto sobre sociedades", "Corporate tax payable", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        bs("476", "Organismos de la Seguridad Social, acreedores", "Social Security payable", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        bs("477", "Hacienda Pública, IVA repercutido", "Output VAT", Nature.CREDIT, BsLine.TRADE_PAYABLES),
        // Grupo 5: Cuentas financieras
        bs("5201", "Deudas a corto plazo por crédito dispuesto", "Credit line drawn", Nature.CREDIT, BsLine.ST_DEBT),
        bs("526", "Dividendo activo a pagar", "Dividends payable", Nature.CREDIT, BsLine.ST_DEBT),
        bs("572", "Bancos e instituciones de crédito c/c vista, euros", "Bank current accounts", Nature.DEBIT, BsLine.CASH),
        // Grupo 6: Compras y gastos
        pyg("600", "Compras de mercaderías", "Purchases of goods for resale", Nature.DEBIT, PygLine.SUPPLIES),
        pyg("601", "Compras de materias primas", "Purchases of raw materials", Nature.DEBIT, PygLine.SUPPLIES),
        pyg("602", "Compras de otros aprovisionamientos", "Purchases of other supplies", Nature.DEBIT, PygLine.SUPPLIES),
        pyg("621", "Arrendamientos y cánones", "Rent and royalties", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("622", "Reparaciones y conservación", "Repairs and maintenance", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("623", "Servicios de profesionales independientes", "Independent professional services", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("624", "Transportes", "Transport", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("625", "Primas de seguros", "Insurance premiums", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("626", "Servicios bancarios y similares", "Bank charges", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("627", "Publicidad, propaganda y relaciones públicas", "Advertising and public relations", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("628", "Suministros", "Utilities", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("629", "Otros servicios", "Other services", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("6300", "Impuesto corriente", "Current income tax", Nature.DEBIT, PygLine.INCOME_TAX),
        pyg("631", "Otros tributos", "Other taxes", Nature.DEBIT, PygLine.OTHER_OPEX),
        pyg("640", "Sueldos y salarios", "Wages and salaries", Nature.DEBIT, PygLine.PERSONNEL),
        pyg("642", "Seguridad Social a cargo de la empresa", "Employer social security", Nature.DEBIT, PygLine.PERSONNEL),
        pyg("649", "Otros gastos sociales", "Other employee benefits", Nature.DEBIT, PygLine.PERSONNEL),
        pyg("662", "Intereses de deudas", "Interest on debts", Nature.DEBIT, PygLine.FINANCIAL_EXPENSE),
        pyg("669", "Otros gastos financieros", "Other finance costs", Nature.DEBIT, PygLine.FINANCIAL_EXPENSE),
        pyg("671", "Pérdidas procedentes del inmovilizado material", "Losses on disposal of property, plant and equipment", Nature.DEBIT, PygLine.DISPOSALS),
        pyg("678", "Gastos excepcionales", "Exceptional expenses", Nature.DEBIT, PygLine.OTHER_RESULTS),
        pyg("680", "Amortización del inmovilizado intangible", "Amortisation of intangible assets", Nature.DEBIT, PygLine.DEPRECIATION),
        pyg("681", "Amortización del inmovilizado material", "Depreciation of property, plant and equipment", Nature.DEBIT, PygLine.DEPRECIATION),
        // Grupo 7: Ventas e ingresos
        pyg("700", "Ventas de mercaderías", "Sales of goods for resale", Nature.CREDIT, PygLine.REVENUE),
        pyg("701", "Ventas de productos terminados", "Sales of finished goods", Nature.CREDIT, PygLine.REVENUE),
        pyg("705", "Prestaciones de servicios", "Services rendered", Nature.CREDIT, PygLine.REVENUE),
    )

    private val byCode: Map<String, Account> = all.associateBy { it.code }

    fun get(code: String): Account = byCode[code] ?: throw IllegalArgumentException("Cuenta inexistente en el plan: $code")

    fun exists(code: String): Boolean = byCode.containsKey(code)

    /** Orden natural del plan: por código como texto (1 < 100 < 112 < 2 < 21...). */
    val codeOrder: Comparator<String> = Comparator { a, b -> a.compareTo(b) }
}
