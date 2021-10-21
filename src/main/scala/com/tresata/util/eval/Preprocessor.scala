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

trait Preprocessor {
  def apply(code: String): String
}

case class IncludePreprocessor(resolvers: Seq[Resolver]) extends Preprocessor {
  def apply(code: String): String = {
    code.linesIterator.map{ line: String =>
      line.trim.split(" +") match {
        case Array("#include", path) =>
          resolvers.find{ resolver: Resolver => resolver.resolvable(path) } match {
            case Some(r: Resolver) =>
              apply(r.getString(path))
            case None =>
              sys.error(s"no resolver could find ${path}")
          }
        case _ => line
      }
    }.mkString("\n")
  }
}
