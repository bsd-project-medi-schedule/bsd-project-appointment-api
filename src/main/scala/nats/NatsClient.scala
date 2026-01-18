package nats

import cats.effect.*
import cats.effect.std.Dispatcher as CatsDispatcher
import cats.effect.std.Queue
import cats.syntax.all.*
import config.objects.NatsConfig
import fs2.Stream
import io.nats.client.*
import io.nats.client.api.*

import java.nio.charset.StandardCharsets
import java.time.Duration as JDuration
import scala.jdk.CollectionConverters.*

case class AckableEvent(event: NatsEvent, ack: IO[Unit])

trait NatsClient {
  def publish(subject: String, event: NatsEvent): IO[Unit]
  def subscribe(subject: String, streamName: String, durablePrefix: String): Stream[IO, AckableEvent]
  def subscribeRaw(subject: String, streamName: String, durablePrefix: String): Stream[IO, Message]
}

object NatsClient {

  def resource(natsConfig: NatsConfig): Resource[IO, NatsClient] =
    Resource
      .make(connect(natsConfig)) { conn =>
        IO.delay {
          try conn.drain(JDuration.ofSeconds(5)) catch { case _: Exception => () }
          try conn.close() catch { case _: Exception => () }
        }
      }
      .evalMap { conn =>
        ensureStream(conn, natsConfig).as(new JetStreamClientImpl(conn))
      }

  private def connect(natsConfig: NatsConfig): IO[Connection] =
    IO.delay {
      val options = Options.builder()
        .server(s"nats://${natsConfig.natsHost}:${natsConfig.natsPort}")
        .connectionName(natsConfig.connectionName)
        .maxReconnects(-1)
        .reconnectWait(JDuration.ofSeconds(2))
        .maxPingsOut(20)
        .build()
      Nats.connect(options)
    }

  private def ensureStream(connection: Connection, natsConfig: NatsConfig): IO[Unit] = {
    val jsm = connection.jetStreamManagement()

    val streamConfig =
      StreamConfiguration
        .builder()
        .name(natsConfig.streamName)
        .subjects(natsConfig.streamSubjects*)
        .storageType(StorageType.Memory)
        .retentionPolicy(RetentionPolicy.Limits)
        .maxMessages(10000)
        .maxAge(JDuration.ofHours(1))
        .build()

    def isApiCode(e: Throwable, code: Int): Boolean =
      e match {
        case j: JetStreamApiException => j.getApiErrorCode == code
        case _                        => false
      }

    val StreamNotFound   = 10059
    val SubjectsOverlap  = 10065

    def streamExists(name: String): IO[Boolean] =
      IO.blocking(jsm.getStreamInfo(name))
        .as(true)
        .recover {
          case e if isApiCode(e, StreamNotFound) => false
        }

    def updateStream(): IO[Unit] =
      IO.blocking(jsm.updateStream(streamConfig)).void

    def addStream(): IO[Unit] =
      IO.blocking(jsm.addStream(streamConfig)).void

    def deleteConflictingStreams(): IO[Unit] =
      IO.blocking(jsm.getStreamNames).flatMap { names =>
        // iterate effectfully; run deletes sequentially to keep behavior deterministic
        names.asScala.toList
          .filterNot(_ == natsConfig.streamName)
          .traverse_(deleteIfOverlaps)
      }

    def overlapsAny(existingSubjects: java.util.List[String]): Boolean = {
      val set = existingSubjects.asScala.toSet
      natsConfig.streamSubjects.exists { subj =>
        set.contains(subj) || subjectsOverlap(subj, existingSubjects) // keep your existing helper
      }
    }

    def deleteIfOverlaps(existingStreamName: String): IO[Unit] =
      (for {
        info <- IO.blocking(jsm.getStreamInfo(existingStreamName))
        subs  = info.getConfiguration.getSubjects
        _    <- IO.whenA(overlapsAny(subs)) {
          IO.blocking(jsm.deleteStream(existingStreamName)).void
        }
      } yield ())
        // best-effort: ignore errors checking/deleting other streams
        .handleErrorWith(_ => IO.unit)

    def addStreamWithOverlapRepair(): IO[Unit] =
      addStream().handleErrorWith {
        case e if isApiCode(e, SubjectsOverlap) =>
          deleteConflictingStreams() *> addStream()
        case other =>
          IO.raiseError(other)
      }

    streamExists(natsConfig.streamName).ifM(
      ifTrue  = updateStream(),
      ifFalse = addStreamWithOverlapRepair()
    )
  }

  private def subjectsOverlap(subject: String, existingSubjects: java.util.List[String]): Boolean = {
    import scala.jdk.CollectionConverters.*
    existingSubjects.asScala.exists { existing =>
      // Simple overlap check for wildcards
      subject.startsWith(existing.replace("*", "").replace(">", "")) ||
        existing.startsWith(subject.replace("*", "").replace(">", ""))
    }
  }

  private class JetStreamClientImpl(connection: Connection) extends NatsClient {
    private val jetStream: JetStream = connection.jetStream()

    override def publish(subject: String, event: NatsEvent): IO[Unit] =
      IO.delay {
        val json = EventCodec.encode(event)
        jetStream.publish(subject, json.getBytes(StandardCharsets.UTF_8))
        ()
      }

    override def subscribe(subject: String, streamName: String, durablePrefix: String): Stream[IO, AckableEvent] =
      subscribeRaw(subject, streamName, durablePrefix).evalMap { msg =>
        val json = new String(msg.getData, StandardCharsets.UTF_8)
        IO.fromEither(EventCodec.decode(json).leftMap(e => new RuntimeException(e)))
          .map(event => AckableEvent(event, IO.delay(msg.ack())))
      }

    override def subscribeRaw(subject: String, streamName: String, durablePrefix: String): Stream[IO, Message] =
      Stream.eval(Queue.unbounded[IO, Option[Message]]).flatMap { queue =>
        // Cats Effect Dispatcher bridges callback APIs -> IO safely (no blocking in callback thread).
        Stream.resource(CatsDispatcher.parallel[IO]).flatMap { ceDispatcher =>

          val subscribeAction: IO[(io.nats.client.Dispatcher, Subscription)] =
            IO.delay {
              val durableName = s"$durablePrefix-${subject.replace(".", "-").replace("*", "all")}"

              val consumerConfig = ConsumerConfiguration.builder()
                .durable(durableName)
                .filterSubject(subject)
                .ackPolicy(AckPolicy.Explicit)
                .deliverPolicy(DeliverPolicy.All) // Get all unacked messages, not just new ones
                .ackWait(JDuration.ofSeconds(30))
                .maxDeliver(3) // Retry failed messages up to 3 times
                .build()

              val pushOptions = PushSubscribeOptions.builder()
                .stream(streamName)
                .configuration(consumerConfig)
                .build()

              val natsDispatcher = connection.createDispatcher()

              val handler: MessageHandler = msg =>
                // Important: do NOT block the NATS callback thread.
                ceDispatcher.unsafeRunAndForget(queue.offer(Some(msg)))

              val subscription =
                jetStream.subscribe(subject, natsDispatcher, handler, false, pushOptions)

              (natsDispatcher, subscription)
            }

          def stop(
            natsDispatcher: io.nats.client.Dispatcher,
            subscription: Subscription
          ): IO[Unit] =
            IO.delay {
              // Dispatcher-owned subscriptions must be unsubscribed via the dispatcher.
              try natsDispatcher.unsubscribe(subscription)
              catch { case _: Exception => () }

              // Drain to process any already-dispatched messages before fully stopping.
              try natsDispatcher.drain(java.time.Duration.ofSeconds(2))
              catch { case _: Exception => () }
            } >> queue.offer(None) // Terminates Stream.fromQueueNoneTerminated

          Stream.bracket(subscribeAction) { case (d, s) => stop(d, s) } >>
            Stream.fromQueueNoneTerminated(queue)
        }
      }

  }
}
