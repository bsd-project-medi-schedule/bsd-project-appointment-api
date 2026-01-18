package utils

import scala.util.matching.Regex

object ValidationService {

  private val EmailRegex: Regex =
    """^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$""".r

  def isValidEmail(email: String): Boolean =
    email != null && EmailRegex.matches(email)

  def isValidPassword(password: String): Boolean =
    password != null && password.length >= 8

}