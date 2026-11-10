package com.alecu.balancetycoon.core.game

import com.alecu.balancetycoon.core.i18n.L
import com.alecu.balancetycoon.core.money.Money
import kotlin.math.pow

/**
 * Departamento de la gestoría. Cada uno ocupa una planta del edificio y factura honorarios
 * por los expedientes que tramita. Los importes van en céntimos.
 * Los valores económicos son estimaciones de diseño que se ajustan con las simulaciones.
 */
data class DeptDef(
    val id: String,
    val emoji: String,
    val name: L,
    val description: L,
    /** Coste de abrir el departamento (campaña de captación de los primeros clientes), sin IVA. */
    val unlockCost: Long,
    /** Coste del nivel 2; cada nivel siguiente se multiplica por [growth]. */
    val baseCost: Long,
    val growth: Double,
    /** Segundos que tarda un expediente. */
    val cycleSeconds: Double,
    /** Honorarios netos (sin IVA) por expediente y por nivel de cartera. */
    val feePerCycle: Long,
    /** Salario bruto mensual de cada trabajador del departamento. */
    val salary: Long,
    val workerTitle: L,
    /** Coste por trabajador del primer nivel de formación; cada nivel siguiente cuesta el triple. */
    val trainingBase: Long,
)

/** Niveles de cartera que duplican la velocidad de los expedientes (economías de escala). */
val MILESTONES = intArrayOf(25, 50, 100, 200, 300, 400)

object Departments {
    val all: List<DeptDef> = listOf(
        DeptDef(
            id = "fiscal", emoji = "🧾", name = L("Fiscal", "Tax"),
            description = L("IVA, IRPF y declaraciones trimestrales", "VAT, income tax and quarterly returns"),
            unlockCost = Money.euros(600L), baseCost = Money.euros(60L), growth = 1.12,
            cycleSeconds = 1.5, feePerCycle = Money.euros(0.50),
            salary = Money.euros(1_350L), workerTitle = L("Técnico/a fiscal", "Tax clerk"),
            trainingBase = Money.euros(250L),
        ),
        DeptDef(
            id = "laboral", emoji = "👥", name = L("Laboral", "Payroll"),
            description = L("Nóminas, contratos y seguros sociales", "Payslips, contracts and social security"),
            unlockCost = Money.euros(1_500L), baseCost = Money.euros(1_000L), growth = 1.13,
            cycleSeconds = 4.0, feePerCycle = Money.euros(1.20),
            salary = Money.euros(1_400L), workerTitle = L("Graduado/a social", "Payroll specialist"),
            trainingBase = Money.euros(350L),
        ),
        DeptDef(
            id = "contable", emoji = "📒", name = L("Contabilidad", "Bookkeeping"),
            description = L("Libros contables y cuentas anuales", "Accounting books and annual accounts"),
            unlockCost = Money.euros(30_000L), baseCost = Money.euros(25_000L), growth = 1.14,
            cycleSeconds = 10.0, feePerCycle = Money.euros(6L),
            salary = Money.euros(1_600L), workerTitle = L("Contable", "Accountant"),
            trainingBase = Money.euros(600L),
        ),
        DeptDef(
            id = "mercantil", emoji = "⚖️", name = L("Mercantil", "Corporate law"),
            description = L("Constituciones, actas y Registro Mercantil", "Incorporations, minutes and the Companies Registry"),
            unlockCost = Money.euros(400_000L), baseCost = Money.euros(350_000L), growth = 1.15,
            cycleSeconds = 24.0, feePerCycle = Money.euros(40L),
            salary = Money.euros(2_000L), workerTitle = L("Abogado/a mercantilista", "Corporate lawyer"),
            trainingBase = Money.euros(1_000L),
        ),
        DeptDef(
            id = "consultoria", emoji = "📈", name = L("Consultoría", "Advisory"),
            description = L("Planificación fiscal y financiera de empresas", "Tax and financial planning for companies"),
            unlockCost = Money.euros(6_000_000L), baseCost = Money.euros(5_000_000L), growth = 1.16,
            cycleSeconds = 60.0, feePerCycle = Money.euros(250L),
            salary = Money.euros(2_600L), workerTitle = L("Consultor/a", "Consultant"),
            trainingBase = Money.euros(2_000L),
        ),
        DeptDef(
            id = "patrimonios", emoji = "💎", name = L("Patrimonios", "Wealth"),
            description = L("Grandes patrimonios, herencias e inversiones", "Private wealth, inheritances and investments"),
            unlockCost = Money.euros(120_000_000L), baseCost = Money.euros(100_000_000L), growth = 1.17,
            cycleSeconds = 150.0, feePerCycle = Money.euros(2_000L),
            salary = Money.euros(3_400L), workerTitle = L("Asesor/a patrimonial", "Wealth adviser"),
            trainingBase = Money.euros(4_000L),
        ),
    )

    private val byId = all.associateBy { it.id }
    fun get(id: String): DeptDef = byId[id] ?: throw IllegalArgumentException("Departamento desconocido: $id")
    fun indexOf(id: String): Int = all.indexOfFirst { it.id == id }

    /** Coste sin IVA de pasar del nivel [level] al siguiente. */
    fun nextLevelCost(def: DeptDef, level: Int): Long =
        if (level == 0) def.unlockCost else Money.times(def.baseCost, def.growth.pow(level - 1))

    /** Coste sin IVA de comprar [count] niveles a partir de [level]. */
    fun costOf(def: DeptDef, level: Int, count: Int): Long {
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

    /** Multiplicador por formación: +15 % por nivel. */
    fun trainingMultiplier(level: Int): Double = 1.0 + 0.15 * level
    const val MAX_TRAINING = 5
}

/** Local de la gestoría: cada uno tiene más plantas (departamentos) y más puestos por planta. */
data class OfficeDef(
    val index: Int,
    val name: L,
    val city: String,
    val floors: Int,
    val desksPerFloor: Int,
    /** Renta mensual sin IVA. */
    val rent: Long,
    /** true si el arrendador es una persona física: la gestoría retiene el 19 % (modelo 115). */
    val landlordIsIndividual: Boolean,
    val landlord: L,
    /** Obras de acondicionamiento al mudarse (otras instalaciones, 215), sin IVA. */
    val fitOut: Long,
    /** Nivel de reputación necesario. */
    val minTier: Int,
    /** Suministros mensuales del local sin IVA (luz, agua, internet), sin contar los puestos. */
    val utilities: Long,
    val insuranceQuarterly: Long,
) {
    /** Fianza de dos mensualidades (art. 36 de la Ley de Arrendamientos Urbanos, uso distinto de vivienda). */
    val deposit: Long get() = rent * 2
}

object Offices {
    val all: List<OfficeDef> = listOf(
        OfficeDef(
            0, L("Bajo comercial", "Street-level office"), "Tomelloso", floors = 2, desksPerFloor = 2,
            rent = Money.euros(300L), landlordIsIndividual = true, landlord = L("Julián Lara Peinado", "Julián Lara Peinado"),
            fitOut = 0L, minTier = 0, utilities = Money.euros(80L), insuranceQuarterly = Money.euros(90L),
        ),
        OfficeDef(
            1, L("Oficina en la plaza", "Office on the main square"), "Tomelloso", floors = 3, desksPerFloor = 3,
            rent = Money.euros(900L), landlordIsIndividual = true, landlord = L("Rosario Morales Cuenca", "Rosario Morales Cuenca"),
            fitOut = Money.euros(20_000L), minTier = 1, utilities = Money.euros(200L), insuranceQuarterly = Money.euros(250L),
        ),
        OfficeDef(
            2, L("Edificio de oficinas", "Office building"), "Ciudad Real", floors = 4, desksPerFloor = 4,
            rent = Money.euros(3_200L), landlordIsIndividual = false, landlord = L("Inmuebles Ronda Sur, S.L.", "Inmuebles Ronda Sur, S.L."),
            fitOut = Money.euros(300_000L), minTier = 2, utilities = Money.euros(700L), insuranceQuarterly = Money.euros(900L),
        ),
        OfficeDef(
            3, L("Oficinas en el distrito financiero", "Offices in the financial district"), "Madrid", floors = 5, desksPerFloor = 5,
            rent = Money.euros(18_000L), landlordIsIndividual = false, landlord = L("Patrimonial Castellana, S.A.", "Patrimonial Castellana, S.A."),
            fitOut = Money.euros(5_000_000L), minTier = 3, utilities = Money.euros(3_000L), insuranceQuarterly = Money.euros(4_000L),
        ),
        OfficeDef(
            4, L("Torre de oficinas", "Office tower"), "Madrid", floors = 6, desksPerFloor = 6,
            rent = Money.euros(80_000L), landlordIsIndividual = false, landlord = L("Torre Meseta Socimi, S.A.", "Torre Meseta Socimi, S.A."),
            fitOut = Money.euros(80_000_000L), minTier = 4, utilities = Money.euros(12_000L), insuranceQuarterly = Money.euros(15_000L),
        ),
    )

    fun get(index: Int): OfficeDef = all[index.coerceIn(0, all.size - 1)]

    /** Suministros mensuales por puesto ocupado. */
    val UTILITIES_PER_WORKER: Long = Money.euros(25L)
}

/** Tipo de cliente según la reputación de la gestoría. */
data class ClientTier(
    val index: Int,
    val name: L,
    val examples: L,
    val emoji: String,
    /** Puntos de reputación necesarios. */
    val reputation: Long,
    /** Multiplicador de los honorarios de todos los departamentos. */
    val feeMultiplier: Double,
    /** Patrimonio medio de cada cliente (para el indicador de patrimonio gestionado). */
    val averageWealth: Long,
    /** Parte de los honorarios que se cobra a plazo y días de cobro. */
    val creditShare: Double,
    val collectionDays: Int,
    /** Honorarios mínimos de una consulta. */
    val caseMinFee: Long,
)

object ClientTiers {
    val all: List<ClientTier> = listOf(
        ClientTier(
            0, L("Autónomos", "Self-employed"), L("Fontaneros, diseñadores, peluquerías", "Plumbers, designers, hairdressers"), "🧑‍🔧",
            reputation = 0, feeMultiplier = 1.0, averageWealth = Money.euros(60_000L),
            creditShare = 0.0, collectionDays = 0, caseMinFee = Money.euros(40L),
        ),
        ClientTier(
            1, L("Comercios y hostelería", "Shops and hospitality"), L("Bares, tiendas, panaderías", "Bars, shops, bakeries"), "🏪",
            reputation = 75, feeMultiplier = 1.3, averageWealth = Money.euros(300_000L),
            creditShare = 0.0, collectionDays = 0, caseMinFee = Money.euros(120L),
        ),
        ClientTier(
            2, L("Pymes", "Small businesses"), L("Queserías, talleres, cooperativas", "Cheese dairies, workshops, cooperatives"), "🏭",
            reputation = 300, feeMultiplier = 1.7, averageWealth = Money.euros(2_500_000L),
            creditShare = 0.4, collectionDays = 30, caseMinFee = Money.euros(500L),
        ),
        ClientTier(
            3, L("Medianas empresas", "Mid-sized companies"), L("Bodegas, transportistas, constructoras", "Wineries, hauliers, builders"), "🏢",
            reputation = 900, feeMultiplier = 2.3, averageWealth = Money.euros(25_000_000L),
            creditShare = 0.7, collectionDays = 30, caseMinFee = Money.euros(3_000L),
        ),
        ClientTier(
            4, L("Grandes empresas", "Large companies"), L("Grupos industriales y cadenas", "Industrial groups and chains"), "🏦",
            reputation = 2_500, feeMultiplier = 3.2, averageWealth = Money.euros(400_000_000L),
            creditShare = 1.0, collectionDays = 60, caseMinFee = Money.euros(20_000L),
        ),
        ClientTier(
            5, L("Grupos y grandes patrimonios", "Groups and family offices"), L("Holdings y grandes fortunas", "Holdings and high-net-worth families"), "💎",
            reputation = 6_500, feeMultiplier = 4.5, averageWealth = Money.euros(3_000_000_000L),
            creditShare = 1.0, collectionDays = 60, caseMinFee = Money.euros(150_000L),
        ),
    )

    fun forReputation(rep: Long): ClientTier = all.last { rep >= it.reputation }
    fun get(index: Int): ClientTier = all[index.coerceIn(0, all.size - 1)]
}

/** Nivel de los equipos informáticos de un departamento (precio por puesto, sin IVA). */
data class EquipmentTier(val index: Int, val name: L, val price: Long, val multiplier: Double)

object EquipmentTiers {
    val all: List<EquipmentTier> = listOf(
        EquipmentTier(0, L("Ordenador de segunda mano", "Second-hand computer"), Money.euros(500L), 1.0),
        EquipmentTier(1, L("PC de oficina nuevo", "New office PC"), Money.euros(900L), 1.25),
        EquipmentTier(2, L("Doble monitor y escáner", "Dual monitor and scanner"), Money.euros(1_800L), 1.5),
        EquipmentTier(3, L("Estación de trabajo", "Workstation"), Money.euros(4_000L), 1.75),
        EquipmentTier(4, L("Servidor y escritorio en la nube", "Server and cloud desktop"), Money.euros(9_000L), 2.0),
        EquipmentTier(5, L("Automatización con IA", "AI automation"), Money.euros(20_000L), 2.5),
    )

    fun get(index: Int): EquipmentTier = all[index.coerceIn(0, all.size - 1)]

    /** Vida útil de los equipos para procesos de información (coeficiente máximo del 25 %). */
    const val USEFUL_LIFE_YEARS = 4
}

/** Programa de gestión de la gestoría (aplicaciones informáticas, 206), común a todos los departamentos. */
data class SoftwareTier(val index: Int, val name: L, val price: Long, val multiplier: Double, val minTier: Int)

object SoftwareTiers {
    val all: List<SoftwareTier> = listOf(
        SoftwareTier(0, L("Hojas de cálculo", "Spreadsheets"), 0L, 1.0, 0),
        SoftwareTier(1, L("Programa de contabilidad y nóminas", "Accounting and payroll software"), Money.euros(1_500L), 1.1, 0),
        SoftwareTier(2, L("Suite de gestión para asesorías", "Practice management suite"), Money.euros(8_000L), 1.2, 1),
        SoftwareTier(3, L("Presentación telemática masiva", "Bulk electronic filing"), Money.euros(40_000L), 1.35, 2),
        SoftwareTier(4, L("Portal del cliente y firma digital", "Client portal and e-signature"), Money.euros(200_000L), 1.5, 3),
        SoftwareTier(5, L("Analítica de datos con IA", "AI data analytics"), Money.euros(1_000_000L), 1.7, 4),
    )

    fun get(index: Int): SoftwareTier = all[index.coerceIn(0, all.size - 1)]

    /** Vida útil de las aplicaciones informáticas (amortización en tres años). */
    const val USEFUL_LIFE_YEARS = 3
}

/** Vida útil de las obras de acondicionamiento de un local (otras instalaciones). */
const val FIT_OUT_USEFUL_LIFE_YEARS = 10
