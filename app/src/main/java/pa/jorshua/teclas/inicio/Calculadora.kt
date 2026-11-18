package pa.jorshua.teclas.inicio

import kotlin.math.abs
import kotlin.math.floor

/** Cuentas escritas en el buscador ("12*3+4", "(5+3)/2"): descenso recursivo de toda la vida. */
object Calculadora {

    fun evaluar(entrada: String): Double? {
        val s = entrada.replace(',', '.').replace('x', '*').replace('×', '*').replace('÷', '/')
            .filter { !it.isWhitespace() }
        if (s.isEmpty() || s.any { it !in "0123456789.+-*/()" }) return null
        if (s.none { it.isDigit() } || s.drop(1).none { it in "+-*/" }) return null
        return try {
            val p = Parser(s)
            val v = p.suma()
            if (p.i != s.length || v.isNaN() || v.isInfinite()) null else v
        } catch (e: IllegalStateException) {
            null
        }
    }

    fun formatear(v: Double): String =
        if (v == floor(v) && abs(v) < 1e15) v.toLong().toString()
        else "%.8f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    private class Parser(val s: String) {
        var i = 0

        fun suma(): Double {
            var v = producto()
            while (i < s.length && (s[i] == '+' || s[i] == '-')) {
                val op = s[i++]
                val d = producto()
                v = if (op == '+') v + d else v - d
            }
            return v
        }

        fun producto(): Double {
            var v = factor()
            while (i < s.length && (s[i] == '*' || s[i] == '/')) {
                val op = s[i++]
                val d = factor()
                v = if (op == '*') v * d else v / d
            }
            return v
        }

        fun factor(): Double {
            check(i < s.length)
            if (s[i] == '-') { i++; return -factor() }
            if (s[i] == '+') { i++; return factor() }
            if (s[i] == '(') {
                i++
                val v = suma()
                check(i < s.length && s[i] == ')')
                i++
                return v
            }
            val ini = i
            while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
            check(i > ini)
            return s.substring(ini, i).toDoubleOrNull() ?: error("número")
        }
    }
}
