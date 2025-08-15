package org.dcsim

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext

import org.dcsim.actors._


object SimulationIntegrationTest {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem[Nothing](Root(), "SimulatorSystemTest")

    // Akka scheduler needs a FiniteDuration + Runnable + implicit ExecutionContext
    implicit val ec: ExecutionContext = system.executionContext

    // terminate after ~ one tick so we don't loop forever
    system.scheduler.scheduleOnce(
      300.millis,
      new Runnable { def run(): Unit = system.terminate() }
    )
  }

  object Root {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { ctx =>
      // Reporter (placeholder)
      val reporter: ActorRef[ReporterActor.Command] =
        ctx.spawn(ReporterActor(), "reporter")

      // Grid model (IMPORTANT: correct protocol type)
      val gridModel: ActorRef[GridModelActor.Command] =
        ctx.spawn(GridModelActor(), "gridModel")

      // Controller
      val controller: ActorRef[SimulationControllerActor.Command] =
        ctx.spawn(SimulationControllerActor(), "simulationController")

      // One test train
      val train1: ActorRef[TrainActor.Command] =
        ctx.spawn(TrainActor("Train1", controller, gridModel, 0.0), "train1")

      // Register and start
      controller ! SimulationControllerActor.RegisterTrain("Train1", train1)
      controller ! SimulationControllerActor.StartSimulation

      Behaviors.empty
    }
  }
}
