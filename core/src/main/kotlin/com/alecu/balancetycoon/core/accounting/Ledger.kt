package com.alecu.balancetycoon.core.accounting

import com.alecu.balancetycoon.core.calendar.GameDate
import com.alecu.balancetycoon.core.i18n.L

/** Línea de un asiento: importe en el debe o en el haber (nunca en los dos). */
data class EntryLine(val account: String, val debit: Long, val credit: Long)

/** Asiento del libro diario. */
data class JournalEntry(
    val number: Int,
    val year: Int,
    val date: GameDate,
    val concept: L,
    val lines: List<EntryLine>,
) {
    val totalDebit: Long get() = lines.sumOf { it.debit }
    val totalCredit: Long get() = lines.sumOf { it.credit }
}

/** Ayuda para construir asientos: ignora las líneas a cero y agrupa por cuenta y lado. */
class EntryBuilder {
    private val debits = LinkedHashMap<String, Long>()
    private val credits = LinkedHashMap<String, Long>()

    fun debit(account: String, amount: Long): EntryBuilder {
        require(amount >= 0) { "Importe negativo en el debe de $account" }
        if (amount > 0) debits[account] = (debits[account] ?: 0L) + amount
        return this
    }

    fun credit(account: String, amount: Long): EntryBuilder {
        require(amount >= 0) { "Importe negativo en el haber de $account" }
        if (amount > 0) credits[account] = (credits[account] ?: 0L) + amount
        return this
    }

    val isEmpty: Boolean get() = debits.isEmpty() && credits.isEmpty()

    fun lines(): List<EntryLine> =
        debits.map { EntryLine(it.key, it.value, 0L) } + credits.map { EntryLine(it.key, 0L, it.value) }
}

class UnbalancedEntryException(message: String) : IllegalArgumentException(message)

/**
 * Libro diario y libro mayor del ejercicio en curso.
 *
 * - Valida la partida doble en cada asiento: si el debe y el haber no coinciden, el asiento se rechaza.
 * - Guarda los totales del debe y del haber de cada cuenta durante el ejercicio (el mayor).
 * - Conserva solo los últimos [maxRecent] asientos para que la partida guardada no crezca sin límite;
 *   los saldos del mayor, en cambio, son completos.
 */
class Ledger(var year: Int, private val maxRecent: Int = 400) {
    var nextNumber: Int = 1
        internal set

    /** Número total de asientos registrados en toda la partida. */
    var totalPosted: Long = 0
        internal set

    private val totals = LinkedHashMap<String, LongArray>()
    private val recentEntries = ArrayDeque<JournalEntry>()

    val recent: List<JournalEntry> get() = recentEntries.toList()

    fun post(date: GameDate, concept: L, builder: EntryBuilder): JournalEntry = post(date, concept, builder.lines())

    fun post(date: GameDate, concept: L, lines: List<EntryLine>): JournalEntry {
        if (lines.size < 2) throw UnbalancedEntryException("Un asiento necesita al menos dos líneas: ${concept.es}")
        for (l in lines) {
            Pgc.get(l.account) // lanza excepción si la cuenta no existe
            if (l.debit < 0 || l.credit < 0 || (l.debit > 0 && l.credit > 0) || (l.debit == 0L && l.credit == 0L)) {
                throw UnbalancedEntryException("Línea no válida en ${l.account}: debe ${l.debit}, haber ${l.credit}")
            }
        }
        val d = lines.sumOf { it.debit }
        val h = lines.sumOf { it.credit }
        if (d != h) throw UnbalancedEntryException("Asiento descuadrado (${concept.es}): debe $d, haber $h")

        val entry = JournalEntry(nextNumber++, year, date, concept, lines)
        for (l in lines) {
            val t = totals.getOrPut(l.account) { LongArray(2) }
            t[0] += l.debit
            t[1] += l.credit
        }
        recentEntries.addLast(entry)
        while (recentEntries.size > maxRecent) recentEntries.removeFirst()
        totalPosted++
        return entry
    }

    fun debitTotal(code: String): Long = totals[code]?.get(0) ?: 0L
    fun creditTotal(code: String): Long = totals[code]?.get(1) ?: 0L

    /** Saldo con signo: positivo si es deudor, negativo si es acreedor. */
    fun balance(code: String): Long = debitTotal(code) - creditTotal(code)

    /** Saldo en el sentido natural de la cuenta (positivo si coincide con su naturaleza). */
    fun natural(code: String): Long =
        if (Pgc.get(code).nature == Nature.DEBIT) balance(code) else -balance(code)

    /** Cuentas con movimientos en el ejercicio, en el orden del plan. */
    fun accountsWithMovement(): List<String> = totals.keys.sortedWith(Pgc.codeOrder)

    /** Comienza un ejercicio nuevo: los totales se reinician y la numeración vuelve a 1. */
    fun startNewYear(newYear: Int) {
        totals.clear()
        year = newYear
        nextNumber = 1
    }

    // --- Restauración desde la partida guardada ---

    internal fun restoreTotals(code: String, debit: Long, credit: Long) {
        totals[code] = longArrayOf(debit, credit)
    }

    internal fun restoreEntry(entry: JournalEntry) {
        recentEntries.addLast(entry)
        while (recentEntries.size > maxRecent) recentEntries.removeFirst()
    }

    internal fun restoreCounters(nextNumber: Int, totalPosted: Long) {
        this.nextNumber = nextNumber
        this.totalPosted = totalPosted
    }

    internal fun allTotals(): Map<String, LongArray> = totals
}
