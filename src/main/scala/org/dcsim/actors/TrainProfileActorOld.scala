package org.dcsim.actors

import akka.actor.typed.{Behavior, ActorRef}
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.mutable

object TrainProfileActorOld {

  // Messages the TrainProfileActor can receive
  sealed trait Command
  final case class GetProfile(trainId: String, replyTo: ActorRef[ProfileResponse]) extends Command
  final case class ProfileResponse(trainId: String, power: List[(Double, Double)]) // (time, power)

  // Dummy data instead of file loading
  val dummyProfile: List[(Double, Double)] = List(
    (0.0, 100000.0),
    (10.0, 200000.0),
    (20.0, 0.0)
  )

  def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case GetProfile(trainId, replyTo) =>
        context.log.info(s"[TrainProfileActor] Sending dummy profile for train $trainId")
        replyTo ! ProfileResponse(trainId, dummyProfile)
    }
    Behaviors.same
  }
}
