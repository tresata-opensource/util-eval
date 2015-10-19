resolvers += "releases" at "http://server01:8080/archiva/repository/internal"

resolvers += "snapshots"  at "http://server01:8080/archiva/repository/snapshots"

addSbtPlugin("com.tresata" % "sbt-common" % "0.6.0-SNAPSHOT")
