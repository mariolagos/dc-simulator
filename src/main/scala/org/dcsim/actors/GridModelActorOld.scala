package org.dcsim.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

object GridModelActorOld {

  sealed trait Command
  final case class UpdateVoltage(time: Double, voltage: Double) extends Command
  final case class ReportState(replyTo: ActorRef[String]) extends Command

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("GridModelActor started.")

      Behaviors.receiveMessage {
        case UpdateVoltage(time, voltage) =>
          context.log.info(s"Grid updated at $time with voltage $voltage")
          Behaviors.same

        case ReportState(replyTo) =>
          replyTo ! "Grid state: OK"
          Behaviors.same
      }
    }
  }
} 