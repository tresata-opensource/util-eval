/*
 * Copyright 2010 Twitter, Inc.
 * Copyright 2016 Tresata, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// this is a fork of twitter's util-eval moved into a new package to avoid name clashes

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
