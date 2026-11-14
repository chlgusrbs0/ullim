package com.ullim.watch.ai

/** Exact YAMNet ontology matches; keep original text when no translation exists. */
object SoundLabels {
    private val korean = mapOf(
        "Speech" to "말소리",
        "Child speech, kid speaking" to "어린이 말소리",
        "Conversation" to "대화",
        "Narration, monologue" to "독백",
        "Knock" to "노크",
        "Tap" to "두드림",
        "Clapping" to "박수",
        "Vehicle horn, car horn, honking" to "차량 경적",
        "Air horn, truck horn" to "트럭 경적",
        "Train horn" to "열차 경적",
        "Siren" to "사이렌",
        "Alarm" to "알람",
        "Doorbell" to "초인종",
        "Dog" to "개 소리",
        "Bark" to "짖는 소리",
        "Silence" to "무음",
        "Noise" to "주변 소음",
        "Music" to "음악",
        "Wind" to "바람",
        "Rain" to "비",
        "Walk, footsteps" to "걷는 소리·발소리",
        "Run" to "달리기",
        "Static" to "지속 잡음",
        "White noise" to "백색 잡음",
        "Pink noise" to "핑크 잡음",
    )
    fun display(label: String): String = korean[label] ?: label
}
