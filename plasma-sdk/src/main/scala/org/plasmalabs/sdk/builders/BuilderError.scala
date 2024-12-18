package org.plasmalabs.sdk.builders

/**
 * A generic error type that is returned by the builders when
 * a build is unsuccessful.
 *
 * @param message The error message
 */
abstract class BuilderError(message: String, cause: Throwable = null) extends RuntimeException(message, cause)

case class UserInputError(message: String) extends BuilderError(message, null)

case class UserInputErrors(causes: Seq[UserInputError])
    extends BuilderError(causes.mkString("User input errors: [", ", ", "]"))

case class BuilderRuntimeError(message: String, cause: Throwable = null) extends BuilderError(message, cause)
