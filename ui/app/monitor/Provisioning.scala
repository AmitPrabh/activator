/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */

package monitor

import akka.actor.ActorRef
import java.io.File
import play.api.libs.ws.ssl.{ DefaultSSLLooseConfig, DefaultSSLConfig, DefaultSSLConfigParser }
import snap.HttpHelper.ProgressObserver

import scala.concurrent.{ ExecutionContext, Future }
import play.api.libs.ws._
import snap.{ JsonHelper, FileHelper }
import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import play.api.libs.functional.syntax._
import play.api.libs.json._
import play.api.libs.json.Json._
import JsonHelper._
import play.api.Play.current
import play.api.libs.ws.ning._
import com.ning.http.client.AsyncHttpClientConfig

object Provisioning {
  import snap.HttpHelper._
  val responseTag = "ProvisioningStatus"

  trait StatusNotifier {
    def notify(status: Status): Unit
  }

  def actorWrapper(sink: ActorRef): StatusNotifier = new StatusNotifier {
    def notify(status: Status): Unit = sink ! status
  }

  case class AuthenticationException(message: String, failureDiagnostics: String, url: String) extends Exception(message)

  trait DownloadPrepExecutor {
    def execute(): Future[DownloadExecutor]
    def failureDiagnostics: String
  }

  trait DownloadExecutor {
    def downloadUrl: String
    def execute(): Future[File]
    def failureDiagnostics: String
  }

  sealed trait Status
  case class ProvisioningError(message: String, exception: Throwable) extends Status
  case class Authenticating(diagnostics: String, url: String) extends Status
  case class Downloading(url: String) extends Status
  case class Progress(value: Either[Int, Double]) extends Status
  case class DownloadComplete(url: String) extends Status
  case object Validating extends Status
  case object Extracting extends Status
  case object Complete extends Status

  // Used to inhibit double notification of errors to the sink
  case class DownloadException(cause: Throwable) extends Exception

  implicit val provisioningErrorWrites: Writes[ProvisioningError] =
    emitResponse(responseTag)(in => Json.obj("type" -> "provisioningError",
      "message" -> in.message))

  implicit val authenticatingWrites: Writes[Authenticating] =
    emitResponse(responseTag)(in => Json.obj("type" -> "authenticating",
      "diagnostics" -> in.diagnostics,
      "url" -> in.url))

  implicit val downloadingWrites: Writes[Downloading] =
    emitResponse(responseTag)(in => Json.obj("type" -> "downloading",
      "url" -> in.url))

  implicit val progressWrites: Writes[Progress] =
    emitResponse(responseTag)(in => Json.obj("type" -> "progress",
      in.value match {
        case Left(b) => "bytes" -> b
        case Right(p) => "percent" -> p
      }))

  implicit val downloadCompleteWrites: Writes[DownloadComplete] =
    emitResponse(responseTag)(in => Json.obj("type" -> "downloadComplete",
      "url" -> in.url))

  implicit val validatingWrites: Writes[Validating.type] =
    emitResponse(responseTag)(_ => Json.obj("type" -> "validating"))

  implicit val extractingWrites: Writes[Extracting.type] =
    emitResponse(responseTag)(_ => Json.obj("type" -> "extracting"))

  implicit val completeWrites: Writes[Complete.type] =
    emitResponse(responseTag)(_ => Json.obj("type" -> "complete"))

  def notificationProgressBuilder(url: String,
    notificationSink: StatusNotifier): ProgressObserver = new ProgressObserver {
    def onCompleted(): Unit =
      notificationSink.notify(DownloadComplete(url))

    def onError(error: Throwable): Unit =
      notificationSink.notify(ProvisioningError(s"Error downloading $url: ${error.getMessage}", error))

    def onNext(data: ChunkData): Unit = {
      data.contentLength match {
        case None =>
          notificationSink.notify(Progress(Left(data.total)))
        case Some(cl) =>
          notificationSink.notify(Progress(Right((data.total.toDouble / cl.toDouble) * 100.0)))
      }
    }
  }

  lazy val defaultWSClient: WSClient = {
    val configParser = new DefaultWSConfigParser(current.configuration, current.classloader)
    val sslConfigParser = new DefaultSSLConfigParser(current.configuration, current.classloader)
    val initialBuilder = new AsyncHttpClientConfig.Builder()
      .setMaximumNumberOfRedirects(50)
    val builder = new NingAsyncHttpClientConfigBuilder(configParser.parse().asInstanceOf[DefaultWSClientConfig].copy(acceptAnyCertificate = Some(true)), initialBuilder)
    builder.configureSSL(sslConfigParser.parse().asInstanceOf[DefaultSSLConfig].copy(loose = Some(DefaultSSLLooseConfig(
      allowWeakCiphers = Some(true),
      allowWeakProtocols = Some(true),
      allowLegacyHelloMessages = Some(true),
      allowUnsafeRenegotiation = Some(true),
      disableHostnameVerification = Some(true)))))
    new NingWSClient(builder.build())
  }

  def simpleDownloadExecutor(client: WSClient,
    downloadUrl: String,
    notificationSink: StatusNotifier,
    timeout: Timeout): DownloadExecutor = {
    val dl = downloadUrl
    new DownloadExecutor {
      def downloadUrl: String = dl
      def execute(): Future[File] =
        retrieveFileHttp(client.url(downloadUrl).withFollowRedirects(true),
          notificationProgressBuilder(downloadUrl, notificationSink),
          timeout = timeout)

      def failureDiagnostics: String = s"Download url: $downloadUrl"
    }
  }

  private def postprocessResults(expected: Future[File],
    validator: File => File,
    targetLocation: File,
    notificationSink: StatusNotifier)(implicit ec: ExecutionContext): Future[File] = {
    expected.transform(x => x, e => DownloadException(e)).map { file =>
      notificationSink.notify(Validating)
      validator(file)
      notificationSink.notify(Extracting)
      FileHelper.unZipFile(file, targetLocation)
    }
    expected.onComplete {
      case Success(_) => notificationSink.notify(Complete)
      case Failure(error @ AuthenticationException(message, username, url)) =>
        notificationSink.notify(ProvisioningError(s"Cannot login to $url with username: $username and password given: $message", error))
      case Failure(DownloadException(_)) => // Already reported
      case Failure(error) => notificationSink.notify(ProvisioningError(s"Error provisioning: ${error.getMessage}", error))
    }
    expected
  }

  def provision(executor: DownloadExecutor,
    validator: File => File,
    targetLocation: File,
    notificationSink: StatusNotifier)(implicit ec: ExecutionContext): Future[File] = {
    notificationSink.notify(Downloading(executor.downloadUrl))
    postprocessResults(executor.execute(),
      validator,
      targetLocation,
      notificationSink)
  }
}
