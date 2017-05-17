package shadowPursuit;

import javafx.geometry.Point2D;

import javafx.scene.effect.Light;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import org.omg.CORBA.DoubleHolder;
import pathfinding.PathVertex;
import simulation.Agent;
import simulation.MapRepresentation;

import javax.sound.midi.SysexMessage;
import java.lang.reflect.Array;
import java.util.ArrayList;

import static additionalOperations.GeometryOperations.*;
import static shadowPursuit.shadowOperations.*;

/**
 * Created by Robins on 30.04.2017.
 */
public class ShadowGraph {


    private ArrayList<ShadowNode> Nodes;
    private ArrayList<Line> polygonEdges;

    private Polygon environment;
    private ArrayList<Polygon> obstacles;
    private ArrayList<Polygon> allPolygons;

    private ArrayList<Point2D> agents;


    public ShadowGraph(MapRepresentation map, ArrayList<Point2D> agents) {
        environment = map.getBorderPolygon();
        obstacles = map.getObstaclePolygons();

        allPolygons = map.getAllPolygons();
        this.agents = agents;

        generateType1();
        generateT1Connections();
    }

    //used for testpurpoes
    public ShadowGraph(Polygon environment, ArrayList<Polygon> Obstacles, ArrayList<Point2D> agents) {
        this.environment = environment;
        this.obstacles = Obstacles;

        allPolygons = new ArrayList<>();
        allPolygons.add(environment);
        allPolygons.addAll(Obstacles);
        polygonEdges = new ArrayList<>();

        Line newLine;
        for (Polygon p : allPolygons) {
            for (int i = 0; i < p.getPoints().size(); i += 2) {

                newLine = new Line(p.getPoints().get(i), p.getPoints().get(i + 1), (p.getPoints().get((i + 2) % p.getPoints().size())), (p.getPoints().get((i + 3) % p.getPoints().size())));
                //System.out.println("new Line added = " + newLine);
                polygonEdges.add(newLine);
            }
        }

        for(Point2D point : polyToPoints(environment))  {
            System.out.println(point);
        }


        this.agents = agents;

        Nodes = new ArrayList<>();
        generateType1();

        generateT1Connections();
        //circleDetect();
        calcT2Points();
        printGraph();

        //calculateType3();
        //printGraph();
        //getType2();

        //printGraph();
    }


    //@TODO problem for objects that are entierly in shadow
    public void calcT2Points()    {
        ArrayList<Point2D> temp, T2Points;
        T2Points = new ArrayList<>();
        Point2D tmpPoint;
        ShadowNode tmpNode, tmp2Node, tNode;
        ArrayList<Point2D> pointy = findReflex(environment,allPolygons, obstacles);
        System.out.println("pointy points :D = " + pointy);


        for(int i = 0; i < Nodes.size(); i++)    {
            tNode = Nodes.get(i);
            //System.out.println("For Node = " + tNode);


            temp = new ArrayList<>();
            if((tNode.prev == null || tNode.next == null) && tNode.getType() == 1)  {
                temp = getAdjacentPoints(tNode.getPosition(), allPolygons);
                //System.out.println("For = " + tNode.getPosition() + "\tAdjacent: " + temp);
                if(tNode.next == null)   {
                    //false
                    //System.out.println("tNode = " + tNode);
                    tmpPoint = tNode.prev.getPosition();
                    if(temp.get(0) == tmpPoint) {
                        if(pointy.contains(temp.get(0))) {
                            Nodes.add(new ShadowNode(temp.get(0), tNode));
                        }
                    }
                    else    {
                        if(pointy.contains(temp.get(1))) {
                            Nodes.add(new ShadowNode(temp.get(1), tNode));
                        }
                    }
                }
                else if(tNode.prev == null)   {
                    //Correct
                    tmpPoint = tNode.next.getPosition();
                    if(temp.get(0) == tmpPoint) {
                        if(pointy.contains(temp.get(1))) {
                            Nodes.add(new ShadowNode(temp.get(1), tNode));
                        }
                    }
                    else    {
                        if(pointy.contains(temp.get(0))) {
                            Nodes.add(new ShadowNode(temp.get(0), tNode));
                        }
                    }


                    /*
                    if(temp.get(0) == tmpPoint && pointy.contains(temp.get(1))) {
                        Nodes.add(new ShadowNode(temp.get(1), tNode));
                    }
                    else if(temp.get(1) == tmpPoint && pointy.contains(temp.get(0)))
                        Nodes.add(new ShadowNode(temp.get(0), tNode));
                    }
                     */
                }

            }
            //System.out.println("i = " + i);
        }
    }

    public void generateType1() {
        ArrayList<Point2D> t1 = getX1Points(environment, obstacles, agents);

        ShadowNode temp;
        for (Point2D point : t1) {


            temp = new ShadowNode(point);
            Nodes.add(temp);
        }


    }


    public void generateT1Connections() {
        ShadowNode start, left, right;
        Polygon tempPoly;
        Point2D leftP, rightP;
        ArrayList<Point2D> tempPoints;




        for (int i = 0; i < Nodes.size(); i++) {
            start = Nodes.get(i);


            if (start.prev == null || start.next == null) {
                //System.out.println("Entered");
                tempPoints = getAdjacentPoints(start.getPosition(), allPolygons);
                if (tempPoints.size() == 0) {
                    System.exit(234567);
                } else {
                    leftP = tempPoints.get(0);
                    rightP = tempPoints.get(1);
                    for (int j = 0; j < Nodes.size(); j++) {
                        ShadowNode node = Nodes.get(j);
                        if (node.getPosition().getX() == leftP.getX() && node.getPosition().getY() == leftP.getY()) {
                            start.prev = node;
                        } else if (node.getPosition() == rightP) {
                            node.next = start;
                        }
                        else if(node.getPosition().getX() == rightP.getX() && node.getPosition().getY() == rightP.getY())   {
                            start.next = node;
                            node.prev = start;
                        }
                    }

                }
            }

        }


    }


    public void printGraph()    {
        ArrayList<ShadowNode> printed = new ArrayList<>();

        ShadowNode start,start2, tmp;
        //for(int i = 0; i < copied.size())
        //ShadowNode temp = copied.get(0);
        double sX, sY, s2X, s2Y, tmpX, tmpY;


        for(int i = 0; i < Nodes.size(); i++)  {
            start = Nodes.get(i);
            sX = start.getPosition().getX();
            sY = start.getPosition().getY();


            if(printed.size() == 0 || !printed.contains(start)) {

                if(start.next != null || start.prev != null)   {
                    //get to start
                    tmp = start.prev;
                    boolean cycle = false;

                    if(tmp != null) {
                        tmpX = tmp.getPosition().getX();
                        tmpY = tmp.getPosition().getY();
                        while (tmp.prev != null) {
                            if (tmpX == sX && tmpY == sY) {
                                System.out.println("Cycle detected");
                                if(tmp.getType() == 1 && start.getType()  == 1) {
                                    System.out.println("entire Obstacle in Shadow");
                                }
                                cycle = true;
                                break;
                            }
                            tmp = tmp.prev;
                            tmpX = tmp.getPosition().getX();
                            tmpY = tmp.getPosition().getY();
                        }
                    }
                    else    {
                        tmp = start;
                        tmpX = tmp.getPosition().getX();
                        tmpY = tmp.getPosition().getY();
                    }

                    //At this point we assume either we are in a cycle or we are at the beginning
                    int j = 1;
                    start2 = tmp;

                    s2X = start2.getPosition().getX();
                    s2Y = start2.getPosition().getY();

                    System.out.println("Beginning: " + start2);
                    printed.add(start2);

                    tmp = start2.next;
                    tmpX = tmp.getPosition().getX();
                    tmpY = tmp.getPosition().getY();


                    while((tmpX != s2X || tmpY != s2Y) && tmp != null)    {

                        printed.add(tmp);
                        if(cycle && printed.get(printed.size()-1).next == null) {
                            start2.prev.setNext(start2);
                        }
                        System.out.println(tmp);
                        tmp = tmp.next;
                        if(tmp != null) {
                            tmpX = tmp.getPosition().getX();
                            tmpY = tmp.getPosition().getY();
                        }
                    }


                    if(tmpX == s2X && tmpY == s2Y)   {
                        //System.out.println(tmp);
                        printed.add(tmp);
                    }

                    System.out.println("End hit");
                    System.out.println("\n");

                }
                else    {
                    System.out.println(start);
                    printed.add(start);
                    System.out.println("\n");
                }



            }


        }
    }



    public void circleDetect()   {
        ArrayList<ShadowNode> printed = new ArrayList<>();


        ShadowNode start,start2, tmp;
        //for(int i = 0; i < copied.size())
        //ShadowNode temp = copied.get(0);
        double sX, sY, s2X, s2Y, tmpX, tmpY;


        for(int i = 0; i < Nodes.size(); i++)  {
            start = Nodes.get(i);
            sX = start.getPosition().getX();
            sY = start.getPosition().getY();

            //System.out.println("Start Node = " + start);

            /*for(int j = 0; j < printed.size(); j++) {
                System.out.println(printed.get(j));
            }
            System.out.println("i = " + i + "\tPrinted countains " + start + " = " + printed.contains(start));
            if(sX == 766 && sY == 213)  {
                System.out.println("Prev = " + start.prev + "\tNext = " + start.next);
                System.out.println("Link left = " + start.prev.next);
            }*/

            if(printed.size() == 0 || !printed.contains(start)) {

                if(start.next != null || start.prev != null)   {
                    //get to start
                    tmp = start.prev;
                    boolean cycle = false;

                    if(tmp != null) {
                        tmpX = tmp.getPosition().getX();
                        tmpY = tmp.getPosition().getY();
                        while (tmp.prev != null) {
                            if (tmpX == sX && tmpY == sY) {
                                cycle = true;
                                break;
                            }
                            tmp = tmp.prev;
                            tmpX = tmp.getPosition().getX();
                            tmpY = tmp.getPosition().getY();
                        }
                    }
                    else    {
                        tmp = start;
                        tmpX = tmp.getPosition().getX();
                        tmpY = tmp.getPosition().getY();
                    }

                    //At this point we assume either we are in a cycle or we are at the beginning
                    int j = 1;
                    start2 = tmp;

                    s2X = start2.getPosition().getX();
                    s2Y = start2.getPosition().getY();

                    printed.add(start2);

                    tmp = start2.next;
                    tmpX = tmp.getPosition().getX();
                    tmpY = tmp.getPosition().getY();


                    while((tmpX != s2X || tmpY != s2Y) && tmp != null)    {

                        printed.add(tmp);
                        if(cycle && printed.get(printed.size()-1).next == null) {
                            start2.prev.setNext(start2);
                        }
                        //System.out.println(tmp);
                        tmp = tmp.next;
                        if(tmp != null) {
                            tmpX = tmp.getPosition().getX();
                            tmpY = tmp.getPosition().getY();
                        }
                    }


                    if(tmpX == s2X && tmpY == s2Y)   {
                        //System.out.println(tmp);
                        printed.add(tmp);
                    }


                }
                else    {
                    System.out.println(start);
                    printed.add(start);
                    System.out.println("\n");
                }
            }
        }
    }

    //TODO @Rob - keep working here
    public void calculateType3()    {
        ArrayList<Point2D> Type3 = new ArrayList<>();
        ArrayList<Point2D> tempList;
        Point2D tempPoint;

        double agentX, agentY, pointX, pointY;
        shadowPursuit.ShadowNode tmp;
        Line tmpLine, Ray;



        double maxYDist, maxXDist, maxY, maxX, minX, minY, rayLength;

        maxYDist = 0;
        maxXDist = 0;

        maxX = Double.MIN_VALUE;
        maxY = Double.MIN_VALUE;

        minX = Double.MAX_VALUE;
        minY = Double.MAX_VALUE;

        Line newL;


        //System.out.println("Before calculation: ");
        for(int i =0; i < Nodes.size(); i++)    {
            tmp = Nodes.get(i);
            if(tmp.getType() == 2){
                //System.out.println(tmp.getPosition());
                newL = new Line(tmp.getPosition().getX(), tmp.getPosition().getY(), agents.get(0).getX(), agents.get(0).getY());
                //System.out.println("Visible? => " + isVisible(tmp.getPosition().getX(), tmp.getPosition().getY(), agents.get(0).getX(), agents.get(0).getY(),polygonEdges));
            }

            if(tmp.getPosition().getX() < minX) {
                minX = tmp.getPosition().getX();
                maxXDist = maxX - minX;
            }
            else if(tmp.getPosition().getX() > maxX) {
                maxX = tmp.getPosition().getX();
                maxXDist = maxX - minX;
            }

            if(tmp.getPosition().getY() < minY) {
                minX = tmp.getPosition().getY();
                maxYDist = maxY - minY;
            }
            else if(tmp.getPosition().getY() > maxY) {
                minX = tmp.getPosition().getY();
                maxYDist = maxY - minY;
            }

        }

        if(maxYDist < maxXDist) {
            rayLength = maxXDist * 2;
        }
        else    {
            rayLength = maxYDist * 2;
        }


        //For every agent that  has a straight line visibility to a point
        for(Point2D agent : agents)   {

            agentX = agent.getX();
            agentY = agent.getY();

            //For every point of Type2
            for(int i =0; i < Nodes.size(); i++)    {
                tmp = Nodes.get(i);
                if(tmp.getType() == 2) {
                    tempList = new ArrayList<>();
                    pointX = tmp.getPosition().getX();
                    pointY = tmp.getPosition().getY();
                    //tmpLine = new Line(agentX, agentY, pointX, pointY);

                    if(isVisible(agentX, agentY, pointX, pointY, polygonEdges)) {

                        //Create occlusion Ray
                        System.out.println("For Agent = " + agent + " and Point = " + tmp.getPosition());
                        Ray = scaleRay(agent, tmp, rayLength);
                        System.out.println("RAY = " + Ray);

                        Line original = new Line(agentX, agentY, tmp.getPosition().getX(), tmp.getPosition().getY());
                        System.out.println("Gradient1 = " + gradient(original) + "\tGradient2 = " + gradient(Ray));

                        Point2D posT3 = getT3Intersect(Ray);



                        System.out.println("");
                        addT3ToGraph(tmp,posT3);
                        Type3.add(posT3);



                    }
                    else    {
                        System.out.println("Not visible");
                    }

                }
            }

            if(Type3.size() == 0)   {
                System.exit(16252);
            }

            for(Point2D point : Type3)  {

            }



        }



    }


    public void addT3ToGraph(ShadowNode t2, Point2D t3)   {


        ShadowNode pT2, newNode;
        //Line tLine;

        Point2D start, end;



        for(Line tLine : polygonEdges)  {
            if(onLine(t3, tLine))   {

                //get both sides
                start = new Point2D(tLine.getStartX(), tLine.getStartY());
                end = new Point2D(tLine.getEndX(), tLine.getEndY());

                boolean found = false;

                //Check which ones is a Type1 Node
                for(ShadowNode tNode : Nodes) {
                    if((tNode.getPosition().getX() == start.getX() && tNode.getPosition().getY() == start.getY())|| (tNode.getPosition().getX() == end.getX() && tNode.getPosition().getY() == end.getY()))    {
                        found = true;
                        if(tNode.getNext() == null) {
                            newNode = new ShadowNode(t3, tNode, t2, true);
                            Nodes.add(newNode);
                        }
                        else if(tNode.getPrev() == null)   {
                            newNode = new ShadowNode(t3, t2, tNode, true);
                            Nodes.add(newNode);
                        }
                        else if(tNode.getNext() != null && tNode.getNext().getNext() == t2) {
                            if(distance(tNode.getPosition(), t3) < distance(tNode.getPosition(), tNode.getNext().getPosition()))    {
                                newNode = new ShadowNode(t3, tNode, t2, true);
                                Nodes.add(newNode);
                            }
                        }
                        else if(tNode.getPrev() != null && tNode.getPrev().getPrev() == t2) {
                            if(distance(tNode.getPosition(), t3) < distance(tNode.getPosition(), tNode.getPrev().getPosition()))    {
                                newNode = new ShadowNode(t3, t2, tNode, true);
                                Nodes.add(newNode);
                            }
                        }
                        break;
                    }
                }

                if(found)
                    break;



            }
        }


        /*
        for(int i = 0; i < Nodes.size() - 1; i++)   {
            tNode = Nodes.get(i);

            if(tNode.getNext() != null && tNode.getNext().getType() == 2)   {
                tLine = new Line(tNode.getPosition().getX(),tNode.getPosition().getY(), tNode.getNext().getPosition().getX(),tNode.getNext().getPosition().getY());
                if(onLine(t3, tLine))   {
                    System.out.println("Entered 1\n T3 = " + t3 + "\nPREV: " + tNode + "\nNEXT: " + t2 + "\n");
                    newNode = new ShadowNode(t3, t2, tNode, true);
                    Nodes.add(newNode);
                }
            }
            else if(tNode.getPrev() != null && tNode.getPrev().getType() == 2)    {

                tLine = new Line(tNode.getPosition().getX(),tNode.getPosition().getY(), tNode.getPrev().getPosition().getX(),tNode.getPrev().getPosition().getY());
                if(onLine(t3, tLine))   {
                    System.out.println("Entered 2");
                    newNode = new ShadowNode(t3, tNode, t2, true);
                    Nodes.add(newNode);
                }
            }



        }

        */

    }


    public void getType2()    {
        ArrayList<ShadowNode> t2 = new ArrayList<>();

        for(ShadowNode node : Nodes)    {
            if(node.getType() == 2) {
                t2.add(node);
            }
        }

        for(ShadowNode node: t2)    {
            System.out.println(node);
        }
    }

    public Point2D getT3Intersect(Line ray)    {
        System.out.print("Passed ray = " + ray);

        ArrayList<Point2D> intersectPoints = new ArrayList<>();
        Line tmpLine;
        Point2D tmpPoint;
        double dist = 0;
        int minPos = 0;

        for(Line inLine : polygonEdges)    {
             if(lineIntersect(inLine, ray)) {
                System.out.println("INTERSECT DETECTED");
                intersectPoints.add(FindIntersection(inLine,ray));
                System.out.println("AT = " + intersectPoints.get(intersectPoints.size()-1) + "\tWITH = " + inLine);
            }
        }

        double min = Double.MAX_VALUE;
        for(int i = 0; i < intersectPoints.size(); i++) {
            tmpPoint = intersectPoints.get(i);
            tmpLine = new Line(ray.getStartX(), ray.getStartY(), tmpPoint.getX(), tmpPoint.getY());
            dist = Math.sqrt(Math.pow((tmpLine.getEndX() - tmpLine.getStartX()), 2) + Math.pow((tmpLine.getEndY() - tmpLine.getStartY()), 2));

            if(dist < min)  {
                minPos = i;
                min=dist;
            }

        }

        if(intersectPoints.size() > 0)
            return intersectPoints.get(minPos);
        else
            return null;
    }


}
