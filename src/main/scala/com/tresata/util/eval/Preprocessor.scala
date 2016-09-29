package com.tresata.util.eval

trait Preprocessor {
  def apply(code: String): String
}

case class IncludePreprocessor(resolvers: Seq[Resolver]) extends Preprocessor {
  def apply(code: String): String = {
    code.lines.map{ line: String =>
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
