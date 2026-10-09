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

        /** Varsayılan gerçek boyutlar (en x boy, cm). */
        val DEFAULT_DIMS: Map<String, Pair<Float, Float>> = mapOf(
            "person" to Pair(50f, 170f),
            "bicycle" to Pair(180f, 110f),
            "car" to Pair(440f, 150f),
            "motorcycle" to Pair(210f, 120f),
            "airplane" to Pair(2500f, 700f),
            "bus" to Pair(1100f, 300f),
            "train" to Pair(1500f, 350f),
            "truck" to Pair(900f, 300f),
            "boat" to Pair(500f, 200f),
            "traffic light" to Pair(30f, 90f),
            "fire hydrant" to Pair(30f, 70f),
            "stop sign" to Pair(75f, 75f),
            "parking meter" to Pair(30f, 130f),
            "bench" to Pair(180f, 90f),
            "bird" to Pair(25f, 20f),
            "cat" to Pair(45f, 25f),
            "dog" to Pair(80f, 50f),
            "horse" to Pair(240f, 160f),
            "sheep" to Pair(130f, 80f),
            "cow" to Pair(220f, 140f),
            "elephant" to Pair(400f, 300f),
            "bear" to Pair(180f, 120f),
            "zebra" to Pair(230f, 140f),
            "giraffe" to Pair(250f, 350f),
            "backpack" to Pair(35f, 50f),
            "umbrella" to Pair(100f, 90f),
            "handbag" to Pair(30f, 25f),
            "tie" to Pair(10f, 50f),
            "suitcase" to Pair(55f, 75f),
            "frisbee" to Pair(27f, 10f),
            "skis" to Pair(170f, 15f),
            "snowboard" to Pair(155f, 25f),
            "sports ball" to Pair(22f, 22f),
            "kite" to Pair(80f, 100f),
            "baseball bat" to Pair(7f, 85f),
            "baseball glove" to Pair(25f, 30f),
            "skateboard" to Pair(80f, 20f),
            "surfboard" to Pair(60f, 200f),
            "tennis racket" to Pair(30f, 70f),
            "bottle" to Pair(8f, 25f),
            "wine glass" to Pair(9f, 20f),
            "cup" to Pair(8f, 12f),
            "fork" to Pair(3f, 20f),
            "knife" to Pair(3f, 25f),
            "spoon" to Pair(4f, 20f),
            "bowl" to Pair(20f, 12f),
            "banana" to Pair(20f, 12f),
            "apple" to Pair(9f, 9f),
            "sandwich" to Pair(15f, 10f),
            "orange" to Pair(9f, 9f),
            "broccoli" to Pair(15f, 20f),
            "carrot" to Pair(5f, 20f),
            "hot dog" to Pair(20f, 8f),
            "pizza" to Pair(30f, 5f),
            "donut" to Pair(10f, 5f),
            "cake" to Pair(25f, 15f),
            "chair" to Pair(50f, 100f),
            "couch" to Pair(200f, 90f),
            "potted plant" to Pair(40f, 60f),
            "bed" to Pair(200f, 60f),
            "dining table" to Pair(150f, 80f),
            "toilet" to Pair(60f, 70f),
            "tv" to Pair(110f, 65f),
            "laptop" to Pair(35f, 25f),
            "mouse" to Pair(10f, 6f),
            "remote" to Pair(5f, 18f),
            "keyboard" to Pair(45f, 15f),
            "cell phone" to Pair(8f, 16f),
            "microwave" to Pair(50f, 30f),
            "oven" to Pair(60f, 60f),
            "toaster" to Pair(28f, 20f),
            "sink" to Pair(55f, 45f),
            "refrigerator" to Pair(70f, 180f),
            "book" to Pair(20f, 28f),
            "clock" to Pair(30f, 30f),
            "vase" to Pair(15f, 30f),
            "scissors" to Pair(8f, 20f),
            "teddy bear" to Pair(30f, 40f),
            "hair drier" to Pair(25f, 25f),
            "toothbrush" to Pair(3f, 20f),
            "escooter" to Pair(110f, 120f)
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
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                cb.setOnCheckedChangeListener { _, _ -> saveSelection() }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                }
                row.addView(cb)
                // "other"ın boyutu yok; diğerlerinde cm düzenleme düğmesi
                if (opt.id != "other") {
                    val dimBtn = android.widget.Button(this).apply {
                        text = dimLabel(opt.id)
                        textSize = 12f
                        isEnabled = !all
                    }
                    dimBtn.setOnClickListener { showDimDialog(opt.id, dimBtn) }
                    row.addView(
                        dimBtn,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                binding.listTargets.addView(
                    row,
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

    private fun dimLabel(id: String): String {
        val w = try { calib.dimWcm(id) } catch (_: Exception) { 0f }
        val h = try { calib.dimHcm(id) } catch (_: Exception) { 0f }
        return if (w > 0f && h > 0f) "%.0f×%.0f".format(w, h) else "cm"
    }

    private fun showDimDialog(id: String, btn: android.widget.Button) {
        try {
            val density = resources.displayMetrics.density
            val pad = (16 * density).toInt()
            fun numField(v: Float): android.widget.EditText {
                return android.widget.EditText(this).apply {
                    inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(v.toString().replace(',', '.'))
                }
            }
            val etW = numField(calib.dimWcm(id))
            val etH = numField(calib.dimHcm(id))
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad)
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                val tvW = android.widget.TextView(this@TargetActivity).apply {
                    text = getString(R.string.dim_w)
                }
                val tvH = android.widget.TextView(this@TargetActivity).apply {
                    text = getString(R.string.dim_h)
                }
                addView(tvW, lp)
                addView(etW, lp)
                addView(tvH, lp)
                addView(etH, lp)
            }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.dim_title))
                .setView(layout)
                .setPositiveButton(getString(R.string.ok)) { _, _ ->
                    try {
                        val w = etW.text.toString().trim().replace(',', '.').toFloatOrNull()
                        val h = etH.text.toString().trim().replace(',', '.').toFloatOrNull()
                        if (w != null && h != null && w > 0f && h > 0f && w < 100000f && h < 100000f) {
                            calib.setDimsCm(id, w, h)
                            btn.text = dimLabel(id)
                        } else {
                            Toast.makeText(
                                this, getString(R.string.msg_invalid), Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (_: Exception) { }
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        } catch (_: Exception) { }
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
