package org.dcsim.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object SimulationControllerActorOld {

  // Marker trait for commands accepted by SimulationController
  sealed trait Command

  // Messages
  case class RegisterTrain(id: String, ref: ActorRef[TrainActorOld.Command]) extends Command
  case object StartSimulation extends Command
  case class TrainPowerRequest(time: Double, id: String, power: Double) extends Command
  case class TrainPositionUpdate(time: Double, id: String, position: Double) extends Command
  case class Tick(time: Double) extends Command

  // Internal state for controller
  case class State(
                    trains: Map[String, ActorRef[TrainActorOld.Command]],
                    time: Double
                  )

  def apply(): Behavior[Command] = controller(State(Map.empty, 0.0))

  private def controller(state: State): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case RegisterTrain(id, ref) =>
        context.log.info(s"Registered train: $ref")
        controller(state.copy(trains = state.trains + (id -> ref)))

      case StartSimulation =>
        context.log.info("Simulation started")
        context.self ! Tick(state.time)
        Behaviors.same

      case Tick(time) =>
        context.log.info(s"Tick @ $time s")
        state.trains.values.foreach(_ ! TrainActorOld.Tick(time))
        context.self ! Tick(time + 1.0)
        controller(state.copy(time = time + 1.0))

      case TrainPowerRequest(time, id, power) =>
        context.log.info(s"Train $id requests power $power at $time")
        Behaviors.same

      case TrainPositionUpdate(time, id, position) =>
        context.log.info(s"Train $id position: $position at $time")
        Behaviors.same
    }
  }
}
