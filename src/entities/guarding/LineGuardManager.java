package entities.guarding;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineSegment;
import entities.base.Entity;
import javafx.scene.shape.Line;
import maps.MapRepresentation;
import simulation.Agent;

import java.util.ArrayList;
import java.util.HashMap;

public class LineGuardManager implements GuardManager {

    private MapRepresentation map;

    private Line originalGuardingLine;
    private LineSegment guardingLineSegment;

    private ArrayList<Coordinate> originalPositions;
    private ArrayList<Agent> guards;
    private HashMap<Agent, Coordinate> guardsToOgPositions;

    private Coordinate currentTargetPos, lastTargetPos, projectedPos;

    public LineGuardManager(Line originalGuardingLine, ArrayList<Coordinate> originalPositions, MapRepresentation map) {
        this.originalGuardingLine = originalGuardingLine;
        this.originalPositions = new ArrayList<>();
        this.originalPositions.addAll(originalPositions);
        this.map = map;
        guardingLineSegment = new LineSegment(originalGuardingLine.getStartX(), originalGuardingLine.getStartY(), originalGuardingLine.getEndX(), originalGuardingLine.getEndY());
    }

    @Override
    public void initTargetPosition(Agent a) {
        currentTargetPos = new Coordinate(a.getXPos(), a.getYPos());
    }

    @Override
    public void updateTargetPosition(Agent a) {
        lastTargetPos = currentTargetPos;
        currentTargetPos = new Coordinate(a.getXPos(), a.getYPos());

        boolean visible = false;
        for (Agent g : guards) {
            if (map.isVisible(a, g)) {
                visible = true;
                break;
            }
        }
        double deltaX, deltaY, length;
        if (visible) {
            for (Agent g : guards) {
                length = Math.sqrt(Math.pow(g.getXPos() - a.getXPos(), 2) + Math.pow(g.getYPos() - a.getYPos(), 2));
                if (length <= g.getSpeed() * Entity.UNIVERSAL_SPEED_MULTIPLIER) {
                    g.moveTo(a.getXPos(), a.getYPos());
                    a.setActive(false);
                }
            }
            double projectionFactor = guardingLineSegment.projectionFactor(currentTargetPos);
            if (projectionFactor < 0.0) {
                projectionFactor = 0.0;
            } else if (projectionFactor > 1.0) {
                projectionFactor = 1.0;
            }
            projectedPos = guardingLineSegment.pointAlong(projectionFactor);

            for (Agent g : guards) {
                if (!(projectedPos.x == g.getXPos() && projectedPos.y == g.getYPos())) {
                    deltaX = projectedPos.x - g.getXPos();
                    deltaY = projectedPos.y - g.getYPos();
                    length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    deltaX /= length;
                    deltaY /= length;
                    deltaX *= g.getSpeed() * Entity.UNIVERSAL_SPEED_MULTIPLIER;
                    deltaY *= g.getSpeed() * Entity.UNIVERSAL_SPEED_MULTIPLIER;

                    if (Math.sqrt(deltaX * deltaX + deltaY * deltaY) >= projectedPos.distance(new Coordinate(g.getXPos(), g.getYPos()))) {
                        g.moveTo(projectedPos.x, projectedPos.y);
                    } else {
                        g.moveBy(deltaX, deltaY);
                    }
                }
            }
        } else {
            for (Agent g : guards) {
                if (!(guardsToOgPositions.get(g).x == g.getXPos() && guardsToOgPositions.get(g).y == g.getYPos())) {
                    deltaX = guardsToOgPositions.get(g).x - g.getXPos();
                    deltaY = guardsToOgPositions.get(g).y - g.getYPos();
                    length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                    deltaX /= length;
                    deltaY /= length;
                    if (length <= g.getSpeed() * Entity.UNIVERSAL_SPEED_MULTIPLIER) {
                        g.moveTo(guardsToOgPositions.get(g).x, guardsToOgPositions.get(g).y);
                    } else {
                        g.moveBy(deltaX * g.getSpeed() * Entity.UNIVERSAL_SPEED_MULTIPLIER, deltaY * g.getSpeed() * Entity.UNIVERSAL_SPEED_MULTIPLIER);
                    }
                }
            }
        }
    }

    @Override
    public void assignGuards(ArrayList<Agent> guards) {
        assert guards.size() >= originalPositions.size();

        this.guards = new ArrayList<>();
        this.guards.addAll(guards);
        guardsToOgPositions = new HashMap<>();
        for (int i = 0; i < originalPositions.size(); i++) {
            guardsToOgPositions.put(guards.get(i), originalPositions.get(i));
        }

    }

    @Override
    public void clearGuards() {
        guards.clear();
    }

    @Override
    public int totalRequiredGuards() {
        return originalPositions.size();
    }

    @Override
    public ArrayList<Coordinate> getOriginalPositions() {
        return originalPositions;
    }

    public Line getOriginalGuardingLine() {
        return originalGuardingLine;
    }

    public boolean crossedLine() {
        LineSegment temp = new LineSegment(lastTargetPos, currentTargetPos);
        return temp.intersection(guardingLineSegment) != null;
    }

    public boolean crossable() {
        boolean visible = false;
        for (Agent g : guards) {
            if (map.isVisible(g.getXPos(), g.getYPos(), currentTargetPos.x, currentTargetPos.y)) {
                visible = true;
                break;
            }
        }
        if (visible) {
            double projectionFactor = guardingLineSegment.projectionFactor(currentTargetPos);
            if (projectionFactor < 0.0) {
                projectionFactor = 0.0;
            } else if (projectionFactor > 1.0) {
                projectionFactor = 1.0;
            }
            projectedPos = guardingLineSegment.pointAlong(projectionFactor);

            double minDistance = Double.MAX_VALUE, curDistance;
            for (Agent g : guards) {
                if ((curDistance = Math.sqrt(Math.pow(g.getXPos() - projectedPos.x, 2) + Math.pow(g.getYPos() - projectedPos.y, 2))) < minDistance) {
                    minDistance = curDistance;
                }
            }

            if (projectedPos.distance(currentTargetPos) < minDistance) {
                return true;
            }
        }
        return false;
    }

    public MapRepresentation getMap() {
        return map;
    }

}
