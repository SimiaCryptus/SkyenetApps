package com.simiacryptus.util

import java.io.File

class  CompressedTokenFile(
  file: File,
  dictionaryFile: File,
) : TokenFile(file) {
  override val tokenCount: Long by lazy { file.length() / 4 }
  val dict = SequenceFile(dictionaryFile)
  val data = IntArrayFile(file)
  val codec by lazy { dict.read().map { String(it) } }

  override fun tokenIterator(position: Long): () -> Iterator<String> = {
    object : Iterator<String> {
      var nextPos = position
      override fun hasNext() = true
      override fun next(): String {
        val get: Int = data.get((nextPos++ % data.length).toInt())
        return codec[get]
      }
    }
  }

  override fun charIterator(position: Long): () -> CharIterator {
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
        } ?: throw IllegalStateException()
      }
    }
  }

}