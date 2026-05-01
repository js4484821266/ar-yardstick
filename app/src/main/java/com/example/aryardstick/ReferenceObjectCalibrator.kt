package com.example.aryardstick

enum class ReferenceObjectType(
    val label: String,
    val widthMeters: Float,
    val heightMeters: Float
) {
    CREDIT_CARD("Credit card", 0.08560f, 0.05398f),
    A4_PAPER("A4 paper", 0.210f, 0.297f)
}

data class KnownReferenceEdge(
    val objectType: ReferenceObjectType,
    val edgeLabel: String,
    val lengthMeters: Float
) {
    val label: String = "${objectType.label} $edgeLabel"

    companion object {
        fun supportedEdges(): List<KnownReferenceEdge> = listOf(
            KnownReferenceEdge(ReferenceObjectType.CREDIT_CARD, "long edge (85.60mm)", ReferenceObjectType.CREDIT_CARD.widthMeters),
            KnownReferenceEdge(ReferenceObjectType.CREDIT_CARD, "short edge (53.98mm)", ReferenceObjectType.CREDIT_CARD.heightMeters),
            KnownReferenceEdge(ReferenceObjectType.A4_PAPER, "long edge (297mm)", ReferenceObjectType.A4_PAPER.heightMeters),
            KnownReferenceEdge(ReferenceObjectType.A4_PAPER, "short edge (210mm)", ReferenceObjectType.A4_PAPER.widthMeters)
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
        require(knownLengthMeters > 0f) { "Known reference length must be positive." }
        require(measuredLengthMeters > 0.001f) { "Measured reference edge is too small." }
        return knownLengthMeters / measuredLengthMeters
    }
}
