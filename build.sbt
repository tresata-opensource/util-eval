lazy val root = (project in file(".")).settings(
  organization := "com.tresata",
  name := "util-eval",
  version := "1.1.0-SNAPSHOT",
  scalaVersion := "2.11.8",
  libraryDependencies := Seq(
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "compile",
    "org.slf4j" % "slf4j-api" % "1.7.5" % "compile",
    "org.scalatest" %% "scalatest" % "3.0.4" % "test",
    "org.slf4j" % "slf4j-log4j12" % "1.7.5" % "test"
  ),
  publishMavenStyle := true,
  pomIncludeRepository := { x => false },
  publishArtifact in Test := false,
  publishTo := {
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("tresata-snapshots" at "http://server02.tresata.com:8081/artifactory/oss-libs-snapshot-local")
    else
      Some("tresata-releases"  at "http://server02.tresata.com:8081/artifactory/oss-libs-release-local")
  },
  credentials += Credentials(Path.userHome / ".m2" / "credentials_artifactory")
)
