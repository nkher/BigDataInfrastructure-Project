package INST767.GirvanNewmanGiraph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.giraph.conf.LongConfOption;
import org.apache.giraph.edge.Edge;
import org.apache.giraph.graph.BasicComputation;
import org.apache.giraph.graph.Vertex;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.log4j.Logger;

public class GNNew extends
BasicComputation<LongWritable, DoubleWritable, FloatWritable, DoubleWritable> {

  // private static long MAX_SUPERSTEPS = 40;

  private static long NEXT_NODE = -1;

  private static boolean runpart2 = false;

  private static long LAST_ITER_START = 0;

  private static final int PART_1 = 3;

  private static final int PART_2 = 5;

  public static final LongConfOption MAX_SUPERSTEPS = new LongConfOption(
      "Maximum super steps", 0, "This sets the maximum number of super steps");
  
  public static final LongConfOption PART2_SS0 = new LongConfOption(
      "PART2_SS0 Checker", 0, "Checks if this is the first super step of Part 2");

  /** The shortest paths id */
  public static final LongConfOption SOURCE_ID = new LongConfOption(
      "CreateParentArray.sourceId", 0, "The shortest paths id");

  /** The shortest paths id */
  public static LongConfOption LAST_NODE = new LongConfOption(
      "LastNodeVisited", -1, "The shortest paths id");

  public static LongConfOption PREV_SUPERSTEP = new LongConfOption(
      "PreviousSuperStep", 0, "Saves the number of the previous superstep.");

  public static ArrayList<Integer> sourceNodes = new ArrayList<Integer>();

  public static LongConfOption SOURCE_NODE_INDEX = new LongConfOption(
      "Source node index", 0, "This marks the index of the source in the array list.");
  
  /** Class logger */
  private static final Logger LOG = Logger
.getLogger(GNNew.class);

  /**
   * Is this vertex the source id?
   *
   * @param vertex Vertex
   * @return True if the source id
   */
  private boolean isSource(Vertex<LongWritable, ?, ?> vertex) {
    return vertex.getId().get() == SOURCE_ID.get(getConf());
  }

  public void setMaxSuperSteps() {
    long numberOfVertices = getTotalNumVertices();
    long maxSuperSteps = (PART_1 + PART_2) * numberOfVertices;
    System.out.println("Setting maximum supersteps to : " + maxSuperSteps);
    MAX_SUPERSTEPS.set(getConf(), maxSuperSteps);
  }

  public void readNodes(String fileName) throws IOException {
    System.out.println("Reading source nodes from file into arraylist.");
    File completePath = new File(fileName);
    FileReader fileReader = new FileReader(completePath);
    @SuppressWarnings("resource")
    BufferedReader bReader = new BufferedReader(fileReader);
    String line = "";
    while ((line = bReader.readLine()) != null) {
      int node = Integer.parseInt(line);
      sourceNodes.add(node);
    }
  }

  // Send message to all out edges which are the neighbors of this vertex
  // Message sent is the current vertex's own number
  public void BFSSend(Vertex<LongWritable, DoubleWritable, FloatWritable> vertex) {
    for (Edge<LongWritable, FloatWritable> edge : vertex.getEdges()) {
      sendMessage(edge.getTargetVertexId(), new DoubleWritable(vertex.getId().get()));
    }
  }

  @Override
  public void preSuperstep() {
    // Set the number of supersteps here
    if (getSuperstep() == 0) {
      setMaxSuperSteps();
      try {
        readNodes("SourceNodes");
      } catch (IOException e) {
        e.printStackTrace();
      }
      SOURCE_ID.set(getConf(),sourceNodes.get((int) SOURCE_NODE_INDEX.get(getConf())));
      System.out.println("Set source to : " + SOURCE_ID.get(getConf()));
    }
  };
  
  public void printAllEdgeValues(Vertex<LongWritable, DoubleWritable, FloatWritable> vertex) {
    System.out.println("Vertex is : " + vertex.getId().get() + " and its edges are as follows: ");
    for (Edge<LongWritable, FloatWritable> edge : vertex.getEdges()) {
      System.out.println(edge.toString());
    }
  }


  @Override
  public void postSuperstep() {
    LAST_NODE.set(getConf(), NEXT_NODE);
    PART2_SS0.set(getConf(), 0);
    if ((LAST_ITER_START + PART_1) == getSuperstep()) { // Means part 1 is complete
      runpart2 = true;
      PART2_SS0.set(getConf(), 1);
    }
    if ((getSuperstep() + 1) % (PART_1 + PART_2) == 0) { // Means one iteration (p1 + p2) is complete 
      SOURCE_NODE_INDEX.set(getConf(), (SOURCE_NODE_INDEX.get(getConf()) + 1));
      if (SOURCE_NODE_INDEX.get(getConf()) < getTotalNumVertices()) {
        SOURCE_ID.set(getConf(), sourceNodes.get((int) SOURCE_NODE_INDEX.get(getConf())));
      }
      System.out.println("Set source to : " + SOURCE_ID.get(getConf()));
      runpart2 = false;
      LAST_ITER_START = getSuperstep(); // 7
    }
  };

  @Override
  public void compute(
      Vertex<LongWritable, DoubleWritable, FloatWritable> vertex,
      Iterable<DoubleWritable> messages) throws IOException {

    // End of part 1 and part 2 
    if (getSuperstep() == MAX_SUPERSTEPS.get(getConf())) {
      vertex.voteToHalt();
    } 
    // Part - 2 : Back tracking
    else if (runpart2) {

      if (!(vertex.getId().get() == SOURCE_ID.get(getConf()))) {
        /* *
         * Logic to receive incoming message from children and send message to
         * parent for the next super step
         */
        double finalIncomingMessage = 0;
        for (DoubleWritable msg : messages) {
          finalIncomingMessage += msg.get();
        }

        FloatWritable updatedValue = new FloatWritable(0);
        long vertexParent = (long) vertex.getValue().get();
        LongWritable vrtxParent = new LongWritable(vertexParent);

        /* Updates the parents edge value with incoming message */
        if (finalIncomingMessage > 0 && !(PART2_SS0.get(getConf()) == 1)) {

          updatedValue.set((vertex.getEdgeValue(vrtxParent).get()) + (float) finalIncomingMessage);
          vertex.setEdgeValue(vrtxParent, updatedValue);
          sendMessage(vrtxParent, new DoubleWritable(1.0));

        } else if (PART2_SS0.get(getConf()) == 1) {
          /* Updates the parent edge with zero as this is the first time */
          updatedValue.set((vertex.getEdgeValue(vrtxParent).get()) + (float) 1.0);
          vertex.setEdgeValue(vrtxParent, updatedValue);
          sendMessage(vrtxParent, new DoubleWritable(1.0));
        }

      }
    }
    // Part - 1 : Calculating Parent Array
    else {
      if (getSuperstep() == 0 || ((getSuperstep()) % (PART_1 + PART_2) == 0)) {
        printAllEdgeValues(vertex);
        if (isSource(vertex)) {
          BFSSend(vertex);
        } else {
          vertex.setValue(new DoubleWritable(0));
        }
      } else {
        double messageRecieved = 0;
        System.out.println("VID: " + vertex.getId().get());
        for (DoubleWritable msg : messages) {
            messageRecieved = msg.get();
            break;
        }
          if (!(isSource(vertex)) && vertex.getValue().get() == 0) {
            vertex.setValue(new DoubleWritable(messageRecieved));
          BFSSend(vertex);
          }
      }
    }
  }
}