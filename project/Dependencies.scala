import Dependencies.Versions._
import sbt._

object Dependencies {

  object Versions {
    val catsCoreVersion = "2.12.0"
    val circeVersion = "0.14.10"
    val protobufSpecsVersion = "0.1.5"
  }

  val catsSlf4j: ModuleID =
    "org.typelevel" %% "log4cats-slf4j" % "2.7.0"

  val fs2Io: ModuleID = "co.fs2" %% "fs2-io" % "3.11.0"

  val circe: Seq[ModuleID] = Seq(
    "io.circe" %% "circe-core"    % circeVersion,
    "io.circe" %% "circe-parser"  % circeVersion,
    "io.circe" %% "circe-generic" % circeVersion
  )

  val scalacheck: Seq[ModuleID] = Seq(
    "org.scalacheck"    %% "scalacheck"      % "1.18.1",
    "org.scalatestplus" %% "scalacheck-1-16" % "3.2.14.0"
  )

  val scalamock: Seq[ModuleID] = Seq(
    "org.scalamock" %% "scalamock" % "6.0.0"
  )

  val scalatest: Seq[ModuleID] = Seq(
    "org.scalatest" %% "scalatest"                     % "3.2.19",
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.6.0"
  )

  val mUnitTest: Seq[ModuleID] = Seq(
    "org.scalameta" %% "munit"                   % "1.0.3",
    "org.scalameta" %% "munit-scalacheck"        % "1.0.0",
    "org.typelevel" %% "munit-cats-effect"       % "2.0.0",
    "org.typelevel" %% "scalacheck-effect-munit" % "1.0.4"
  )

  val cats: Seq[ModuleID] = Seq(
    "org.typelevel" %% "cats-core"   % catsCoreVersion,
    "org.typelevel" %% "cats-free"   % catsCoreVersion,
    "org.typelevel" %% "cats-effect" % "3.5.7"
  )

  val protobufSpecs: Seq[ModuleID] = Seq(
    "org.plasmalabs" %% "protobuf-fs2" % protobufSpecsVersion
  )

  val sqlite: Seq[ModuleID] = Seq(
    "org.xerial" % "sqlite-jdbc" % "3.47.1.0"
  )

  val grpcNetty = "io.grpc" % "grpc-netty" % "1.69.0"

  object Crypto {

    lazy val sources: Seq[ModuleID] =
      Seq("org.bouncycastle" % "bcprov-jdk18on" % "1.79") ++
      circe ++
      cats

    lazy val tests: Seq[ModuleID] =
      (
        scalatest ++
          scalamock ++
          scalacheck
      )
        .map(_ % Test)
  }

  object PlasmaSdk {

    lazy val sources: Seq[ModuleID] = protobufSpecs :+ grpcNetty

    lazy val tests: Seq[ModuleID] =
      (
        mUnitTest ++
          scalamock
      ).map(_ % Test)
  }

  object ServiceKit {

    lazy val sources: Seq[ModuleID] = sqlite

    lazy val tests: Seq[ModuleID] = (
      mUnitTest ++ sqlite
    ).map(_ % Test)
  }

  object Quivr4s {

    lazy val sources: Seq[ModuleID] = protobufSpecs

    lazy val tests: Seq[ModuleID] = mUnitTest.map(_ % Test)
  }
}
