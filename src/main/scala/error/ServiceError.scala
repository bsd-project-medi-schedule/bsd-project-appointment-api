package error

sealed trait ServiceError {
  def message: String
}

object ServiceError {
  case class NotFound(message: String) extends ServiceError
  case class Forbidden(message: String) extends ServiceError
  case class BadRequest(message: String) extends ServiceError
  case class InternalError(message: String) extends ServiceError
  case class Unauthorized(message: String) extends ServiceError
}