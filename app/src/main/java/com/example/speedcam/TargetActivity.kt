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
            Opt("airplane", setOf("airplane")),
            Opt("traffic light", setOf("traffic light")),
            Opt("fire hydrant", setOf("fire hydrant")),
            Opt("stop sign", setOf("stop sign")),
            Opt("parking meter", setOf("parking meter")),
            Opt("bench", setOf("bench")),
            Opt("bird", setOf("bird")),
            Opt("sheep", setOf("sheep")),
            Opt("cow", setOf("cow")),
            Opt("elephant", setOf("elephant")),
            Opt("bear", setOf("bear")),
            Opt("zebra", setOf("zebra")),
            Opt("giraffe", setOf("giraffe")),
            Opt("backpack", setOf("backpack")),
            Opt("umbrella", setOf("umbrella")),
            Opt("handbag", setOf("handbag")),
            Opt("tie", setOf("tie")),
            Opt("suitcase", setOf("suitcase")),
            Opt("kite", setOf("kite")),
            Opt("baseball bat", setOf("baseball bat")),
            Opt("baseball glove", setOf("baseball glove")),
            Opt("tennis racket", setOf("tennis racket")),
            Opt("bottle", setOf("bottle")),
            Opt("wine glass", setOf("wine glass")),
            Opt("cup", setOf("cup")),
            Opt("fork", setOf("fork")),
            Opt("knife", setOf("knife")),
            Opt("spoon", setOf("spoon")),
            Opt("bowl", setOf("bowl")),
            Opt("banana", setOf("banana")),
            Opt("apple", setOf("apple")),
            Opt("sandwich", setOf("sandwich")),
            Opt("orange", setOf("orange")),
            Opt("broccoli", setOf("broccoli")),
            Opt("carrot", setOf("carrot")),
            Opt("hot dog", setOf("hot dog")),
            Opt("pizza", setOf("pizza")),
            Opt("donut", setOf("donut")),
            Opt("cake", setOf("cake")),
            Opt("chair", setOf("chair")),
            Opt("couch", setOf("couch")),
            Opt("potted plant", setOf("potted plant")),
            Opt("bed", setOf("bed")),
            Opt("dining table", setOf("dining table")),
            Opt("toilet", setOf("toilet")),
            Opt("tv", setOf("tv")),
            Opt("laptop", setOf("laptop")),
            Opt("mouse", setOf("mouse")),
            Opt("remote", setOf("remote")),
            Opt("keyboard", setOf("keyboard")),
            Opt("cell phone", setOf("cell phone")),
            Opt("microwave", setOf("microwave")),
            Opt("oven", setOf("oven")),
            Opt("toaster", setOf("toaster")),
            Opt("sink", setOf("sink")),
            Opt("refrigerator", setOf("refrigerator")),
            Opt("book", setOf("book")),
            Opt("clock", setOf("clock")),
            Opt("vase", setOf("vase")),
            Opt("scissors", setOf("scissors")),
            Opt("teddy bear", setOf("teddy bear")),
            Opt("hair drier", setOf("hair drier")),
            Opt("toothbrush", setOf("toothbrush")),
            Opt("other", emptySet(), R.string.target_other)
        )

        /** Bilinen tüm COCO anahtarları (diğer-filtre için). */
        val ALL_COCO: Set<String> by lazy {
            val s = HashSet<String>()
            for (o in OPTIONS) s.addAll(o.coco)
            s
        }
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
