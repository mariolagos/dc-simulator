package org.dcsim.actors

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object ReporterActor {

  sealed trait Command
  final case class ReportTrainPosition(time: Double, trainId: String, position: Double) extends Command
  final case class ReportTrainPower(time: Double, trainId: String, power: Double) extends Command

  def apply(): Behavior[Command] = Behaviors.receive { (context, message) =>
    message match {
      case ReportTrainPosition(time, trainId, pos) =>
        context.log.info(s"[Reporter] Position report: $trainId at $pos m (t = $time)")
      case ReportTrainPower(time, trainId, power) =>
        context.log.info(s"[Reporter] Power report: $trainId requested $power W (t = $time)")
    }
    Behaviors.same
  }
}
