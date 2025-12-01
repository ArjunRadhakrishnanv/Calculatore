package org.fossify.math.activities

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.core.content.res.ResourcesCompat
import org.fossify.commons.extensions.viewBinding
import org.fossify.math.simpler.R
import org.fossify.math.simpler.databinding.ActivityDateCalculatorBinding
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class DateCalculatorActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityDateCalculatorBinding::inflate)

    private var diffStartDate: LocalDate = LocalDate.now()
    private var diffEndDate: LocalDate = LocalDate.now()
    private var addSubStartDate: LocalDate = LocalDate.now()

    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupEdgeToEdge(padBottomSystem = listOf(binding.dateCalcScrollview))
        setupTopAppBar(binding.dateCalcAppbar)
        setupMaterialScrollListener(binding.dateCalcScrollview, binding.dateCalcAppbar)

        setSupportActionBar(binding.dateCalcToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.dateCalcToolbar.setNavigationOnClickListener { finish() }

        setupUI()
        updateTheme()
    }

    private fun setupUI() {
        // Mode Switching
        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_mode_diff -> {
                        binding.sectionDiff.visibility = View.VISIBLE
                        binding.sectionAddSub.visibility = View.GONE
                        updateToggleButtonColors(checkedId)
                    }
                    R.id.btn_mode_add_sub -> {
                        binding.sectionDiff.visibility = View.GONE
                        binding.sectionAddSub.visibility = View.VISIBLE
                        updateToggleButtonColors(checkedId)
                        binding.btnCalculateNewDate.setTextColor(android.graphics.Color.BLACK)
                    }
                }
            }
        }
        // Initial setup for toggle button colors
        updateToggleButtonColors(binding.modeToggleGroup.checkedButtonId)

        // Difference Section
        binding.btnDiffStartDate.text = dateFormatter.format(diffStartDate)
        binding.btnDiffEndDate.text = dateFormatter.format(diffEndDate)
        updateDiffResult()

        binding.btnDiffStartDate.setOnClickListener {
            pickDate(diffStartDate) { date ->
                diffStartDate = date
                binding.btnDiffStartDate.text = dateFormatter.format(date)
                updateDiffResult()
            }
        }

        binding.btnDiffEndDate.setOnClickListener {
            pickDate(diffEndDate) { date ->
                diffEndDate = date
                binding.btnDiffEndDate.text = dateFormatter.format(date)
                updateDiffResult()
            }
        }

        // Add/Subtract Section
        binding.btnAddSubStartDate.text = dateFormatter.format(addSubStartDate)
        updateAddSubResult()

        binding.btnAddSubStartDate.setOnClickListener {
            pickDate(addSubStartDate) { date ->
                addSubStartDate = date
                binding.btnAddSubStartDate.text = dateFormatter.format(date)
                updateAddSubResult()
            }
        }

        val units = arrayOf(getString(R.string.days), getString(R.string.months), getString(R.string.years))
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, units)
        binding.spinnerUnit.adapter = adapter

        binding.btnCalculateNewDate.setOnClickListener {
            updateAddSubResult()
        }
    }

    private fun pickDate(current: LocalDate, onDatePicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                onDatePicked(LocalDate.of(year, month + 1, dayOfMonth))
            },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth
        ).show()
    }

    private fun updateDiffResult() {
        val period = Period.between(diffStartDate, diffEndDate)
        val years = period.years
        val months = period.months
        val days = period.days
        
        // Build a nice string (e.g., "1 Year, 2 Months, 5 Days")
        val parts = mutableListOf<String>()
        if (years != 0) parts.add("$years ${getString(R.string.years)}")
        if (months != 0) parts.add("$months ${getString(R.string.months)}")
        if (days != 0) parts.add("$days ${getString(R.string.days)}")
        
        val resultText = if (parts.isEmpty()) {
            "0 ${getString(R.string.days)}"
        } else {
            parts.joinToString(", ")
        }
        
        // Also show total days if meaningful
        val totalDays = java.time.temporal.ChronoUnit.DAYS.between(diffStartDate, diffEndDate)
        val fullText = if (parts.size > 1 || years > 0 || months > 0) {
            "$resultText\n($totalDays ${getString(R.string.days)})"
        } else {
            resultText
        }

        binding.tvDiffResult.text = fullText
    }

    private fun updateAddSubResult() {
        val valueStr = binding.etValue.text.toString()
        val value = valueStr.toLongOrNull() ?: 0L
        val unitIndex = binding.spinnerUnit.selectedItemPosition
        val isAdd = binding.rbAdd.isChecked

        var resultDate = addSubStartDate
        val sign = if (isAdd) 1 else -1

        resultDate = when (unitIndex) {
            0 -> resultDate.plusDays(value * sign) // Days
            1 -> resultDate.plusMonths(value * sign) // Months
            2 -> resultDate.plusYears(value * sign) // Years
            else -> resultDate
        }

        binding.tvAddSubResult.text = dateFormatter.format(resultDate)
    }

    private fun updateTheme() {
        val black = android.graphics.Color.BLACK
        val white = android.graphics.Color.WHITE
        
        window.statusBarColor = black
        binding.root.setBackgroundColor(black)
        binding.dateCalcToolbar.setBackgroundColor(black)
        binding.dateCalcToolbar.setTitleTextColor(white)
        
        val textViews = listOf(
            binding.tvDiffResult, 
            binding.tvAddSubResult,
            binding.rbAdd,
            binding.rbSubtract,
            binding.etValue
        )
        
        // Note: Simple text color setting might not cover all needed styling (like Spinner text)
        // but we'll set the basics.
    }
    private fun updateToggleButtonColors(checkedId: Int) {
        val black = android.graphics.Color.BLACK
        val white = android.graphics.Color.WHITE

        binding.btnModeDiff.setTextColor(if (checkedId == R.id.btn_mode_diff) black else white)
        binding.btnModeAddSub.setTextColor(if (checkedId == R.id.btn_mode_add_sub) black else white)
        binding.btnCalculateNewDate.setTextColor(if (checkedId == R.id.btn_mode_add_sub) black else white) // Assuming this only appears with Add/Sub
    }
    
    override fun onResume() {
        super.onResume()
        updateTheme()
        updateToggleButtonColors(binding.modeToggleGroup.checkedButtonId) // Ensure colors are correct on resume
    }
}
