package org.dcsim

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import com.typesafe.config.{Config, ConfigFactory}
import org.dcsim.actors._
import org.dcsim.electric.GridModelLoader

object DcSim {

  def main(args: Array[String]): Unit = {
    ActorSystem[Nothing](Root(), "SimulatorSystem")
  }

  object Root {
    def apply(): Behavior[Nothing] = Behaviors.setup[Nothing] { context =>

      // Skapa reporter
      val reporter: ActorRef[ReporterActor.Command] =
        context.spawn(ReporterActor(), "reporter")
      context.log.info("ReporterActor spawned.")

      // Skapa profilhanterare
      val profileManager: ActorRef[TrainProfileActor.Command] =
        context.spawn(TrainProfileActor(), "trainProfileActor")
      context.log.info("TrainProfileActor spawned.")

      // Läs konfiguration från produktionsfil
      val config: Config = ConfigFactory.load()

      // Läs elnätsdata från konfiguration
      val gridData = GridModelLoader.load(config)
      context.log.info(s"Loaded ${gridData.getNodes.size} nodes, ${gridData.getLines.size} lines, ${gridData.getSubstations.size} substations")

      // Skapa elnätsmodell
      val gridModel: ActorRef[GridModelActor.Command] =
        context.spawn(GridModelActor(), "gridModel")
      context.log.info("GridModelActor spawned.")

      // Skapa kontrollaktör
      val controller: ActorRef[SimulationControllerActor.Command] =
        context.spawn(SimulationControllerActor(), "simulationController")
      context.log.info("SimulationControllerActor spawned.")

      // OBS: Inga tåg registreras här — det ska ske via konfiguration eller externa system

      Behaviors.empty
    }
  }
}
