lazy val root = (project in file(".")).settings(
  organization := "com.tresata",
  name := "util-eval",
  version := "1.5.0-SNAPSHOT",
  scalaVersion := "2.12.15",
  crossScalaVersions := Seq("2.12.15", "2.13.5"),
  scalacOptions += "-deprecation",
  libraryDependencies := Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "compile",
    "org.slf4j" % "slf4j-api" % "1.7.30" % "compile",
    "io.github.classgraph" % "classgraph" % "4.8.133" % "compile",
    "org.scalatest" %% "scalatest-wordspec" % "3.2.10" % "test",
    "org.slf4j" % "slf4j-log4j12" % "1.7.30" % "test"
  ),
  licenses += "Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt"),
  publishMavenStyle := true,
  pomIncludeRepository := { x => false },
  Test / publishArtifact := false,
  publishTo := {
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("tresata-snapshots" at "https://server02.tresata.com:8084/artifactory/oss-libs-snapshot-local")
    else
      Some("tresata-releases"  at "https://server02.tresata.com:8084/artifactory/oss-libs-release-local")
  },
  credentials += Credentials(Path.userHome / ".m2" / "credentials_artifactory")
)
