package jk.ut61eTool

import android.view.View
import com.jake.UT61e_decoder
import jk.ut61eTool.databinding.LogActivityBinding

class UI(val binding: LogActivityBinding) {
    var logger : DataLogger? = null

    init {
        binding.logSwitch.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                logger?.startLog()
            } else {
                logger?.stopLog()
            }
        }
    }

    fun update(ut61e: UT61e_decoder) {
        binding.dataValue.text = ut61e.toString()
        enableTextView(binding.Neg, ut61e.getValue() < 0)
        enableTextView(binding.OL, ut61e.isOL)

        if (ut61e.isFreq || ut61e.isDuty) {
            enableTextView(binding.FreqDuty, true)
            enableTextView(binding.ACDC, false)
            binding.FreqDuty.text = when {
                ut61e.isDuty -> "Duty"
                ut61e.isFreq -> "Freq."
                else -> ""
            }
        } else {
            enableTextView(binding.FreqDuty, false)
            enableTextView(binding.ACDC, true)

            binding.ACDC.text = when {
                ut61e.isDC -> "DC"
                ut61e.isAC -> "AC"
                else -> {
                    enableTextView(binding.ACDC, false)
                    ""
                }
            }
        }

        // additional UT61-C flag indicators
        enableTextView(binding.HOLD, ut61e.isHold())
        enableTextView(binding.REL, ut61e.isRel())
        enableTextView(binding.MIN, ut61e.isMin())
        enableTextView(binding.MAX, ut61e.isMax())
        enableTextView(binding.DIODE, ut61e.isDiode())
        enableTextView(binding.BEEP, ut61e.isBeep())
    }

    private fun enableTextView(v: View, enabled: Boolean) {
        v.alpha = if (enabled) 1.0f else 0.2f
    }
}