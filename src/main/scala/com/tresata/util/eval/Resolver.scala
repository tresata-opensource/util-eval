package com.tresata.util.eval

import java.io.{ InputStream, ByteArrayOutputStream, File, FileInputStream }

trait Resolver {
  def resolvable(path: String): Boolean

  def get(path: String): InputStream

  def getString(path: String): String = {
    val bos = new ByteArrayOutputStream
    val buffer = new Array[Byte](1024)
    val is = get(path)
    try {
      var len = is.read(buffer)
      while (len != -1) {
        bos.write(buffer, 0, len)
        len = is.read(buffer)
      }
    } finally {
      is.close()
    }
    bos.toString
  }
}

case class FilesystemResolver(root: File) extends Resolver {
  private def file(path: String): File =
    new File(root.getAbsolutePath + File.separator + path)

  def resolvable(path: String): Boolean = file(path).exists

  def get(path: String): InputStream = new FileInputStream(file(path))
}
