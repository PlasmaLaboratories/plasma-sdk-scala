package org.plasmalabs.sdk.servicekit

import cats.effect.Async
import cats.effect.kernel.Resource

import java.sql.{Connection, DriverManager}

/**
 * A resource that provides a connection to a wallet state database.
 */
trait WalletStateResource {

  /**
   * Creates a resource that provides a connection to a wallet state database.
   *
   * @param name the name of the file containing the wallet state database. It might be a path if needed.
   * @return a resource that provides a connection to a wallet state database.
   */
  def walletResource[F[_]: Async](name: String): Resource[F, Connection] = Resource
    .make(
      {
        // Without this line, repeated runs fail with "No suitable driver found for jdbc:sqlite:..."
        Class.forName("org.sqlite.JDBC")
        Async[F].delay(
          DriverManager.getConnection(
            s"jdbc:sqlite:${name}"
          )
        )
      }
    )(conn => Async[F].delay(conn.close()))
}

/**
 * A resource that provides a connection to a wallet state database.
 */

object WalletStateResource extends WalletStateResource
