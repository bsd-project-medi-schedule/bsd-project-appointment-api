package natstools.handlers

import cats.effect.IO
import nats.EventHandler
import nats.NatsEvent
import natstools.events.UserDeleteEvent
import repo.UserRepo

final class UserDeleteHandler(
  userRepo: UserRepo
) extends EventHandler {

  override val handles: String = UserDeleteEvent.EVENT_TYPE

  override def handle(event: NatsEvent): IO[List[NatsEvent]] =
    event match {
      case e: UserDeleteEvent =>
        for {
          _ <- userRepo.deleteById(e.userId)
        } yield List.empty

      case _ => IO.pure(List.empty)
    }
}

object UserDeleteHandler {
  def apply(userRepo: UserRepo): EventHandler =
    new UserDeleteHandler(userRepo)
}
