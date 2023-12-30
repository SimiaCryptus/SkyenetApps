package com.simiacryptus.util.index

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset

class WordTokenFile(
  file: File,
  charsetName: String = "UTF-8",
  val maxCharSize: Int = 8
) : TokenFile(file) {
  private val charset = Charset.forName(charsetName)
  override var tokenCount: Long = file.length()
  override val indices by lazy { indexArray.asIterable() }

  private val indexArray: LongArray by lazy {
    val charSeq = (0 until fileLength).runningFold(-1L to (null as String?)) { position, index ->
      val buffer = ByteArray(maxCharSize)
      read(position.first, buffer)
      val first = charset.decode(ByteBuffer.wrap(buffer)).first()
      val size = first.toString().encodeToByteArray().size
      (if (position.first < 0) size.toLong() else (position.first + size)) to first.toString()
    }.takeWhile { it.first < fileLength }
    (charSeq.zipWithNext { a, b ->
      when {
        a.second == null -> 0L
        b.second == null -> a.first
        a.second!!.isBlank() && b.second!!.isNotBlank() -> a.first
        a.second!!.isNotBlank() && b.second!!.isBlank() -> a.first
        else -> null
      }
    }.filterNotNull().zipWithNext { from, to ->
      val buffer = when {
        to < from -> ByteArray(((fileLength + to) - from).toInt())
        else -> ByteArray((to - from).toInt())
      }
      read(from, buffer)
      val string = charset.decode(ByteBuffer.wrap(buffer)).toString()
      from
    } + charSeq.last().first).toLongArray()
  }

  init {
    tokenCount = indexArray.size.toLong()
  }


  override fun readString(position: Long, n: Int, skip: Int): String {
    val prev = indices.takeWhile { it <= position }.last()
    return tokenIterator(prev).invoke().asSequence().runningFold("", { a, b -> a + b })
      .dropWhile { it.length < skip + n }.first().drop(skip + (position - prev).toInt()).take(n)
  }

  override fun tokenIterator(position: Long): () -> Iterator<String> = {
    StringIterator(position)
  }

  inner class StringIterator(
    private val position: Long
  ) : Iterator<String> {
    var nextPos =
      indices.indexOf(position).apply { if (this < 0) throw IllegalArgumentException("Position $position not found") }

    override fun hasNext() = true
    override fun next(): String {
      val from = indexArray[(nextPos++ % indexArray.size)]
      val to = indexArray[(nextPos % indexArray.size)]
      val buffer = when {
        to < from -> ByteArray(((fileLength + to) - from).toInt())
        to == from -> return ""
        else -> ByteArray((to - from).toInt())
      }
      read(from, buffer)
      val string = charset.decode(ByteBuffer.wrap(buffer)).toString()
      return string
    }
  }

}

