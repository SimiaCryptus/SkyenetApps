package com.simiacryptus.util

import java.io.File

class  CompressedTokenFile(
  file: File,
  dictionaryFile: File,
) : TokenFile(file) {
  override val indices: Iterable<Long>
    get() = (0 until tokenCount).asIterable()
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

}