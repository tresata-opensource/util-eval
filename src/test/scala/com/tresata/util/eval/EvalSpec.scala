package com.tresata.util.eval

import java.io.File
import org.scalatest.wordspec.AnyWordSpec
import scala.reflect.internal.util.Position
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.{FilteringReporter, Reporter}

@deprecated("dont use this anymore", "long time ago")
object test {
  val x = 2
}

class EvalSpec extends AnyWordSpec {
  "Evaluator" should {

    "apply('expression')" in {
      assert((new Eval()).apply[Int]("1 + 1") == 2)
    }

    "emit deprecation warnings" in {
      assert((new Eval()).apply[Int]("com.tresata.util.eval.test.x + 1") == 3)
    }

    "allow custom error reporting" when {
      class Ctx {
        val eval = new Eval() {
          @volatile var errors: Seq[(String, String)] = Nil

          override lazy val reporter: Reporter = new FilteringReporter {
            override val settings: Settings = compilerSettings
            override def doReport(pos: Position, msg: String, severity: this.type#Severity): Unit = {
              errors = errors :+ ((msg, severity.toString))
            }
          }
        }
      }

      "not report errors on success" in {
        val ctx = new Ctx
        import ctx._

        assert(eval[Int]("val a = 3; val b = 2; a + b") == 5)
        assert(eval.errors.isEmpty)
      }

      "report errors on bad code" in {
        val ctx = new Ctx
        import ctx._

        intercept[Throwable] {
          eval[Int]("val a = 3; val b = q; a + b")
        }
        assert(eval.errors.nonEmpty)
      }

      "support include statements" in {
        assert((new Eval(preprocessors = Seq(IncludePreprocessor(Seq(FilesystemResolver(new File("test/data"))))))).apply[Int]("""
          #include test.scala
          x + 1
        """) == 2)
      }
    }
  }
}
