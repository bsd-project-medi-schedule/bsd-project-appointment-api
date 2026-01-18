package utils

import java.security.SecureRandom
import java.util.Base64

object NonceService {
  private val random = new SecureRandom()

  def generateNonce(lengthBytes: Int = 16): String = {
    val bytes = new Array[Byte](lengthBytes)
    random.nextBytes(bytes)
    Base64.getUrlEncoder.withoutPadding().encodeToString(bytes)
  }

  def generatePin(length: Int = 6): String =
    (0 until length).map(_ => random.nextInt(10).toString).mkString
}
