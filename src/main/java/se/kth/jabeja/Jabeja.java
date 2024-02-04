package se.kth.jabeja;

import org.apache.log4j.Logger;
import se.kth.jabeja.config.Config;
import se.kth.jabeja.config.NodeSelectionPolicy;
import se.kth.jabeja.io.FileIO;
import se.kth.jabeja.rand.RandNoGenerator;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Jabeja {
  final static Logger logger = Logger.getLogger(Jabeja.class);
  private final Config config;
  private final HashMap<Integer/*id*/, Node/*neighbors*/> entireGraph;
  private final List<Integer> nodeIds;
  private int numberOfSwaps;
  private int round;
  private float T;
  private float T_min;
  private int annealingPolicy;
  private boolean resultFileCreated = false;



  //-------------------------------------------------------------------
  public Jabeja(HashMap<Integer, Node> graph, Config config) {
    this.annealingPolicy = config.getAnnealingPolicy();
    this.entireGraph = graph;
    this.nodeIds = new ArrayList(entireGraph.keySet());
    this.round = 0;
    this.numberOfSwaps = 0;
    this.config = config;
    this.T = config.getTemperature();
    this.T_min = config.getAnnealingPolicy() == 0 ? 1f : 0.00001f;
  }


  //-------------------------------------------------------------------
  public void startJabeja() throws IOException {
    for (round = 0; round < config.getRounds(); round++) {
      for (int id : entireGraph.keySet()) {
        sampleAndSwap(id);
      }
      //one cycle for all nodes have completed.

      //check if execution is with restart
      if(config.getRestartPolicy() && round % 400 == 0){
       T = config.getTemperature();
      }
      //reduce the temperature
      saCoolDown();
      report();
    }
  }

  /**
   * Simulated analealing cooling function
   */
  private void saCoolDown(){
    // TODO for second task
    // Linear annealing
    if (annealingPolicy == 0){
      T = T > 1 ? T - config.getDelta() : 1;
    }
    //exponential annealing
    else if (annealingPolicy == 1){
      T = T *  config.getDelta() > T_min ? T *  config.getDelta() : T_min ;
    }
    //https://towardsdatascience.com/optimization-techniques-simulated-annealing-d6a4785a1de7
  }


  /**
   * Sample and swap algorith at node p
   * @param nodeId
   */
  private void sampleAndSwap(int nodeId) {
    // The partners of the node (initially null)
    Node partner = null;
    // nodep is the node that looks for the nbrs and tries to swap colors (nodeId)
    Node nodep = entireGraph.get(nodeId);
    // HYBRID: If local  fails, then perform random sample
    // LOCAL: Looks in their neighbours
    if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
            || config.getNodeSelectionPolicy() == NodeSelectionPolicy.LOCAL) {
      // swap with random neighbors
      // TODO (DONE)
      partner = findPartner(nodeId, getNeighbors(nodep));
    }

    if (config.getNodeSelectionPolicy() == NodeSelectionPolicy.HYBRID
            || config.getNodeSelectionPolicy() == NodeSelectionPolicy.RANDOM) {
      // if local policy fails then randomly sample the entire graph (getSample)
      // TODO (DONE)
      if (partner == null) {
        partner = findPartner(nodeId, getSample(nodeId));
      }
    }

    // swap the colors
    // TODO (DONE)
    if (partner != null) {
      if (nodep.getColor() == partner.getColor()) return;

      int temporary_color = partner.getColor();
      // partner gets the color of node p
      partner.setColor(nodep.getColor());
      // Node p gets the color of partner
      nodep.setColor(temporary_color);
      // Increase the number of swaps
      numberOfSwaps++;
    }
  }

  public Node findPartner(int nodeId, Integer[] nodes) {

    Node nodep = entireGraph.get(nodeId);

    Node bestPartner = null;
    double highestBenefit = 0;

    // TODO
    // for loop through the sample of nodes (nbrs or random sample) of node p
    for (Integer node : nodes) {

      // node q is a node in the sample of nodes
      Node nodeq = entireGraph.get(node);
      // Error criteria
      if (nodep.getColor() == nodeq.getColor()) continue;

      // Number of p nbrs that have color p
      int degree_pp = getDegree(nodep, nodep.getColor());
      // Number of q nbrs that have color q
      int degree_qq = getDegree(nodeq, nodeq.getColor());
      // old part of the energy
      double old_d = Math.pow(degree_pp, config.getAlpha()) + Math.pow(degree_qq, config.getAlpha());
      // Number of p nbrs that have color q
      int degree_pq = getDegree(nodep, nodeq.getColor());
      // Number of q nbrs that have color p
      int degree_qp = getDegree(nodeq, nodep.getColor());
      // new part of the energy
      double new_d = Math.pow(degree_pq, config.getAlpha()) + Math.pow(degree_qp, config.getAlpha());

      if (annealingPolicy == 0) {
        //Cost function
        /*Allows bad swaps by multiplying new_d,
        so if new_d = 2 and t = 2 and old_d = 3
        we still have 4>3 and therefore allow bad swaps
         */
        if (new_d * T > old_d && new_d > highestBenefit) {
          bestPartner = nodeq;
          highestBenefit = new_d;
        }
      } else if (annealingPolicy == 1) {
        //Probablility function as as a new cost functioo
        double accProb = Math.exp((new_d - old_d) / T);
        //How does it work: Some thoughts
        /*
        With d = 0.9
        round 0: e^((5-4)/1) ~ 2.17
        round 0: e^((5-3)/1) ~ 7.38
        round 1: e^((5-4)/0.9) ~ 3
        round 1: e^((5-3/0.9) ~ 9.2
        --> Cost function remains almost same, since bigger ranges have a high impact on prob values.
        --> This is as expected!
        --> True power:
        round 0: e^((4-5/1) ~ 0.36 -> 36 % chance to get accepted
        round 0: e^((4-8/1) ~ 0.02 -> 2 % chance to get accepted
        round 1: e^((4-5/0.9) ~ 0.32 -> 32 % chance to get accepted
        --> Chance for bad swaps get reduced after each cooldown.
        */
        // Check for conditions
        if (accProb > Math.random() && new_d > highestBenefit && new_d != old_d && new_d > highestBenefit) {
          {
            bestPartner = nodeq;
            highestBenefit = new_d;
          }
        }
      }
      return bestPartner;
    }
  }
  /**
   * The the degreee on the node based on color
   * @param node
   * @param colorId
   * @return how many neighbors of the node have color == colorId
   */
  private int getDegree(Node node, int colorId){
    int degree = 0;
    for(int neighborId : node.getNeighbours()){
      Node neighbor = entireGraph.get(neighborId);
      if(neighbor.getColor() == colorId){
        degree++;
      }
    }
    return degree;
  }

  /**
   * Returns a uniformly random sample of the graph
   * @param currentNodeId
   * @return Returns a uniformly random sample of the graph
   */
  private Integer[] getSample(int currentNodeId) {
    int count = config.getUniformRandomSampleSize();
    int rndId;
    int size = entireGraph.size();
    ArrayList<Integer> rndIds = new ArrayList<Integer>();

    while (true) {
      rndId = nodeIds.get(RandNoGenerator.nextInt(size));
      if (rndId != currentNodeId && !rndIds.contains(rndId)) {
        rndIds.add(rndId);
        count--;
      }

      if (count == 0)
        break;
    }

    Integer[] ids = new Integer[rndIds.size()];
    return rndIds.toArray(ids);
  }

  /**
   * Get random neighbors. The number of random neighbors is controlled using
   * -closeByNeighbors command line argument which can be obtained from the config
   * using {@link Config#getRandomNeighborSampleSize()}
   * @param node
   * @return
   */
  private Integer[] getNeighbors(Node node) {
    ArrayList<Integer> list = node.getNeighbours();
    int count = config.getRandomNeighborSampleSize();
    int rndId;
    int index;
    int size = list.size();
    ArrayList<Integer> rndIds = new ArrayList<Integer>();

    if (size <= count)
      rndIds.addAll(list);
    else {
      while (true) {
        index = RandNoGenerator.nextInt(size);
        rndId = list.get(index);
        if (!rndIds.contains(rndId)) {
          rndIds.add(rndId);
          count--;
        }

        if (count == 0)
          break;
      }
    }

    Integer[] arr = new Integer[rndIds.size()];
    return rndIds.toArray(arr);
  }


  /**
   * Generate a report which is stored in a file in the output dir.
   *
   * @throws IOException
   */
  private void report() throws IOException {
    int grayLinks = 0;
    int migrations = 0; // number of nodes that have changed the initial color
    int size = entireGraph.size();

    for (int i : entireGraph.keySet()) {
      Node node = entireGraph.get(i);
      int nodeColor = node.getColor();
      ArrayList<Integer> nodeNeighbours = node.getNeighbours();

      if (nodeColor != node.getInitColor()) {
        migrations++;
      }

      if (nodeNeighbours != null) {
        for (int n : nodeNeighbours) {
          Node p = entireGraph.get(n);
          int pColor = p.getColor();

          if (nodeColor != pColor)
            grayLinks++;
        }
      }
    }

    int edgeCut = grayLinks / 2;

    logger.info("round: " + round +
            ", edge cut:" + edgeCut +
            ", swaps: " + numberOfSwaps +
            ", migrations: " + migrations);

    saveToFile(edgeCut, migrations);
  }

  private void saveToFile(int edgeCuts, int migrations) throws IOException {
    String delimiter = "\t\t";
    String outputFilePath;

    //output file name
    File inputFile = new File(config.getGraphFilePath());
    outputFilePath = config.getOutputDir() +
            File.separator +
            inputFile.getName() + "_" +
            "NS" + "_" + config.getNodeSelectionPolicy() + "_" +
            "GICP" + "_" + config.getGraphInitialColorPolicy() + "_" +
            "T" + "_" + config.getTemperature() + "_" +
            "D" + "_" + config.getDelta() + "_" +
            "RNSS" + "_" + config.getRandomNeighborSampleSize() + "_" +
            "URSS" + "_" + config.getUniformRandomSampleSize() + "_" +
            "A" + "_" + config.getAlpha() + "_" +
            "R" + "_" + config.getRounds() + ".txt";

    if (!resultFileCreated) {
      File outputDir = new File(config.getOutputDir());
      if (!outputDir.exists()) {
        if (!outputDir.mkdir()) {
          throw new IOException("Unable to create the output directory");
        }
      }
      // create folder and result file with header
      String header = "# Migration is number of nodes that have changed color.";
      header += "\n\nRound" + delimiter + "Edge-Cut" + delimiter + "Swaps" + delimiter + "Migrations" + delimiter + "Skipped" + "\n";
      FileIO.write(header, outputFilePath);
      resultFileCreated = true;
    }

    FileIO.append(round + delimiter + (edgeCuts) + delimiter + numberOfSwaps + delimiter + migrations + "\n", outputFilePath);
  }
}
