package simulation;

import entities.CentralisedEntity;
import entities.Entity;
import javafx.collections.ObservableList;
import javafx.scene.shape.Circle;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

public class AdaptedSimulation {

    private Timer simulationTimer;

    private MapRepresentation map;
    private long timeStep = 300;

    public AdaptedSimulation(MapRepresentation map) {
        this.map = map;
        timerSetup();
    }

    private void timerSetup() {
        simulationTimer = FxTimer.runPeriodically(Duration.ofMillis(timeStep), () -> {
            for (Entity e : map.getEvadingEntities()) {
                if (e.isActive()) {
                    e.move();
                }
            }
            for (Entity e : map.getPursuingEntities()) {
                if (e.isActive()) {
                    e.move();
                }
            }

            // check whether any new evaders have been captured
            /*for (Agent a1 : agents) {
                if (a1.isEvader()) {
                    for (Agent a2 : agents) {
                        if (a2.isPursuer() && a2.inRange(a1.getXPos(), a1.getYPos())) {
                            // remove captured controlledAgents
                            a1.setActive(false);
                        }
                    }
                }
            }*/

            // check whether all evaders are captured
            // assumption here is that the pursuing entities take care of capturing/deactivating evading ones themselves
            boolean simulationOver = true;
            for (Entity e : map.getEvadingEntities()) {
                if (e.isActive()) {
                    simulationOver = false;
                }
            }

            if (simulationOver) {
                simulationTimer.stop();
                // send message to UI
                System.out.println("All evaders captured");
            }
        });
    }

    public void pause() {
        simulationTimer.stop();
    }

    public void unPause() {
        simulationTimer.restart();
    }

    public void setTimeStep(long timeStep) {
        this.timeStep = timeStep;
        simulationTimer.stop();
        timerSetup();
    }

    public static CentralisedEntity testCentralisedEntity;

}
