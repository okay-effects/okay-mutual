package okay.mutual.wallet

import okay.business.faces.Skin
import okay.business.laminar.Laminar
import okay.business.model.{Settings, Ui}
import okay.business.web.InThePage
import okay.mutual.chain.{Signer, SignerWeb}
import okay.scala3.*
import okay.scala3.std.*
import org.scalajs.dom
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

/**
 * THE WALLET IN A BROWSER, a phone's too: the whole application is the line below — the screens on okay-faces'
 * Laminar face in okay-ui's calm skin, what it remembers in the page's storage, the clock the browser's, signatures
 * checked by Web Crypto, this device's key Web Crypto's unextractable one, the node by `fetch`
 */
object InTheBrowser:
  type Browser[X] = (Async + Ui + Settings + Clock + Net + Device + Signer)[X]
  val skin: Skin = Skin.calm(accent = "#2f5d50", deep = "#1c2b26")

  def main(args: Array[String]): Unit =
    val root = Option(dom.document.getElementById("app")).getOrElse(dom.document.body)
    val running = Async.start(Laminar.run(root, skin)(InThePage.kept(NetInPage.prefix)(ClockInPage.run(
      SignerWeb.run(DeviceInPage.run(NetInPage.run(Wallet[Browser].main)))))))
    running.future.failed.foreach {
      case _: Cancelled => ()
      case e => dom.console.error(e.toString)
    }
    dom.window.addEventListener("pagehide", (_: dom.Event) => running.cancel())
