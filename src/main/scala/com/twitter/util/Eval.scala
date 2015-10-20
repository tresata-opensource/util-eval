/*
 * Copyright 2010 Twitter, Inc.
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

package com.twitter.util

import java.util.UUID
import java.io.File
import java.net.URLClassLoader
import scala.collection.mutable
import scala.reflect.internal.util.{ BatchSourceFile, Position }
import scala.tools.nsc.interpreter.AbstractFileClassLoader
import scala.tools.nsc.io.{ AbstractFile, VirtualDirectory }
import scala.tools.nsc.reporters.{ Reporter, AbstractReporter }
import scala.tools.nsc.{ Global, Settings }

object Eval {
  case class CompilerException(val messages: Seq[Seq[String]]) extends Exception(
    "Compiler exception " + messages.map(_.mkString("\n")).mkString("\n")
  )

  trait MessageCollector {
    def messages: Seq[Seq[String]]
  }

  private class DefaultReporter(lineOffset: Int, val settings: Settings) extends AbstractReporter with MessageCollector {
    private val messageBuffer = new mutable.ListBuffer[List[String]]

    override def messages: Seq[Seq[String]] = messageBuffer.toList

    override def display(pos: Position, message: String, severity: Severity): Unit = {
      severity.count += 1
      val severityName = severity match {
        case ERROR   => "error: "
        case WARNING => "warning: "
        case _ => ""
      }
      // the line number is not always available
      val lineMessage =
        try {
          s"line ${pos.line - lineOffset}: "
        } catch {
          case _: Throwable => ""
        }
      messageBuffer += s"${severityName}${lineMessage}${message}" ::
      (if (pos.isDefined) {
        pos.inUltimateSource(pos.source).lineContent.stripLineEnd ::
        (" " * (pos.column - 1) + "^") ::
        Nil
      } else {
        Nil
      })
    }

    override def displayPrompt: Unit = {
      // no
    }
  }

  /**
   * Dynamic scala compiler. Lots of (slow) state is created, so it may be advantageous to keep
   * around one of these and reuse it.
   */
  private class StringCompiler(lineOffset: Int, settings: Settings, reporter: Reporter) {
    val global = new Global(settings, reporter)

    /**
     * Compile scala code.
     */
    def apply(code: String) {
      // if you're looking for the performance hit, it's 1/2 this line...
      val compiler = new global.Run
      val sourceFiles = List(new BatchSourceFile("(inline)", code))
      // ...and 1/2 this line:
      compiler.compileSources(sourceFiles)

      if (reporter.hasErrors || reporter.WARNING.count > 0) {
        val msgs: Seq[Seq[String]] = reporter match {
          case collector: MessageCollector => collector.messages
          case _ => List(List(reporter.toString))
        }
        throw new CompilerException(msgs)
      }
    }
  }
}

/**
 * Evaluates a string as Scala code, and returns a result.
 *
 * If `target` is `None`, the results are compiled to memory (and are therefore ephemeral). If
 * `target` is `Some(path)`, the path must point to a directory, and classes will be saved into
 * that directory.
 *
 *
 * The flow of evaluation is:
 * - wrap code in an `apply` method in a generated class
 * - compile the class
 * - contruct an instance of that class
 */
class Eval(target: Option[File] = None) {
  import Eval._

  private lazy val compilerPath = try {
    classPathOfClass("scala.tools.nsc.Interpreter")
  } catch {
    case e: Throwable =>
      throw new RuntimeException("Unable to load Scala interpreter from classpath (scala-compiler jar is missing?)", e)
  }

  private lazy val libPath = try {
    classPathOfClass("scala.AnyVal")
  } catch {
    case e: Throwable =>
      throw new RuntimeException("Unable to load scala base object from classpath (scala-library jar is missing?)", e)
  }

  // For derived classes do customize or override the default compiler settings.
  protected lazy val compilerSettings: Settings = new EvalSettings

  // For derived classes to provide an alternate compiler message handler.
  protected lazy val reporter: Reporter = new DefaultReporter(codeWrapperLineOffset, compilerSettings)

  lazy val compilerOutputDir = target match {
    case Some(dir) => AbstractFile.getDirectory(dir)
    case None => new VirtualDirectory("(memory)", None)
  }

  // Primary encapsulation around native Scala compiler
  private[this] lazy val compiler = new StringCompiler(codeWrapperLineOffset, compilerSettings, reporter)

  /**
   * val i: Int = new Eval()("1 + 1") // => 2
   *
   * Will generate a classname of the form Evaluater__<unique>,
   * where unique is computed from the jvmID (a random number)
   * and a digest of code
   */
  def apply[T](code: String): T = {
    val id = UUID.randomUUID().toString.take(20).filter(_ != '-')
    val className = "Eval__" + id
    apply(code, className)
  }

  private def apply[T](code: String, className: String): T = {
    compiler(wrapCodeInClass(code, className))
    classLoader.loadClass(className).getConstructor().newInstance().asInstanceOf[() => Any].apply().asInstanceOf[T]
  }

  /*
   * Wraps source code in a new class with an apply method.
   * NB: If this method is changed, make sure `codeWrapperLineOffset` is correct.
   */
  private def wrapCodeInClass(code: String, className: String) =
    s"""class ${className} extends (() => Any) {
          def apply() = {
           ${code}
         }
       }"""

  /*
   * Defines the number of code lines that proceed evaluated code.
   * Used to ensure compile error messages report line numbers aligned with user's code.
   * NB: If `wrapCodeInClass(String,String)` is changed, make sure this remains correct.
   */
  private val codeWrapperLineOffset = 2

  /*
   * For a given FQ classname, trick the resource finder into telling us the containing jar.
   */
  private def classPathOfClass(className: String) = {
    val resource = className.split('.').mkString("/", "/", ".class")
    val path = getClass.getResource(resource).getPath
    if (path.indexOf("file:") >= 0) {
      val indexOfFile = path.indexOf("file:") + 5
      val indexOfSeparator = path.lastIndexOf('!')
      List(path.substring(indexOfFile, indexOfSeparator))
    } else {
      require(path.endsWith(resource))
      List(path.substring(0, path.length - resource.length + 1))
    }
  }

  /*
   * Try to guess our app's classpath.
   * This is probably fragile.
   */
  lazy val impliedClassPath: List[String] = {
    def getClassPath(cl: ClassLoader, acc: List[List[String]] = List.empty): List[List[String]] = {
      val cp = cl match {
        case urlClassLoader: URLClassLoader => urlClassLoader.getURLs.filter(_.getProtocol == "file").map(u => new File(u.toURI).getPath).toList
        case _ => Nil
      }
      cl.getParent match {
        case null => (cp :: acc).reverse
        case parent => getClassPath(parent, cp :: acc)
      }
    }

    getClassPath(getClass.getClassLoader).flatten
  }

  /*
   * Class loader for finding classes compiled by this StringCompiler.
   */
  private lazy val classLoader = new AbstractFileClassLoader(compilerOutputDir, getClass.getClassLoader)

  class EvalSettings extends Settings {
    nowarnings.value = true // warnings are exceptions, so disable
    outputDirs.setSingleOutput(compilerOutputDir)
    private val pathList = compilerPath ::: libPath
    bootclasspath.value = pathList.mkString(File.pathSeparator)
    classpath.value = (pathList ::: impliedClassPath).mkString(File.pathSeparator)
  }
}
