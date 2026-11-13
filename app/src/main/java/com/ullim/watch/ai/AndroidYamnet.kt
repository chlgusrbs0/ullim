package com.ullim.watch.ai

import android.content.Context
import com.ullim.watch.core.SoundClassifier
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

object AndroidYamnet {
    private const val MODEL_SHA256 = "10c95ea3eb9a7bb4cb8bddf6feb023250381008177ac162ce169694d05c317de"
    private const val LABEL_SHA256 = "8e1267a120c1932b7273c0d0e0c5529edbb9a35512b437b1c8982baa59047051"

    /** Call on the AI worker; this loads native code and allocates model tensors. */
    fun load(context: Context): SoundClassifier {
        val labelsBytes = context.assets.open("yamnet-labels.txt").use { it.readBytes() }
        require(digest(ByteBuffer.wrap(labelsBytes)) == LABEL_SHA256) { "YAMNet labels changed" }
        val labels = labelsBytes.toString(Charsets.UTF_8).lineSequence().filter { it.isNotEmpty() }.toList()
        val model = context.assets.openFd("yamnet.tflite").use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).use { file ->
                file.channel.map(FileChannel.MapMode.READ_ONLY, descriptor.startOffset, descriptor.declaredLength)
            }
        }
        require(digest(model.asReadOnlyBuffer()) == MODEL_SHA256) { "YAMNet model changed" }
        val interpreter = Interpreter(model, Interpreter.Options().setNumThreads(1))
        try {
            require(interpreter.inputTensorCount == 1 && interpreter.outputTensorCount == 1)
            require(interpreter.getInputTensor(0).dataType() == DataType.FLOAT32)
            require(interpreter.getInputTensor(0).shape().contentEquals(intArrayOf(15600)))
            require(interpreter.getOutputTensor(0).dataType() == DataType.FLOAT32)
            require(interpreter.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 521)))
            return YamnetClassifier(labels, InterpreterEngine(interpreter))
        } catch (failure: Throwable) {
            interpreter.close()
            throw failure
        }
    }

    private fun digest(bytes: ByteBuffer): String {
        val hash = MessageDigest.getInstance("SHA-256")
        hash.update(bytes)
        return hash.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private class InterpreterEngine(private val interpreter: Interpreter) : YamnetEngine {
        private val input = ByteBuffer.allocateDirect(15600 * 4).order(ByteOrder.nativeOrder())
        private val output = arrayOf(FloatArray(521))
        override fun infer(samples: FloatArray): FloatArray {
            input.rewind()
            for (sample in samples) input.putFloat(sample)
            input.rewind()
            try { interpreter.run(input, output) } finally {
                input.rewind()
                repeat(15600) { input.putFloat(0f) }
                input.rewind()
            }
            return output[0]
        }
        override fun close() { output[0].fill(0f); interpreter.close() }
    }
}
