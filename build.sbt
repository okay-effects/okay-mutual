/** okay-mutual: a mutual credit ledger on effects — accounts, limits, transfers, vouching, the network's measures
 * (specs/mutual.md) */
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / organization := "io.github.okay-effects"
ThisBuild / homepage := Some(url("https://github.com/okay-effects/okay-mutual"))
ThisBuild / version := "0.1.0"
ThisBuild / scalaVersion := "3.3.8"
ThisBuild / scalacOptions ++= Seq("-deprecation", "-feature", "-Wunused:imports,privates,locals")
ThisBuild / licenses := List("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.txt"))
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / resolvers ++= Seq("okay-scala" at "https://okay-effects.github.io/okay-scala/maven",
  "okay-simple" at "https://okay-effects.github.io/okay-simple/maven",
  "okay-faces" at "https://okay-effects.github.io/okay-faces/maven",
  "okay-http" at "https://okay-effects.github.io/okay-http/maven",
  "okay-serverless" at "https://okay-effects.github.io/okay-serverless/maven",
  "okay-ui" at "https://okay-effects.github.io/okay-ui/maven")
/** RELEASES, ON GITHUB PAGES: `sbt publish` writes the module into `target/pages-maven/` */
val pagesRepository = settingKey[File]("the Maven repository a release is written to, for GitHub Pages")
ThisBuild / pagesRepository := (ThisBuild / baseDirectory).value / "target" / "pages-maven"
ThisBuild / publishTo := Some(MavenCache("pages", (ThisBuild / pagesRepository).value))
ThisBuild / Compile / packageDoc / publishArtifact := false

// 0.1.0: what the first host (okay-mall) builds on; moved to 0.2.0 with it
val okayServerless = "0.1.0"
val munit = Def.setting("org.scalameta" %%% "munit" % "1.2.3" % Test)

/** THE LEDGER: the model and its rules, the effect `Ledger`, its handler onto okay-serverless' durable state, the
 * calls over HTTP, the object that keeps it */
lazy val mutual = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("mutual"))
  .settings(name := "okay-mutual",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-serverless" % okayServerless, munit.value))

/** THE CHAINS (specs/protocol.md, stage 1): an account's signed records, their hashes, the fold that makes the
 * balance, the checks, fork proofs; signing as the effect `Signer` — the JDK's and Web Crypto's behind it. On
 * okay-scala alone: a wallet in a page links nothing else */
lazy val chain = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("chain"))
  .settings(name := "okay-mutual-chain",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-scala" % "0.1.1",
      "io.github.okay-effects" %%% "okay-effects-json" % "0.1.1", munit.value))
  .jvmSettings(
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala-jvm",
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "scala-jvm")
  .jsSettings(
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala-js",
    Test / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "test" / "scala-js")

/** A NODE (specs/protocol.md, stage 2): an object an account — its chain, the node's signature, a payment carried to
 * its receiver or bounced —, the directory with the node's signed head, the calls a wallet and another node make. On
 * okay-serverless: a Cloudflare Worker, or one JVM process */
lazy val node = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("node")).dependsOn(chain)
  .settings(name := "okay-mutual-node",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-serverless" % "0.2.0", munit.value))

/** THE WALLET (specs/protocol.md, stage 3): a member's side and the community's office, a program of okay-ui's
 * screens; its world asked as effects — the node (`Net`), this device's key (`Device`). Its tests play it against
 * the node itself, in memory; in a page it is one line (`InTheBrowser`) */
lazy val wallet = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("wallet"))
  .dependsOn(chain, node % "test->test")
  .settings(name := "okay-mutual-wallet",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-ui" % "0.1.0",
      "io.github.okay-effects" %%% "okay-effects-time-value" % "0.1.1", munit.value))
  .jsSettings(
    Compile / unmanagedSourceDirectories += baseDirectory.value.getParentFile / "src" / "main" / "scala-js",
    scalaJSUseMainModuleInitializer := true, Compile / mainClass := Some("okay.mutual.wallet.InTheBrowser"))

/** A NODE ON ONE MACHINE (docs/run-a-node.md): the node on okay-serverless' host — one JVM process, one directory —
 * serving the site beside its calls. `sbt host/run`, or `sbt host/assembly` and `java -jar target/okay-mutual-node.jar` */
lazy val host = (project in file("host")).dependsOn(node.jvm)
  .settings(name := "okay-mutual-host", publish / skip := true,
    libraryDependencies += "io.github.okay-effects" %% "okay-serverless-host" % "0.2.0",
    Compile / mainClass := Some("okay.mutual.host.Serve"), run / fork := true,
    run / baseDirectory := (ThisBuild / baseDirectory).value,
    assembly / assemblyJarName := "okay-mutual-node.jar",
    assembly / target := (ThisBuild / baseDirectory).value / "target",
    assembly / assemblyMergeStrategy := {
      case "module-info.class" => MergeStrategy.discard
      case x if x.endsWith("/module-info.class") => MergeStrategy.discard
      case x => (assembly / assemblyMergeStrategy).value(x)
    })

/** THE SITE: `sbt site` — target/site: the pages of site/ and the wallet linked beside its page (wallet/main.js).
 * Static: any server serves it; a node's host serves it beside its calls */
val site = taskKey[File]("the site: its pages and the wallet's script")
lazy val siteTask = Def.task {
  val base = (ThisBuild / baseDirectory).value
  val out = base / "target" / "site"
  val linked = (wallet.js / Compile / fullLinkJS).value.data.publicModules.head.jsFileName
  val from = (wallet.js / Compile / fullLinkJS / scalaJSLinkerOutputDirectory).value / linked
  IO.delete(out)
  IO.copyFile(base / "site" / "icon.svg", out / "icon.svg")
  IO.copyDirectory(base / "site" / "wallet", out / "wallet")
  IO.copyFile(from, out / "wallet" / "main.js")
  // THE LANDING, a language a page: site/landing.html with the words of site/landing.<lang>.txt (`key = value` a
  // line) in its `{{key}}`s — English at the root, the others in their directories
  val template = IO.read(base / "site" / "landing.html")
  val languages = List("en" -> "EN", "pl" -> "PL", "uk" -> "UA", "ru" -> "RU")
  for ((lang, _) <- languages) {
    val root = if (lang == "en") "" else "../"
    val words = IO.readLines(base / "site" / s"landing.$lang.txt").filter(_.contains(" = ")).map { line =>
      val at = line.indexOf(" = ")
      line.take(at).trim -> line.drop(at + 3).trim
    }.toMap ++ Map("root" -> root, "wallet" -> s"${root}wallet/?lang=$lang",
      "langs" -> languages.map { case (l, label) =>
        val href = if (l == "en") (if (root.isEmpty) "./" else root) else s"$root$l/"
        val current = if (l == lang) """ aria-current="true"""" else ""
        "<a href=\"" + href + "\"" + current + ">" + label + "</a>"
      }.mkString(" "))
    val page = words.foldLeft(template) { case (t, (k, v)) => t.replace(s"{{$k}}", v) }
    require(!page.contains("{{"), s"landing.$lang.txt lacks a word: " + "\\{\\{[a-z0-9_]+\\}\\}".r.findFirstIn(page))
    IO.write(if (lang == "en") out / "index.html" else out / lang / "index.html", page)
  }
  out
}

lazy val root = (project in file("."))
  .aggregate(mutual.jvm, mutual.js, chain.jvm, chain.js, node.jvm, node.js, wallet.jvm, wallet.js, host)
  .settings(site := siteTask.value)
  .settings(publish / skip := true)
