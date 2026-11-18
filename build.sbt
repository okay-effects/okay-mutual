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
  "okay-serverless" at "https://okay-effects.github.io/okay-serverless/maven")
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

lazy val root = (project in file(".")).aggregate(mutual.jvm, mutual.js, chain.jvm, chain.js, node.jvm, node.js)
  .settings(publish / skip := true)
