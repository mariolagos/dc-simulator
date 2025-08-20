package org.dcsim.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object TrainActorOld {

  // Messages that TrainActor can receive
  sealed trait Command

  final case class Tick(time: Double) extends Command

  def apply(
             id: String,
             controller: ActorRef[SimulationControllerActorOld.Command],
             gridModel: ActorRef[GridModelActorOld.Command],
             startTime: Double
           ): Behavior[Command] = Behaviors.setup { context =>

    context.log.info(s"TrainActor $id started at time $startTime")

    Behaviors.receiveMessage {
      case Tick(time) =>
        context.log.info(s"TrainActor $id processing Tick at $time")
        // Example placeholder logic for power request and position update
        val requestedPower = 500000.0  // in watts
        val position = 1000.0 + time * 10 // dummy example position

        controller ! SimulationControllerActorOld.TrainPowerRequest(time, id, requestedPower)
        controller ! SimulationControllerActorOld.TrainPositionUpdate(time, id, position)

        Behaviors.same
    }
  }
}
