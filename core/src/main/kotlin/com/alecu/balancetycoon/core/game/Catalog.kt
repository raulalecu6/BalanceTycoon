package com.alecu.balancetycoon.core.game

import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.money.Money
import com.alecu.balancetycoon.core.tax.Vat
import com.alecu.balancetycoon.core.tax.Withholding
import kotlin.math.pow

/**
 * Definición fija de una línea de negocio. Los importes van en céntimos.
 * Los valores económicos son estimaciones iniciales que se ajustan con las simulaciones.
 */
data class LineDef(
    val id: String,
    val emoji: String,
    val name: L,
    /** Coste del primer nivel (desbloqueo), sin IVA. */
    val unlockCost: Long,
    /** Coste del nivel 2; cada nivel siguiente se multiplica por [growth]. */
    val baseCost: Long,
    val growth: Double,
    val cycleSeconds: Double,
    /** Venta neta (sin IVA) por ciclo y por nivel. */
    val revenuePerCycle: Long,
    val salesAccount: String,
    val salesVat: Int,
    val purchaseAccount: String,
    val purchaseVat: Int,
    /** Compras de aprovisionamientos sobre la venta neta. */
    val costRatio: Double,
    /** Parte de la venta que se cobra a crédito (el resto, al contado). */
    val creditShare: Double,
    val collectionDays: Int,
    /** Días de pago a proveedores (0 = al contado). */
    val supplierDays: Int,
    val assetAccount: String,
    val depreciationAccount: String,
    val usefulLifeYears: Int,
    val assetName: L,
    val managerSalary: Long,
    val managerTitle: L,
)

/** Niveles que duplican la velocidad del ciclo (economías de escala). */
val MILESTONES = intArrayOf(10, 25, 50, 100, 200)

object Lines {
    val all: List<LineDef> = listOf(
        LineDef(
            id = "bakery", emoji = "🥖", name = L("Panadería", "Bakery"),
            unlockCost = Money.euros(600L), baseCost = Money.euros(60L), growth = 1.12,
            cycleSeconds = 1.5, revenuePerCycle = Money.euros(0.60),
            salesAccount = "701", salesVat = Vat.SUPER_REDUCED,
            purchaseAccount = "601", purchaseVat = Vat.SUPER_REDUCED, costRatio = 0.35,
            creditShare = 0.0, collectionDays = 0, supplierDays = 30,
            assetAccount = "213", depreciationAccount = "2813", usefulLifeYears = 10,
            assetName = L("Hornos y amasadoras", "Ovens and mixers"),
            managerSalary = Money.euros(1_350L), managerTitle = L("Maestro panadero", "Head baker"),
        ),
        LineDef(
            id = "cafe", emoji = "☕", name = L("Cafetería", "Café"),
            unlockCost = Money.euros(1_500L), baseCost = Money.euros(1_500L), growth = 1.13,
            cycleSeconds = 4.0, revenuePerCycle = Money.euros(6L),
            salesAccount = "700", salesVat = Vat.REDUCED,
            purchaseAccount = "600", purchaseVat = Vat.REDUCED, costRatio = 0.30,
            creditShare = 0.0, collectionDays = 0, supplierDays = 0,
            assetAccount = "216", depreciationAccount = "2816", usefulLifeYears = 10,
            assetName = L("Mobiliario y cafeteras", "Furniture and coffee machines"),
            managerSalary = Money.euros(1_400L), managerTitle = L("Encargado de sala", "Floor manager"),
        ),
        LineDef(
            id = "garage", emoji = "🔧", name = L("Taller mecánico", "Garage"),
            unlockCost = Money.euros(15_000L), baseCost = Money.euros(15_000L), growth = 1.14,
            cycleSeconds = 10.0, revenuePerCycle = Money.euros(70L),
            salesAccount = "705", salesVat = Vat.GENERAL,
            purchaseAccount = "602", purchaseVat = Vat.GENERAL, costRatio = 0.25,
            creditShare = 0.30, collectionDays = 30, supplierDays = 30,
            assetAccount = "213", depreciationAccount = "2813", usefulLifeYears = 10,
            assetName = L("Elevadores y herramienta", "Lifts and tools"),
            managerSalary = Money.euros(1_700L), managerTitle = L("Jefe de taller", "Workshop manager"),
        ),
        LineDef(
            id = "cheese", emoji = "🧀", name = L("Quesería artesanal", "Artisan cheese dairy"),
            unlockCost = Money.euros(90_000L), baseCost = Money.euros(90_000L), growth = 1.15,
            cycleSeconds = 24.0, revenuePerCycle = Money.euros(550L),
            salesAccount = "701", salesVat = Vat.SUPER_REDUCED,
            purchaseAccount = "601", purchaseVat = Vat.SUPER_REDUCED, costRatio = 0.40,
            creditShare = 0.60, collectionDays = 30, supplierDays = 30,
            assetAccount = "212", depreciationAccount = "2812", usefulLifeYears = 10,
            assetName = L("Cámaras de maduración", "Maturing rooms"),
            managerSalary = Money.euros(2_000L), managerTitle = L("Maestra quesera", "Head cheesemaker"),
        ),
        LineDef(
            id = "winery", emoji = "🍷", name = L("Bodega", "Winery"),
            unlockCost = Money.euros(1_200_000L), baseCost = Money.euros(1_200_000L), growth = 1.16,
            cycleSeconds = 60.0, revenuePerCycle = Money.euros(3_500L),
            salesAccount = "701", salesVat = Vat.GENERAL,
            purchaseAccount = "602", purchaseVat = Vat.GENERAL, costRatio = 0.35,
            creditShare = 1.0, collectionDays = 60, supplierDays = 60,
            assetAccount = "213", depreciationAccount = "2813", usefulLifeYears = 10,
            assetName = L("Prensas y embotelladora", "Presses and bottling line"),
            managerSalary = Money.euros(2_400L), managerTitle = L("Enóloga", "Winemaker"),
        ),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): LineDef = byId[id] ?: throw IllegalArgumentException("Línea desconocida: $id")

    /** Coste sin IVA de pasar del nivel [level] al siguiente. */
    fun nextLevelCost(def: LineDef, level: Int): Long =
        if (level == 0) def.unlockCost else Money.times(def.baseCost, def.growth.pow(level - 1))

    /** Coste sin IVA de comprar [count] niveles a partir de [level]. */
    fun costOf(def: LineDef, level: Int, count: Int): Long {
        var total = 0L
        var l = level
        repeat(count) {
            total = Money.add(total, nextLevelCost(def, l))
            l++
        }
        return total
    }

    fun milestonesReached(level: Int): Int = MILESTONES.count { level >= it }

    fun nextMilestone(level: Int): Int? = MILESTONES.firstOrNull { level < it }
}

/** Salario mensual bruto del auxiliar contable. */
val ASSISTANT_SALARY: Long = Money.euros(1_500L)

enum class WithholdingModel(val code: String) { M111("111"), M115("115"), M123("123") }

/** Plantilla de un documento de la bandeja. */
data class DocTemplate(
    val id: String,
    val emoji: String,
    val title: L,
    val issuer: L,
    val description: L,
    val correctAccount: String,
    val distractors: List<String>,
    val vat: Int,
    val withholding: Int,
    val withholdingModel: WithholdingModel?,
    /** true si se carga directamente en el banco (comisiones, tributos); false si pasa por la 410. */
    val directDebit: Boolean,
    val minAmount: Long,
    /** Peso sobre las ventas del último mes: importe ≈ weight × 0,5 % de las ventas de 30 días. */
    val weight: Double,
    /** Frecuencia relativa de aparición aleatoria (0 = solo programado). */
    val frequency: Int,
    val explanation: L,
)

object Documents {
    val notary = DocTemplate(
        id = "notary", emoji = "📜",
        title = L("Factura de la notaría", "Notary's invoice"),
        issuer = L("Notaría de la Plaza (Tomelloso)", "Notaría de la Plaza (Tomelloso)"),
        description = L("Escritura de constitución de la sociedad", "Deed of incorporation of the company"),
        correctAccount = "623", distractors = listOf("629", "631", "100"),
        vat = Vat.GENERAL, withholding = Withholding.PROFESSIONALS, withholdingModel = WithholdingModel.M111,
        directDebit = false, minAmount = Money.euros(300L), weight = 0.0, frequency = 0,
        explanation = L(
            "Los honorarios del notario son un servicio de un profesional independiente (623). Como el notario es una persona física, la sociedad le practica una retención del 15% que ingresa en Hacienda con el modelo 111. No es capital (100): el capital solo recoge las aportaciones de los socios.",
            "Notary fees are a service from an independent professional (623). Because the notary is an individual, the company withholds 15% and pays it to the Tax Agency with form 111. It is not share capital (100): capital only records the partners' contributions.",
        ),
    )

    val rent = DocTemplate(
        id = "rent", emoji = "🏠",
        title = L("Recibo del alquiler del local", "Shop rent invoice"),
        issuer = L("Julián Lara Peinado (propietario)", "Julián Lara Peinado (landlord)"),
        description = L("Alquiler mensual del local de negocio", "Monthly rent of the business premises"),
        correctAccount = "621", distractors = listOf("211", "622", "623"),
        vat = Vat.GENERAL, withholding = Withholding.RENT, withholdingModel = WithholdingModel.M115,
        directDebit = false, minAmount = Money.euros(150L), weight = 2.0, frequency = 0,
        explanation = L(
            "El alquiler es un gasto de arrendamientos (621). No es una construcción (211), porque el local no es de la empresa. El alquiler de un local de negocio lleva IVA del 21% y, si el propietario es un particular, la empresa le retiene un 19% que ingresa con el modelo 115.",
            "Rent is recorded under rent and royalties (621). It is not a building (211), because the company does not own the premises. Business premises rent carries 21% VAT and, when the landlord is an individual, the company withholds 19% and pays it with form 115.",
        ),
    )

    val random: List<DocTemplate> = listOf(
        DocTemplate(
            id = "power", emoji = "💡",
            title = L("Factura de electricidad", "Electricity bill"),
            issuer = L("Eléctrica Manchega, S.A.", "Eléctrica Manchega, S.A."),
            description = L("Consumo eléctrico del periodo", "Electricity consumption for the period"),
            correctAccount = "628", distractors = listOf("622", "621", "602"),
            vat = Vat.GENERAL, withholding = 0, withholdingModel = null,
            directDebit = false, minAmount = Money.euros(25L), weight = 1.2, frequency = 3,
            explanation = L(
                "La luz es un suministro (628), igual que el agua o el gas: algo que se consume y no se puede almacenar. No es un aprovisionamiento (602), porque no se compra para incorporarlo a la producción como existencia.",
                "Electricity is a utility (628), like water or gas: it is consumed and cannot be stored. It is not a supply purchase (602), because it is not bought to be held as inventory.",
            ),
        ),
        DocTemplate(
            id = "insurance", emoji = "🛡️",
            title = L("Recibo del seguro multirriesgo", "Business insurance premium"),
            issuer = L("Seguros La Llanura, S.A.", "Seguros La Llanura, S.A."),
            description = L("Prima del seguro del local y la maquinaria", "Insurance premium for premises and machinery"),
            correctAccount = "625", distractors = listOf("623", "626", "629"),
            vat = Vat.EXEMPT, withholding = 0, withholdingModel = null,
            directDebit = false, minAmount = Money.euros(30L), weight = 0.8, frequency = 1,
            explanation = L(
                "Las primas de seguros tienen su propia cuenta (625). Las operaciones de seguro están exentas de IVA (art. 20.Uno.16.º de la Ley del IVA), así que el recibo no lleva IVA soportado.",
                "Insurance premiums have their own account (625). Insurance is VAT-exempt (art. 20.One.16 of the Spanish VAT Act), so the premium carries no input VAT.",
            ),
        ),
        DocTemplate(
            id = "bankfee", emoji = "🏦",
            title = L("Cargo de comisión bancaria", "Bank charge"),
            issuer = L("Banca Llanura Manchega", "Banca Llanura Manchega"),
            description = L("Comisión de mantenimiento de la cuenta y del TPV", "Account and card terminal maintenance fee"),
            correctAccount = "626", distractors = listOf("662", "669", "625"),
            vat = Vat.EXEMPT, withholding = 0, withholdingModel = null,
            directDebit = true, minAmount = Money.euros(8L), weight = 0.3, frequency = 2,
            explanation = L(
                "Las comisiones del banco son servicios bancarios (626), un gasto de explotación. No son intereses (662): los intereses son el precio de una deuda y van al resultado financiero. Los servicios financieros están exentos de IVA.",
                "Bank fees are bank charges (626), an operating expense. They are not interest (662): interest is the price of debt and goes to the financial result. Financial services are VAT-exempt.",
            ),
        ),
        DocTemplate(
            id = "advertising", emoji = "📻",
            title = L("Factura de publicidad", "Advertising invoice"),
            issuer = L("Onda Viñedo Radio, S.L.", "Onda Viñedo Radio, S.L."),
            description = L("Cuñas publicitarias en la radio local", "Radio commercials on the local station"),
            correctAccount = "627", distractors = listOf("629", "623", "622"),
            vat = Vat.GENERAL, withholding = 0, withholdingModel = null,
            directDebit = false, minAmount = Money.euros(40L), weight = 1.0, frequency = 2,
            explanation = L(
                "Los anuncios, carteles y patrocinios van a publicidad, propaganda y relaciones públicas (627). La emisora es una sociedad, así que no hay retención.",
                "Ads, posters and sponsorships go to advertising and public relations (627). The radio station is a company, so there is no withholding.",
            ),
        ),
        DocTemplate(
            id = "repair", emoji = "🛠️",
            title = L("Factura de reparación", "Repair invoice"),
            issuer = L("Reparaciones Molino Nuevo, S.L.", "Reparaciones Molino Nuevo, S.L."),
            description = L("Reparación de una avería en la maquinaria", "Repair of a machinery breakdown"),
            correctAccount = "622", distractors = listOf("213", "628", "629"),
            vat = Vat.GENERAL, withholding = 0, withholdingModel = null,
            directDebit = false, minAmount = Money.euros(35L), weight = 1.0, frequency = 2,
            explanation = L(
                "Una reparación que solo devuelve la máquina a su estado normal es un gasto de reparaciones y conservación (622). Solo se lleva al inmovilizado (213) si alarga la vida útil o mejora la capacidad del bien.",
                "A repair that only restores the machine to working order is a repairs and maintenance expense (622). It is capitalised as machinery (213) only if it extends the asset's useful life or improves its capacity.",
            ),
        ),
        DocTemplate(
            id = "lawyer", emoji = "⚖️",
            title = L("Minuta del abogado", "Lawyer's fee note"),
            issuer = L("Elena Serrano Ruiz, abogada", "Elena Serrano Ruiz, lawyer"),
            description = L("Revisión de contratos con proveedores", "Review of supplier contracts"),
            correctAccount = "623", distractors = listOf("640", "629", "627"),
            vat = Vat.GENERAL, withholding = Withholding.PROFESSIONALS, withholdingModel = WithholdingModel.M111,
            directDebit = false, minAmount = Money.euros(60L), weight = 0.9, frequency = 1,
            explanation = L(
                "Los honorarios de abogados, asesores o arquitectos van a servicios de profesionales independientes (623). No son sueldos (640), porque la abogada no es trabajadora de la empresa. Al ser persona física, se le retiene un 15% (modelo 111).",
                "Fees from lawyers, advisers or architects go to independent professional services (623). They are not wages (640), because the lawyer is not an employee. As an individual, she is subject to 15% withholding (form 111).",
            ),
        ),
        DocTemplate(
            id = "transport", emoji = "🚚",
            title = L("Factura de transporte", "Transport invoice"),
            issuer = L("Transportes Vía Pecuaria, S.L.", "Transportes Vía Pecuaria, S.L."),
            description = L("Portes de envío a clientes", "Shipping to customers"),
            correctAccount = "624", distractors = listOf("600", "628", "629"),
            vat = Vat.GENERAL, withholding = 0, withholdingModel = null,
            directDebit = false, minAmount = Money.euros(30L), weight = 0.9, frequency = 2,
            explanation = L(
                "Los portes de las ventas que paga la empresa a una agencia son transportes (624). Si los portes fueran de una compra y vinieran en la factura del proveedor, se sumarían al precio de la compra (600).",
                "Shipping on sales paid to a carrier is transport (624). If the shipping belonged to a purchase and came on the supplier's invoice, it would be added to the purchase cost (600).",
            ),
        ),
        DocTemplate(
            id = "office", emoji = "📎",
            title = L("Factura de material de oficina", "Office supplies invoice"),
            issuer = L("Papelería Tintero Manchego, S.L.", "Papelería Tintero Manchego, S.L."),
            description = L("Papel, tóner y material de escritorio", "Paper, toner and stationery"),
            correctAccount = "629", distractors = listOf("602", "217", "628"),
            vat = Vat.GENERAL, withholding = 0, withholdingModel = null,
            directDebit = false, minAmount = Money.euros(15L), weight = 0.6, frequency = 2,
            explanation = L(
                "El material de oficina se registra en otros servicios (629), donde el PGC incluye los gastos de oficina. No es un equipo informático (217), porque se consume en el año y no es inmovilizado.",
                "Office supplies are recorded under other services (629), which the Spanish GAAP uses for office expenses. They are not IT equipment (217), because they are used up within the year and are not fixed assets.",
            ),
        ),
        DocTemplate(
            id = "ibi", emoji = "🏛️",
            title = L("Recibo del IBI", "Property tax bill (IBI)"),
            issuer = L("Ayuntamiento de Tomelloso", "Tomelloso Town Council"),
            description = L("Impuesto sobre Bienes Inmuebles del almacén", "Property tax on the warehouse"),
            correctAccount = "631", distractors = listOf("6300", "621", "678"),
            vat = Vat.EXEMPT, withholding = 0, withholdingModel = null,
            directDebit = true, minAmount = Money.euros(40L), weight = 0.7, frequency = 1,
            explanation = L(
                "El IBI, el IAE o las tasas municipales son otros tributos (631), un gasto de explotación. No es el impuesto sobre beneficios (6300), que solo recoge el Impuesto sobre Sociedades. Los tributos no llevan IVA.",
                "Property tax, business tax or municipal fees are other taxes (631), an operating expense. It is not income tax (6300), which only records corporate tax. Taxes carry no VAT.",
            ),
        ),
    )

    val all: List<DocTemplate> = listOf(notary, rent) + random
    private val byId = all.associateBy { it.id }
    fun get(id: String): DocTemplate = byId[id] ?: throw IllegalArgumentException("Documento desconocido: $id")
}
