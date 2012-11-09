package aws.ses

import java.util.Date

import scala.concurrent.ExecutionContext.Implicits.global

import play.api.libs.ws._
import play.api.libs.ws.WS._

import aws.core._
import aws.core.Types._
import aws.core.parsers._
import aws.core.utils._

case class SESMetadata(requestId: String) extends Metadata
case class EmailResult(messageId: String)

object ContentTypes extends Enumeration {
  type ContentType = Value
  val HTML, PLAIN_TEXT, BOTH = Value
}

object Simulators {
  val SUCCESS       = "success@simulator.amazonses.com"   // The recipient’s ISP accepts your email and delivers it to the recipient’s inbox.
  val BOUNCE        = "bounce@simulator.amazonses.com"    // The recipient’s ISP rejects your email with an SMTP 550 5.1.1 response code ("Unknown User")
  val OUT_OF_OFFICE = "ooto@simulator.amazonses.com"      // The recipient’s ISP accepts your email and delivers it to the recipient’s inbox. The ISP sends an out-of-the-office (OOTO) message to Amazon SES.
  val COMPLAINT     = "complaint@simulator.amazonses.com" // The recipient’s ISP accepts your email and delivers it to the recipient’s inbox. The recipient, however, does not want to receive your message and clicks "Mark as Spam" within an email application that uses an ISP that sends a complaint response to Amazon SES.
  val BLACKLIST     = "blacklist@simulator.amazonses.com" // Your attempt to send the email fails with a MessageRejected exception that contains an “Address Blacklisted” error message.
}

sealed trait Destination {
  val name: String
  val address: String
}
case class To(address: String) extends Destination {
  override val name = "To"
}
case class CC(address: String) extends Destination {
  override val name = "Cc"
}
case class BCC(address: String) extends Destination {
  override val name = "Bcc"
}

case class Email(subject: String, body: String, contentType: ContentTypes.ContentType, source: String, destinations: Seq[Destination], replyTo: Seq[String] = Nil, returnPath: Option[String] = None)

object SES {

  object Parameters {
    def Date(d: Date) = ("Date" -> AWS.httpDateFormat(d))
    def X_AMZN_AUTHORIZATION(accessKeyId: String, algorithm: String, signature: String) =
      ("X-Amzn-Authorization" -> s"AWS3-HTTPS AWSAccessKeyId=$accessKeyId,Algorithm=$algorithm,Signature=$signature")
  }

  private def tryParse[T](resp: Response)(implicit p: Parser[Result[SESMetadata, T]]) =
    Parser.parse[Result[SESMetadata, T]](resp).fold(e => throw new RuntimeException(e), identity)

  private def request[T](action: String, params: Seq[(String, String)])(implicit region: SESRegion, p: Parser[Result[SESMetadata, T]]) = {
    val date = Parameters.Date(new Date)

    val signature = Crypto.base64(Crypto.hmacSHA1(date._2.getBytes(), AWS.secret))
    val allHeaders = Seq(
      date,
      Parameters.X_AMZN_AUTHORIZATION(AWS.key, "HmacSHA1", signature))

    val ps = (params :+ AWS.Parameters.Action(action))
      .toMap.mapValues(Seq(_))

    WS.url(s"https://email.${region.subdomain}.amazonaws.com")
      .withHeaders(allHeaders: _*)
      .post(ps)
      .map(tryParse[T])
  }


  import aws.ses.SESParsers._

  def send(email: Email)(implicit region: SESRegion) = {
    val ps = Seq(
      "Source" -> email.source,
      "Message.Subject.Data" -> email.subject,
      "Message.Body.Text.Data" -> email.body
    ) ++
    email.destinations.groupBy(_.name).flatMap{ case (_, ds) =>
      ds.zipWithIndex.map { case (d, i) =>
        s"Destination.${d.name}Addresses.member.${i+1}" -> d.address
      }
    } ++
    email.replyTo.zipWithIndex.map{ case (r, i) =>
      s"ReplyToAddresses.member.${i+1}" -> r
    } ++
    email.returnPath.toSeq.map("ReturnPath" -> _)

    request[EmailResult]("SendEmail", ps)
  }

  def sendRaw(rawMessage: String)(implicit region: SESRegion) = {
    request[EmailResult]("SendRawEmail", Seq(
      "RawMessage.Data" -> Crypto.base64(rawMessage.getBytes)
    ))
  }
}
