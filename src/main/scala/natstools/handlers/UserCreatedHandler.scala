package natstools.handlers

import DTO.UserDTO
import cats.effect.IO
import nats.{EventHandler, NatsEvent}
import natstools.events.UserCreatedEvent
import repo.UserRepo

final class UserCreatedHandler(
  userRepo: UserRepo
) extends EventHandler {

  override val handles: String = UserCreatedEvent.EVENT_TYPE

  override def handle(event: NatsEvent): IO[List[NatsEvent]] =
    event match {
      case e: UserCreatedEvent =>
        for {
          _ <- userRepo.createAndGetId(UserDTO(e.userId, e.email, e.role))
        } yield List.empty

      case _ => IO.pure(List.empty)
    }
}

object UserCreatedHandler {
  def apply(userRepo: UserRepo): EventHandler =
    new UserCreatedHandler(userRepo)
}
