package com.twitter.util

import org.scalatest.WordSpec
import scala.reflect.internal.util.Position
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.{AbstractReporter, Reporter}

class EvalTest extends WordSpec {
  "Evaluator" should {

    "apply('expression')" in {
      assert((new Eval()).apply[Int]("1 + 1") == 2)
    }

    "allow custom error reporting" when {
      class Ctx {
        val eval = new Eval() {
          @volatile var errors: Seq[(String, String)] = Nil

          override lazy val reporter: Reporter = new AbstractReporter {
            override val settings: Settings = compilerSettings
            override def displayPrompt(): Unit = ()
            override def display(pos: Position, msg: String, severity: this.type#Severity): Unit = {
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
    }
  }
}
