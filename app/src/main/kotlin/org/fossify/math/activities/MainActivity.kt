package org.fossify.math.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import me.grantland.widget.AutofitHelper
import org.fossify.commons.extensions.appLaunched
import org.fossify.commons.extensions.copyToClipboard
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.hideKeyboard
import org.fossify.commons.extensions.launchMoreAppsFromUsIntent
import org.fossify.commons.extensions.performHapticFeedback
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.value
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.APP_ICON_IDS
import org.fossify.commons.helpers.LICENSE_AUTOFITTEXTVIEW
import org.fossify.commons.helpers.LOWER_ALPHA_INT
import org.fossify.commons.helpers.MEDIUM_ALPHA_INT
import org.fossify.commons.models.FAQItem
import org.fossify.math.simpler.BuildConfig
import org.fossify.math.simpler.R
import org.fossify.math.databases.CalculatorDatabase
import org.fossify.math.simpler.databinding.ActivityMainBinding
import org.fossify.math.dialogs.HistoryDialog
import org.fossify.math.extensions.config
import org.fossify.math.extensions.updateViewColors
import org.fossify.math.helpers.CALCULATOR_STATE
import org.fossify.math.helpers.Calculator
import org.fossify.math.helpers.CalculatorImpl
import org.fossify.math.helpers.DIVIDE
import org.fossify.math.helpers.HistoryHelper
import org.fossify.math.helpers.MINUS
import org.fossify.math.helpers.MULTIPLY
import org.fossify.math.helpers.PERCENT
import org.fossify.math.helpers.PLUS
import org.fossify.math.helpers.getDecimalSeparator

class MainActivity : SimpleActivity(), Calculator {
    private var storedTextColor = 0
    private var vibrateOnButtonPress = true
    private var saveCalculatorState: String = ""
    private lateinit var calc: CalculatorImpl

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupEdgeToEdge(padBottomSystem = listOf(binding.mainNestedScrollview))
        setupMaterialScrollListener(binding.mainNestedScrollview, binding.mainAppbar!!)
        setSupportActionBar(binding.mainToolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.mainToolbar.setNavigationIcon(R.drawable.ic_date_range)
        binding.mainToolbar.setNavigationOnClickListener {
            startActivity(Intent(applicationContext, DateCalculatorActivity::class.java))
        }

        if (savedInstanceState != null) {
            saveCalculatorState = savedInstanceState.getCharSequence(CALCULATOR_STATE) as String
        }

        calc = CalculatorImpl(
            calculator = this,
            context = applicationContext,
            calculatorState = saveCalculatorState
        )
        binding.btnPlus?.setOnClickOperation(PLUS)
        binding.btnMinus?.setOnClickOperation(MINUS)
        binding.btnMultiply?.setOnClickOperation(MULTIPLY)
        binding.btnDivide?.setOnClickOperation(DIVIDE)
        binding.btnPercent?.setOnClickOperation(PERCENT)
        binding.root.findViewById<TextView>(R.id.btn_open_parenthesis).setVibratingOnClickListener { calc.appendOpenParenthesis() }
        binding.root.findViewById<TextView>(R.id.btn_close_parenthesis).setVibratingOnClickListener { calc.appendCloseParenthesis() }
        binding.btnMinus?.setOnLongClickListener { calc.turnToNegative() }
        binding.btnClear?.setVibratingOnClickListener { calc.handleClear() }
        binding.btnClear?.setOnLongClickListener {
            calc.handleReset()
            true
        }

        getButtonIds().forEach {
            it?.setVibratingOnClickListener { view ->
                calc.numpadClicked(view.id)
            }
        }

        binding.btnEquals?.setVibratingOnClickListener { calc.handleEquals() }
        binding.formula?.setOnLongClickListener { copyToClipboard(false) }
        binding.result?.setOnLongClickListener { copyToClipboard(true) }
        AutofitHelper.create(binding.result)
        AutofitHelper.create(binding.formula)
        storeStateVariables()
        setupDecimalButton()
        checkAppOnSDCard()
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.mainAppbar!!)
        setupMaterialScrollListener(binding.mainNestedScrollview, binding.mainAppbar)

        binding.mainToolbar.setNavigationIcon(R.drawable.ic_date_range)
        binding.mainToolbar.setNavigationOnClickListener {
            startActivity(Intent(applicationContext, DateCalculatorActivity::class.java))
        }

        if (config.preventPhoneFromSleeping) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        setupDecimalButton()
        vibrateOnButtonPress = config.vibrateOnButtonPress

        val white = android.graphics.Color.WHITE
        val black = android.graphics.Color.BLACK
        val red = android.graphics.Color.RED
        val darkGrey = android.graphics.Color.DKGRAY

        window.statusBarColor = black
        binding.root.setBackgroundColor(black)
        binding.mainToolbar?.setBackgroundColor(black)
        binding.result?.setTextColor(white)
        binding.formula?.setTextColor(white)

        val btnOpen = binding.root.findViewById<TextView>(R.id.btn_open_parenthesis)
        val btnClose = binding.root.findViewById<TextView>(R.id.btn_close_parenthesis)

        binding.apply {
            // Clear Buttons (White bg, Red text)
            arrayOf(btnClear, btnReset).forEach {
                it?.background = ResourcesCompat.getDrawable(
                    resources, R.drawable.ripple_white_bg, theme
                )
                it?.setTextColor(red)
            }

            // Operators (White bg, Black text)
            arrayOf(
                btnPercent, btnOpen, btnClose, btnDivide, btnMultiply, btnPlus,
                btnMinus, btnEquals
            ).forEach {
                it?.background = ResourcesCompat.getDrawable(
                    resources, R.drawable.ripple_white_bg, theme
                )
                it?.setTextColor(black)
            }

            // Numbers (Dark Grey bg, White text)
            arrayOf(btn0, btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9, btnDecimal).forEach {
                it?.background = ResourcesCompat.getDrawable(
                    resources, R.drawable.ripple_dark_grey_bg, theme
                )
                it?.setTextColor(white)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        if (config.preventPhoneFromSleeping) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isChangingConfigurations) {
            CalculatorDatabase.destroyInstance()
        }
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString(CALCULATOR_STATE, calc.getCalculatorStateJson().toString())
    }

    private fun storeStateVariables() {
        config.apply {
            storedTextColor = textColor
        }
    }

    private fun checkHaptic(view: View) {
        if (vibrateOnButtonPress) {
            view.performHapticFeedback()
        }
    }

    private fun showHistory() {
        HistoryHelper(this).getHistory {
            if (it.isEmpty()) {
                toast(R.string.history_empty)
            } else {
                HistoryDialog(this, it, calc)
            }
        }
    }

    private fun getButtonIds() = binding.run {
        arrayOf(btnDecimal, btn0, btn1, btn2, btn3, btn4, btn5, btn6, btn7, btn8, btn9)
    }

    private fun copyToClipboard(copyResult: Boolean): Boolean {
        var value = binding.formula?.value
        if (copyResult) {
            value = binding.result?.value
        }

        return if (value.isNullOrEmpty()) {
            false
        } else {
            copyToClipboard(value)
            true
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu?.findItem(R.id.history)?.icon?.setTint(android.graphics.Color.WHITE)
        menu?.findItem(R.id.settings)?.icon?.setTint(android.graphics.Color.WHITE)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
            R.id.history -> showHistory()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun showNewResult(value: String, context: Context) {
        binding.result?.text = value
    }

    override fun showNewFormula(value: String, context: Context) {
        binding.formula?.text = value
    }

    private fun setupDecimalButton() {
        binding.btnDecimal?.text = getDecimalSeparator()
    }

    private fun View.setVibratingOnClickListener(callback: (view: View) -> Unit) {
        setOnClickListener {
            callback(it)
            checkHaptic(it)
        }
    }

    private fun View.setOnClickOperation(operation: String) {
        setVibratingOnClickListener {
            calc.handleOperation(operation)
        }
    }
}
