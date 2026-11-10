package pt.comandoprojetor.net

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Codificador/descodificador protobuf mínimo — chega para as poucas mensagens
 * do protocolo Android TV Remote v2, sem precisar do plugin protoc.
 */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    private fun varint(value: Long) {
        var x = value
        while (true) {
            if ((x and 0x7FL.inv()) == 0L) {
                out.write(x.toInt())
                return
            }
            out.write(((x and 0x7F) or 0x80).toInt())
            x = x ushr 7
        }
    }

    private fun tag(field: Int, wireType: Int) = varint(((field shl 3) or wireType).toLong())

    fun int(field: Int, value: Int): ProtoWriter {
        tag(field, 0)
        varint(value.toLong())
        return this
    }

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        tag(field, 2)
        varint(value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray(Charsets.UTF_8))

    fun message(field: Int, value: ProtoWriter) = bytes(field, value.toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()
}

class ProtoMessage private constructor(private val fields: Map<Int, List<Any>>) {

    fun has(field: Int) = fields.containsKey(field)

    fun int(field: Int): Int? = (fields[field]?.firstOrNull() as? Long)?.toInt()

    fun bytes(field: Int): ByteArray? = fields[field]?.firstOrNull() as? ByteArray

    fun message(field: Int): ProtoMessage? = bytes(field)?.let { parse(it) }

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val map = HashMap<Int, MutableList<Any>>()
            var i = 0

            fun varint(): Long {
                var result = 0L
                var shift = 0
                while (true) {
                    if (i >= data.size) throw IOException("Mensagem truncada")
                    val b = data[i++].toInt() and 0xFF
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if ((b and 0x80) == 0) return result
                    shift += 7
                }
            }

            while (i < data.size) {
                val key = varint()
                val field = (key ushr 3).toInt()
                when ((key and 7).toInt()) {
                    0 -> map.getOrPut(field) { mutableListOf() }.add(varint())
                    1 -> i += 8
                    2 -> {
                        val n = varint().toInt()
                        if (n < 0 || i + n > data.size) throw IOException("Mensagem truncada")
                        map.getOrPut(field) { mutableListOf() }.add(data.copyOfRange(i, i + n))
                        i += n
                    }
                    5 -> i += 4
                    else -> throw IOException("Tipo de campo inválido")
                }
            }
            return ProtoMessage(map)
        }
    }
}

/** Cada mensagem no fio vai precedida do seu tamanho em varint. */
object Framing {
    fun write(out: OutputStream, payload: ByteArray) {
        val buf = ByteArrayOutputStream(payload.size + 5)
        var n = payload.size
        while (n >= 0x80) {
            buf.write((n and 0x7F) or 0x80)
            n = n ushr 7
        }
        buf.write(n)
        buf.write(payload)
        out.write(buf.toByteArray())
        out.flush()
    }

    fun read(input: InputStream): ByteArray {
        var len = 0
        var shift = 0
        while (true) {
            val b = input.read()
            if (b < 0) throw EOFException("Ligação fechada")
            len = len or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift > 28) throw IOException("Tamanho de mensagem inválido")
        }
        val buf = ByteArray(len)
        var off = 0
        while (off < len) {
            val r = input.read(buf, off, len - off)
            if (r < 0) throw EOFException("Ligação fechada")
            off += r
        }
        return buf
    }
}
