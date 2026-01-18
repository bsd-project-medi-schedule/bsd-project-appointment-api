package utils

import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64
import javax.crypto.Cipher

object RsaService {
  def encryptWithPemPublicKey(pem: String, plainText: String): String = {
    val publicKey = parsePublicKey(pem)
    encryptWithPublicKey(publicKey, plainText)
  }

  private def parsePublicKey(pem: String): PublicKey = {
    val cleanPem = pem
      .replace("-----BEGIN PUBLIC KEY-----", "")
      .replace("-----END PUBLIC KEY-----", "")
      .replaceAll("\\s", "")

    val decoded = Base64.getDecoder.decode(cleanPem)
    val keySpec = new X509EncodedKeySpec(decoded)
    val keyFactory = KeyFactory.getInstance("RSA")
    keyFactory.generatePublic(keySpec)
  }

  private def encryptWithPublicKey(publicKey: PublicKey, plainText: String): String = {
    val cipher = Cipher.getInstance("RSA")
    cipher.init(Cipher.ENCRYPT_MODE, publicKey)
    val encryptedBytes = cipher.doFinal(plainText.getBytes("UTF-8"))
    Base64.getEncoder.encodeToString(encryptedBytes)
  }
}
