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

  /** THE LANGUAGE: the one asked for (`?lang=`, kept from then on), else the one kept, else the browser's */
  private def language: String =
    val asked = Option(new dom.URLSearchParams(dom.window.location.search).get("lang")).filter(_.nonEmpty)
    asked.foreach(dom.window.localStorage.setItem(NetInPage.prefix + "lang", _))
    asked.orElse(Option(dom.window.localStorage.getItem(NetInPage.prefix + "lang")))
      .getOrElse(dom.window.navigator.language)

  /** the page around the application in the wallet's words: its language, the tag line, "About" (the landing of
   * that language), the language in use marked */
  private def dressed(words: Words): Unit =
    dom.document.documentElement.setAttribute("lang", words.code)
    Option(dom.document.getElementById("tag")).foreach(_.textContent = words.tagline)
    Option(dom.document.getElementById("about")).foreach { a =>
      a.textContent = words.about
      a.setAttribute("href", if words.code == "en" then "../" else s"../${words.code}/")
    }
    Option(dom.document.querySelector(s"header .langs a[href='?lang=${words.code}']"))
      .foreach(_.setAttribute("aria-current", "true"))

  /** A LINK THAT INVITES (`?node=<a node's address>`): the node in use from then on. Only an address of a node —
   * `https://…` or a place at this hub — is taken */
  private def invited(): Unit =
    Option(new dom.URLSearchParams(dom.window.location.search).get("node"))
      .map(_.trim.stripSuffix("/")).filter(a => a.matches("https?://[^\\s\"'<>]+") || a.matches("/n/[a-z0-9-]+"))
      .foreach { address =>
        // a place at this page's own hub is kept as a place
        val own = dom.window.location.origin
        dom.window.localStorage.setItem(NetInPage.prefix + "node",
          if NetInPage.hub.isEmpty && address.startsWith(own + "/n/") then address.stripPrefix(own) else address)
      }

  def main(args: Array[String]): Unit =
    invited()
    val words = Words.of(language)
    dressed(words)
    // okay-faces' own words (Back, Help…) in the same language
    val skin = this.skin.copy(look = this.skin.look.copy(words = okay.faces.Words.of(words.code)))
    val root = Option(dom.document.getElementById("app")).getOrElse(dom.document.body)
    val running = Async.start(Laminar.run(root, skin)(InThePage.kept(NetInPage.prefix)(ClockInPage.run(
      SignerWeb.run(DeviceInPage.run(NetInPage.run(Wallet[Browser](words).main)))))))
    running.future.failed.foreach {
      case _: Cancelled => ()
      case e => dom.console.error(e.toString)
    }
    dom.window.addEventListener("pagehide", (_: dom.Event) => running.cancel())
