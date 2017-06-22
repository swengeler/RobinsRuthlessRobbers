package entities.specific;

import additionalOperations.*;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.impl.CoordinateArraySequence;
import entities.base.Entity;
import entities.base.PartitioningEntity;
import entities.guarding.GuardManager;
import entities.guarding.LineGuardManager;
import entities.utils.*;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.scene.shape.Polygon;
import maps.MapRepresentation;
import org.javatuples.Quintet;
import org.javatuples.Triplet;
import org.jdelaunay.delaunay.error.DelaunayError;
import org.jdelaunay.delaunay.geometries.*;
import simulation.*;
import ui.Main;

import java.util.*;

/**
 * DCRL = Divide and Conquer, Randomised, Line Guards
 */
public class DCRLEntity extends PartitioningEntity {

    private static final boolean CONSTANT_TARGET_TEST = true;

    private enum Stage {
        CATCHER_TO_SEARCHER, FIND_TARGET, INIT_FIND_TARGET, FOLLOW_TARGET
    }

    private TraversalHandler traversalHandler;

    private Point2D origin, pseudoBlockingVertex, lastPointVisible;
    private boolean pocketCounterClockwise;
    private Stage currentStage;

    private Agent searcher, catcher;
    private PlannedPath currentSearcherPath, currentCatcherPath;
    private ArrayList<PathLine> pathLines;
    private int searcherPathLineCounter, catcherPathLineCounter;

    private ShortestPathRoadMap testSPRM;
    private LineGuardManager testCrossedLGM;
    private ArrayList<DTriangle> testPossibleTriangles;
    private boolean spottedOnce, linesSecured;

    private Coordinate previousTargetPosition, currentTargetPosition;

    private Group catchGraphics;
    private Group guardGraphics;

    public DCRLEntity(MapRepresentation map) {
        super(map);
        catchGraphics = new Group();
        guardGraphics = new Group();
        Main.pane.getChildren().addAll(catchGraphics, guardGraphics);
    }

    @Override
    protected void determineTarget() {
        outer:
        for (Entity e : map.getEvadingEntities()) {
            if (e.isActive()) {
                for (Agent a : e.getControlledAgents()) {
                    if (a.isActive()) {
                        target = a;
                        for (GuardManager gm : guardManagers) {
                            gm.initTargetPosition(target);
                        }
                        break outer;
                    }
                }
            }
        }
    }

    @Override
    protected void doPrecedingOperations() {
        if (target != null) {
            if (currentTargetPosition != null) {
                previousTargetPosition = currentTargetPosition;
            } else {
                previousTargetPosition = new Coordinate(target.getXPos(), target.getYPos());
            }
            currentTargetPosition = new Coordinate(target.getXPos(), target.getYPos());
        }
    }

    @Override
    protected void doGuardOperations() {
        // perhaps register when pursuer crosses line without being caught
        if (guardsPositioned() && target != null) {
            if (!linesSecured) {
                linesSecured = true;
                for (GuardManager gm : guardManagers) {
                    if (((LineGuardManager) gm).crossable()) {
                        linesSecured = false;
                        testCrossedLGM = null;
                        Line l = new Line(((LineGuardManager) gm).getOriginalGuardingLine().getStartX(), ((LineGuardManager) gm).getOriginalGuardingLine().getStartY(), ((LineGuardManager) gm).getOriginalGuardingLine().getEndX(), ((LineGuardManager) gm).getOriginalGuardingLine().getEndY());
                        l.setStroke(Color.INDIANRED);
                        Main.pane.getChildren().add(l);
                        //break;
                    } else {
                        Line l = new Line(((LineGuardManager) gm).getOriginalGuardingLine().getStartX(), ((LineGuardManager) gm).getOriginalGuardingLine().getStartY(), ((LineGuardManager) gm).getOriginalGuardingLine().getEndX(), ((LineGuardManager) gm).getOriginalGuardingLine().getEndY());
                        l.setStroke(Color.GREEN);
                        Main.pane.getChildren().add(l);
                    }
                }
            }
            /*if (!linesSecured && testCrossedLGM == null) {
                for (GuardManager gm : guardManagers) {
                    if (((LineGuardManager) gm).crossedLine()) {
                        testCrossedLGM = (LineGuardManager) gm;
                        break;
                    }
                }
            }*/
        }
    }

    @Override
    protected void doSearchAndCatchOperations() {
        if (currentStage == Stage.CATCHER_TO_SEARCHER) {
            catcherToSearcher();
        } else if (currentStage == Stage.INIT_FIND_TARGET) {
            initFindTarget();
        } else if (currentStage == Stage.FOLLOW_TARGET) {
            followTarget();
        } else if (currentStage == Stage.FIND_TARGET) {
            findTarget();
        }
    }

    @Override
    protected void doSucceedingOperations() {
    }

    private void catcherToSearcher() {
        // only move catcher
        pathLines = currentCatcherPath.getPathLines();
        length = Math.sqrt(Math.pow(pathLines.get(catcherPathLineCounter).getEndX() - pathLines.get(catcherPathLineCounter).getStartX(), 2) + Math.pow(pathLines.get(catcherPathLineCounter).getEndY() - pathLines.get(catcherPathLineCounter).getStartY(), 2));
        deltaX = (pathLines.get(catcherPathLineCounter).getEndX() - pathLines.get(catcherPathLineCounter).getStartX()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
        deltaY = (pathLines.get(catcherPathLineCounter).getEndY() - pathLines.get(catcherPathLineCounter).getStartY()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;

        if (pathLines.get(catcherPathLineCounter).contains(catcher.getXPos() + deltaX, catcher.getYPos() + deltaY)) {
            // move along line
            catcher.moveBy(deltaX, deltaY);
        } else {
            // move to end of line
            // TODO: instead take a "shortcut" to the next line
            catcher.moveBy(pathLines.get(catcherPathLineCounter).getEndX() - catcher.getXPos(), pathLines.get(catcherPathLineCounter).getEndY() - catcher.getYPos());
            catcherPathLineCounter++;
        }

        // check if searcher position reached and the next stage can begin
        if (catcher.getXPos() == searcher.getXPos() && catcher.getYPos() == searcher.getYPos()) {
            currentStage = Stage.INIT_FIND_TARGET;
        }
    }

    private void initFindTarget() {
        // move searcher and catcher together (for its assumed they have the same speed
        if (guardsPositioned() && linesSecured) {
            if (currentSearcherPath == null) {
                try {
                    currentSearcherPath = traversalHandler.getRandomTraversal(searcher.getXPos(), searcher.getYPos());
                    currentCatcherPath = currentSearcherPath;
                    searcherPathLineCounter = 0;
                    catcherPathLineCounter = 0;
                } catch (DelaunayError e) {
                    e.printStackTrace();
                }
            }

            if (traversalHandler.getNodeIndex(searcher.getXPos(), searcher.getYPos()) == currentSearcherPath.getEndIndex()) {
                // end of path reached, compute new path
                try {
                    currentSearcherPath = traversalHandler.getRandomTraversal(searcher.getXPos(), searcher.getYPos());
                    currentCatcherPath = currentSearcherPath;
                } catch (DelaunayError e) {
                    e.printStackTrace();
                }
                searcherPathLineCounter = 0;
                catcherPathLineCounter = 0;
            }

            // move searcher and catcher using same paths
            pathLines = currentSearcherPath.getPathLines();
            length = Math.sqrt(Math.pow(pathLines.get(searcherPathLineCounter).getEndX() - pathLines.get(searcherPathLineCounter).getStartX(), 2) + Math.pow(pathLines.get(searcherPathLineCounter).getEndY() - pathLines.get(searcherPathLineCounter).getStartY(), 2));
            deltaX = (pathLines.get(searcherPathLineCounter).getEndX() - pathLines.get(searcherPathLineCounter).getStartX()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
            deltaY = (pathLines.get(searcherPathLineCounter).getEndY() - pathLines.get(searcherPathLineCounter).getStartY()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
            if (pathLines.get(searcherPathLineCounter).contains(searcher.getXPos() + deltaX, searcher.getYPos() + deltaY)) {
                // move along line
                searcher.moveBy(deltaX, deltaY);
            } else {
                // move to end of line
                searcher.moveBy(pathLines.get(searcherPathLineCounter).getEndX() - searcher.getXPos(), pathLines.get(searcherPathLineCounter).getEndY() - searcher.getYPos());
                searcherPathLineCounter++;
            }

            // move catcher using same path
            pathLines = currentCatcherPath.getPathLines();
            length = Math.sqrt(Math.pow(pathLines.get(catcherPathLineCounter).getEndX() - pathLines.get(catcherPathLineCounter).getStartX(), 2) + Math.pow(pathLines.get(catcherPathLineCounter).getEndY() - pathLines.get(catcherPathLineCounter).getStartY(), 2));
            deltaX = (pathLines.get(catcherPathLineCounter).getEndX() - pathLines.get(catcherPathLineCounter).getStartX()) / length * catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
            deltaY = (pathLines.get(catcherPathLineCounter).getEndY() - pathLines.get(catcherPathLineCounter).getStartY()) / length * catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
            if (pathLines.get(catcherPathLineCounter).contains(catcher.getXPos() + deltaX, catcher.getYPos() + deltaY)) {
                // move along line
                catcher.moveBy(deltaX, deltaY);
            } else {
                // move to end of line
                catcher.moveBy(pathLines.get(catcherPathLineCounter).getEndX() - catcher.getXPos(), pathLines.get(catcherPathLineCounter).getEndY() - catcher.getYPos());
                catcherPathLineCounter++;
            }

            if (CONSTANT_TARGET_TEST) {
                // check whether target is visible
                if (target != null) {
                    if (target.isActive() && map.isVisible(target, searcher)) {
                        System.out.println("Target found");
                        //spottedOnce = true;
                        origin = new Point2D(catcher.getXPos(), catcher.getYPos());
                        Label l = new Label("Origin");
                        l.setTranslateX(origin.getX() + 5);
                        l.setTranslateY(origin.getY() + 5);
                        Main.pane.getChildren().addAll(new Circle(origin.getX(), origin.getY(), 7, Color.GRAY), l);
                        currentStage = Stage.FOLLOW_TARGET;
                    }
                }
            } else {
                // check whether an evader is visible
                for (int i = 0; currentStage == Stage.INIT_FIND_TARGET && i < map.getEvadingEntities().size(); i++) {
                    if (map.getEvadingEntities().get(i).isActive()) {
                        for (int j = 0; currentStage == Stage.INIT_FIND_TARGET && j < map.getEvadingEntities().get(i).getControlledAgents().size(); j++) {
                            if (map.getEvadingEntities().get(i).getControlledAgents().get(j).isActive() && map.isVisible(map.getEvadingEntities().get(i).getControlledAgents().get(j), searcher) && !GeometryOperations.lineIntersectSeparatingLines(map.getEvadingEntities().get(i).getControlledAgents().get(j).getXPos(), map.getEvadingEntities().get(i).getControlledAgents().get(j).getYPos(), searcher.getXPos(), searcher.getYPos(), separatingLines)) {
                                target = map.getEvadingEntities().get(i).getControlledAgents().get(j);
                                System.out.println("Target found");
                                for (GuardManager gm : guardManagers) {
                                    gm.initTargetPosition(target);
                                }
                                origin = new Point2D(catcher.getXPos(), catcher.getYPos());
                                Label l = new Label("Origin");
                                l.setTranslateX(origin.getX() + 5);
                                l.setTranslateY(origin.getY() + 5);
                                Main.pane.getChildren().addAll(new Circle(origin.getX(), origin.getY(), 7, Color.GRAY), l);
                                currentStage = Stage.FOLLOW_TARGET;
                            }
                        }
                    }
                }
            }
        } else if (map.isVisible(target, catcher)) {
            length = Math.sqrt(Math.pow(target.getXPos() - catcher.getXPos(), 2) + Math.pow(target.getYPos() - catcher.getYPos(), 2));
            deltaX = (target.getXPos() - catcher.getXPos()) / length * catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
            deltaY = (target.getYPos() - catcher.getYPos()) / length * catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
            if (length < catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER) {
                catcher.moveTo(target.getXPos(), target.getYPos());
                target.setActive(false);
                target = null;
            } else {
                catcher.moveBy(deltaX, deltaY);
                searcher.moveBy(deltaX, deltaY);
            }
        }
    }

    private void followTarget() {
        length = Math.sqrt(Math.pow(target.getXPos() - catcher.getXPos(), 2) + Math.pow(target.getYPos() - catcher.getYPos(), 2));
        if (map.isVisible(target, catcher) && length <= catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER) {
            pseudoBlockingVertex = null;
            lastPointVisible = null;
            catcher.moveBy(target.getXPos() - catcher.getXPos(), target.getYPos() - catcher.getYPos());
            target.setActive(false);
            target = null;
            origin = null;
            currentStage = Stage.CATCHER_TO_SEARCHER;
        } else if (map.isVisible(target, catcher)) {
            pseudoBlockingVertex = null;
            lastPointVisible = null;

            PlannedPath temp = shortestPathRoadMap.getShortestPath(target.getXPos(), target.getYPos(), catcher.getXPos(), catcher.getYPos());
            temp.addPathToEnd(shortestPathRoadMap.getShortestPath(catcher.getXPos(), catcher.getYPos(), origin));
            PathLine lionsMoveLine = temp.getPathLine(0);

            //System.out.printf("lionsMoveLine: (%.3f|%.3f) to (%.3f|%.3f)\n", lionsMoveLine.getStartX(), lionsMoveLine.getStartY(), lionsMoveLine.getEndX(), lionsMoveLine.getEndY());

            // calculate the perpendicular distance of the catcher's position to the line
            // based on this find the parallel distance in either direction that will give the legal distance of movement
            // find the two points and take the one closer to the target as the point to move to
            PointVector closestPoint = GeometryOperations.closestPoint(catcher.getXPos(), catcher.getYPos(), lionsMoveLine);
            PointVector normal = new PointVector(closestPoint.getX() - catcher.getXPos(), closestPoint.getY() - catcher.getYPos());
            double parallelLength = Math.sqrt(Math.pow(catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER, 2) - Math.pow(normal.length(), 2));
            PointVector gradient = new PointVector(lionsMoveLine.getEndX() - lionsMoveLine.getStartX(), lionsMoveLine.getEndY() - lionsMoveLine.getStartY());
            gradient = VectorOperations.multiply(gradient, 1 / gradient.length());
            Point2D candidate1 = VectorOperations.add(closestPoint, VectorOperations.multiply(gradient, parallelLength)).toPoint();
            Point2D candidate2 = VectorOperations.add(closestPoint, VectorOperations.multiply(gradient, -parallelLength)).toPoint();

            if (catcher.shareLocation(searcher)) {
                if (Math.sqrt(Math.pow(candidate1.getX() - target.getXPos(), 2) + Math.pow(candidate1.getY() - target.getYPos(), 2)) < Math.sqrt(Math.pow(candidate2.getX() - target.getXPos(), 2) + Math.pow(candidate2.getY() - target.getYPos(), 2))) {
                    // move to first candidate point
                    catcher.moveBy(candidate1.getX() - catcher.getXPos(), candidate1.getY() - catcher.getYPos());
                    searcher.moveBy(candidate1.getX() - searcher.getXPos(), candidate1.getY() - searcher.getYPos());
                    Main.pane.getChildren().add(new Circle(candidate1.getX(), candidate1.getY(), 1, Color.BLACK));
                } else {
                    // move to second candidate point
                    catcher.moveBy(candidate2.getX() - catcher.getXPos(), candidate2.getY() - catcher.getYPos());
                    searcher.moveBy(candidate2.getX() - searcher.getXPos(), candidate2.getY() - searcher.getYPos());
                    Main.pane.getChildren().add(new Circle(candidate2.getX(), candidate2.getY(), 1, Color.BLACK));
                }
            } else {
                if (Math.sqrt(Math.pow(candidate1.getX() - target.getXPos(), 2) + Math.pow(candidate1.getY() - target.getYPos(), 2)) < Math.sqrt(Math.pow(candidate2.getX() - target.getXPos(), 2) + Math.pow(candidate2.getY() - target.getYPos(), 2))) {
                    // move to first candidate point
                    catcher.moveBy(candidate1.getX() - catcher.getXPos(), candidate1.getY() - catcher.getYPos());
                    Main.pane.getChildren().add(new Circle(candidate1.getX(), candidate1.getY(), 1, Color.BLACK));
                } else {
                    // move to second candidate point
                    catcher.moveBy(candidate2.getX() - catcher.getXPos(), candidate2.getY() - catcher.getYPos());
                    Main.pane.getChildren().add(new Circle(candidate2.getX(), candidate2.getY(), 1, Color.BLACK));
                }
            }
        } else {
            if (pseudoBlockingVertex == null) {
                System.out.println("target around corner, calculate path to first vertex");
                ShortestPathRoadMap.drawLines = true;

                //PlannedPath temp = traversalHandler.getRestrictedShortestPathRoadMap().getShortestPath(target.getXPos(), target.getYPos(), catcher.getXPos(), catcher.getYPos());
                PlannedPath temp = traversalHandler.getRestrictedShortestPathRoadMap().getShortestPath(catcher.getXPos(), catcher.getYPos(), target.getXPos(), target.getYPos());

                pseudoBlockingVertex = new Point2D(temp.getPathVertex(1).getEstX(), temp.getPathVertex(1).getEstY());
                lastPointVisible = new Point2D(catcher.getXPos(), catcher.getYPos());
                pocketCounterClockwise = GeometryOperations.leftTurnPredicate(lastPointVisible.getX(), -lastPointVisible.getY(), pseudoBlockingVertex.getX(), -pseudoBlockingVertex.getY(), target.getXPos(), -target.getYPos());

                catchGraphics.getChildren().add(new Circle(pseudoBlockingVertex.getX(), pseudoBlockingVertex.getY(), 4, Color.BLUEVIOLET));

                /*currentSearcherPath = (testInGuardedSquare ? testSPRM : traversalHandler.getRestrictedShortestPathRoadMap()).getShortestPath(searcher.getXPos(), searcher.getYPos(), pseudoBlockingVertex);
                currentCatcherPath = catcher.shareLocation(searcher) ? currentSearcherPath : (testInGuardedSquare ? testSPRM : traversalHandler.getRestrictedShortestPathRoadMap()).getShortestPath(catcher.getXPos(), catcher.getYPos(), pseudoBlockingVertex);*/
                /*currentSearcherPath = (testInGuardedSquare ? testSPRM : shortestPathRoadMap).getShortestPath(searcher.getXPos(), searcher.getYPos(), pseudoBlockingVertex);
                currentCatcherPath = catcher.shareLocation(searcher) ? currentSearcherPath : (testInGuardedSquare ? testSPRM : shortestPathRoadMap).getShortestPath(catcher.getXPos(), catcher.getYPos(), pseudoBlockingVertex);*/
                currentSearcherPath = traversalHandler.getRestrictedShortestPathRoadMap().getShortestPath(searcher.getXPos(), searcher.getYPos(), pseudoBlockingVertex);
                currentCatcherPath = catcher.shareLocation(searcher) ? currentSearcherPath : traversalHandler.getRestrictedShortestPathRoadMap().getShortestPath(catcher.getXPos(), catcher.getYPos(), pseudoBlockingVertex);
                //System.out.println("currentSearcherPath: " + currentSearcherPath + ", testInGuardedSquare: " + testInGuardedSquare);
                searcherPathLineCounter = 0;
                catcherPathLineCounter = 0;
            }

            // move searcher
            if (currentSearcherPath != null) {
                pathLines = currentSearcherPath.getPathLines();
                if (!(searcher.getXPos() == currentSearcherPath.getEndX() && searcher.getYPos() == currentSearcherPath.getEndY())) {
                    length = Math.sqrt(Math.pow(pathLines.get(searcherPathLineCounter).getEndX() - pathLines.get(searcherPathLineCounter).getStartX(), 2) + Math.pow(pathLines.get(searcherPathLineCounter).getEndY() - pathLines.get(searcherPathLineCounter).getStartY(), 2));
                    deltaX = (pathLines.get(searcherPathLineCounter).getEndX() - pathLines.get(searcherPathLineCounter).getStartX()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
                    deltaY = (pathLines.get(searcherPathLineCounter).getEndY() - pathLines.get(searcherPathLineCounter).getStartY()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
                    if (pathLines.get(searcherPathLineCounter).contains(searcher.getXPos() + deltaX, searcher.getYPos() + deltaY)) {
                        // move along line
                        searcher.moveBy(deltaX, deltaY);
                    } else {
                        // move to end of line
                        searcher.moveBy(pathLines.get(searcherPathLineCounter).getEndX() - searcher.getXPos(), pathLines.get(searcherPathLineCounter).getEndY() - searcher.getYPos());
                        searcherPathLineCounter++;
                    }
                } else {
                    // after the last (searcher move) the evader was still visible and the pseudo-blocking vertex was reached
                    System.out.println("Searcher reached end of line (1)");
                    System.out.println("Evader still visible (1): " + map.isVisible(searcher, target));
                }
            }

            Main.pane.getChildren().add(new Circle(catcher.getXPos(), catcher.getYPos(), 1, Color.FUCHSIA));

            // move catcher
            pathLines = currentCatcherPath.getPathLines();
            if (!(catcher.getXPos() == currentCatcherPath.getEndX() && catcher.getYPos() == currentCatcherPath.getEndY())) {
                length = Math.sqrt(Math.pow(pathLines.get(catcherPathLineCounter).getEndX() - pathLines.get(catcherPathLineCounter).getStartX(), 2) + Math.pow(pathLines.get(catcherPathLineCounter).getEndY() - pathLines.get(catcherPathLineCounter).getStartY(), 2));
                deltaX = (pathLines.get(catcherPathLineCounter).getEndX() - pathLines.get(catcherPathLineCounter).getStartX()) / length * catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
                deltaY = (pathLines.get(catcherPathLineCounter).getEndY() - pathLines.get(catcherPathLineCounter).getStartY()) / length * catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
                if (pathLines.get(catcherPathLineCounter).contains(catcher.getXPos() + deltaX, catcher.getYPos() + deltaY)) {
                    // move along line
                    catcher.moveBy(deltaX, deltaY);
                } else {
                    // move to end of line
                    catcher.moveBy(pathLines.get(catcherPathLineCounter).getEndX() - catcher.getXPos(), pathLines.get(catcherPathLineCounter).getEndY() - catcher.getYPos());
                    catcherPathLineCounter++;
                }
            } else {
                System.out.println("Catcher reached end of line");
            }

            // if pseudo-blocking vertex has been reached without seeing the evader again
            // if evader does become visible again, the old strategy is continued (should maybe already do that here)

            if (catcher.getXPos() == pseudoBlockingVertex.getX() && catcher.getYPos() == pseudoBlockingVertex.getY() && !map.isVisible(catcher, target)) {
                // do randomised search in pocket
                // pocket to be calculated from blocking vertex and position that the evader was last seen from (?)
                currentStage = Stage.FIND_TARGET;

                // pocket from lastPointVisible over pseudoBlockingVertex to polygon boundary
                // needs to be the current component though
                int componentIndex = 0;
                if (componentBoundaryLines.size() != 1) {
                    for (int i = 0; i < traversalHandler.getComponents().size(); i++) {
                        if (componentBoundaryShapes.get(i).contains(target.getXPos(), target.getYPos())) {
                            componentIndex = i;
                            break;
                        }
                    }
                }
                double rayStartX = lastPointVisible.getX();
                double rayStartY = lastPointVisible.getY();
                double rayDeltaX = pseudoBlockingVertex.getX() - rayStartX;
                double rayDeltaY = pseudoBlockingVertex.getY() - rayStartY;
                // determine pocket boundary line
                Point2D currentPoint, pocketBoundaryEndPoint = null;
                double minLengthSquared = Double.MAX_VALUE, currentLengthSquared;
                Line intersectedLine = null;
                for (Line line : componentBoundaryLines.get(componentIndex)) {
                    currentPoint = GeometryOperations.rayLineSegIntersection(rayStartX, rayStartY, rayDeltaX, rayDeltaY, line);
                    if (currentPoint != null && (currentLengthSquared = Math.pow(catcher.getXPos() - currentPoint.getX(), 2) + Math.pow(catcher.getYPos() - currentPoint.getY(), 2)) < minLengthSquared) {
                        minLengthSquared = currentLengthSquared;
                        pocketBoundaryEndPoint = currentPoint;
                        intersectedLine = line;
                    }
                }

                if (pocketBoundaryEndPoint == null) {
                    System.out.println("No pocket boundary end point found.");
                } else {
                    Line boundaryLine = new Line(pocketBoundaryEndPoint.getX(), pocketBoundaryEndPoint.getY(), catcher.getXPos(), catcher.getYPos());
                    catchGraphics.getChildren().add(boundaryLine);
                    Label l = new Label("v");
                    l.setTranslateX(pseudoBlockingVertex.getX() + 5);
                    l.setTranslateY(pseudoBlockingVertex.getY() + 5);
                    catchGraphics.getChildren().addAll(new Circle(pseudoBlockingVertex.getX(), pseudoBlockingVertex.getY(), 7, Color.BLUEVIOLET), l);

                    // find the new "pocket component"
                    //System.out.printf("Catcher at (%f|%f)\nReal at (%f|%f)\nFake at (%f|%f)\n", catcher.getXPos(), catcher.getYPos(), currentCatcherPath.getLastPathVertex().getRealX(), currentCatcherPath.getLastPathVertex().getRealY(), currentCatcherPath.getLastPathVertex().getEstX(), currentCatcherPath.getLastPathVertex().getEstY());
                    Tuple<ArrayList<DTriangle>, int[][]> pocketInfo = findPocketComponent(boundaryLine, componentIndex, currentCatcherPath.getLastPathVertex().getRealX(), currentCatcherPath.getLastPathVertex().getRealY(), separatingLines.contains(intersectedLine) ? intersectedLine : null);
                    traversalHandler.restrictToPocket(pocketInfo.getFirst(), pocketInfo.getSecond(), map, separatingLines.contains(intersectedLine) ? intersectedLine : null);

                    try {
                        currentSearcherPath = traversalHandler.getRandomTraversal(searcher.getXPos(), searcher.getYPos());
                        searcherPathLineCounter = 0;
                    } catch (DelaunayError e) {
                        e.printStackTrace();
                    }
                }
            } else if (catcher.getXPos() == pseudoBlockingVertex.getX() && catcher.getYPos() == pseudoBlockingVertex.getY()) {
                currentStage = Stage.FOLLOW_TARGET;
                System.out.println("Still visible, following");
            }
        }
    }

    private void findTarget() {
        boolean visible = false;
        for (Agent g : guards) {
            if (map.isVisible(g, target)) {
                visible = true;
                break;
            }
        }
        if (map.isVisible(target.getXPos(), target.getYPos(), catcher.getXPos(), catcher.getYPos()) && Math.sqrt(Math.pow(target.getXPos() - catcher.getXPos(), 2) + Math.pow(target.getYPos() - catcher.getYPos(), 2)) <= catcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER) {
            pseudoBlockingVertex = null;
            lastPointVisible = null;
            catcher.moveBy(target.getXPos() - catcher.getXPos(), target.getYPos() - catcher.getYPos());
            target.setActive(false);
            target = null;
            origin = null;
            currentStage = Stage.CATCHER_TO_SEARCHER;
        } else if (map.isVisible(target.getXPos(), target.getYPos(), catcher.getXPos(), catcher.getYPos())) {
            pseudoBlockingVertex = null;
            System.out.println("pseudoBlockingVertex null because target visible in FIND_TARGET");
            currentStage = Stage.FOLLOW_TARGET;
        } else {
            if (traversalHandler.getNodeIndex(searcher.getXPos(), searcher.getYPos()) == currentSearcherPath.getEndIndex()) {
                try {
                    currentSearcherPath = traversalHandler.getRandomTraversal(searcher.getXPos(), searcher.getYPos());
                } catch (DelaunayError e) {
                    e.printStackTrace();
                }
                searcherPathLineCounter = 0;
            }

            pathLines = currentSearcherPath.getPathLines();
            if (!(searcher.getXPos() == currentSearcherPath.getEndX() && searcher.getYPos() == currentSearcherPath.getEndY())) {
                length = Math.sqrt(Math.pow(pathLines.get(searcherPathLineCounter).getEndX() - pathLines.get(searcherPathLineCounter).getStartX(), 2) + Math.pow(pathLines.get(searcherPathLineCounter).getEndY() - pathLines.get(searcherPathLineCounter).getStartY(), 2));
                deltaX = (pathLines.get(searcherPathLineCounter).getEndX() - pathLines.get(searcherPathLineCounter).getStartX()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
                deltaY = (pathLines.get(searcherPathLineCounter).getEndY() - pathLines.get(searcherPathLineCounter).getStartY()) / length * searcher.getSpeed() * UNIVERSAL_SPEED_MULTIPLIER;
                if (pathLines.get(searcherPathLineCounter).contains(searcher.getXPos() + deltaX, searcher.getYPos() + deltaY)) {
                    // move along line
                    searcher.moveBy(deltaX, deltaY);
                } else {
                    // move to end of line
                    searcher.moveBy(pathLines.get(searcherPathLineCounter).getEndX() - searcher.getXPos(), pathLines.get(searcherPathLineCounter).getEndY() - searcher.getYPos());
                    searcherPathLineCounter++;
                }
            } else {
                // after the last (searcher move) the evader was still visible and the pseudo-blocking vertex was reached
                System.out.println("Searcher reached end of line (2)");
                System.out.println("Evader still visible (2): " + map.isVisible(searcher, target));
            }

            if (map.isVisible(target.getXPos(), target.getYPos(), searcher.getXPos(), searcher.getYPos())) {
                System.out.println("target found again by searcher");
                catchGraphics.getChildren().clear();

                PlannedPath temp = traversalHandler.getRestrictedShortestPathRoadMap().getShortestPath(catcher.getXPos(), catcher.getYPos(), target.getXPos(), target.getYPos());
                catchGraphics.getChildren().addAll(temp.getPathLines());

                lastPointVisible = new Point2D(pseudoBlockingVertex.getX(), pseudoBlockingVertex.getY());
                pseudoBlockingVertex = new Point2D(temp.getPathVertex(1).getEstX(), temp.getPathVertex(1).getEstY());
                pocketCounterClockwise = GeometryOperations.leftTurnPredicate(lastPointVisible.getX(), -lastPointVisible.getY(), pseudoBlockingVertex.getX(), -pseudoBlockingVertex.getY(), temp.getPathVertex(2).getEstX(), -temp.getPathVertex(2).getEstY());

                catchGraphics.getChildren().add(new Circle(pseudoBlockingVertex.getX(), pseudoBlockingVertex.getY(), 5, Color.MEDIUMPURPLE));

                int componentIndex = 0;
                if (componentBoundaryLines.size() != 1) {
                    for (int i = 0; i < traversalHandler.getComponents().size(); i++) {
                        if (componentBoundaryShapes.get(i).contains(catcher.getXPos(), catcher.getYPos())) {
                            componentIndex = i;
                            break;
                        }
                    }
                }
                double rayStartX = lastPointVisible.getX();
                double rayStartY = lastPointVisible.getY();
                double rayDeltaX = pseudoBlockingVertex.getX() - rayStartX;
                double rayDeltaY = pseudoBlockingVertex.getY() - rayStartY;
                // determine pocket boundary line
                Point2D currentPoint, pocketBoundaryEndPoint = null;
                double minLengthSquared = Double.MAX_VALUE, currentLengthSquared;
                Line intersectedLine = null;
                boolean ignored;
                for (Line line : componentBoundaryLines.get(componentIndex)) {
                    currentPoint = GeometryOperations.rayLineSegIntersection(rayStartX, rayStartY, rayDeltaX, rayDeltaY, line);
                    if (currentPoint != null && (currentLengthSquared = Math.pow(catcher.getXPos() - currentPoint.getX(), 2) + Math.pow(catcher.getYPos() - currentPoint.getY(), 2)) < minLengthSquared/*&& map.isVisible(catcher.getXPos(), catcher.getYPos(), pocketBoundaryEndPoint.getEstX(), pocketBoundaryEndPoint.getEstY())*/) {
                        minLengthSquared = currentLengthSquared;
                        pocketBoundaryEndPoint = currentPoint;
                        intersectedLine = line;
                    }
                }
                Line boundaryLine = new Line(pocketBoundaryEndPoint.getX(), pocketBoundaryEndPoint.getY(), pseudoBlockingVertex.getX(), pseudoBlockingVertex.getY());
                catchGraphics.getChildren().add(boundaryLine);
                catchGraphics.getChildren().add(new Circle(pocketBoundaryEndPoint.getX(), pocketBoundaryEndPoint.getY(), 6, Color.BLACK));
                Tuple<ArrayList<DTriangle>, int[][]> pocketInfo = findPocketComponent(boundaryLine, componentIndex, pseudoBlockingVertex.getX(), pseudoBlockingVertex.getY(), separatingLines.contains(intersectedLine) ? intersectedLine : null);
                traversalHandler.restrictToPocket(pocketInfo.getFirst(), pocketInfo.getSecond(), map, separatingLines.contains(intersectedLine) ? intersectedLine : null);

                Label l = new Label("v");
                l.setTranslateX(pseudoBlockingVertex.getX() + 5);
                l.setTranslateY(pseudoBlockingVertex.getY() + 5);
                catchGraphics.getChildren().addAll(new Circle(pseudoBlockingVertex.getX(), pseudoBlockingVertex.getY(), 7, Color.BLUEVIOLET), l);

                currentSearcherPath = null;
                //currentSearcherPath = traversalHandler.getRestrictedShortestPathRoadMap().getShortestPath(searcher.getXPos(), searcher.getYPos(), pseudoBlockingVertex);
                // not sure about which one of these it should be
                // I think the first one probably works if the random traversal truly includes the triangles in the guarding square
                // but it would fuck up otherwise because the pseudo-blocking vertex might lie somewhere completely different
                //currentCatcherPath = (testInGuardedSquare ? testSPRM : traversalHandler.getRestrictedShortestPathRoadMap()).getShortestPath(catcher.getXPos(), catcher.getYPos(), pseudoBlockingVertex);
                /*currentCatcherPath = (testInGuardedSquare ? testSPRM : traversalHandler.getRestrictedShortestPathRoadMap()).getShortestPath(catcher.getXPos(), catcher.getYPos(), pseudoBlockingVertex);*/
                //currentCatcherPath = (testInGuardedSquare ? testSPRM : shortestPathRoadMap).getShortestPath(catcher.getXPos(), catcher.getYPos(), pseudoBlockingVertex);
                currentCatcherPath = shortestPathRoadMap.getShortestPath(catcher.getXPos(), catcher.getYPos(), pseudoBlockingVertex);
                searcherPathLineCounter = 0;
                catcherPathLineCounter = 0;

                System.out.println("pseudoBlockingVertex null because target visible in FIND_TARGET (2)");
                currentStage = Stage.FOLLOW_TARGET;
            }
        }
    }

    @Override
    protected void assignTasks() {
        super.assignTasks();
        // the computed PlannedPath objects will initially be used to position all the guards in their correct locations
        // the (at least 2) remaining agents will be assigned to be searcher (and catcher)
        for (Agent a : availableAgents) {
            if (!guards.contains(a)) {
                searcher = a;
                break;
            }
        }
        for (Agent a : availableAgents) {
            if (!guards.contains(a) && !a.equals(searcher)) {
                catcher = a;
                break;
            }
        }
        currentStage = Stage.CATCHER_TO_SEARCHER;
        currentCatcherPath = shortestPathRoadMap.getShortestPath(catcher.getXPos(), catcher.getYPos(), searcher.getXPos(), searcher.getYPos());
    }

    @Override
    protected void computeRequirements() {
        // build needed data structures and analyse map to see how many agents are required
        try {
            // computing the triangulation of the given map
            Tuple<ArrayList<DTriangle>, ArrayList<DTriangle>> triangles = triangulate(map);
            ArrayList<DTriangle> nodes = triangles.getFirst();
            ArrayList<DTriangle> holeTriangles = triangles.getSecond();

            // computing adjacency between the triangles in the map -> modelling it as a graph
            Tuple<int[][], int[]> matrices = computeAdjacency(nodes);
            int[][] originalAdjacencyMatrix = matrices.getFirst();
            int[] degreeMatrix = matrices.getSecond();

            // grouping hole triangles by holes (through adjacency)
            ArrayList<ArrayList<DTriangle>> holes = computeHoles(holeTriangles);

            // compute separating triangles and the updated adjacency matrix
            Triplet<ArrayList<DTriangle>, ArrayList<DEdge>, int[][]> separation = computeSeparatingTriangles(nodes, holes, originalAdjacencyMatrix, degreeMatrix);
            ArrayList<DTriangle> separatingTriangles = separation.getValue0();
            ArrayList<DEdge> nonSeparatingLines = separation.getValue1();
            int[][] spanningTreeAdjacencyMatrix = separation.getValue2();

            // compute the simply connected components in the graph
            ArrayList<DTriangle> componentNodes = new ArrayList<>();
            for (DTriangle dt : nodes) {
                if (!separatingTriangles.contains(dt)) {
                    componentNodes.add(dt);
                }
            }
            Tuple<ArrayList<ArrayList<DTriangle>>, int[]> componentInfo = computeConnectedComponents(nodes, componentNodes, spanningTreeAdjacencyMatrix);
            ArrayList<ArrayList<DTriangle>> simplyConnectedComponents = componentInfo.getFirst();

            Triplet<ArrayList<Line>, ArrayList<DEdge>, ArrayList<DEdge>> lineSeparation = computeGuardingLines(separatingTriangles, nonSeparatingLines);
            ArrayList<Line> separatingLines = lineSeparation.getValue0();
            ArrayList<DEdge> reconnectingEdges = lineSeparation.getValue1();
            ArrayList<DEdge> separatingEdges = lineSeparation.getValue2();
            this.separatingLines = separatingLines;
            this.separatingEdges = separatingEdges;

            Tuple<int[][], ArrayList<ArrayList<DTriangle>>> reconnectedAdjacency = computeReconnectedAdjacency(nodes, simplyConnectedComponents, reconnectingEdges, spanningTreeAdjacencyMatrix, separatingTriangles);
            int[][] reconnectedAdjacencyMatrix = reconnectedAdjacency.getFirst();
            ArrayList<ArrayList<DTriangle>> reconnectedComponents = reconnectedAdjacency.getSecond();

            Tuple<ArrayList<ArrayList<Line>>, ArrayList<Shape>> componentBoundaries = computeComponentBoundaries(reconnectedComponents, separatingEdges, separatingLines);
            componentBoundaryLines = componentBoundaries.getFirst();
            componentBoundaryShapes = componentBoundaries.getSecond();

            guardManagers = computeGuardManagers(separatingLines);

            traversalHandler = new TraversalHandler(shortestPathRoadMap, nodes, simplyConnectedComponents, spanningTreeAdjacencyMatrix);
            traversalHandler.separatingLineBased(separatingLines, reconnectedComponents, reconnectedAdjacencyMatrix);

            for (GuardManager gm : guardManagers) {
                requiredAgents += gm.totalRequiredGuards();
            }
            requiredAgents += 2;
            System.out.println("\nrequiredAgents: " + requiredAgents);
        } catch (DelaunayError error) {
            error.printStackTrace();
        }
    }

    private ArrayList<GuardManager> computeGuardManagers(ArrayList<Line> separatingLines) {
        ArrayList<GuardManager> lineGuardManagers = new ArrayList<>(separatingLines.size());
        LineGuardManager tempLGM;

        // for every reflex vertex of the polygon, calculate its visibility polygon
        // identify reflex vertices:
        // from shortest path map, get all vertices and convert them into coordinates
        List<Coordinate> vertices = Arrays.asList(map.getPolygon().getCoordinates());

        ArrayList<Coordinate> reflexVertices = new ArrayList<>();
        Set<PathVertex> temp = shortestPathRoadMap.getVertices();
        for (PathVertex pv : temp) {
            reflexVertices.add(new Coordinate(pv.getRealX(), pv.getRealY()));
        }

        long before = System.currentTimeMillis();
        ArrayList<Tuple<Geometry, Group>> visibilityInfo = new ArrayList<>(vertices.size());
        ArrayList<Geometry> visibilityPolygons = new ArrayList<>();
        for (Coordinate c1 : reflexVertices) {
            visibilityInfo.add(computeVisibilityPolygon(c1, vertices));
            visibilityPolygons.add(visibilityInfo.get(visibilityInfo.size() - 1).getFirst());
        }
        System.out.println("Time to compute visibility polygons (vertices: " + vertices.size() + ", reflex vertices: " + reflexVertices.size() + "): " + (System.currentTimeMillis() - before) + " ms");

        before = System.currentTimeMillis();
        int c = 0;
        for (Line l : separatingLines) {
            l.setStroke(Color.LIGHTBLUE);
            Main.pane.getChildren().add(l);
            tempLGM = computeSingleGuardManager(l, reflexVertices, visibilityPolygons);
            lineGuardManagers.add(tempLGM);
            System.out.println("Time to compute " + (++c) + " guard(s): " + (System.currentTimeMillis() - before) + " ms");
        }
        System.out.println("Time to compute guard(s): " + (System.currentTimeMillis() - before) + " ms");

        return lineGuardManagers;
    }

    private LineGuardManager computeSingleGuardManager(Line separatingLine, ArrayList<Coordinate> reflexVertices, ArrayList<Geometry> visibilityPolygons) {
        LineString lineString = new LineString(new CoordinateArraySequence(new Coordinate[]{new Coordinate(separatingLine.getStartX(), separatingLine.getStartY()), new Coordinate(separatingLine.getEndX(), separatingLine.getEndY())}), GeometryOperations.factory);
        LineSegment lineSegment = new LineSegment(separatingLine.getStartX(), separatingLine.getStartY(), separatingLine.getEndX(), separatingLine.getEndY());
        // determine the reflex vertices visible from the line
        Geometry curIntersection;
        LineSegment curVisibleSegment;
        ArrayList<Coordinate> visibleVertices = new ArrayList<>();
        ArrayList<LineSegment> visibleSegments = new ArrayList<>();
        ArrayList<Coordinate> closestPoints = new ArrayList<>();
        ArrayList<Double> distances = new ArrayList<>();
        HashMap<Coordinate, double[]> fractionInfo = new HashMap<>();

        long before = System.currentTimeMillis();

        Line l;
        double length = lineSegment.getLength();
        double deltaX = (lineSegment.getCoordinate(1).x - lineSegment.getCoordinate(0).x) / length;
        double deltaY = (lineSegment.getCoordinate(1).y - lineSegment.getCoordinate(0).y) / length;
        for (int i = 0; i < reflexVertices.size(); i++) {
            if (!((reflexVertices.get(i).x == separatingLine.getStartX() && reflexVertices.get(i).y == separatingLine.getStartY()) || (reflexVertices.get(i).x == separatingLine.getEndX() && reflexVertices.get(i).y == separatingLine.getEndY()))) {
                long what1 = System.currentTimeMillis();
                try {
                    curIntersection = lineString.intersection(visibilityPolygons.get(i).buffer(GeometryOperations.PRECISION_EPSILON * 100));
                    if (curIntersection.getCoordinates().length >= 2) {
                        curVisibleSegment = new LineSegment(curIntersection.getCoordinates()[0].x, curIntersection.getCoordinates()[0].y, curIntersection.getCoordinates()[curIntersection.getCoordinates().length - 1].x, curIntersection.getCoordinates()[curIntersection.getCoordinates().length - 1].y);
                        visibleVertices.add(reflexVertices.get(i));
                        visibleSegments.add(curVisibleSegment);
                        closestPoints.add(curVisibleSegment.closestPoint(reflexVertices.get(i)));
                        distances.add(curVisibleSegment.distance(reflexVertices.get(i)));
                        fractionInfo.put(visibleVertices.get(visibleVertices.size() - 1), new double[]{lineSegment.segmentFraction(closestPoints.get(closestPoints.size() - 1)) + (distances.get(distances.size() - 1) / length), lineSegment.segmentFraction(closestPoints.get(closestPoints.size() - 1)) - (distances.get(distances.size() - 1) / length)});
                    }
                } catch (TopologyException e) {
                    e.printStackTrace();
                }
                System.out.println("What 1: " + (System.currentTimeMillis() - what1));
            } else {
                long what2 = System.currentTimeMillis();
                // check whether both sides are properly visible
                LineSegment[] segs = new LineSegment[2];
                for (LineSegment lseg : map.getAllLines()) {
                    if (lseg.distance(reflexVertices.get(i)) == 0) {
                        if (segs[0] == null) {
                            segs[0] = lseg;
                        } else {
                            segs[1] = lseg;
                            boolean first0 = (reflexVertices.get(i).x == segs[0].getCoordinate(0).x && reflexVertices.get(i).y == segs[0].getCoordinate(0).y);
                            boolean first1 = (reflexVertices.get(i).x == segs[1].getCoordinate(0).x && reflexVertices.get(i).y == segs[1].getCoordinate(0).y);
                            boolean firstSepLine = (reflexVertices.get(i).x == lineSegment.getCoordinate(0).x && reflexVertices.get(i).y == lineSegment.getCoordinate(0).y);

                            boolean leftTurn0To1 = GeometryOperations.leftTurnPredicateInverted(first0 ? segs[0].getCoordinate(1) : segs[0].getCoordinate(0), reflexVertices.get(i), first1 ? segs[1].getCoordinate(1) : segs[1].getCoordinate(0));
                            if (leftTurn0To1) {
                                // segs[0] is the more counter-clockwise segment adjacent to the vertex
                                boolean leftTurn1ToLineEnd = GeometryOperations.leftTurnPredicateInverted(first1 ? segs[1].getCoordinate(1) : segs[1].getCoordinate(0), reflexVertices.get(i), firstSepLine ? lineSegment.getCoordinate(1) : lineSegment.getCoordinate(0));
                                boolean rightTurn0ToLineEnd = !GeometryOperations.leftTurnPredicateInverted(first0 ? segs[0].getCoordinate(0) : segs[0].getCoordinate(0), reflexVertices.get(i), firstSepLine ? lineSegment.getCoordinate(1) : lineSegment.getCoordinate(0));

                                if (!leftTurn1ToLineEnd || !rightTurn0ToLineEnd) {
                                    curVisibleSegment = new LineSegment(reflexVertices.get(i).x, reflexVertices.get(i).y, reflexVertices.get(i).x, reflexVertices.get(i).y);
                                    visibleVertices.add(reflexVertices.get(i));
                                    visibleSegments.add(curVisibleSegment);
                                    closestPoints.add(curVisibleSegment.closestPoint(reflexVertices.get(i)));
                                    distances.add(curVisibleSegment.distance(reflexVertices.get(i)));
                                    fractionInfo.put(visibleVertices.get(visibleVertices.size() - 1), new double[]{lineSegment.segmentFraction(closestPoints.get(closestPoints.size() - 1)) + (distances.get(distances.size() - 1) / length), lineSegment.segmentFraction(closestPoints.get(closestPoints.size() - 1)) - (distances.get(distances.size() - 1) / length)});
                                }
                            } else {
                                // segs[0] is the more clockwise segment adjacent to the vertex
                                boolean rightTurn1ToLineEnd = !GeometryOperations.leftTurnPredicateInverted(first1 ? segs[1].getCoordinate(1) : segs[1].getCoordinate(0), reflexVertices.get(i), firstSepLine ? lineSegment.getCoordinate(1) : lineSegment.getCoordinate(0));
                                boolean leftTurn0ToLineEnd = GeometryOperations.leftTurnPredicateInverted(first0 ? segs[0].getCoordinate(0) : segs[0].getCoordinate(0), reflexVertices.get(i), firstSepLine ? lineSegment.getCoordinate(1) : lineSegment.getCoordinate(0));

                                if (!rightTurn1ToLineEnd || !leftTurn0ToLineEnd) {
                                    curVisibleSegment = new LineSegment(reflexVertices.get(i).x, reflexVertices.get(i).y, reflexVertices.get(i).x, reflexVertices.get(i).y);
                                    visibleVertices.add(reflexVertices.get(i));
                                    visibleSegments.add(curVisibleSegment);
                                    closestPoints.add(curVisibleSegment.closestPoint(reflexVertices.get(i)));
                                    distances.add(curVisibleSegment.distance(reflexVertices.get(i)));
                                    fractionInfo.put(visibleVertices.get(visibleVertices.size() - 1), new double[]{lineSegment.segmentFraction(closestPoints.get(closestPoints.size() - 1)) + (distances.get(distances.size() - 1) / length), lineSegment.segmentFraction(closestPoints.get(closestPoints.size() - 1)) - (distances.get(distances.size() - 1) / length)});
                                }
                            }
                            break;
                        }
                    }
                }
                System.out.println("What 2: " + (System.currentTimeMillis() - what2));
            }
        }

        System.out.println("Time taken for first loop: " + (System.currentTimeMillis() - before));
        before = System.currentTimeMillis();

        visibleVertices.removeIf(c -> {
            double[] temp = fractionInfo.get(c);
            return (temp[0] <= 0 && temp[1] >= 1) || (temp[0] >= 1 && temp[1] <= 0);
        });

        //System.out.println();
        double[] tempFrac1, tempFrac2;
        HashMap<Coordinate, Double> lengthsSquared = new HashMap<>(visibleVertices.size());
        for (Coordinate v : visibleVertices) {
            tempFrac1 = fractionInfo.get(v);
            if (tempFrac1[0] < 0.0) {
                tempFrac1[0] = 0.0;
            } else if (tempFrac1[0] > 1.0) {
                tempFrac1[0] = 1.0;
            }
            if (tempFrac1[1] < 0.0) {
                tempFrac1[1] = 0.0;
            } else if (tempFrac1[1] > 1.0) {
                tempFrac1[1] = 1.0;
            }
            if (tempFrac1[0] > tempFrac1[1]) {
                double temp = tempFrac1[0];
                tempFrac1[0] = tempFrac1[1];
                tempFrac1[1] = temp;
            }

            lengthsSquared.put(v, Math.pow(tempFrac1[1] - tempFrac1[0], 2));
            //System.out.println("one: " + tempFrac1[0] + ", two: " + tempFrac1[1]);
            double rand1 = (Math.random() - 0.5) * 200;
            l = new Line(lineSegment.pointAlong(tempFrac1[0]).x + deltaX * rand1, lineSegment.pointAlong(tempFrac1[0]).y - (deltaX / deltaY) * rand1, lineSegment.pointAlong(tempFrac1[1]).x + deltaX * rand1, lineSegment.pointAlong(tempFrac1[1]).y - (deltaX / deltaY) * rand1);
            //Main.pane.getChildren().add(l);
        }

        visibleVertices.sort((v1, v2) -> {
            if (lengthsSquared.get(v1) < lengthsSquared.get(v2)) {
                return -1;
            } else if (lengthsSquared.get(v1) > lengthsSquared.get(v2)) {
                return 1;
            } else {
                return 0;
            }
        });

        System.out.println("Time taken for second loop: " + (System.currentTimeMillis() - before));
        before = System.currentTimeMillis();

        /*System.out.println("\nSORTED:\n");
        for (Coordinate v : visibleVertices) {
            System.out.println("one: " + fractionInfo.get(v)[0] + ", two: " + fractionInfo.get(v)[1]);
        }

        System.out.println("\nSTART MERGING:\n");*/

        ArrayList<Coordinate> guardPoints = new ArrayList<>();
        LineSegment ls1, ls2, lsTemp;
        // start with the shortest ("most restricting") segment that needs to be covered
        for (int i = 0; i < visibleVertices.size(); i++) {
            tempFrac1 = fractionInfo.get(visibleVertices.get(i));
            //System.out.println("one: " + tempFrac1[0] + ", two: " + tempFrac1[1] + "\n------------------------------");
            ls1 = new LineSegment(lineSegment.pointAlong(tempFrac1[0]), lineSegment.pointAlong(tempFrac1[1]));
            for (int j = i + 1; j < visibleVertices.size(); j++) {
                //System.out.println("1: j = " + j + ", length: " + visibleVertices.size());
                tempFrac2 = fractionInfo.get(visibleVertices.get(j));
                ls2 = new LineSegment(lineSegment.pointAlong(tempFrac2[0]), lineSegment.pointAlong(tempFrac2[1]));
                if (ls1.getLength() == 0 && ls2.projectionFactor(ls1.getCoordinate(0)) >= 0.0 && ls2.projectionFactor(ls1.getCoordinate(0)) <= 1.0) {
                    //System.out.println("(1) one: " + tempFrac2[0] + ", two: " + tempFrac2[1]);
                    visibleVertices.remove(j);
                    j--;
                } else if (ls2.getLength() > 0 && (lsTemp = ls2.project(ls1)) != null) {
                    //System.out.println("(2) one: " + tempFrac2[0] + ", two: " + tempFrac2[1]);
                    ls1 = lsTemp;
                    visibleVertices.remove(j);
                    j--;
                }
                //System.out.println("2: j = " + j + ", length: " + visibleVertices.size());
            }
            visibleVertices.remove(i);
            i--;
            if (ls1.getLength() > 0.0) {
                l = new Line(ls1.getCoordinate(0).x, ls1.getCoordinate(0).y, ls1.getCoordinate(1).x, ls1.getCoordinate(1).y);
                l.setStrokeWidth(1.5);
                l.setStroke(Color.DARKMAGENTA);
                Main.pane.getChildren().add(l);

                guardPoints.add(new Coordinate(ls1.midPoint().x, ls1.midPoint().y));
            } else {
                Main.pane.getChildren().add(new Circle(ls1.getCoordinate(0).x, ls1.getCoordinate(0).y, 4, Color.DARKMAGENTA));

                guardPoints.add(new Coordinate(ls1.getCoordinate(0).x, ls1.getCoordinate(0).y));
            }
        }
        if (guardPoints.size() == 0) {
            Coordinate midPoint = lineSegment.midPoint();
            guardPoints.add(midPoint);
            l = new Line(separatingLine.getStartX(), separatingLine.getStartY(), separatingLine.getEndX(), separatingLine.getEndY());
            l.setStroke(Color.DARKMAGENTA);
            Main.pane.getChildren().addAll(l/*, new Circle(midPoint.x, midPoint.y, 4, Color.DARKMAGENTA)*/);
        }

        System.out.println("Time taken for third loop: " + (System.currentTimeMillis() - before));
        return new LineGuardManager(separatingLine, guardPoints, map);
    }

    private Tuple<Geometry, Group> computeVisibilityPolygon(Coordinate c1, List<Coordinate> vertices) {
        class PointPair {
            private int index1, index2;

            private PointPair(int index1, int index2) {
                this.index1 = index1;
                this.index2 = index2;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof PointPair && ((((PointPair) o).index1 == this.index1 && ((PointPair) o).index2 == this.index2) || (((PointPair) o).index1 == this.index2 && ((PointPair) o).index2 == this.index1));
            }
        }

        // algorithm (run once for each vertex)
        Geometry visibilityPolygon;
        Group visibilityShape = new Group();
        ;
        visibilityShape.getChildren().add(new Circle(c1.x, c1.y, 4, Color.GREEN));

        LinearRing currentTriangle;
        Line line;
        Label label;
        LineString ls;
        LineSegment[] segs1, segs2;
        Geometry intersection;
        ArrayList<Quintet<Coordinate, Coordinate, LineString, LineSegment[], LineSegment[]>> rays = new ArrayList<>(); // coordinate on the boundary, vertex of the polygon, ray from source to vertex of polygon and coordinate on boundary, intersecting boundary lines of the polygon
        HashMap<Quintet, Double> angles = new HashMap<>();

        ArrayList<PointPair> checkedPairs;
        PointPair currentPair;
        Coordinate[] temp;
        boolean same;
        for (Coordinate c2 : vertices) {
            if (!c1.equals2D(c2) && map.isVisible(c1, c2)) {
                same = false;
                double deltaX = c2.x - c1.x;
                double deltaY = c2.y - c1.y;
                double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                deltaX /= length;
                deltaY /= length;
                ls = new LineString(new CoordinateArraySequence(new Coordinate[]{c2, new Coordinate(c2.x + 1E8 * deltaX, c2.y + 1E8 * deltaY)}), GeometryOperations.factory);
                intersection = ls.intersection(map.getBoundary());

                double curDistanceSquared, minDistanceSquared = Double.MAX_VALUE;
                Coordinate closest = null;
                for (Coordinate c : intersection.getCoordinates()) {
                    if (!c.equals2D(c2)) {
                        curDistanceSquared = Math.pow(c2.x - c.x, 2) + Math.pow(c2.y - c.y, 2);
                        if (curDistanceSquared < minDistanceSquared) {
                            minDistanceSquared = curDistanceSquared;
                            closest = c;
                        }
                    }
                }
                if (closest == null) {
                    same = true;
                    closest = new Coordinate(c2.x, c2.y);
                }

                //if (closest.equals2D(c2) || map.isVisible(c2, new Coordinate(closest.x - deltaX * 1E-10, closest.y - deltaY * 1E-10))) {
                if (closest.equals2D(c2) || map.isVisible(c2, new Coordinate(closest.x - deltaX * 1E-10, closest.y - deltaY * 1E-10))) {
                    /*label.setTranslateX(closest.x + 5);
                    label.setTranslateY(closest.y - 20);*/
                    //Main.pane.getChildren().addAll(new Circle(closest.x, closest.y, 4, Color.BLUE));
                    //} else if (!map.isVisible(c2, new Coordinate(closest.x - deltaX * 1E-10, closest.y - deltaY * 1E-10))) {
                } else if (!map.isVisible(c2, closest)) {
                    same = true;
                    closest = new Coordinate(c2.x, c2.y);
                    //Main.pane.getChildren().addAll(new Circle(closest.x, closest.y, 4, Color.BLUE));
                }

                double angle = Math.toDegrees(Math.atan2(c2.y - c1.y, c2.x - c1.x));
                if (angle < 0) {
                    angle += 360;
                }

                if (closest.equals2D(c2)) {
                    same = true;
                }
                segs1 = new LineSegment[2];
                segs2 = null;
                if (!same) {
                    segs2 = new LineSegment[2];
                }
                for (LineSegment seg : map.getAllLines()) {
                    double distance1 = seg.distance(closest);
                    if (distance1 < GeometryOperations.PRECISION_EPSILON) {
                        if (segs1[0] == null) {
                            segs1[0] = seg;
                        } else if (segs1[1] == null) {
                            segs1[1] = seg;
                        }
                    }
                    if (!same) {
                        double distance2 = seg.distance(c2);
                        if (distance2 < GeometryOperations.PRECISION_EPSILON) {
                            if (segs2[0] == null) {
                                segs2[0] = seg;
                            } else if (segs2[1] == null) {
                                segs2[1] = seg;
                            }
                        }
                    }
                }
                if (same) {
                    segs2 = segs1;
                }

                ls = new LineString(new CoordinateArraySequence(new Coordinate[]{c1, closest}), GeometryOperations.factory);
                rays.add(new Quintet<>(closest, c2, ls, segs1, segs2));
                angles.put(rays.get(rays.size() - 1), angle);
            }
        }

        rays.sort((l1, l2) -> {
            if (angles.get(l1) < angles.get(l2)) {
                return -1;
            } else if (angles.get(l2) < angles.get(l1)) {
                return 1;
            } else {
                return 0;
            }
        });

        Coordinate c3, c4;
        Quintet<Coordinate, Coordinate, LineString, LineSegment[], LineSegment[]> d1, d2;
        LineSegment[] segments = new LineSegment[2];
        Polygon p;

        visibilityPolygon = new com.vividsolutions.jts.geom.Polygon(null, null, GeometryOperations.factory);
        checkedPairs = new ArrayList<>();

        boolean boundaryPointsShareSegment, originalPointsShareSegment, boundaryAndOriginalShareSegment, originalAndBoundaryShareSegment;
        boolean previousBoundaryPointsShareSegment, previousOriginalPointsShareSegment, previousBoundaryAndOriginalShareSegment, previousOriginalAndBoundaryShareSegment;
        ArrayList<Coordinate> coordinates = new ArrayList<>();
        for (int i = 0; i < rays.size(); i++) {
            d1 = rays.get(i);
            d2 = rays.get((i + 1) % rays.size());

            if (i == 0) {
                coordinates.add(c1);
            }
            currentPair = new PointPair(i, (i + 1) % rays.size());

            //try {
            boundaryPointsShareSegment = d1.getValue3()[0].equals(d2.getValue3()[0]) || (d2.getValue3()[1] != null && d1.getValue3()[0].equals(d2.getValue3()[1])) || (d1.getValue3()[1] != null && d1.getValue3()[1].equals(d2.getValue3()[0])) || (d1.getValue3()[1] != null && d2.getValue3()[1] != null && d1.getValue3()[1].equals(d2.getValue3()[1]));
            originalPointsShareSegment = d1.getValue4()[0].equals(d2.getValue4()[0]) || (d2.getValue4()[1] != null && d1.getValue4()[0].equals(d2.getValue4()[1])) || (d1.getValue4()[1] != null && d1.getValue4()[1].equals(d2.getValue4()[0])) || (d1.getValue4()[1] != null && d2.getValue4()[1] != null && d1.getValue4()[1].equals(d2.getValue4()[1]));
            boundaryAndOriginalShareSegment = d1.getValue3()[0].equals(d2.getValue4()[0]) || (d2.getValue4()[1] != null && d1.getValue3()[0].equals(d2.getValue4()[1])) || (d1.getValue3()[1] != null && d1.getValue3()[1].equals(d2.getValue4()[0])) || (d1.getValue3()[1] != null && d2.getValue4()[1] != null && d1.getValue3()[1].equals(d2.getValue4()[1]));
            originalAndBoundaryShareSegment = d1.getValue4()[0].equals(d2.getValue3()[0]) || (d2.getValue3()[1] != null && d1.getValue4()[0].equals(d2.getValue3()[1])) || (d1.getValue4()[1] != null && d1.getValue4()[1].equals(d2.getValue3()[0])) || (d1.getValue4()[1] != null && d2.getValue3()[1] != null && d1.getValue4()[1].equals(d2.getValue3()[1]));
                /*} catch (Exception e) {
                    System.out.println(d1.getValue3()[0]);
                    System.out.println(d1.getValue3()[1]);
                    System.out.println(d1.getValue4()[0]);
                    System.out.println(d1.getValue4()[1]);
                    System.out.println(d2.getValue3()[0]);
                    System.out.println(d2.getValue3()[1]);
                    System.out.println(d2.getValue4()[0]);
                    System.out.println(d2.getValue4()[1]);
                    //System.exit(0);
                    //return null;
                }*/

                /*originalPointsShareSegment = d1.getValue4()[0].equals(d2.getValue4()[0]) || d1.getValue4()[0].equals(d2.getValue4()[1]) || d1.getValue4()[1].equals(d2.getValue4()[0]) || d1.getValue4()[1].equals(d2.getValue4()[1]);
                boundaryAndOriginalShareSegment = d1.getValue3()[0].equals(d2.getValue4()[0]) || d1.getValue3()[0].equals(d2.getValue4()[1]) || d1.getValue3()[1].equals(d2.getValue4()[0]) || d1.getValue3()[1].equals(d2.getValue4()[1]);
                originalAndBoundaryShareSegment = d1.getValue4()[0].equals(d2.getValue3()[0]) || d1.getValue4()[0].equals(d2.getValue3()[1]) || d1.getValue4()[1].equals(d2.getValue3()[0]) || d1.getValue4()[1].equals(d2.getValue3()[1]);*/

            if (originalPointsShareSegment) {
                currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, d1.getValue1(), d2.getValue1(), c1}), GeometryOperations.factory);
                visibilityPolygon = visibilityPolygon.union(currentTriangle);
                coordinates.add(d1.getValue1());
                coordinates.add(d2.getValue1());

                p = new Polygon(c1.x, c1.y, d1.getValue1().x, d1.getValue1().y, d2.getValue1().x, d2.getValue1().y);
                p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                Line l = new Line(d1.getValue1().x, d1.getValue1().y, d2.getValue1().x, d2.getValue1().y);
                l.setStrokeWidth(2);
                l.setStroke(Color.INDIANRED);
                visibilityShape.getChildren().addAll(p, l);
            } else if (boundaryPointsShareSegment) {
                currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, d1.getValue0(), d2.getValue0(), c1}), GeometryOperations.factory);
                visibilityPolygon = visibilityPolygon.union(currentTriangle);
                coordinates.add(d1.getValue0());
                coordinates.add(d2.getValue0());

                p = new Polygon(c1.x, c1.y, d1.getValue0().x, d1.getValue0().y, d2.getValue0().x, d2.getValue0().y);
                p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                Line l = new Line(d1.getValue0().x, d1.getValue0().y, d2.getValue0().x, d2.getValue0().y);
                l.setStrokeWidth(2);
                l.setStroke(Color.INDIANRED);
                visibilityShape.getChildren().addAll(p, l);
            } else if (boundaryAndOriginalShareSegment) {
                currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, d1.getValue0(), d2.getValue1(), c1}), GeometryOperations.factory);
                visibilityPolygon = visibilityPolygon.union(currentTriangle);
                coordinates.add(d1.getValue0());
                coordinates.add(d2.getValue1());

                p = new Polygon(c1.x, c1.y, d1.getValue0().x, d1.getValue0().y, d2.getValue1().x, d2.getValue1().y);
                p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                Line l = new Line(d1.getValue0().x, d1.getValue0().y, d2.getValue1().x, d2.getValue1().y);
                l.setStrokeWidth(2);
                l.setStroke(Color.INDIANRED);
                visibilityShape.getChildren().addAll(p, l);
            } else if (originalAndBoundaryShareSegment) {
                currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, d1.getValue1(), d2.getValue0(), c1}), GeometryOperations.factory);
                visibilityPolygon = visibilityPolygon.union(currentTriangle);
                coordinates.add(d1.getValue1());
                coordinates.add(d2.getValue0());

                p = new Polygon(c1.x, c1.y, d1.getValue1().x, d1.getValue1().y, d2.getValue0().x, d2.getValue0().y);
                p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                Line l = new Line(d1.getValue1().x, d1.getValue1().y, d2.getValue0().x, d2.getValue0().y);
                l.setStrokeWidth(2);
                l.setStroke(Color.INDIANRED);
                visibilityShape.getChildren().addAll(p, l);
            }

            if (i == rays.size() - 1) {
                coordinates.add(c1);
            }

            previousBoundaryPointsShareSegment = boundaryPointsShareSegment;
            previousOriginalPointsShareSegment = originalPointsShareSegment;
            previousBoundaryAndOriginalShareSegment = boundaryAndOriginalShareSegment;
            previousOriginalAndBoundaryShareSegment = originalAndBoundaryShareSegment;

                /*if (map.isVisible(d1.getValue0(), d2.getValue0())) {
                    line = new Line(rays.get(i).getValue0().x, rays.get(i).getValue0().y, rays.get((i + 1) % rays.size()).getValue0().x, rays.get((i + 1) % rays.size()).getValue0().y);
                    *//*Main.pane.getChildren().addAll(new Circle(rays.get(i).getFirst().x, rays.get(i).getFirst().y, 4, Color.RED));
                    Main.pane.getChildren().addAll(line);*//*
                    checkedPairs.add(currentPair);
                    currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(rays.get((i + 1) % rays.size()).getValue0().x, rays.get((i + 1) % rays.size()).getValue0().y), c1}), GeometryOperations.factory);
                    //visibilityPolygon = visibilityPolygon.union(currentTriangle);

                    p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, rays.get((i + 1) % rays.size()).getValue0().x, rays.get((i + 1) % rays.size()).getValue0().y, c1.x, c1.y);
                    p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                    visibilityShape.getChildren().add(p);


                    // check between the original points

                }*/

        }

        /*for (int i = 0; i < rays.size(); i++) {
            segments[0] = null;
            segments[1] = null;

            currentPair = new PointPair(i, (i + 1) % rays.size());
            if (!checkedPairs.contains(currentPair)) {
                double deltaX = rays.get(i).getValue2().getCoordinateN(1).x - rays.get(i).getValue2().getCoordinateN(0).x;
                double deltaY = rays.get(i).getValue2().getCoordinateN(1).y - rays.get(i).getValue2().getCoordinateN(0).y;
                double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                deltaX /= length;
                deltaY /= length;
                c3 = new Coordinate(rays.get(i).getValue0().x - deltaX * 1E-8, rays.get(i).getValue0().y - deltaY * 1E-8);

                deltaX = rays.get((i + 1) % rays.size()).getValue2().getCoordinateN(1).x - rays.get(i).getValue2().getCoordinateN(0).x;
                deltaY = rays.get((i + 1) % rays.size()).getValue2().getCoordinateN(1).y - rays.get(i).getValue2().getCoordinateN(0).y;
                length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                deltaX /= length;
                deltaY /= length;
                c4 = new Coordinate(rays.get((i + 1) % rays.size()).getValue0().x - deltaX * 1E-8, rays.get((i + 1) % rays.size()).getValue0().y - deltaY * 1E-8);

                if (map.isVisible(rays.get(i).getValue0(), rays.get((i + 1) % rays.size()).getValue0())) {
                    line = new Line(rays.get(i).getValue0().x, rays.get(i).getValue0().y, rays.get((i + 1) % rays.size()).getValue0().x, rays.get((i + 1) % rays.size()).getValue0().y);
                    *//*Main.pane.getChildren().addAll(new Circle(rays.get(i).getFirst().x, rays.get(i).getFirst().y, 4, Color.RED));
                    Main.pane.getChildren().addAll(line);*//*
                    checkedPairs.add(currentPair);
                    currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(rays.get((i + 1) % rays.size()).getValue0().x, rays.get((i + 1) % rays.size()).getValue0().y), c1}), GeometryOperations.factory);
                    //visibilityPolygon = visibilityPolygon.union(currentTriangle);

                    p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, rays.get((i + 1) % rays.size()).getValue0().x, rays.get((i + 1) % rays.size()).getValue0().y, c1.x, c1.y);
                    p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                    visibilityShape.getChildren().add(p);
                } else {
                    for (LineSegment seg : map.getBorderLines()) {
                        double distance = seg.distance(rays.get(i).getValue0());
                        if (distance < GeometryOperations.PRECISION_EPSILON) {
                        *//*deltaX = seg.getCoordinate(1).x - seg.getCoordinate(0).x;
                        deltaY = seg.getCoordinate(1).y - seg.getCoordinate(0).y;
                        length = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
                        deltaX /= length;
                        deltaY /= length;
                        line = new Line(rays.get(i).getFirst().x, rays.get(i).getFirst().y, rays.get(i).getFirst().x + deltaX * 10, rays.get(i).getFirst().y + deltaY * 10);
                        Main.pane.getChildren().add(line);
                        line = new Line(rays.get(i).getFirst().x, rays.get(i).getFirst().y, rays.get(i).getFirst().x - deltaX * 10, rays.get(i).getFirst().y - deltaY * 10);
                        Main.pane.getChildren().add(line);
                        Main.pane.getChildren().addAll(new Circle(rays.get(i).getFirst().x, rays.get(i).getFirst().y, 4, Color.GREEN));*//*
                            if (segments[0] == null) {
                                segments[0] = seg;
                            } else {
                                segments[1] = seg;
                                break;
                            }
                        }
                    }
                    double distance1 = Double.MAX_VALUE;
                    if (segments[0] != null) {
                        distance1 = rays.get((i + 1) % rays.size()).getValue2().distance(segments[0].toGeometry(GeometryOperations.factory));
                    }
                    double distance2 = Double.MAX_VALUE;
                    if (segments[1] != null) {
                        distance2 = rays.get((i + 1) % rays.size()).getValue2().distance(segments[1].toGeometry(GeometryOperations.factory));
                    }
                    if (distance1 < distance2 && distance1 < GeometryOperations.PRECISION_EPSILON) {
                        temp = DistanceOp.nearestPoints(rays.get((i + 1) % rays.size()).getValue2(), segments[0].toGeometry(GeometryOperations.factory));
                        line = new Line(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y);
                        //Main.pane.getChildren().add(line);
                        checkedPairs.add(currentPair);
                        currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(temp[0].x, temp[0].y), c1}), GeometryOperations.factory);
                        //visibilityPolygon = visibilityPolygon.union(currentTriangle);

                        p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y, c1.x, c1.y);
                        p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                        visibilityShape.getChildren().add(p);
                    } else if (distance2 < distance1 && distance2 < GeometryOperations.PRECISION_EPSILON) {
                        temp = DistanceOp.nearestPoints(rays.get((i + 1) % rays.size()).getValue2(), segments[1].toGeometry(GeometryOperations.factory));
                        line = new Line(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y);
                        //Main.pane.getChildren().add(line);
                        checkedPairs.add(currentPair);
                        currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(temp[0].x, temp[0].y), c1}), GeometryOperations.factory);
                        //visibilityPolygon = visibilityPolygon.union(currentTriangle);

                        p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y, c1.x, c1.y);
                        p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                        visibilityShape.getChildren().add(p);
                    } else if (distance1 < GeometryOperations.PRECISION_EPSILON && distance2 < GeometryOperations.PRECISION_EPSILON) {
                        distance1 = segments[0].distance(c1);
                        distance2 = segments[1].distance(c1);
                        if (distance1 > distance2) {
                            temp = DistanceOp.nearestPoints(rays.get((i + 1) % rays.size()).getValue2(), segments[0].toGeometry(GeometryOperations.factory));
                        } else {
                            temp = DistanceOp.nearestPoints(rays.get((i + 1) % rays.size()).getValue2(), segments[1].toGeometry(GeometryOperations.factory));
                        }
                        checkedPairs.add(currentPair);
                        currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(temp[0].x, temp[0].y), c1}), GeometryOperations.factory);
                        //visibilityPolygon = visibilityPolygon.union(currentTriangle);

                        p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y, c1.x, c1.y);
                        p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                        visibilityShape.getChildren().add(p);
                    }
                }
            }

            segments[0] = null;
            segments[1] = null;

            currentPair = new PointPair(i, i == 0 ? rays.size() - 1 : i - 1);
            if (!checkedPairs.contains(currentPair)) {
                for (LineSegment seg : map.getBorderLines()) {
                    double distance = seg.distance(rays.get(i).getValue0());
                    if (distance < GeometryOperations.PRECISION_EPSILON) {
                        if (segments[0] == null) {
                            segments[0] = seg;
                        } else {
                            segments[1] = seg;
                            break;
                        }
                    }
                }
                double distance1 = Double.MAX_VALUE;
                if (segments[0] != null) {
                    distance1 = rays.get(i == 0 ? rays.size() - 1 : i - 1).getValue2().distance(segments[0].toGeometry(GeometryOperations.factory));
                }
                double distance2 = Double.MAX_VALUE;
                if (segments[1] != null) {
                    distance2 = rays.get(i == 0 ? rays.size() - 1 : i - 1).getValue2().distance(segments[1].toGeometry(GeometryOperations.factory));
                }
                if (distance1 < distance2 && distance1 < GeometryOperations.PRECISION_EPSILON) {
                    temp = DistanceOp.nearestPoints(rays.get(i == 0 ? rays.size() - 1 : i - 1).getValue2(), segments[0].toGeometry(GeometryOperations.factory));
                    line = new Line(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y);
                    //Main.pane.getChildren().add(line);
                    checkedPairs.add(currentPair);
                    currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(temp[0].x, temp[0].y), c1}), GeometryOperations.factory);
                    //visibilityPolygon = visibilityPolygon.union(currentTriangle);

                    p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y, c1.x, c1.y);
                    p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                    visibilityShape.getChildren().add(p);
                } else if (distance2 < distance1 && distance2 < GeometryOperations.PRECISION_EPSILON) {
                    temp = DistanceOp.nearestPoints(rays.get(i == 0 ? rays.size() - 1 : i - 1).getValue2(), segments[1].toGeometry(GeometryOperations.factory));
                    line = new Line(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y);
                    //Main.pane.getChildren().add(line);
                    checkedPairs.add(currentPair);
                    currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(temp[0].x, temp[0].y), c1}), GeometryOperations.factory);
                    //visibilityPolygon = visibilityPolygon.union(currentTriangle);

                    p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y, c1.x, c1.y);
                    p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                    visibilityShape.getChildren().add(p);
                } else if (distance1 < GeometryOperations.PRECISION_EPSILON && distance2 < GeometryOperations.PRECISION_EPSILON) {
                    distance1 = segments[0].distance(c1);
                    distance2 = segments[1].distance(c1);
                    if (distance1 > distance2) {
                        temp = DistanceOp.nearestPoints(rays.get(i == 0 ? rays.size() - 1 : i - 1).getValue2(), segments[0].toGeometry(GeometryOperations.factory));
                    } else {
                        temp = DistanceOp.nearestPoints(rays.get(i == 0 ? rays.size() - 1 : i - 1).getValue2(), segments[1].toGeometry(GeometryOperations.factory));
                    }
                    checkedPairs.add(currentPair);
                    currentTriangle = new LinearRing(new CoordinateArraySequence(new Coordinate[]{c1, new Coordinate(rays.get(i).getValue0().x, rays.get(i).getValue0().y), new Coordinate(temp[0].x, temp[0].y), c1}), GeometryOperations.factory);
                    visibilityPolygon = visibilityPolygon.union(currentTriangle);

                    p = new Polygon(rays.get(i).getValue0().x, rays.get(i).getValue0().y, temp[0].x, temp[0].y, c1.x, c1.y);
                    p.setFill(Color.YELLOW.deriveColor(1.0, 1.0, 1.0, 0.3));
                    visibilityShape.getChildren().add(p);
                }
            }
        }*/
        return new Tuple<>(visibilityPolygon, visibilityShape);
    }

    private Tuple<ArrayList<DTriangle>, int[][]> findPocketComponent(Line boundaryLine, int componentIndex, double pseudoBlockingVertX, double pseudoBlockingVertY, Line crossedSeparatingLine) {
        // go through all triangles of the current component and find those intersecting the boundary line
        // start to find the rest of the triangles of the pocket component using adjacency matrices
        // if a triangle is adjacent to one of the intersected triangles, test whether the edge connecting them
        // lies to the right or left (clockwise or counter-clockwise) of the boundary line (by checking endpoints)
        double length = Math.sqrt(Math.pow(boundaryLine.getEndX() - boundaryLine.getStartX(), 2) + Math.pow(boundaryLine.getEndY() - boundaryLine.getStartY(), 2));
        double deltaX = (boundaryLine.getStartX() - boundaryLine.getEndX()) / length * 0.001;
        double deltaY = (boundaryLine.getStartY() - boundaryLine.getEndY()) / length * 0.001;
        DPoint approxPosition = null;
        try {
            approxPosition = new DPoint(pseudoBlockingVertX + deltaX, pseudoBlockingVertY + deltaY, 0);
        } catch (DelaunayError e) {
            e.printStackTrace();
        }

        ArrayList<DTriangle> pocketBoundaryTriangles = new ArrayList<>();
        DTriangle currentTriangle = null;
        DPoint currentPoint = null;
        // find the DPoint corresponding to the vertex where the catcher is positioned
        //outer:
        double minDistance = Double.MAX_VALUE, currentDistance;
        for (DTriangle dt : traversalHandler.getComponents().get(componentIndex)) {
            for (DPoint dp : dt.getPoints()) {
                currentDistance = Math.pow(dp.getX() - pseudoBlockingVertX, 2) + Math.pow(dp.getY() - pseudoBlockingVertY, 2);
                if (currentDistance < minDistance) {
                    currentPoint = dp;
                    minDistance = currentDistance;
                }
            }
        }

        for (DTriangle dt : traversalHandler.getComponents().get(componentIndex)) {
            if (dt.contains(approxPosition)) {
                currentTriangle = dt;
                break;
            }
        }

        try {
            System.out.println("Catcher vertex: (" + currentPoint.getX() + "|" + currentPoint.getY() + ")");
        } catch (NullPointerException e) {
            e.printStackTrace();
            AdaptedSimulation.masterPause("in DCRSEntity");
        }

        Polygon plgn;
        plgn = new Polygon(currentTriangle.getPoint(0).getX(), currentTriangle.getPoint(0).getY(), currentTriangle.getPoint(1).getX(), currentTriangle.getPoint(1).getY(), currentTriangle.getPoint(2).getX(), currentTriangle.getPoint(2).getY());
        plgn.setFill(Color.BLUE.deriveColor(1, 1, 1, 0.1));
        catchGraphics.getChildren().add(plgn);
        pocketBoundaryTriangles.add(currentTriangle);
        // add all triangles that are intersected by the boundary line to the list
        LineString lineString = new LineString(new CoordinateArraySequence(new Coordinate[]{new Coordinate(boundaryLine.getStartX(), boundaryLine.getStartY()), new Coordinate(boundaryLine.getEndX(), boundaryLine.getEndY())}), GeometryOperations.factory);
        LinearRing linearRing;
        double distance0, distance1, distance2;
        Coordinate firstCoord;
        for (DTriangle dt : traversalHandler.getComponents().get(componentIndex)) {
            //if (!testInGuardedSquare || (testInGuardedSquare && ((SquareGuardManager) testGuardManagers.get(0)).inGuardedSquare(pseudoBlockingVertX, pseudoBlockingVertY) && gSqrIntersectingTriangles.get(guardManagers.indexOf(testGuardManagers.get(0))).contains(dt))) {
            firstCoord = new Coordinate(dt.getPoint(0).getX(), dt.getPoint(0).getY());
            linearRing = new LinearRing(new CoordinateArraySequence(new Coordinate[]{
                    firstCoord,
                    new Coordinate(dt.getPoint(1).getX(), dt.getPoint(1).getY()),
                    new Coordinate(dt.getPoint(2).getX(), dt.getPoint(2).getY()),
                    firstCoord
            }), GeometryOperations.factory);
            distance0 = Math.sqrt(Math.pow(pseudoBlockingVertX - dt.getPoint(0).getX(), 2) + Math.pow(pseudoBlockingVertY - dt.getPoint(0).getY(), 2));
            distance1 = Math.sqrt(Math.pow(pseudoBlockingVertX - dt.getPoint(1).getX(), 2) + Math.pow(pseudoBlockingVertY - dt.getPoint(1).getY(), 2));
            distance2 = Math.sqrt(Math.pow(pseudoBlockingVertX - dt.getPoint(2).getX(), 2) + Math.pow(pseudoBlockingVertY - dt.getPoint(2).getY(), 2));
            if (dt != currentTriangle && (linearRing.intersects(lineString)) && distance0 > minDistance && distance1 > minDistance && distance2 > minDistance) {
                plgn = new Polygon(dt.getPoint(0).getX(), dt.getPoint(0).getY(), dt.getPoint(1).getX(), dt.getPoint(1).getY(), dt.getPoint(2).getX(), dt.getPoint(2).getY());
                plgn.setFill(Color.GREEN.deriveColor(1, 1, 1, 0.1));
                //catchGraphics.getChildren().add(plgn);
                pocketBoundaryTriangles.add(dt);
            }
        }

        ArrayList<DTriangle> connectingTriangles = new ArrayList<>();
        ArrayList<DEdge> connectingEdges = new ArrayList<>();
        for (DTriangle dt1 : pocketBoundaryTriangles) {
            for (DEdge de : dt1.getEdges()) {
                boolean found = pocketBoundaryTriangles.contains(de.getOtherTriangle(dt1));
                if (!found && (!componentBoundaryEdges.get(componentIndex).contains(de) || separatingEdges.contains(de))) {
                    if (GeometryOperations.leftTurnPredicate(boundaryLine.getStartX(), boundaryLine.getStartY(), boundaryLine.getEndX(), boundaryLine.getEndY(), de.getPointLeft().getX(), de.getPointLeft().getY()) == pocketCounterClockwise &&
                            GeometryOperations.leftTurnPredicate(boundaryLine.getStartX(), boundaryLine.getStartY(), boundaryLine.getEndX(), boundaryLine.getEndY(), de.getPointRight().getX(), de.getPointRight().getY()) == pocketCounterClockwise) {
                        connectingTriangles.add(dt1);
                        connectingEdges.add(de);
                        Line l = new Line(de.getPointLeft().getX(), de.getPointLeft().getY(), de.getPointRight().getX(), de.getPointRight().getY());
                        l.setStroke(Color.BLUE);
                        l.setStrokeWidth(2);
                        catchGraphics.getChildren().add(l);
                    }
                }
            }
        }

        int[][] pocketAdjacencyMatrix = new int[traversalHandler.getAdjacencyMatrix().length][traversalHandler.getAdjacencyMatrix().length];
        ArrayList<DTriangle> pbtClone = new ArrayList<>();
        pbtClone.addAll(pocketBoundaryTriangles);
        ArrayList<DTriangle> currentLayer = new ArrayList<>(), nextLayer;
        boolean unexploredLeft = true;
        for (DTriangle dt : connectingTriangles) {
            currentLayer.add(dt);
            pocketBoundaryTriangles.remove(dt);
        }
        boolean first = true;
        while (unexploredLeft) {
            nextLayer = new ArrayList<>();
            for (DTriangle dt : currentLayer) {
                pocketBoundaryTriangles.add(dt);
                for (int i = 0; i < traversalHandler.getAdjacencyMatrix().length; i++) {
                    // either they are properly adjacent and connected anyway
                    // or they share a separating edge, i.e. a guarding square is connected to the triangle
                    if (pocketAdjacencyMatrix[traversalHandler.getTriangles().indexOf(dt)][i] != 1) {
                        if (traversalHandler.getAdjacencyMatrix()[traversalHandler.getTriangles().indexOf(dt)][i] == 1 && !pocketBoundaryTriangles.contains(traversalHandler.getTriangles().get(i))) {
                            if (!first || (traversalHandler.getTriangles().get(i).equals(connectingEdges.get(connectingTriangles.indexOf(dt)).getOtherTriangle(dt)))) {
                                nextLayer.add(traversalHandler.getTriangles().get(i));
                                pocketAdjacencyMatrix[traversalHandler.getTriangles().indexOf(dt)][i] = 1;
                                pocketAdjacencyMatrix[i][traversalHandler.getTriangles().indexOf(dt)] = 1;
                            }
                        }
                    }
                }
            }
            currentLayer = nextLayer;
            if (nextLayer.size() == 0) {
                unexploredLeft = false;
            }
            first = false;
        }

        for (DTriangle dt1 : pbtClone) {
            for (DTriangle dt2 : pbtClone) {
                //System.out.println("dt1: " + dt1 + "\ndt2: " + dt2 + "\ndt1.getEdge(0).getOtherTriangle(dt1): " + dt1.getEdge(0).getOtherTriangle(dt1) + "\ndt1.getEdge(1).getOtherTriangle(dt1): " + dt1.getEdge(1).getOtherTriangle(dt1) + "\ndt1.getEdge(2).getOtherTriangle(dt1): " + dt1.getEdge(2).getOtherTriangle(dt1));
                if (dt1 != dt2 && ((dt1.getEdge(0).getOtherTriangle(dt1) != null && dt1.getEdge(0).getOtherTriangle(dt1).equals(dt2)) ||
                        (dt1.getEdge(1).getOtherTriangle(dt1) != null && dt1.getEdge(1).getOtherTriangle(dt1).equals(dt2)) || (dt1.getEdge(2).getOtherTriangle(dt1) != null && dt1.getEdge(2).getOtherTriangle(dt1).equals(dt2)))) {
                    pocketAdjacencyMatrix[traversalHandler.getTriangles().indexOf(dt1)][traversalHandler.getTriangles().indexOf(dt2)] = 1;
                    pocketAdjacencyMatrix[traversalHandler.getTriangles().indexOf(dt2)][traversalHandler.getTriangles().indexOf(dt1)] = 1;
                }
            }
        }

        /*DEdge de0, de1, de2;
        Line l;
        for (int i = 1; i < pocketAdjacencyMatrix.length; i++) {
            for (int j = 0; j < i; j++) {
                if (pocketAdjacencyMatrix[i][j] == 1 *//*&& there is actually a separating edge between these triangles, except when the evader actually entered through that separating edge*//*) {
                    de0 = traversalHandler.getTriangles().get(j).getEdge(0);
                    de1 = traversalHandler.getTriangles().get(j).getEdge(1);
                    de2 = traversalHandler.getTriangles().get(j).getEdge(2);
                    *//*if ((Arrays.asList(traversalHandler.getTriangles().get(i).getEdges()).contains(de0) && separatingEdges.contains(de0) && !testCrossedLines.contains(separatingLines.get(separatingEdges.indexOf(de0)))) ||
                            (Arrays.asList(traversalHandler.getTriangles().get(i).getEdges()).contains(de1) && separatingEdges.contains(de1) && !testCrossedLines.contains(separatingLines.get(separatingEdges.indexOf(de1)))) ||
                            (Arrays.asList(traversalHandler.getTriangles().get(i).getEdges()).contains(de2) && separatingEdges.contains(de2) && !testCrossedLines.contains(separatingLines.get(separatingEdges.indexOf(de2))))) {
                        pocketAdjacencyMatrix[i][j] = 0;
                        pocketAdjacencyMatrix[j][i] = 0;
                    }*//*
                    if ((Arrays.asList(traversalHandler.getTriangles().get(i).getEdges()).contains(de0) && separatingEdges.contains(de0)) ||
                            (Arrays.asList(traversalHandler.getTriangles().get(i).getEdges()).contains(de1) && separatingEdges.contains(de1)) ||
                            (Arrays.asList(traversalHandler.getTriangles().get(i).getEdges()).contains(de2) && separatingEdges.contains(de2))) {
                        pocketAdjacencyMatrix[i][j] = 0;
                        pocketAdjacencyMatrix[j][i] = 0;
                    } *//*else {
                        try {
                            l = new Line(traversalHandler.getTriangles().get(i).getBarycenter().getX(), traversalHandler.getTriangles().get(i).getBarycenter().getY(), traversalHandler.getTriangles().get(j).getBarycenter().getX(), traversalHandler.getTriangles().get(j).getBarycenter().getY());
                            l.setStroke(Color.INDIANRED);
                            l.setStrokeWidth(2);
                            catchGraphics.getChildren().add(l);
                        } catch (DelaunayError delaunayError) {
                            delaunayError.printStackTrace();
                        }
                    }*//*
                }
            }
        }*/

        Polygon p;
        for (DTriangle dt : pocketBoundaryTriangles) {
            p = new Polygon(dt.getPoint(0).getX(), dt.getPoint(0).getY(), dt.getPoint(1).getX(), dt.getPoint(1).getY(), dt.getPoint(2).getX(), dt.getPoint(2).getY());
            p.setFill(Color.BLUE.deriveColor(1, 1, 1, 0.1));
            catchGraphics.getChildren().add(p);
        }
        return new Tuple<>(pocketBoundaryTriangles, pocketAdjacencyMatrix);
    }

}
