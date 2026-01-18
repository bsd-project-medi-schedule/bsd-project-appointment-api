package nats

import cats.effect.*
import cats.syntax.all.*
import fs2.Stream

trait EventHandler {
  def handles: String
  def handle(event: NatsEvent): IO[List[NatsEvent]]
}

trait EventProcessor {
  def register(handler: EventHandler): IO[Unit]
  def run: Stream[IO, Unit]
}

object EventProcessor {

  def create(eventBus: EventBus): IO[EventProcessor] =
    Ref.of[IO, List[EventHandler]](List.empty).map { handlersRef =>
      new EventProcessor {
        override def register(handler: EventHandler): IO[Unit] =
          handlersRef.update(_ :+ handler)

        override def run: Stream[IO, Unit] =
          Stream.eval(handlersRef.get).flatMap { handlers =>
            val handlerStreams = handlers.map { handler =>
              eventBus
                .subscribe(handler.handles)
                .evalMap { ackableEvent =>
                  handler.handle(ackableEvent.event).flatMap { newEvents =>
                    newEvents.traverse_(eventBus.publish)
                  } >> ackableEvent.ack
                }
            }
            Stream.emits(handlerStreams).parJoinUnbounded
          }
      }
    }
}
