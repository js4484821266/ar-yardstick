package com.example.aryardstick

enum class ReferenceObjectType(
    val label: String,
    val widthMeters: Float,
    val heightMeters: Float
) {
    CREDIT_CARD("신용카드", 0.08560f, 0.05398f),
    A4_PAPER("A4 용지", 0.210f, 0.297f)
}

data class KnownReferenceEdge(
    val objectType: ReferenceObjectType,
    val edgeLabel: String,
    val lengthMeters: Float
) {
    val label: String = "${objectType.label} $edgeLabel"

    companion object {
        fun supportedEdges(): List<KnownReferenceEdge> = listOf(
            KnownReferenceEdge(ReferenceObjectType.CREDIT_CARD, "긴 변 (85.60mm)", ReferenceObjectType.CREDIT_CARD.widthMeters),
            KnownReferenceEdge(ReferenceObjectType.CREDIT_CARD, "짧은 변 (53.98mm)", ReferenceObjectType.CREDIT_CARD.heightMeters),
            KnownReferenceEdge(ReferenceObjectType.A4_PAPER, "긴 변 (297mm)", ReferenceObjectType.A4_PAPER.heightMeters),
            KnownReferenceEdge(ReferenceObjectType.A4_PAPER, "짧은 변 (210mm)", ReferenceObjectType.A4_PAPER.widthMeters)
        )
    }
}

data class AutomaticReferenceDetection(
    val objectType: ReferenceObjectType,
    val detectedCorners: List<WorldPoint>
)

interface ReferenceObjectDetector {
    val isAutomaticDetectionAvailable: Boolean
    fun detect(): AutomaticReferenceDetection?
}

class NoOpReferenceObjectDetector : ReferenceObjectDetector {
    override val isAutomaticDetectionAvailable: Boolean = false
    override fun detect(): AutomaticReferenceDetection? = null
}

class ManualReferenceObjectCalibrator {
    fun calculateCorrectionFactor(knownLengthMeters: Float, measuredLengthMeters: Float): Float {
        require(knownLengthMeters > 0f) { "기준 길이는 0보다 커야 합니다." }
        require(measuredLengthMeters > 0.001f) { "측정한 기준 변이 너무 짧습니다." }
        return knownLengthMeters / measuredLengthMeters
    }
}
