/** okay-mutual: a mutual credit ledger on effects — accounts, limits, transfers, vouching, the network's measures
 * (specs/mutual.md) */
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / organization := "io.github.okay-effects"
ThisBuild / homepage := Some(url("https://github.com/okay-effects/okay-mutual"))
ThisBuild / version := "0.1.0-SNAPSHOT"
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

val okayServerless = "0.2.0"
val munit = Def.setting("org.scalameta" %%% "munit" % "1.2.3" % Test)

/** THE LEDGER: the model and its rules, the effect `Ledger`, its handler onto okay-serverless' durable state, the
 * calls over HTTP, the object that keeps it */
lazy val mutual = crossProject(JVMPlatform, JSPlatform).crossType(CrossType.Pure).in(file("mutual"))
  .settings(name := "okay-mutual",
    libraryDependencies ++= Seq("io.github.okay-effects" %%% "okay-serverless" % okayServerless, munit.value))

lazy val root = (project in file(".")).aggregate(mutual.jvm, mutual.js).settings(publish / skip := true)
