
Seq(
  "com.eed3si9n"            % "sbt-assembly"              % "2.3.0",
  "org.scoverage"           % "sbt-scoverage"             % "2.2.2",
  "com.github.sbt"          % "sbt-release"               % "1.4.0",
  "org.scalameta"           % "sbt-scalafmt"              % "2.5.2",
  "ch.epfl.scala"           % "sbt-scalafix"              % "0.13.0",
  "com.eed3si9n"            % "sbt-buildinfo"             % "0.13.1",
  "com.github.sbt"          % "sbt-ci-release"            % "1.9.0",
  "net.bzzt"                % "sbt-reproducible-builds"   % "0.32",
  "com.github.sbt"          % "sbt-unidoc"                % "0.5.0",
  "ch.epfl.scala"           % "sbt-bloop"                 % "2.0.5"
).map(addSbtPlugin)
