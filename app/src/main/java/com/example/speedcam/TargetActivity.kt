package com.example.speedcam

import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.speedcam.databinding.ActivityTargetBinding

/**
 * Hedef nesne seçimi: yalnız seçililer ölçülür. Tümü açıkken her şey.
 * Tek sınıflı seçenekler o dildeki sınıf adıyla, E-Scooter özel adla görünür.
 */
class TargetActivity : AppCompatActivity() {

    data class Opt(
        val id: String,
        val coco: Set<String>,
        val nameRes: Int? = null
    )

    companion object {
        val OPTIONS = listOf(
            Opt("car", setOf("car")),
            Opt("motorcycle", setOf("motorcycle")),
            Opt("escooter", setOf("motorcycle"), R.string.target_escooter),
            Opt("bicycle", setOf("bicycle")),
            Opt("truck", setOf("truck")),
            Opt("bus", setOf("bus")),
            Opt("person", setOf("person")),
            Opt("sports ball", setOf("sports ball")),
            Opt("frisbee", setOf("frisbee")),
            Opt("skateboard", setOf("skateboard")),
            Opt("skis", setOf("skis")),
            Opt("snowboard", setOf("snowboard")),
            Opt("dog", setOf("dog")),
            Opt("cat", setOf("cat")),
            Opt("horse", setOf("horse")),
            Opt("train", setOf("train")),
            Opt("boat", setOf("boat")),
            Opt("surfboard", setOf("surfboard")),
            Opt("airplane", setOf("airplane"))
        )
    }

    private lateinit var binding: ActivityTargetBinding
    private lateinit var calib: CalibrationManager
    private val boxes = mutableListOf<CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTargetBinding.inflate(layoutInflater)
        setContentView(binding.root)
        calib = CalibrationManager(this)

        binding.swAll.text = getString(R.string.target_all)
        binding.swAll.isChecked = calib.targetAll
        binding.tvTargetsNote.text = getString(R.string.targets_note)

        rebuildList(calib.targetAll)

        binding.swAll.setOnCheckedChangeListener { _, on ->
            try {
                calib.targetAll = on
                rebuildList(on)
            } catch (_: Exception) { }
        }
    }

    private fun rebuildList(all: Boolean) {
        try {
            binding.listTargets.removeAllViews()
            boxes.clear()
            val checked = calib.targetIds()
            for (opt in OPTIONS) {
                val name = if (opt.nameRes != null) {
                    try { getString(opt.nameRes) } catch (_: Exception) { opt.id }
                } else {
                    DetectionLabels.of(opt.id) ?: opt.id
                }
                val cb = CheckBox(this).apply {
                    text = name
                    isChecked = all || checked.contains(opt.id)
                    isEnabled = !all
                    textSize = 16f
                }
                cb.setOnCheckedChangeListener { _, _ -> saveSelection() }
                binding.listTargets.addView(
                    cb,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                boxes.add(cb)
            }
        } catch (_: Exception) {
            try {
                Toast.makeText(this, getString(R.string.msg_invalid), Toast.LENGTH_SHORT).show()
            } catch (_: Exception) { }
        }
    }

    private fun saveSelection() {
        try {
            val sel = HashSet<String>()
            for (i in OPTIONS.indices) {
                if (i < boxes.size && boxes[i].isChecked) sel.add(OPTIONS[i].id)
            }
            calib.setTargetIds(sel)
        } catch (_: Exception) { }
    }
}
