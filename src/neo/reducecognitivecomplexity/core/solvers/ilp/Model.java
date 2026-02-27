package neo.reducecognitivecomplexity.core.solvers.ilp;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import neo.reducecognitivecomplexity.app.Constants;
import neo.reducecognitivecomplexity.core.Sequence;
import neo.reducecognitivecomplexity.core.Solution;
import neo.reducecognitivecomplexity.core.graphs.ExtractionVertex;
import neo.reducecognitivecomplexity.core.graphs.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.jgrapht.graph.SimpleGraph;

/**
 * The Integer Linear Programming (ILP) Model wrapper.
 * <p>
 * This class translates the refactoring graphs into a mathematical
 * model solvable by CPLEX.
 * </p>
 * <p>
 * <b>Variables:</b>
 * <ul>
 * <li>$X_i$: Binary decision variable. 1 if vertex $i$ is extracted, 0 otherwise.</li>
 * <li>$Z_{ji}$: Auxiliary binary variable. 1 if both ancestor $j$ and descendant $i$ are extracted.</li>
 * </ul>
 * </p>
 * * 
 *
 * @param <V> The vertex type (must extend {@link ExtractionVertex}).
 * @param <E> The edge type.
 */
public class Model<V extends ExtractionVertex, E> {

    private static final Logger LOGGER = Logger.getLogger(Model.class.getName());

    public IloCplex cplex;

    // --- Model Data ---
    private final SimpleGraph<ExtractionVertex, DefaultEdge> conflictGraph;
    private final SimpleDirectedGraph<V, E> graphNoConflicts; // Dependency/Nesting graph
    private final SimpleDirectedGraph<V, E> fullGraph;
    
    private final List<ExtractionVertex> sortedVertices;
    private final Map<ExtractionVertex, Integer> vertexToIndexMap;
    private final int numExtractions;

    // --- Decision Variables ---
    /** Array of X variables (1 per vertex). */
    private IloNumVar[] decisionVariables;
    /** Matrix of Z variables (linearization of X_j * X_i). */
    private Map<ZVariableKey, IloNumVar> zVariables;
    
    /** Stores unique solution binary strings found by the solver. */
    public Set<String> uniqueSolutions;

    /**
     * Constructs the ILP model from the provided graphs.
     *
     * @param conflicts   The graph representing mutually exclusive refactorings.
     * @param noConflicts The graph representing nesting (allows calculating complexity reduction).
     * @param graph       The full graph containing all relationships.
     * @throws IloException If CPLEX initialization fails.
     */
    public Model(SimpleGraph<ExtractionVertex, DefaultEdge> conflicts, 
                 SimpleDirectedGraph<V, E> noConflicts,
                 SimpleDirectedGraph<V, E> graph) throws IloException {

        this.conflictGraph = conflicts;
        this.graphNoConflicts = noConflicts;
        this.fullGraph = graph;
        
        // Pre-process vertices: Sort lexicographically for consistent indexing
        this.sortedVertices = Utils.getVerticesSortedByTheirLexicographicPosition((Graph<ExtractionVertex, E>) fullGraph);
        this.vertexToIndexMap = Utils.mapVerticesToTheirLexicographicPosition((Graph<ExtractionVertex, E>) fullGraph);
        this.uniqueSolutions = new HashSet<>();

        this.cplex = new IloCplex();
        
        // Mute the standard solver chatter (presolve, node logs, etc.)
        cplex.setOut(null);

        // Initialize Variables
        this.numExtractions = graph.vertexSet().size();
        this.zVariables = new HashMap<>(numExtractions * 10); // Capacity hint
        
        
        // Define X variables (Boolean: 0 or 1)
        setDecisionVariables(cplex.boolVarArray(numExtractions));

        // Define Z variables SPARSELY - only for ancestor relationships
        for (int i = 0; i < numExtractions; i++) {
            ExtractionVertex vertexI = sortedVertices.get(i);
            List<ExtractionVertex> ancestors = (List<ExtractionVertex>) Utils.previousVertices(graphNoConflicts, (V) vertexI);
            
            for (ExtractionVertex ancestor : ancestors) {
                int j = vertexToIndexMap.get(ancestor);
                
                // Create Z variable only for this (j,i) pair
                IloNumVar zVar = cplex.boolVar();
                ZVariableKey key = new ZVariableKey(j, i);
                zVar.setName(key.toString());
                zVariables.put(key, zVar);
            }
        }

        buildModel();
    }

    private void buildModel() throws IloException {
        // 1. Structural Constraints
        addConflictConstraints();
        
        // 2. Cognitive Complexity Constraints
        addComplexityConstraints();
        
        // 3. Objective Function
        addObjective();
    }
    
    // Helper method to safely get Z variables
    private IloNumVar getZVariable(int j, int i) {
        IloNumVar z = zVariables.get(new ZVariableKey(j, i));
        if (z == null) {
            // This shouldn't happen if logic is correct
            throw new IllegalStateException("Z variable for (" + j + "," + i + ") not found - are you sure " + j + " is an ancestor of " + i + "?");
        }
        return z;
    }
    
    /**
     * Defines the Objective Function.
     * <p>
     * Current objective: Minimize the total number of extracted methods
     * (Note: The code sums X_i with coefficient 1).
     * </p>
     */
    private void addObjective() throws IloException {
        // Hard constraint: The first vertex (Root/Method Declaration) must always be "selected" (represented as 1)
        // or perhaps index 0 represents the method body itself?
        cplex.addEq(1, this.decisionVariables[0]);

        IloLinearNumExpr objectiveExpr = cplex.linearNumExpr();
        for (int i = 0; i < decisionVariables.length; i++) {
            objectiveExpr.addTerm(1, decisionVariables[i]);
        }
        cplex.addMinimize(objectiveExpr);
    }

    /**
     * Adds constraints related to Cognitive Complexity reduction.
     * <p>
     * Ensures that the refactorings selected actually reduce complexity below the threshold,
     * accounting for the penalty of nested extractions.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private void addComplexityConstraints() throws IloException {
        for (int i = 0; i < sortedVertices.size(); i++) {
            ExtractionVertex vertexI = sortedVertices.get(i);
            IloLinearNumExpr expr = cplex.linearNumExpr();
            
            // Base complexity reduction of extracting vertex I
            int complexityDelta = vertexI.getAccumulatedInherentComponent() 
                                + vertexI.getAccumulatedNestingComponent()
                                - Constants.COGNITIVE_COMPLEXITY_THRESHOLD;
            
            expr.addTerm(complexityDelta, this.decisionVariables[i]);
            
            // Adjust for ancestors (nested extractions)
            List<ExtractionVertex> ancestors = (List<ExtractionVertex>) Utils.previousVertices(graphNoConflicts, (V) vertexI);
            
            for (ExtractionVertex ancestor : ancestors) {
                int indexJ = vertexToIndexMap.get(ancestor);
                
                // Calculate penalty/adjustment if both I and J are extracted
                int sum = ancestor.getAccumulatedInherentComponent() + ancestor.getAccumulatedNestingComponent();
                int distance = Utils.getNestingDistanceBetweenVertices(
                        (SimpleDirectedGraph<ExtractionVertex, E>) this.graphNoConflicts, 
                        sortedVertices.get(indexJ), 
                        vertexI
                );
                int contributors = ancestor.getNumberNestingContributors();
                sum += distance * contributors;

                // Subtract the interaction term: -Sum * Z_ji
                expr.addTerm(-sum, getZVariable(indexJ, i));

                // Enforce Z variable logic (Linearization of AND)
                IloLinearNumExpr zExpr = createLinearizationConstraint(indexJ, i);
                cplex.addLe(zExpr, 0);
            }

            // Main complexity constraint: Total adjusted complexity <= 0
            cplex.addLe(expr, 0);
        }
    }

    /**
     * Creates the constraint linking $Z_{ji}$ to $X_j$ and $X_i$.
     * <p>
     * Handles the logic: If $X_j$ and $X_i$ are true, $Z_{ji}$ must be true,
     * considering intermediate nodes in the path.
     * </p>
     */
    @SuppressWarnings("unchecked")
    private IloLinearNumExpr createLinearizationConstraint(int j, int i) throws IloException {
        IloLinearNumExpr expr = cplex.linearNumExpr();
        
        // Term: Z_ji
        IloNumVar zVar = getZVariable(j, i);
        expr.addTerm(1, zVar);
        
        // Find vertices strictly between J and I
        List<ExtractionVertex> intermediateNodes = Utils.verticesBetweenTwoVertices(
                (Graph<ExtractionVertex, E>) this.graphNoConflicts, 
                sortedVertices.get(i), 
                sortedVertices.get(j)
        );
        
        int pathSize = intermediateNodes.size();

        // Formula: Z_ji * (1 + |L|) - |L| - X_j + Sum(X_k for k in L) <= 0
        // This forces Z_ji to be 0 if the path is broken (i.e., not all intermediates are selected)?
        // Or strictly links them based on path continuity.
        
        expr.addTerm(pathSize, zVar);
        expr.setConstant(-pathSize);
        expr.addTerm(-1, this.decisionVariables[j]);
        
        for (ExtractionVertex k : intermediateNodes) {
            int indexK = vertexToIndexMap.get(k);
            expr.addTerm(1, this.decisionVariables[indexK]);
        }

        return expr;
    }

    /**
     * Adds constraints for mutually exclusive refactorings.
     * <p>
     * If two refactoring candidates conflict (overlap textually), we cannot select both.
     * Constraint: $X_i + X_j \le 1$
     * </p>
     */
    private void addConflictConstraints() throws IloException {
        for (int i = 0; i < sortedVertices.size() - 1; i++) {
            for (int j = i + 1; j < sortedVertices.size(); j++) {
                if (this.conflictGraph.containsEdge(sortedVertices.get(i), sortedVertices.get(j))) {
                    IloLinearNumExpr conflictExpr = cplex.linearNumExpr();
                    conflictExpr.addTerm(1, decisionVariables[i]);
                    conflictExpr.addTerm(1, decisionVariables[j]);
                    cplex.addLe(conflictExpr, 1);
                }
            }
        }
    }

    // --- Solution Extraction & Visualization ---

    /**
     * Extracts a Solution object from the CPLEX result at the given index.
     *
     * @param index                 The index of the solution in the solution pool.
     * @param unit                  The compilation unit.
     * @param compilationUnit       The AST compilation unit.
     * @param methodDeclarationNode The AST node of the method.
     * @return The constructed Solution.
     */
    public Solution getSolution(int index, ICompilationUnit unit, CompilationUnit compilationUnit, ASTNode methodDeclarationNode) {
        List<Sequence> listOfSequences = new ArrayList<>();

        try {
            // Iterate starting from 1 (skipping the root/method node at 0)
            for (int i = 1; i < sortedVertices.size(); i++) {
                double val = cplex.getValue(decisionVariables[i], index);
                
                // Check if variable is 1 (allowing for floating point tolerance)
                if (val > 0.9 && val < 1.1) {
                    ExtractionVertex v = sortedVertices.get(i);
                    int initialOffset = v.getInitialOffset();
                    int length = v.getEndOffset() - initialOffset;
                    int startLine = compilationUnit.getLineNumber(initialOffset);
                    int startColumn = compilationUnit.getColumnNumber(initialOffset);

                    // Use the utility visitor to find the actual AST nodes for this range
                    List<ASTNode> listOfNodes = new neo.reducecognitivecomplexity.core.jdt.Utils.NodeFinderVisitorForGivenSelection(
                            compilationUnit, 
                            compilationUnit.getPosition(startLine, startColumn), 
                            length + 1
                    ).getNodes();

                    listOfSequences.add(new Sequence(compilationUnit, listOfNodes));
                }
            }

        } catch (IloException e) {
            LOGGER.severe("Error extracting solution from CPLEX: " + e.getMessage());
            throw new RuntimeException(e);
        }

        return new Solution(listOfSequences, compilationUnit, methodDeclarationNode);
    }

    /**
     * prints all solutions found in the pool to Standard Output.
     */
    public void showSolutionPop() throws IloException {
        int numSolutions = cplex.getSolnPoolNsolns();
        for (int i = 0; i < numSolutions; i++) {
            showSolution(i);
        }
    }
    
    public void showSolution(int index) {
        try {
            System.out.println("--- Solution " + index + " ---");
            System.out.println("Objective Value: " + (cplex.getObjValue(index) - 1));

            for (int i = 1; i < sortedVertices.size(); i++) {
                double val = cplex.getValue(decisionVariables[i], index);
                if (val > 0.9 && val < 1.1) {
                    System.out.println("  Extracted: " + sortedVertices.get(i));
                }
            }
        } catch (IloException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Counts and displays the number of *unique* optimal solutions found.
     * <p>
     * CPLEX may return multiple solutions with the same objective value. 
     * This method filters them based on the actual set of selected vertices.
     * </p>
     * * @return The count of unique optimal solutions.
     */
    public int showSolutionPopOptima() throws IloException {
        uniqueSolutions.clear();
        double bestObjValue = cplex.getObjValue(); // Get best objective found
        
        for (int x = 0; x < cplex.getSolnPoolNsolns(); x++) {
            // Check if this solution is optimal
            if (Math.abs(cplex.getObjValue(x) - bestObjValue) < 1E-6) {
                
                // Build a binary string signature of the solution
                StringBuilder signature = new StringBuilder();
                signature.append('0'); // Index 0 is always fixed/ignored in signature
                
                for (int i = 1; i < sortedVertices.size(); i++) {
                    double val = cplex.getValue(decisionVariables[i], x);
                    if (val > 0.9 && val < 1.1) {
                        signature.append('1');
                    } else {
                        signature.append('0');
                    }
                }
                uniqueSolutions.add(signature.toString());
            }
        }
        
        // Print unique optimal solutions
        int counter = 1;
        for (String s : uniqueSolutions) {
            System.out.println("Unique Optimal Solution #" + counter);
            // System.out.println("Signature: " + s); 
            for (int j = 1; j < s.length(); j++) {
                if (s.charAt(j) == '1') {
                    System.out.println("  Extracted Node " + j + ": " + sortedVertices.get(j));
                }
            }
            counter++;
        }
        
        return uniqueSolutions.size();
    }

    public void clearModel() {
        try {
            cplex.clearModel();
        } catch (IloException e) {
            throw new RuntimeException("Failed to clear CPLEX model", e);
        }
    }

    public IloNumVar[] getDecisionVariables() {
        return decisionVariables;
    }

    public void setDecisionVariables(IloNumVar[] decisionVariables) {
        this.decisionVariables = decisionVariables;
        for (int i = 0; i < this.decisionVariables.length; i++) {
            this.decisionVariables[i].setName("X_" + i);
        }
    }
}