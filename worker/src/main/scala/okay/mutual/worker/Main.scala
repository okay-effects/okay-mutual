package okay.mutual.worker

import okay.mutual.node.NodeApp
import okay.serverless.cloudflare.Worker

import scala.scalajs.js.annotation.JSExportTopLevel

/** THE WORKER, one line: the hub's app and its objects, handed to worker/entry.mjs */
object Main:
  @JSExportTopLevel("worker")
  val worker: Worker = Worker(NodeApp, NodeApp.actors)
