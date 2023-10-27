package com.aac.svs

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.*

class TextToSpeechManager(context: Context) {

    private var textToSpeech: TextToSpeech? = null

    init {
        textToSpeech = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                println("TTS_INIT_SUCCESS")
                //ver idiomas disponibles
                val locales = textToSpeech?.availableLanguages
                locales?.forEach {
                    //println("Idioma: ${it.language} - ${it.country}")
                }
            } else {
                println("Error TTS_INIT_FAILED")
            }
        }
    }

    fun isLanguageAvailable(locale: Locale): Boolean {
        return textToSpeech?.isLanguageAvailable(locale) == TextToSpeech.LANG_AVAILABLE
    }

    fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    fun setLanguage(locale: Locale) {
        textToSpeech?.language = locale
    }
}