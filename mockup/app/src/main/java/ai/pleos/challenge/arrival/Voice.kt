package ai.pleos.challenge.arrival

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.util.Log
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInitListener
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInitListener

/**
 * On-device TTS and STT. On-device mode needs no Playground app registration or network.
 * The emulator microphone records silence, so a non-empty [demoScript] feeds recorded phrases instead:
 * each [listen] sends the next asset (16 kHz mono PCM) in order.
 */
class Voice(
    private val context: Context,
    demoScript: List<String>,
    private val onSpoken: () -> Unit,
    private val onListening: () -> Unit,
    private val onHeard: (text: String, isFinal: Boolean) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)
    private val stt = SpeechToText(context, SttMode.ON_DEVICE)
    private val demoQueue = ArrayDeque(demoScript)

    private val ttsListener = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onDone() = onSpoken()
        override fun onError(type: TtsError) = onError("음성 안내 오류: $type")
        override fun onStop() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    private val sttListener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) = onHeard(text, isFinal)
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onStartedRecognition() = Unit
        override fun onEndedRecognition() = Unit
        override fun onError(type: SttError) = onError("음성 인식 오류: $type")
        override fun onReady() = onListening()
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        // addEventListener before initialize() crashes inside the TTS SDK, so register on success.
        tts.initialize(object : TtsInitListener {
            override fun onSuccess() = tts.addEventListener(ttsListener)
            override fun onFailure(type: TtsError) = onError("음성 안내 초기화 실패: $type")
        })
        stt.addListener(sttListener)
        stt.initialize(object : SttInitListener {
            override fun onSuccess() = Unit
            override fun onFailure(type: SttError) = onError("음성 인식 초기화 실패: $type")
        })
    }

    fun speak(text: String) = tts.speak(text, true)

    /** Starts recognition; in demo mode the next scripted asset replaces the microphone. */
    fun listen() {
        stt.request()
        val asset = demoQueue.removeFirstOrNull() ?: return
        val pcm = context.assets.open(asset).use { it.readBytes() }
        pcm.toList().chunked(3200).forEach { stt.sendAudio(it.toByteArray()) }
        stt.completeAudioSend()
        Log.i("Voice", "fed demo audio $asset (${pcm.size} bytes)")
    }

    fun stopListening() = runCatching { stt.stop() }

    fun release() {
        runCatching { tts.stop() }
        runCatching { tts.release() }
        runCatching { stt.release() }
    }
}
