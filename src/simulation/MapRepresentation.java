package simulation;

import additionalOperations.GeometryOperations;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import entities.Entity;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import ui.MapPolygon;

import java.util.ArrayList;

public class MapRepresentation {

    private Polygon borderPolygon;
    private ArrayList<Polygon> obstaclePolygons;
    private ArrayList<Polygon> allPolygons;
    private ArrayList<Line> polygonEdges;

    private com.vividsolutions.jts.geom.Polygon polygon;
    private Geometry boundary;
    private ArrayList<LineSegment> allLines;
    private ArrayList<LineSegment> borderLines;
    private ArrayList<LineSegment> obstacleLines;

    private ArrayList<Entity> pursuingEntities;
    private ArrayList<Entity> evadingEntities;

    public MapRepresentation(ArrayList<MapPolygon> map) {
        init(map);
        pursuingEntities = new ArrayList<>();
        evadingEntities = new ArrayList<>();
    }

    public MapRepresentation(ArrayList<MapPolygon> map, ArrayList<Entity> pursuingEntities, ArrayList<Entity> evadingEntities) {
        init(map);
        this.pursuingEntities = pursuingEntities == null ? new ArrayList<>() : pursuingEntities;
        this.evadingEntities = evadingEntities == null ? new ArrayList<>() : evadingEntities;
    }

    private void init(ArrayList<MapPolygon> map) {
        allPolygons = new ArrayList<>();
        obstaclePolygons = new ArrayList<>();
        for (MapPolygon p : map) {
            if (p.getPoints().size() > 0) {
                allPolygons.add(p.getPolygon());
                obstaclePolygons.add(allPolygons.get(allPolygons.size() - 1));
            }
        }
        borderPolygon = allPolygons.get(0);
        obstaclePolygons.remove(0);

        polygonEdges = new ArrayList<>();
        for (Polygon p : allPolygons) {
            for (int i = 0; i < p.getPoints().size(); i += 2) {
                polygonEdges.add(new Line(p.getPoints().get(i), p.getPoints().get(i + 1), (p.getPoints().get((i + 2) % p.getPoints().size())), (p.getPoints().get((i + 3) % p.getPoints().size()))));
            }
        }

        allLines = new ArrayList<>();
        borderLines = new ArrayList<>();
        for (int i = 0; i < borderPolygon.getPoints().size(); i += 2) {
            borderLines.add(new LineSegment(borderPolygon.getPoints().get(i), borderPolygon.getPoints().get(i + 1), borderPolygon.getPoints().get((i + 2) % borderPolygon.getPoints().size()), borderPolygon.getPoints().get((i + 3) % borderPolygon.getPoints().size())));
        }
        allLines.addAll(borderLines);
        obstacleLines = new ArrayList<>();
        for (Polygon p : obstaclePolygons) {
            for (int i = 0; i < p.getPoints().size(); i += 2) {
                obstacleLines.add(new LineSegment(p.getPoints().get(i), p.getPoints().get(i + 1), p.getPoints().get((i + 2) % p.getPoints().size()), p.getPoints().get((i + 3) % p.getPoints().size())));
            }
        }
        allLines.addAll(obstacleLines);

        CoordinateSequence coordinateSequence = new CoordinateArraySequence(1);
        // TODO: construct LinearRing objects from polygons, construct a Polygon object from that
    }

    public boolean legalPosition(double xPos, double yPos) {
        if (!borderPolygon.contains(xPos, yPos)) {
            return false;
        }
        for (Polygon p : obstaclePolygons) {
            if (GeometryOperations.inPolygonWithoutBorder(p, xPos, yPos)) {
                return false;
            }
            /*if (p.contains(xPos, yPos)) {
                return false;
            }*/
        }
        return true;
    }

    public boolean legalPosition(Coordinate c) {
        return legalPosition(c.x, c.y);
    }

    public ArrayList<LineSegment> getBorderLines() {
        return borderLines;
    }

    public ArrayList<LineSegment> getObstacleLines() {
        return obstacleLines;
    }

    public ArrayList<LineSegment> getAllLines() {
        return allLines;
    }

    public Polygon getBorderPolygon() {
        return borderPolygon;
    }

    public ArrayList<Polygon> getObstaclePolygons() {
        return obstaclePolygons;
    }

    public ArrayList<Polygon> getAllPolygons() {
        return allPolygons;
    }

    public ArrayList<Line> getPolygonEdges() {
        return polygonEdges;
    }

    public ArrayList<Entity> getPursuingEntities() {
        return pursuingEntities;
    }

    public ArrayList<Entity> getEvadingEntities() {
        return evadingEntities;
    }

    /*public boolean isVisible(double x1, double y1, double x2, double y2) {
        // check whether the second controlledAgents is visible from the position of the first controlledAgents
        // (given its field of view and the structure of the map)
        for (Line l : polygonEdges) {
            if (GeometryOperations.lineIntersect(l, x1, y1, x2, y2)) {
                return false;
            }
        }
        for (Polygon p : obstaclePolygons) {
            if (GeometryOperations.inPolygonWithoutBorder(p, x1, y1, x2, y2)) {
                return false;
            }
        }
        if (!GeometryOperations.inPolygon(borderPolygon, x1, y1, x2, y2)) {
            return false;
        }
        return true;
    }*/

    public boolean isVisible(double x1, double y1, double x2, double y2) {
        // check whether the second controlledAgents is visible from the position of the first controlledAgents
        // (given its field of view and the structure of the map)
        if (!GeometryOperations.inPolygon(borderPolygon, x1, y1, x2, y2)) {
            return false;
        }
        for (Polygon p : obstaclePolygons) {
            if (GeometryOperations.inPolygonWithoutBorder(p, x1, y1, x2, y2) || GeometryOperations.inPolygonWithoutBorder(p, x1, y1) || GeometryOperations.inPolygonWithoutBorder(p, x2, y2)) {
                return false;
            }
        }
        LineSegment l = new LineSegment(x1, y1, x2, y2);
        Coordinate c;
        for (LineSegment ls : allLines) {
            c = ls.intersection(l);
            /*if (c != null && !(c.equals2D(l.getCoordinate(0)) || c.equals2D(l.getCoordinate(1)) || c.equals2D(ls.getCoordinate(0)) || c.equals2D(ls.getCoordinate(1)))) {
                return false;
            }*/
            if (c != null && !(c.equals2D(l.getCoordinate(0)) || c.equals2D(l.getCoordinate(1)) || c.equals2D(ls.getCoordinate(0)) || c.equals2D(ls.getCoordinate(1)))) {
                return false;
            }
        }
        return true;
    }

    public boolean isVisible(double x1, double y1, double x2, double y2, String string1, String string2) {
        System.out.println("\nisVisible-check for index " + string1 + " and " + string2);
        // check whether the second controlledAgents is visible from the position of the first controlledAgents
        // (given its field of view and the structure of the map)
        if (!GeometryOperations.inPolygon(borderPolygon, x1, y1, x2, y2)) {
            return false;
        }
        for (Polygon p : obstaclePolygons) {
            if (GeometryOperations.inPolygonWithoutBorder(p, x1, y1, x2, y2) || GeometryOperations.inPolygonWithoutBorder(p, x1, y1) || GeometryOperations.inPolygonWithoutBorder(p, x2, y2)) {
                return false;
            }
        }
        LineSegment l = new LineSegment(x1, y1, x2, y2);
        Coordinate c;
        for (LineSegment ls : allLines) {
            c = ls.intersection(l);
            /*if (c != null && !(c.equals2D(l.getCoordinate(0)) || c.equals2D(l.getCoordinate(1)) || c.equals2D(ls.getCoordinate(0)) || c.equals2D(ls.getCoordinate(1)))) {
                return false;
            }*/
            if (c != null && !(c.equals2D(l.getCoordinate(0)) || c.equals2D(l.getCoordinate(1)) || c.equals2D(ls.getCoordinate(0)) || c.equals2D(ls.getCoordinate(1)))) {
                System.out.println("There is an intersection\n");
                return false;
            }
        }
        System.out.println("There is no intersection\n");
        return true;
    }

    public boolean isVisible(Agent a1, Agent a2) {
        return isVisible(a1.getXPos(), a1.getYPos(), a2.getXPos(), a2.getYPos());
    }

    // methods for the controlledAgents to extract the knowledge it has access to

}
