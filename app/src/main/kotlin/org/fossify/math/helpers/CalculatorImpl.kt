package org.fossify.math.helpers

import android.content.Context
import org.fossify.commons.extensions.showErrorToast
import org.fossify.math.simpler.R
import org.fossify.math.models.History
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal

class CalculatorImpl(
    calculator: Calculator,
    private val context: Context,
    calculatorState: String = ""
) {
    private var callback: Calculator? = calculator
    private var currentResult = "0"
    private var previousCalculation = ""
    private var inputDisplayedFormula = "0"
    private var lastKey = ""
    private val operations = listOf("+", "-", "×", "÷", "%")
    private val formatter = NumberFormatHelper()

    private var openParenthesesCount = 0

    private val decimalSeparator: String get() = formatter.decimalSeparator
    private val groupingSeparator: String get() = formatter.groupingSeparator
    private val numbersRegex: Regex get() =
        "[^0-9${Regex.escape(decimalSeparator)}${Regex.escape(groupingSeparator)}]".toRegex()

    init {
        if (calculatorState != "") {
            setFromSaveInstanceState(calculatorState)
        }
        showNewResult(currentResult)
        showNewFormula(previousCalculation)
    }

    private fun addDigit(number: Int) {
        if (inputDisplayedFormula == "0") {
            inputDisplayedFormula = ""
        }

        if (inputDisplayedFormula.isNotEmpty()) {
            val lastChar = inputDisplayedFormula.last()
            if (lastChar == ')' || lastChar == '%') {
                inputDisplayedFormula += "×"
            }
        }

        inputDisplayedFormula += number
        addThousandsDelimiter()
        showNewResult(inputDisplayedFormula)
    }

    private fun zeroClicked() {
        if (inputDisplayedFormula == "0") return

        if (inputDisplayedFormula.isNotEmpty()) {
            val lastChar = inputDisplayedFormula.last()
            if (lastChar == ')' || lastChar == '%') {
                inputDisplayedFormula += "×"
            }
        }

        inputDisplayedFormula += "0"
        addThousandsDelimiter()
        showNewResult(inputDisplayedFormula)
    }

    private fun decimalClicked() {
        val lastNumber = inputDisplayedFormula.split(Regex("[-+×÷%()^]")).lastOrNull() ?: ""
        if (!lastNumber.contains(decimalSeparator)) {
            if (inputDisplayedFormula.isEmpty() || !inputDisplayedFormula.last().isDigit()) {
                inputDisplayedFormula += "0"
            }
            inputDisplayedFormula += decimalSeparator
        }
        showNewResult(inputDisplayedFormula)
    }

    private fun addThousandsDelimiter() {
        val valuesToCheck = numbersRegex.split(inputDisplayedFormula)
            .filter { it.trim().isNotEmpty() }
        var tempFormula = inputDisplayedFormula
        valuesToCheck.forEach {
            val formatted = formatter.formatForDisplay(it)
            tempFormula = tempFormula.replace(it, formatted)
        }
        inputDisplayedFormula = tempFormula
    }

    fun appendOpenParenthesis() {
        if (inputDisplayedFormula == "0") inputDisplayedFormula = ""

        if (inputDisplayedFormula.isNotEmpty()) {
            val lastChar = inputDisplayedFormula.last()
            if (lastChar.isDigit() || lastChar == ')' || lastChar == '%') {
                inputDisplayedFormula += "×"
            }
        }
        inputDisplayedFormula += "("
        openParenthesesCount++
        showNewResult(inputDisplayedFormula)
    }

    fun appendCloseParenthesis() {
        if (openParenthesesCount > 0) {
            inputDisplayedFormula += ")"
            openParenthesesCount--
            showNewResult(inputDisplayedFormula)
        }
    }

    fun handleOperation(operation: String) {
        if (inputDisplayedFormula == "NaN" || inputDisplayedFormula == "Error") {
            inputDisplayedFormula = "0"
        }

        val sign = getSign(operation)

        if (inputDisplayedFormula.isEmpty()) {
            if (sign == "-") {
                inputDisplayedFormula = "-"
            }
        } else {
            val lastChar = inputDisplayedFormula.last()
            if (operations.contains(lastChar.toString()) || lastChar == decimalSeparator.single()) {
                inputDisplayedFormula = inputDisplayedFormula.dropLast(1)
                if (inputDisplayedFormula.isNotEmpty() && operations.contains(inputDisplayedFormula.last().toString())) {
                    inputDisplayedFormula = inputDisplayedFormula.dropLast(1)
                }
                inputDisplayedFormula += sign
            } else if (lastChar == '(') {
                if (sign == "-") {
                    inputDisplayedFormula += sign
                }
            } else {
                inputDisplayedFormula += sign
            }
        }
        showNewResult(inputDisplayedFormula)
    }

    fun turnToNegative(): Boolean {
        // Simplified: Only support negative if input is effectively empty or last char is operator
        // For complex formulas, this is tricky. Disabling for now or implementing basic logic.
        // Actually, let's just insert "-" if allowed.
        if (inputDisplayedFormula == "0") {
            inputDisplayedFormula = "-"
            showNewResult(inputDisplayedFormula)
            return true
        }
        return false
    }

    fun handleEquals() {
        if (inputDisplayedFormula.isEmpty()) return
        evaluate()
    }

    private fun evaluate() {
        var tempFormula = inputDisplayedFormula
        while (openParenthesesCount > 0) {
            tempFormula += ")"
            openParenthesesCount--
        }

        val expression = tempFormula
            .replace(groupingSeparator, "")
            .replace(decimalSeparator, ".")
            .replace("×", "*")
            .replace("÷", "/")

        try {
            val result = evaluateExpression(expression)
            val resultBigDecimal = BigDecimal(result)
            val formattedResult = formatter.bigDecimalToString(resultBigDecimal)

            HistoryHelper(context).insertOrUpdateHistoryEntry(
                History(
                    id = null,
                    formula = tempFormula,
                    result = formattedResult,
                    timestamp = System.currentTimeMillis()
                )
            )
            showNewFormula(tempFormula)
            inputDisplayedFormula = formattedResult
            currentResult = formattedResult
            openParenthesesCount = 0
            showNewResult(formattedResult)
        } catch (e: Exception) {
            showNewResult("Error")
        }
    }

    private fun evaluateExpression(expression: String): Double {
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < expression.length) expression[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < expression.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    if (eat('+'.code)) x += parseTerm()
                    else if (eat('-'.code)) x -= parseTerm()
                    else return x
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    if (eat('*'.code)) x *= parseFactor()
                    else if (eat('/'.code)) x /= parseFactor()
                    else return x
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return parseFactor()
                if (eat('-'.code)) return -parseFactor()
                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) {
                    while (ch >= '0'.code && ch <= '9'.code || ch == '.'.code) nextChar()
                    x = expression.substring(startPos, pos).toDouble()
                } else if (ch >= 'a'.code && ch <= 'z'.code) {
                    while (ch >= 'a'.code && ch <= 'z'.code) nextChar()
                    val func = expression.substring(startPos, pos)
                    x = parseFactor()
                    x = if (func == "sqrt") Math.sqrt(x) else throw RuntimeException("Unknown function: $func")
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }
                if (eat('^'.code)) x = Math.pow(x, parseFactor())
                if (eat('%'.code)) x /= 100.0
                return x
            }
        }.parse()
    }

    private fun showNewResult(value: String) {
        currentResult = value
        callback!!.showNewResult(value, context)
    }

    private fun showNewFormula(value: String) {
        previousCalculation = value
        callback!!.showNewFormula(value, context)
    }

    fun handleClear() {
        if (inputDisplayedFormula == "Error" || inputDisplayedFormula == "NaN") {
            handleReset()
            return
        }

        if (inputDisplayedFormula.isNotEmpty()) {
            val lastChar = inputDisplayedFormula.last()
            if (lastChar == '(') {
                openParenthesesCount--
            } else if (lastChar == ')') {
                openParenthesesCount++
            }
        }

        var newValue = inputDisplayedFormula.dropLast(1)
        if (newValue == "" || newValue == "0") {
            newValue = "0"
            lastKey = CLEAR
            resetValues()
        } else {
            // Logic for lastKey update if needed, skipping for now
        }

        inputDisplayedFormula = newValue
        addThousandsDelimiter()
        showNewResult(inputDisplayedFormula)
    }

    fun handleReset() {
        resetValues()
        showNewResult("0")
        showNewFormula("")
        inputDisplayedFormula = ""
    }

    private fun resetValues() {
        openParenthesesCount = 0
        lastKey = ""
    }

    private fun getSign(lastOperation: String) = when (lastOperation) {
        MINUS -> "-"
        MULTIPLY -> "×"
        DIVIDE -> "÷"
        PERCENT -> "%"
        else -> "+"
    }

    fun numpadClicked(id: Int) {
        if (inputDisplayedFormula == "NaN" || inputDisplayedFormula == "Error") {
            inputDisplayedFormula = ""
        }

        when (id) {
            R.id.btn_decimal -> decimalClicked()
            R.id.btn_0 -> zeroClicked()
            R.id.btn_1 -> addDigit(1)
            R.id.btn_2 -> addDigit(2)
            R.id.btn_3 -> addDigit(3)
            R.id.btn_4 -> addDigit(4)
            R.id.btn_5 -> addDigit(5)
            R.id.btn_6 -> addDigit(6)
            R.id.btn_7 -> addDigit(7)
            R.id.btn_8 -> addDigit(8)
            R.id.btn_9 -> addDigit(9)
        }
    }

    fun addNumberToFormula(number: String) {
        handleReset()
        inputDisplayedFormula = number
        addThousandsDelimiter()
        showNewResult(inputDisplayedFormula)
    }

    fun getCalculatorStateJson(): JSONObject {
        val jsonObj = JSONObject()
        jsonObj.put(RES, currentResult)
        jsonObj.put(PREVIOUS_CALCULATION, previousCalculation)
        jsonObj.put(LAST_KEY, lastKey)
        jsonObj.put(INPUT_DISPLAYED_FORMULA, inputDisplayedFormula)
        jsonObj.put("openParenthesesCount", openParenthesesCount)
        return jsonObj
    }

    private fun setFromSaveInstanceState(json: String) {
        val jsonObject = JSONTokener(json).nextValue() as JSONObject
        currentResult = jsonObject.getString(RES)
        previousCalculation = jsonObject.getString(PREVIOUS_CALCULATION)
        lastKey = jsonObject.getString(LAST_KEY)
        inputDisplayedFormula = jsonObject.getString(INPUT_DISPLAYED_FORMULA)
        openParenthesesCount = jsonObject.optInt("openParenthesesCount", 0)
    }
}
