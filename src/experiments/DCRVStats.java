package experiments;

import com.vividsolutions.jts.geom.Coordinate;

import java.util.Arrays;
import java.util.stream.IntStream;

public class DCRVStats {

    private int nrEvaders;
    private int nrPursuers;
    private int currentEvader, what;
    public Coordinate[] initEvaderPositions;
    public Coordinate[] initPursuerPositions;
    public int[] nrLeafRuns;
    public int[] nrSteps;
    public boolean[] caughtBySearcher;
    public boolean[] caughtAfterGuardPositioning;

    public DCRVStats(int nrEvaders, int nrPursuers) {
        this.nrEvaders = nrEvaders;
        this.nrPursuers = nrPursuers;
        currentEvader = 0;
        what = 0;
        initEvaderPositions = new Coordinate[nrEvaders];
        initPursuerPositions = new Coordinate[nrPursuers];
        nrLeafRuns = new int[nrEvaders];
        nrSteps = new int[nrEvaders];
        caughtBySearcher = new boolean[nrEvaders];
        caughtAfterGuardPositioning = new boolean[nrEvaders];
    }

    public void print() {
        System.out.println("Number of evaders: " + nrEvaders);
        System.out.println("Initial evader positions: ");
        System.out.println(Arrays.toString(initEvaderPositions));
        /*for (int i = 0; i < initEvaderPositions.length; i++) {
            System.out.println((i + 1) + ": (" + initEvaderPositions[i].x + "|" + initEvaderPositions[i].y + ")");
        }*/
        System.out.println("Number of pursuers: " + nrPursuers);
        System.out.println(Arrays.toString(initPursuerPositions));
        /*System.out.println("Initial pursuer positions: ");
        for (int i = 0; i < initPursuerPositions.length; i++) {
            System.out.println((i + 1) + ": (" + initPursuerPositions[i].x + "|" + initPursuerPositions[i].y + ")");
        }*/
        System.out.println("Total number of leaf runs: " + IntStream.of(nrLeafRuns).sum());
        System.out.println("Number of leaf runs per evader: ");
        for (int i = 0; i < nrLeafRuns.length; i++) {
            System.out.println((i + 1) + ": " + nrLeafRuns[i]);
        }
        System.out.println("Number of steps per evader: ");
        for (int i = 0; i < nrSteps.length; i++) {
            System.out.println((i + 1) + ": " + nrSteps[i]);
        }
        System.out.println("Caught by searcher?: ");
        for (int i = 0; i < caughtBySearcher.length; i++) {
            System.out.println((i + 1) + ": " + caughtBySearcher[i]);
        }
    }

    public int getCounter() {
        return currentEvader;
    }

    public void targetCaught() {
        currentEvader++;
    }

    public void increaseNrLeafRuns() {
        nrLeafRuns[currentEvader % nrEvaders]++;
    }

    public void increaseNrSteps() {
        nrSteps[currentEvader % nrEvaders]++;
    }

    public void setCaughtBySearcher(boolean caughtBySearcher) {
        this.caughtBySearcher[currentEvader % nrEvaders] = caughtBySearcher;
    }
}
