package org.dcsim.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object FeederStationActorOld {

  sealed trait Command
  final case class SupplyPower(time: Double, voltage: Double) extends Command
  final case class Shutdown() extends Command

  def apply(id: String): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info(s"FeederStationActor $id started")

      Behaviors.receiveMessage {
        case SupplyPower(time, voltage) =>
          context.log.info(s"Feeder $id supplying power at $time with voltage $voltage")
          Behaviors.same

        case Shutdown() =>
          context.log.info(s"Feeder $id shutting down")
          Behaviors.stopped
      }
    }
  }
}
