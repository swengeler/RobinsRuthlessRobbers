package control;

import maps.MapRepresentation;
import simulation.Agent;
import simulation.Simulation;
import ui.*;

import java.util.ArrayList;

public class Controller {

    private static Main userInterface;
    private static Simulation simulation;

    public static void setUserInterface(Main userInterfaceInput) {
        userInterface = userInterfaceInput;
    }

    public static void setSimulation(Simulation simulationInput) {
        simulation = simulationInput;
    }

    public static Simulation getSimulation() {
        return simulation;
    }

    public static void theBestTest(ArrayList<MapPolygon> map, ArrayList<VisualAgent> visualAgents) {
        // converting the map
        /*ArrayList<Polygon> obstaclePolygons = new ArrayList<>();
        for (MapPolygon p : map) {
            if (p.getPoints().size() > 0) {
                obstaclePolygons.add(p.getPolygon());
            }
        }
        ArrayList<Polygon> subList = new ArrayList<>();
        for (int i = 1; i < map.size() - 1; i++) {
            subList.add(obstaclePolygons.get(i));
        }
        MapRepresentation mapRepresentation = new GridMapRepresentation(obstaclePolygons.get(0), subList);*/
        MapRepresentation mapRepresentation = new MapRepresentation(map, null, null);

        // adding internal representations of the agents
        ArrayList<Agent> agents = new ArrayList<>();
        Agent temp;
        for (VisualAgent a : visualAgents) {
            temp = new Agent(a.getSettings());
            temp.setPolicy(mapRepresentation, a.getSettings().getMovePolicy());
            agents.add(temp);
            /*AgentSettings s = a.getSettings();
            temp = new Agent(s.getXPos(), s.getYPos(), s.getSpeed(), s.getTurnSpeed(), s.getFieldOfViewAngle(), s.getFieldOfViewRange());
            temp.setPolicy(new RandomMovePolicy(temp, s.isPursuing(), mapRepresentation));
            a.centerXProperty().bind(temp.xPosProperty());
            a.centerYProperty().bind(temp.yPosProperty());
            a.turnAngleProperty().bind(temp.turnAngleProperty());
            agents.add(temp);*/
        }

        Simulation sim = new Simulation(mapRepresentation, agents);
        setSimulation(sim);
    }

    /*public static void betterTest(ArrayList<MapPolygon> map, ArrayList<Circle> pursuers, ArrayList<Circle> evaders) {
        ArrayList<Polygon> obstaclePolygons = new ArrayList<>();
        for (MapPolygon p : map) {
            obstaclePolygons.add(p.getPolygon());
        }
        ArrayList<Polygon> subList = new ArrayList<>();
        for (int i = 1; i < map.size(); i++) {
            subList.add(obstaclePolygons.get(i));
        }
        MapRepresentation mapRepresentation = new GridMapRepresentation(obstaclePolygons.get(0), subList);

        ArrayList<Agent> agents = new ArrayList<>();
        Agent temp;
        for (Circle c : pursuers) {
            temp = new Agent(c.getCenterX(), c.getCenterY(), 100, 10, 10, 10);
            temp.setPolicy(new RandomMovePolicy(temp, false, mapRepresentation));
            c.centerXProperty().bind(temp.xPosProperty());
            c.centerYProperty().bind(temp.yPosProperty());
            agents.add(temp);
        }
        for (Circle c : evaders) {
            temp = new Agent(c.getCenterX(), c.getCenterY(), 100, 10, 10, 10);
            temp.setPolicy(new RandomMovePolicy(temp, false, mapRepresentation));
            c.centerXProperty().bind(temp.xPosProperty());
            c.centerYProperty().bind(temp.yPosProperty());
            agents.add(temp);
        }

        Simulation sim = new Simulation(mapRepresentation, agents);
    }*/

}