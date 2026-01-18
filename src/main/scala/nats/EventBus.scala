package nats

import cats.effect.*
import config.objects.NatsConfig
import fs2.Stream

trait EventBus {
  def publish(event: NatsEvent): IO[Unit]
  def publish(events: Seq[NatsEvent]): IO[Unit]
  def subscribe(pattern: String): Stream[IO, AckableEvent]
}

object EventBus {

  def fromNats(client: NatsClient, natsConfig: NatsConfig): EventBus =
    new EventBus {
      override def publish(event: NatsEvent): IO[Unit] =
        client.publish(event.eventType, event)

      override def publish(events: Seq[NatsEvent]): IO[Unit] =
        IO.parSequence(events.map(e => client.publish(e.eventType, e))).map(_ => ())

      override def subscribe(pattern: String): Stream[IO, AckableEvent] =
        client.subscribe(pattern, natsConfig.streamName, natsConfig.durablePrefix)
    }

  def resource(natsConfig: NatsConfig): Resource[IO, EventBus] =
    NatsClient.resource(natsConfig).map(client => fromNats(client, natsConfig))
}
