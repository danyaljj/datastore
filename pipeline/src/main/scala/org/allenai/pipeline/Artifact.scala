package org.allenai.pipeline

import java.io._

/** Represents data in a persistent store
  */
trait Artifact {
  def exists: Boolean
}

/** Single file
  */
trait FlatArtifact extends Artifact {
  /** Reading from a flat file gives an InputStream.
    * The client code is responsible for closing this.  This is necessary to support streaming
    */
  def read: InputStream

  /** The write interface enforces atomic writes to a FlatArtifact
    * The client code is unable to close any OutputStream, nor to keep it open after the writer function terminates
    * Furthermore, if a write operation fails (throws an Exception), the resulting Artifact must not be created
    */
  def write[T](writer: ArtifactStreamWriter => T): T

  def copyTo(other: FlatArtifact): Unit = {
    other.write { writer =>
      val is = read
      val buffer = new Array[Byte](16384)
      Iterator.continually(is.read(buffer)).takeWhile(_ != -1).foreach(n => writer.write(buffer, 0, n))
      is.close()
    }
  }
}

/** Artifact with nested structure
  */
trait StructuredArtifact extends Artifact {
  def reader: StructuredArtifactReader

  /** Like FlatArtifacts, the write interface enforces atomic writes to a StructuredArtifact
    * Client code cannot open/close an OutputStream, and a failed write should create no Artifact
    * @param writer
    * @tparam T
    * @return
    */
  def write[T](writer: StructuredArtifactWriter => T): T

  def copyTo(other: StructuredArtifact): Unit = {
    other.write { writer =>
      for ((name, is) <- reader.readAll) {
        val buffer = new Array[Byte](16384)
        writer.writeEntry(name) { entryWriter =>
          Iterator.continually(is.read(buffer)).takeWhile(_ != -1).foreach(n => entryWriter.write(buffer, 0, n))
          is.close()
        }
      }
    }
  }
}

trait StructuredArtifactReader {
  /** Read a single entry by name */
  def read(entryName: String): InputStream

  /** Read all entries in order */
  def readAll: Iterator[(String, InputStream)]
}

trait StructuredArtifactWriter {
  /** Write a single entry */
  def writeEntry[T](name: String)(writer: ArtifactStreamWriter => T): T
}

/** Class for writing that exposes a more restrictive interface than OutputStream
  * In particular, we don't want clients to close the stream
  * Also, we force character encoding to UTF-8
  * @param out
  */
class ArtifactStreamWriter(out: OutputStream) {
  def write(data: Array[Byte]): Unit = {
    out.write(data, 0, data.size)
  }

  def write(data: Array[Byte], offset: Int, size: Int): Unit = {
    out.write(data, offset, size)
  }

  def write(s: String): Unit = {
    write(asUTF8(s))
  }

  def println(s: String): Unit = {
    write(s)
    out.write('\n')
  }

  // This was lifted from java.util.zip.ZipOutputStream
  private def asUTF8(s: String): Array[Byte] = {
    val c = s.toCharArray()
    val len = c.length
    // Count the number of encoded bytes...
    var count = 0
    for (i <- (0 until len)) {
      val ch = c(i)
      if (ch <= 0x7f) {
        count += 1
      } else if (ch <= 0x7ff) {
        count += 2
      } else {
        count += 3
      }
    }
    // Now return the encoded bytes...
    val b = new Array[Byte](count);
    var off = 0;
    for (i <- (0 until len)) {
      val ch = c(i)
      if (ch <= 0x7f) {
        b(off) = ch.toByte
        off += 1
      } else if (ch <= 0x7ff) {
        b(off) = ((ch >> 6) | 0xc0).toByte
        off += 1
        b(off) = ((ch & 0x3f) | 0x80).toByte
        off += 1
      } else {
        b(off) = ((ch >> 12) | 0xe0).toByte
        off += 1
        b(off) = (((ch >> 6) & 0x3f) | 0x80).toByte
        off += 1
        b(off) = ((ch & 0x3f) | 0x80).toByte
        off += 1
      }
    }
    b
  }
}
