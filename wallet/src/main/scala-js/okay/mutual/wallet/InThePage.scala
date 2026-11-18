package okay.mutual.wallet

import okay.effects.json.{Descent, Json}
import okay.mutual.chain.B64
import okay.scala3.*
import okay.scala3.std.Async

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.scalajs.js
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}

/**
 * `Net` IN A PAGE: the browser's `fetch`. THE HUB is where the page says (`<meta name="okay-mutual-hub">`: a wallet
 * published apart from any hub names one), else the page's own origin. THE NODE IN USE is what `Settings` keeps as
 * `okay-mutual.node`: a place at the hub (`/n/<slug>`) or a whole address of a node elsewhere; none: the hub itself,
 * which is no node — the wallet then asks which. No answer at all — offline, a wrong address — is status 0
 */
object NetInPage:
  val prefix = "okay-mutual."
  def hub: String =
    val meta = js.Dynamic.global.document.querySelector("meta[name=okay-mutual-hub]")
    if meta != null then meta.content.asInstanceOf[String].stripSuffix("/") else ""
  /** the node in use, as an address to call */
  def node: String =
    val kept = js.Dynamic.global.localStorage.getItem(prefix + "node")
    val place = if kept == null then "" else kept.asInstanceOf[String]
    if place.startsWith("/") then hub + place else if place.nonEmpty then place else hub
  /** the wallet's own address with the node in use in it, whole: what is sent to one who is invited */
  def invitation: String =
    val location = js.Dynamic.global.location
    val whole = if node.startsWith("http") then node else location.origin.asInstanceOf[String] + node
    location.origin.asInstanceOf[String] + location.pathname.asInstanceOf[String] + "?node=" +
      js.Dynamic.global.encodeURIComponent(whole).asInstanceOf[String]

  private def call(url: String, method: String, body: Option[Json], token: String)(using ExecutionContext)
    : Future[(Int, Json)] =
    val headers = js.Dictionary[String]()
    if body.isDefined then headers("content-type") = "application/json"
    if token.nonEmpty then headers("x-setup-token") = token
    val init = js.Dynamic.literal(method = method, headers = headers)
    body.foreach(b => init.body = Json.print(b))
    (for
      answer <- js.Dynamic.global.fetch(url, init).asInstanceOf[js.Promise[js.Dynamic]].toFuture
      text <- answer.text().asInstanceOf[js.Promise[String]].toFuture
    yield (answer.status.asInstanceOf[Int], Descent.parse(text).toOption.getOrElse(Json.Null)))
      .recover { case _ => (0, Json.Null) }

  def run[R[_], Rest[_], A](p: A ! R)(using Split[Net, R, Rest], In[Async, Rest]): A ! Rest =
    given ExecutionContext = Async.defaultContext
    handle(p, Handler.general[Net, Rest, A, A](pure)([X] => (op: Net[X], k: X => A ! Rest) => op match
      case Net.Call(method, path, body, token) =>
        Async.fromFuture[(Int, Json), Rest](call(node + path, method, body, token)).flatMap(k)
      case Net.AtHub(method, path, body) =>
        Async.fromFuture[(Int, Json), Rest](call(hub + path, method, body, "")).flatMap(k)
      case Net.Elsewhere(address, path) => Async.fromFuture[(Int, Json), Rest](call(
          (if address.startsWith("/") then hub + address else address) + path, "GET", None, "")).flatMap(k)
      case Net.Invite() => k(invitation)))

/**
 * `Device` IN A PAGE: a P-256 key pair made by Web Crypto with its secret half UNEXTRACTABLE — no script, this one
 * included, can read it; it can only be asked to sign — and kept by the browser itself (IndexedDB keeps the key
 * object, not its bytes). Clearing the site's data loses the key: the account then needs another device, or its
 * recovery
 */
object DeviceInPage:
  private val ecdsa = js.Dynamic.literal(name = "ECDSA", namedCurve = "P-256")
  private val sha256 = js.Dynamic.literal(name = "ECDSA", hash = "SHA-256")
  private def subtle: js.Dynamic = js.Dynamic.global.crypto.subtle
  private def await[T](promise: js.Dynamic): Future[T] = promise.asInstanceOf[js.Promise[T]].toFuture

  /** an IndexedDB request as a future */
  private def asked(request: js.Dynamic): Future[js.Dynamic] =
    val done = Promise[js.Dynamic]()
    request.onsuccess = (_: js.Any) => done.trySuccess(request.result)
    request.onerror = (_: js.Any) => done.tryFailure(new RuntimeException("the browser's key store refused"))
    done.future

  private def store(mode: String)(using ExecutionContext): Future[js.Dynamic] =
    val opening = js.Dynamic.global.indexedDB.open("okay-mutual", 1)
    opening.onupgradeneeded = (_: js.Any) => opening.result.createObjectStore("keys")
    asked(opening).map(db => db.transaction("keys", mode).objectStore("keys"))

  private def pair(using ExecutionContext): Future[Option[js.Dynamic]] =
    store("readonly").flatMap(s => asked(s.get("device"))).map(p => if js.isUndefined(p) || p == null then None else
      Some(p))

  private def public(pair: js.Dynamic)(using ExecutionContext): Future[String] =
    await[js.Any](subtle.exportKey("raw", pair.publicKey)).map { raw =>
      val a = new Uint8Array(raw.asInstanceOf[ArrayBuffer])
      B64.encode(Array.tabulate(a.length)(i => a(i).toByte))
    }

  private def make(using ExecutionContext): Future[String] =
    for
      made <- await[js.Dynamic](subtle.generateKey(ecdsa, false, js.Array("sign", "verify")))
      keys <- store("readwrite")
      _ <- asked(keys.put(made, "device"))
      pub <- public(made)
    yield pub

  private def sign(text: String)(using ExecutionContext): Future[String] = pair.flatMap {
    case None => Future.failed(new RuntimeException("this device has no key"))
    case Some(p) =>
      val bytes = text.getBytes("UTF-8")
      val data = new Uint8Array(bytes.length)
      for i <- bytes.indices do data(i) = (bytes(i) & 0xff).toShort
      await[js.Any](subtle.sign(sha256, p.privateKey, data)).map { sig =>
        val a = new Uint8Array(sig.asInstanceOf[ArrayBuffer])
        B64.encode(Array.tabulate(a.length)(i => a(i).toByte))
      }
  }

  def run[R[_], Rest[_], A](p: A ! R)(using Split[Device, R, Rest], In[Async, Rest]): A ! Rest =
    given ExecutionContext = Async.defaultContext
    handle(p, Handler.general[Device, Rest, A, A](pure)([X] => (op: Device[X], k: X => A ! Rest) => op match
      case Device.Key() => Async.fromFuture[Option[String], Rest](pair.flatMap {
          case Some(p) => public(p).map(Some(_))
          case None => Future.successful(None)
        }).flatMap(k)
      case Device.Make() => Async.fromFuture[String, Rest](make).flatMap(k)
      case Device.Sign(text) => Async.fromFuture[String, Rest](sign(text)).flatMap(k)))

/** `Clock` IN A PAGE: the browser's time; a wait its timer */
object ClockInPage:
  import okay.scala3.std.Clock
  def run[R[_], Rest[_], A](p: A ! R)(using Split[Clock, R, Rest], In[Async, Rest]): A ! Rest =
    handle(p, Handler.general[Clock, Rest, A, A](pure)([X] => (op: Clock[X], k: X => A ! Rest) => op match
      case Clock.Now() => k(js.Date.now().toLong)
      case Clock.Sleep(millis) =>
        val done = Promise[Unit]()
        js.timers.setTimeout(millis.toDouble)(done.trySuccess(()))
        Async.fromFuture[Unit, Rest](done.future).flatMap(k)))
