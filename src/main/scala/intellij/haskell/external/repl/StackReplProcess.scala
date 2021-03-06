/*
 * Copyright 2016 Rik van der Kleij
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package intellij.haskell.external.repl

import java.io._
import java.util.concurrent.LinkedBlockingDeque

import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.util.EnvironmentUtil
import intellij.haskell.HaskellNotificationGroup
import intellij.haskell.sdk.HaskellSdkType
import intellij.haskell.util._

import scala.collection.JavaConversions._
import scala.collection.mutable.{ArrayBuffer, ListBuffer}
import scala.concurrent.SyncVar
import scala.concurrent.duration._
import scala.io._
import scala.sys.process._

private[repl] abstract class StackReplProcess(val project: Project, val extraStartOptions: Seq[String] = Seq(), val beforeStartDoBuild: Boolean = false) extends ProjectComponent {

  private final val LineSeparator = '\n'

  private[this] var available = false

  private[this] val outputStream = new SyncVar[OutputStream]

  private[this] val stdOut = new LinkedBlockingDeque[String]

  private[this] val stdErr = new LinkedBlockingDeque[String]

  private final val LoadTimeout = 5.seconds
  private final val DefaultTimeout = 2.seconds

  private final val BuildTimeout = 30.minutes

  private final val EndOfOutputIndicator = "^IntellijHaskell^"

  private final val DelayBetweenReads = 10.millis

  protected def execute(command: String): Option[StackReplOutput] = {

    if (!available) {
      logWarning(s"Stack repl is not yet available. Command was: $command")
      return None
    }

    if (!outputStream.isSet) {
      logError("Can not write to Stack repl. Check if your Haskell/Stack environment is working okay")
      return None
    }

    try {
      stdOut.clear()
      stdErr.clear()

      val stdOutResult = new ArrayBuffer[String]
      val stdErrResult = new ArrayBuffer[String]

      def reachedEndOfOutput: Boolean = stdOutResult.lastOption.exists(_.startsWith(EndOfOutputIndicator))

      writeToOutputStream(command)

      val timeout = if (command.startsWith(":load")) LoadTimeout else DefaultTimeout
      val deadline = timeout.fromNow
      while (deadline.hasTimeLeft && (stdOutResult.isEmpty || !reachedEndOfOutput)) {
        stdOut.drainTo(stdOutResult)
        stdErr.drainTo(stdErrResult)

        // We have to wait...
        Thread.sleep(DelayBetweenReads.toMillis)
      }

      if (!reachedEndOfOutput) {
        logError(s"No result from Stack repl within $timeout. Command was: $command")

        None
      } else {
        stdOut.drainTo(stdOutResult)
        stdErr.drainTo(stdErrResult)

        logInfo("command: " + command)
        logInfo("stdOut: " + stdOutResult.mkString("\n"))
        logInfo("errOut: " + stdErrResult.mkString("\n"))

        Some(StackReplOutput(convertOutputToOneMessagePerLine(removePrompt(stdOutResult)), convertOutputToOneMessagePerLine(stdErrResult)))
      }
    }
    catch {
      case e: Exception =>
        logError(s"Error in communication with Stack repl: ${e.getMessage}. Check if your Haskell/Stack environment is working okay. Command was: $command")
        exit()
        None
    }
  }

  def start(): Unit = {

    def writeOutputToLogInfo(): Unit = {
      if (stdOut.nonEmpty) {
        logInfo(stdOut.mkString("\n"))
      }

      if (stdErr.nonEmpty) {
        logInfo(stdErr.mkString("\n"))
      }
    }

    if (available) {
      logError("Stack repl can not be started because it's already running or busy with starting")
      return
    }

    // TODO: Create action to rebuild project with first `stack clean full`
    if (beforeStartDoBuild) {
      val buildArguments = Seq("build", "--test", "--only-dependencies", "--haddock", "--fast")
      logInfo("Build is starting")
      logInfo(s"""Build command is `stack ${buildArguments.mkString(" ")}`""")
      StackUtil.runCommand(buildArguments, project, BuildTimeout.toMillis, captureOutputToLog = true)
      logInfo("Build is finished")
    }

    HaskellSdkType.getStackPath(project).foreach { stackPath =>
      try {
        val command = (Seq(stackPath, "repl", "--with-ghc", "intero", "--verbosity", "warn", "--fast", "--no-load", "--no-build",
          "--ghc-options", "-v1", "--terminal") ++ extraStartOptions).mkString(" ")

        logInfo(s"Stack repl will be started with command: $command")

        val process = getEnvParameters match {
          case None => Process(command, new File(project.getBasePath))
          case Some(ep) => Process(command, new File(project.getBasePath), ep.toArray: _*)
        }
        process.run(
          new ProcessIO(
            in => outputStream.put(in),
            (out: InputStream) => Source.fromInputStream(out).getLines.foreach(stdOut.add),
            (err: InputStream) => Source.fromInputStream(err).getLines.foreach(stdErr.add)
          ))

        stdOut.clear()
        stdErr.clear()

        // We have to wait...
        Thread.sleep(1000)

        writeOutputToLogInfo()

        // `enter` to pass prompt which asks for which module to load
        // See Stack 1.2 issue https://github.com/commercialhaskell/stack/issues/2603
        // TODO: Remove this because in Stack 1.2.1 fixed
        writeToOutputStream("")
        Thread.sleep(100)

        writeToOutputStream(s""":set prompt "$EndOfOutputIndicator\\n"""")

        available = true

        logInfo("Stack repl is started.")
      }
      catch {
        case e: Exception =>
          logError("Could not start Stack repl. Make sure you have set right path to Stack in settings.")
          logError(s"Error message while trying to start Stack repl: ${e.getMessage}")
          exit(forceExit = true)
      }
    }
  }

  def exit(forceExit: Boolean = false): Unit = {
    if (!forceExit && !available) {
      logWarning("Stack repl can not be stopped because it's already stopped or busy with stopping")
      return
    }

    try {
      try {
        if (outputStream.isSet) {
          writeToOutputStream(":q")
        }
        if (outputStream.isSet) {
          outputStream.take(100).close()
        }
      }
      catch {
        case e: Exception =>
          logError(s"Error while shutting down Stack repl for project ${project.getName}. Error message: ${e.getMessage}")
      }
      if (stdin != null) {
        stdin.close()
      }
      if (stdout != null) {
        stdout.close()
      }
      if (stderr != null) {
        stderr.close()
      }
    } finally {
      if (outputStream.isSet) {
        try {
          outputStream.take(100).close()
        } catch {
          case _: Exception => ()
        }
      }
      available = false
    }
    logInfo("Stack repl is stopped.")
  }

  def restart() = {
    exit()
    start()
  }

  private def logError(message: String) = {
    HaskellNotificationGroup.logError(s"[$getComponentName] $message")
  }

  private def logWarning(message: String) = {
    HaskellNotificationGroup.logWarning(s"[$getComponentName] $message")
  }

  private def logInfo(message: String) = {
    HaskellNotificationGroup.logInfo(s"[$getComponentName] $message")
  }

  private def removePrompt(output: Seq[String]): Seq[String] = {
    if (output.isEmpty) {
      output
    } else {
      output.init
    }
  }

  private def writeToOutputStream(command: String) = {
    outputStream.get.write(command.getBytes)
    outputStream.get.write(LineSeparator)
    outputStream.get.flush()
  }

  private def getEnvParameters: Option[java.util.Map[String, String]] = {
    // TODO: Try to fix https://github.com/rikvdkleij/intellij-haskell/issues/49
    //    if (OSUtil.isOSX) {
    Option(EnvironmentUtil.getEnvironmentMap)
    //    } else {
    //      None
    //    }
  }

  private def convertOutputToOneMessagePerLine(output: Seq[String]) = {
    joinIndentedLines(output.filterNot(_.isEmpty))
  }

  private def joinIndentedLines(lines: Seq[String]): Seq[String] = {
    if (lines.size == 1) {
      lines
    } else {
      try {
        lines.foldLeft(ListBuffer[StringBuilder]())((lb, s) =>
          if (s.startsWith("  ")) {
            lb.last.append(s)
            lb
          }
          else {
            lb += new StringBuilder(2, s)
          }).map(_.toString)
      } catch {
        case e: NoSuchElementException =>
          HaskellNotificationGroup.notifyBalloonWarning(s"Could not join indented lines. Probably first line started with spaces. Unexpected input was: ${lines.mkString(", ")}")
          Seq()
      }
    }
  }

  override def projectOpened(): Unit = {}

  override def projectClosed(): Unit = exit()

  override def initComponent(): Unit = {}

  override def disposeComponent(): Unit = {}
}

case class StackReplOutput(stdOutLines: Seq[String] = Seq(), stdErrLines: Seq[String] = Seq())
