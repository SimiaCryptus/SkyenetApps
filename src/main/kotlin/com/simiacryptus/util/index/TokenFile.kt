package com.simiacryptus.util.index

import com.simiacryptus.util.files.IntArrayAppendFile
import com.simiacryptus.util.files.IntArrayMappedFile
import com.simiacryptus.util.files.SequenceFile
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

abstract class TokenFile(val file: File) {
  abstract val indices: Iterable<Long>
  private val channel by lazy { FileChannel.open(file.toPath(), StandardOpenOption.READ) }
  protected val mappedByteBuffer by lazy { channel.map(FileChannel.MapMode.READ_ONLY, 0, fileLength) }

  val fileLength = file.length()
  abstract val tokenCount: Long

  @JvmInline
  value class TokenIndex(val index: Long)

  @JvmInline
  value class BytePosition(val position: Long)


  private class PrefixLookup(codec: Collection<String>) {
    val children by lazy {
      codec.filter { it.length > 1 }.groupBy { it.first() }.mapValues { PrefixLookup(it.value.map { it.drop(1) }) }
    }
    val matches by lazy { codec.filter { it.length == 1 }.groupBy { it.first() }.keys }
    fun find(prefix: String): List<String>? {
      val first = prefix.firstOrNull()
      return when {
        first == null -> null
        prefix.length == 1 -> exactMatches(first)
        else -> children[first]?.find(prefix.drop(1))?.map { first + it } ?: exactMatches(first)
      }
    }

    private fun exactMatches(first: Char) = if (matches.contains(first)) listOf(first.toString()) else null
  }

  fun writeCompressed(codec: List<String>): Pair<File, File> {
    val maxPrefixLength = codec.maxOfOrNull { it.length } ?: throw IllegalStateException()
    val compressedSequence = File(file.parentFile, "${file.name}.compressed")
    val indexMap = codec.mapIndexed() { index, str ->
      str to index
    }.toMap()
    val dictionary = writeDictionary(codec)
    val prefixLookup = PrefixLookup(codec)
    val arrayFile = IntArrayAppendFile(compressedSequence)
    var position = 0L
    while (position < tokenCount) {
      val string = readString(position, maxPrefixLength)
      val prefix = prefixLookup.find(string)?.firstOrNull()
      prefix ?: throw IllegalStateException("No prefix found for $string")
      val value = indexMap[prefix]
      val size = prefix.length
      arrayFile.append(value!!)
      position += size
    }
    arrayFile.close()
    return compressedSequence to dictionary
  }

  private fun writeDictionary(codec: List<String>): File {
    val dictionaryFile = File(file.parentFile, "${file.name}.dictionary")
    val sequenceFile = SequenceFile(dictionaryFile)
    val indexMap = codec.mapIndexed { index, str ->
      require(index == sequenceFile.append(str.encodeToByteArray()))
      str to index
    }.toMap()
    sequenceFile.close()
    return dictionaryFile
  }


  fun read(i: Long, buffer: ByteArray) {
    when {
      i < 0 -> read((i % fileLength) + fileLength, buffer)
      i >= fileLength -> read(i % fileLength, buffer)
      (i + buffer.size) > fileLength -> {
        val splitAt = (fileLength - i).toInt()
        mappedByteBuffer.get(i.toInt(), buffer, 0, splitAt)
        mappedByteBuffer.get(0, buffer, splitAt, buffer.size - splitAt)
      }

      else -> mappedByteBuffer.get(i.toInt(), buffer)
    }
  }

  open fun readString(position: Long, n: Int, skip: Int = 0) =
    tokenIterator(position).invoke().asSequence().runningFold("", {a,b->a+b})
      .dropWhile { it.length < skip + n }.first().drop(skip).take(n)

  open fun charIterator(position: Long): () -> CharIterator {
    return {
      object : CharIterator() {
        val iterator = tokenIterator(position).invoke()
        var current: String? = null
        var pos = 0
        override fun hasNext() = true
        override fun nextChar() : Char = when {
          current == null -> {
            current = iterator.next()
            pos = 0
            nextChar()
          }
          pos >= current!!.length -> {
            current = iterator.next()
            pos = 0
            nextChar()
          }
          else -> current!![pos++]
        }
      }
    }
  }

  open fun tokenIterator(position: Long): () -> Iterator<String> {
    val charIterator = charIterator(position)
    return {
      object : Iterator<String> {
        val iterator = charIterator.invoke()
        override fun hasNext() = iterator.hasNext()
        override fun next(): String = iterator.next().toString()
      }
    }
  }

  fun close() {
    channel.close()
  }

  fun expand(codecMap: List<String>, compressed: File?, file: File) {
    val codec = codecMap.mapIndexed { index, str ->
      index to str
    }.toMap()
    val arrayFile = IntArrayMappedFile(compressed!!)
    val writer = file.writer()
    var position = 0L
    while (position < arrayFile.length) {
      val index = arrayFile.get(position.toInt())
      val string = codec[index]!!
      //val size = string.encodeToByteArray().size
      writer.write(string)
      position += 1
    }
    writer.close()
  }
}