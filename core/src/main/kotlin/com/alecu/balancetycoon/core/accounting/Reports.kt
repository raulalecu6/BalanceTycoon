package com.alecu.balancetycoon.core.accounting

/**
 * Cuenta de pérdidas y ganancias. Los ingresos son positivos y los gastos, negativos,
 * igual que en el modelo oficial, donde los gastos aparecen entre paréntesis.
 */
data class PygReport(
    val revenue: Long,
    val supplies: Long,
    val personnel: Long,
    val otherOpex: Long,
    val depreciation: Long,
    val otherResults: Long,
    val financialIncome: Long,
    val financialExpense: Long,
    val incomeTax: Long,
) {
    val operatingResult: Long get() = revenue + supplies + personnel + otherOpex + depreciation + otherResults
    val financialResult: Long get() = financialIncome + financialExpense
    val resultBeforeTax: Long get() = operatingResult + financialResult
    val netResult: Long get() = resultBeforeTax + incomeTax
}

data class BalanceSheetReport(val lines: Map<BsLine, Long>) {
    fun section(section: BsSection): Long = lines.filterKeys { it.section == section }.values.sum()

    val totalAssets: Long get() = section(BsSection.NON_CURRENT_ASSETS) + section(BsSection.CURRENT_ASSETS)
    val equity: Long get() = section(BsSection.EQUITY)
    val liabilities: Long get() = section(BsSection.NON_CURRENT_LIABILITIES) + section(BsSection.CURRENT_LIABILITIES)

    /** El balance cuadra si el activo es igual al patrimonio neto más el pasivo. */
    val balanced: Boolean get() = totalAssets == equity + liabilities
}

/** Fila del balance de sumas y saldos. */
data class TrialRow(val account: String, val debit: Long, val credit: Long) {
    val debitBalance: Long get() = maxOf(0L, debit - credit)
    val creditBalance: Long get() = maxOf(0L, credit - debit)
}

object Reports {
    fun pyg(ledger: Ledger): PygReport {
        val byLine = HashMap<PygLine, Long>()
        for (code in ledger.accountsWithMovement()) {
            val acc = Pgc.get(code)
            val line = acc.pygLine ?: continue
            // Haber menos debe: los ingresos suman y los gastos restan.
            byLine[line] = (byLine[line] ?: 0L) - ledger.balance(code)
        }
        fun v(l: PygLine) = byLine[l] ?: 0L
        return PygReport(
            revenue = v(PygLine.REVENUE),
            supplies = v(PygLine.SUPPLIES),
            personnel = v(PygLine.PERSONNEL),
            otherOpex = v(PygLine.OTHER_OPEX),
            depreciation = v(PygLine.DEPRECIATION),
            otherResults = v(PygLine.OTHER_RESULTS),
            financialIncome = v(PygLine.FINANCIAL_INCOME),
            financialExpense = v(PygLine.FINANCIAL_EXPENSE),
            incomeTax = v(PygLine.INCOME_TAX),
        )
    }

    /**
     * Balance de situación. Mientras el ejercicio está abierto, el resultado del ejercicio
     * incluye el saldo de la 129 más los ingresos y gastos aún no regularizados.
     */
    fun balanceSheet(ledger: Ledger): BalanceSheetReport {
        val lines = LinkedHashMap<BsLine, Long>()
        for (l in BsLine.values()) lines[l] = 0L
        for (code in ledger.accountsWithMovement()) {
            val acc = Pgc.get(code)
            val line = acc.bsLine ?: continue
            val signed = if (line.section.isAsset) ledger.balance(code) else -ledger.balance(code)
            lines[line] = lines.getValue(line) + signed
        }
        lines[BsLine.YEAR_RESULT] = lines.getValue(BsLine.YEAR_RESULT) + pyg(ledger).netResult
        return BalanceSheetReport(lines)
    }

    fun trialBalance(ledger: Ledger): List<TrialRow> =
        ledger.accountsWithMovement().map { TrialRow(it, ledger.debitTotal(it), ledger.creditTotal(it)) }

    /** Importe neto de la cifra de negocios acumulado en el ejercicio. */
    fun revenue(ledger: Ledger): Long = pyg(ledger).revenue
}
