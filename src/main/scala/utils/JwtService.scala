package utils

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.{MACSigner, MACVerifier}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import config.objects.AuthConfig

import java.util.{Date, UUID}
import scala.util.Try

final case class JwtService()(implicit authConfig: AuthConfig) {

  private val signer: JWSSigner = new MACSigner(authConfig.secretKey.getBytes)
  private val verifier: JWSVerifier = new MACVerifier(authConfig.secretKey.getBytes)

  def createToken(subject: UUID, role: Int, firstName: String, expirationMinutes: Int = 15): String = {
    val now = new Date()
    val expirationTime = new Date(now.getTime + expirationMinutes * 60 * 1000)

    val claimsSet = new JWTClaimsSet.Builder()
      .subject(subject.toString)
      .issuer(authConfig.issuer)
      .claim("role", role.toString)
      .claim("firstName", firstName)
      .issueTime(now)
      .expirationTime(expirationTime)
      .build()

    val signedJWT = new SignedJWT(
      new JWSHeader(JWSAlgorithm.HS256),
      claimsSet
    )

    signedJWT.sign(signer)
    signedJWT.serialize()
  }

  def verifyToken(tokenStr: String): Option[(JWTClaimsSet, Boolean)] = {
    Try {
      val signedJWT = SignedJWT.parse(tokenStr)

      val signatureValid = signedJWT.verify(verifier)
      val claims = signedJWT.getJWTClaimsSet
      val isExpired = claims.getExpirationTime.before(new Date())
      val correctIssuer = claims.getIssuer == authConfig.issuer

      if (signatureValid && correctIssuer) Some(claims, isExpired) else None
    }.toOption.flatten
  }
}