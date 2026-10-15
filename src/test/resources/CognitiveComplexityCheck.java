import java.awt.Component;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;

import javax.swing.JComponent;
import javax.swing.border.Border;

import FileDrop.Listener;

class CognitiveComplexityCheck {


  public int ternaryOp(int a, int b) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 2 to the 0 allowed.}}
//           ^^^^^^^^^

    int c = a>b?b:a;
//             ^<

    return c>20?4:7;
//             ^<

  }

  public boolean extraConditions() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 3 to the 0 allowed.}}
    return a && b || foo(b && c);
  }
  public boolean extraConditions2() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 2 to the 0 allowed.}}
    return a && (b || c) || d;
  }
  public void extraConditions3() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 3 to the 0 allowed.}}
    if (a && b || c || d) {}
  }
  public void extraConditions4() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 5 to the 0 allowed.}}
    if (a && b || c && d || e) {}
  }
  public void extraConditions5() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 5 to the 0 allowed.}}
    if (a || b && c || d && e) {}
  }
  public void extraConditions6() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 3 to the 0 allowed.}}
    if (a && b && c || d || e) {}
  }
  public void extraConditions7() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 1 to the 0 allowed.}}
    if (a) {}
  }
  public void extraConditions8() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 2 to the 0 allowed.}}
    if (a && b && c && d && e) {}
  }
  public void extraConditions9() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 2 to the 0 allowed.}}
      if (a || b || c || d || e) {}
  }
  public void extraCondition10() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 4 to the 0 allowed.}}
    if (a && b && c || d || e && f){}
  }


  public void switch2(){ // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 12 to the 0 allowed.}}
//            ^^^^^^^

    switch(foo){                              //+1
//  ^^^^^^<
      case 1:
        break;
      case ASSIGNMENT:
        if (lhs.is(Tree.Kind.IDENTIFIER)) {   //+2 (nesting=1)
//      ^^<
          if (a && b && c || d) {             //+5 (nesting=2)
//        ^^<   ^^<       ^^<

          }

          if(element.is(Tree.Kind.ASSIGNMENT)) { //+3 (nesting=2)
//        ^^<
            out.remove(symbol);
          } else {                               //+1
//          ^^^^<
            out.add(symbol);
          }
        }
        break;
    }
  }

  public void extraCondition11() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 2 to the 0 allowed.}}
    if (a || (b || c)) {}
  }

  public void extraConditions12() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 7 to the 0 allowed.}}
            //^^^^^^^^^^^^^^^^^
    if (     // +1
//  ^^<
      a
      && b   // +1 - secondary on each first operator of a new sequence
//    ^^<
      && c
      || d   // +1
//    ^^<
      || e
      && f   // +1
//    ^^<
      && g
      || (h  // +1
//    ^^<
      || (i
      && j   // +1
//    ^^<
      || k)) // +1 - parentheses completely ignored
//    ^^<
      || l
      || m
      ){}
  }

  public void breakWithLabel(java.util.Collection<Boolean> objects) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 2 to the 0 allowed.}}
    doABarrelRoll:
    for(Object o : objects) { // +1
      break doABarrelRoll;    // +1
    }
  }

  public void doFilter(ServletRequest servletRequest) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 13 to the 0 allowed.}}

    if (consumedByStaticFile) {                             // 1
      return;
    }

    try {

    } catch (HaltException halt) {                          // 1

    } catch (Exception generalException) {                  // 1

    }

    if (body.notSet() && responseWrapper.isRedirected()) {  // 2
      body.set("");
    }

    if (body.notSet() && hasOtherHandlers) {                // 2
      if (servletRequest instanceof HttpRequestWrapper) {   // 2 (nesting=1)
        ((HttpRequestWrapper) servletRequest).notConsumed(true);
        return;
      }
    }

    if (body.notSet() && !externalContainer) {               // 2
      LOG.info("The requested route [" + uri + "] has not been mapped in Spark");
    }

    if (body.isSet()) {                                      // 1
      body.serializeTo(httpResponse, serializerChain, httpRequest);
    } else if (chain != null) {                              // 1
      chain.doFilter(httpRequest, httpResponse);
    }
  }


  public final T to(U u) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 7 to the 0 allowed.}}

    for (int ctr=0; ctr<args.length; ctr++)
      if (args[ctr].equals("-debug"))
        debug = true ;

    for (int i = chain.length - 1; i >= 0; i--)
      result = chain[i].to(result);

    if (foo)
      for (int i = 0; i < 10; i++)
        doTheThing();

    return (T) result;
  }


  static boolean enforceLimits(BoundTransportAddress boundTransportAddress) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 1 to the 0 allowed.}}
    Iterable<JoinTuple> itr = () -> new JoinTupleIterator(tuples.tuples(), parentIndex, parentReference);

    Predicate<TransportAddress> isLoopbackOrLinkLocalAddress = t -> t.address().getAddress().isLinkLocalAddress()
            || t.address().getAddress().isLoopbackAddress();

  }

  String bulkActivate(Iterator<String> rules) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 6 to the 0 allowed.}}

    try {
      while (rules.hasNext()) {  // +1
        try {
          if (!changes.isEmpty()) {  }  // +2, nesting 1
        } catch (BadRequestException e) { }  // +2, nesting 1
      }
    } finally {
      if (condition) {  // +1
        doTheThing();
      }
    }
    return result;
  }

  private static String getValueToEval( Measure.Level alertLevel, Color foo ) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 6 to the 0 allowed.}}

    if (Measure.Level.ERROR.equals(alertLevel) // +1
      && foo == YELLOW) {   // +1
      return condition.getErrorThreshold();
    } else if (Measure.Level.WARN.equals(alertLevel)) {             // 1
      return condition.getWarningThreshold();
    } else {                                                        // 1
      while (true) {                                                // 2 (nesting = 1)
        doTheThing();
      }
      throw new IllegalStateException(alertLevel.toString());
    }
  }

  boolean isPalindrome(char [] s, int len) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 3 to the 0 allowed.}}

    if(len < 2)
      return true;
    else
      return s[0] == s[len-1] && isPalindrome(s[1], len-2); // TODO find recursion
  }

  void extraConditions() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 10 to the 0 allowed.}}

    if (a < b) {                // 1
      doTheThing();
    }

    if (a == b || c > 3 || b-7 == c) {  // 2
      while (a-- > 0 && b++ < 10) {     // 3 (nesting = 1)
        doTheOtherThing();
      }
    }

    do {                                // 1

    } while (a-- > 0 || b != YELLOW);   // 1 (for ||)

    for (int i = 0; i < 10 && j > 20; i++) {  // 2
      doSomethingElse();
    }
  }

  public static void main (String [] args) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 4 to the 0 allowed.}}

    Runnable r = () -> {
      if (condition) {
        System.out.println("Hello world!");
      }
    };

    r = new MyRunnable();

    r = new Runnable () {
      public void run(){
    	if (condition) {
          System.out.println("Well, hello again");
        }
      }
    };
  }

  int sumOfNonPrimes(int limit) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 9 to the 0 allowed.}}

    int sum = 0;
    OUTER: for (int i = 0; i < limit; ++i) {
      if (i <= 2) {
        continue;
      }
      for (int j = 2; j < 1; ++j) {
        if (i % j == 0) {
          continue OUTER;
        }
      }
      sum += i;
    }
    return sum;
  }

  String getWeight(int i){ // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 4 to the 0 allowed.}}

    if (i <=0) {
      return "no weight";
    }
    if (i < 10) {
      return "light";
    }
    if (i < 20) {
      return "medium";
    }
    if (i < 30) {
      return "heavy";
    }
    return "very heavy";
  }

  public static HighlightingType toProtocolType(TypeOfText textType) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 1 to the 0 allowed.}}

    switch (textType) {
      case ANNOTATION: {
        return HighlightingType.ANNOTATION;
      }
      case CONSTANT:
        return HighlightingType.CONSTANT;
      case CPP_DOC:
        return HighlightingType.CPP_DOC;
      default:
        throw new IllegalArgumentException(textType.toString());
    }
  }

  public String getSpecifiedByKeysAsCommaList() {
    return getRuleKeysAsString(specifiedBy);
  }

  void localClasses() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 3 to the 0 allowed.}}
    class local {
      boolean plop() { // compliant : will be counted in the enclosing method
        return a && b || c && d;
      }
    }
  }

  void noNestingForIfElseIf() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 21 to the 0 allowed.}}
    while (true) { // +1
      if (true) { // +2 (nesting=1)
        for (;;) { // +3 (nesting=2)
          if (true) { // +4 (nesting=3)
          } else if (true) { // +1
          } else { // +1
            if (true) {
            } // +5 (nesting=4)
          }

          if (true) {} // +4 (nesting=3)
        }
      }
    }
  }
  
  public void EBEsInitialize(String file) throws FileNotFoundException { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 20 to the 0 allowed.}}
	    // CALCULAR dd Y CA (CANTIDADES DE NUDOS COARTADOS) AL CARGAR EL ARCHIVO
	    // CON ESTO EVITO RECALCULARLOS CADA VEZ QUE SE BUSCA UNA SOLUCIÃƒÂ³N
	    // CONTAR EN PENALIZACIÃƒÂ³N DE LA MATRIZ CA Y NO CN, CON ESTO
	    // EVITO RECORRER INNECESARIAMENTE TODOS LOS NUDOS

	    setName("Ebes");
	    numberOfEval_ = 1;

	    try {
	      // read file topology structural
	      EBEsReadDataFile(file);
	    } catch (JMetalException ex) {
	      Logger.getLogger(Ebes.class.getName()).log(Level.SEVERE, null, ex);
	    }

	    // variables and restrictions
	    // la forma de la secciÃƒÂ³n determina las cantidades de variables
	    // y la cantidad inicial de restricciones

	    //numberOfVariables_=0;
	    int numberOfConstraints_=0;
	      /*
	      for(int gr=0;gr<numberOfGroupElements_;gr++){
	        numberOfVariables_+= Groups_[gr][VARIABLES];
	        numberOfConstraintsGeometric_+= Groups_[gr][CONSTRAINT];
	      }
	      */
	    // variable position, amount variables and geometric constraints
	    setNumberOfVariables(Variable_Position());
	    // geomtric constraints for shape
	    numberOfConstraints_=numberOfConstraintsGeometric_;

	    // constraint for stress
	    numberOfConstraints_+= numberOfGroupElements_ * 3;

	    // total restrictions
	    numberOfConstraints_+= numberOfConstraintsNodes_;
	    setNumberOfConstraints(numberOfConstraints_);

	    // amount objectives
	    setNumberOfObjectives(OF_.length);

	    // problem data print
	    System.out.println("Structure");
	    System.out.println("  file: " + file);
	    System.out.println("  Number of Nodes: " + numberOfNodes);
	    System.out.println("  Number of Bars: " + numberOfElements_);
	    System.out.println("  Number of Groups: " + numberOfGroupElements_);
	    System.out.println("Optimization multi-objective: ");
	    System.out.println("  Number of objective function: " + getNumberOfObjectives());
	    String txt="";
	    for(int i=0; i<getNumberOfObjectives(); i++)
	    {
	      txt = txt + OF_[i] + " ";
	    }
	    System.out.println("  " + txt);
	    System.out.println("  Number of Variables: " + getNumberOfVariables());
	    System.out.println("  Number of constraints for Geometric: " + numberOfConstraintsGeometric_);
	    System.out.println("  Number of constraints for Stress: " + (numberOfGroupElements_ * 3));
	    System.out.println("  Number of constraints for Deflection: " + numberOfConstraintsNodes_);
	    System.out.println("  Number of Constraints: " + numberOfConstraints_);
	    System.out.println("  Number of groups to check geometry: " + numberOfGroupsToCheckGeometry_);

	    // objectives
	    // Weight, Deflections, stress squared absolute error;

	    System.out.println("Algorithm configuration: ");

	    //Fill lower and upper limits
	    Double[] lowerLimit_ = new Double[getNumberOfVariables()];
	    Double[] upperLimit_ = new Double[getNumberOfVariables()];
	    int var=0;
	    for (int gr=0; gr<numberOfGroupElements_;gr++){
	      var+=Groups_[gr][VARIABLES];

	      if (Groups_[gr][SHAPE]==CIRCLE){
	        lowerLimit_[var-1] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // diameter min
	        upperLimit_[var-1] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // diameter max
	      }
	      else if (Groups_[gr][SHAPE]==HOLE_CIRCLE){
	        lowerLimit_[var-2] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // diameter min
	        lowerLimit_[var-1] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // thickness min
	        upperLimit_[var-2] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // diameter max
	        upperLimit_[var-1] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // thickness max
	      }
	      else if (Groups_[gr][SHAPE]==RECTANGLE){
	        lowerLimit_[var-2] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // higth min for rectangle
	        lowerLimit_[var-1] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // witdth min
	        upperLimit_[var-2] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // higth max for rectangle
	        upperLimit_[var-1] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // width max for rectangle
	      }
	      else if (Groups_[gr][SHAPE]==HOLE_RECTANGLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT];  // tickness min in Y principal local axis
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT];  // tickness min in Z principal local axis
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // tickness max in Y principal local axis
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // tickness max in Z principal local axis
	      }
	      else if (Groups_[gr][SHAPE]==I_SINGLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // tickness min in Y principal local axis
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // ticknees min in Z principal local axis
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max in y axis
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z axiz
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // tickness max in Y principal local axis
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // tickness max in Z principal local axis
	      }
	      else if (Groups_[gr][SHAPE]==I_DOUBLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // tickness min in Y principal local axis
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // tickness min in Z principal local axis
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // tickness max in Y principal local axis
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // thickness max in plate z
	      }
	      else if (Groups_[gr][SHAPE]==H_SINGLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // tickness min in Y principal local axis
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // ticknees min in Z principal local axis
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max in y axis
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z axiz
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // tickness max in Y principal local axis
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // tickness max in Z principal local axis
	      }
	      else if (Groups_[gr][SHAPE]==H_DOUBLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // tickness min in Y principal local axis
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // tickness min in Z principal local axis
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // tickness max in Y principal local axis
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // thickness max in plate z
	      }
	      else if (Groups_[gr][SHAPE]==L_SINGLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // ticknees min in plate y
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // ticknees min in plate z
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // thickness max in
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // thickness max in
	      }
	      else if (Groups_[gr][SHAPE]==L_DOUBLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // ticknees min in
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // ticknees min in
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // thickness max in
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // thickness max in
	      }
	      else if (Groups_[gr][SHAPE]==T_SINGLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // ticknees min in plate y
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // ticknees min in plate z
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // thickness max in
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // thickness max in
	      }
	      else if (Groups_[gr][SHAPE]==T_DOUBLE){
	        lowerLimit_[var-4] = Groups_[gr][VAR_Y_LOWER_LIMIT]; // height min
	        lowerLimit_[var-3] = Groups_[gr][VAR_Z_LOWER_LIMIT]; // wide min
	        lowerLimit_[var-2] = Groups_[gr][VAR_eY_LOWER_LIMIT]; // ticknees min in
	        lowerLimit_[var-1] = Groups_[gr][VAR_eZ_LOWER_LIMIT]; // ticknees min in
	        upperLimit_[var-4] = Groups_[gr][VAR_Y_UPPER_LIMIT]; // height max
	        upperLimit_[var-3] = Groups_[gr][VAR_Z_UPPER_LIMIT]; // wide max in z
	        upperLimit_[var-2] = Groups_[gr][VAR_eY_UPPER_LIMIT]; // thickness max in
	        upperLimit_[var-1] = Groups_[gr][VAR_eZ_UPPER_LIMIT]; // thickness max in
	      }
	      else{
	        System.out.println("Error in LIMITES LOWER/UPPER: transversal section not considerated for: " + gr + " group") ;
	      } // end if
	    } // gr

	    setLowerLimit(new ArrayList<Double>(Arrays.<Double>asList(lowerLimit_)));
	    setUpperLimit(new ArrayList<Double>(Arrays.<Double>asList(upperLimit_)));

	    // greates difference between nodes
	    elementsBetweenDiffGreat_ = 0;
	    for(int ba = 0; ba<numberOfElements_; ba++){
	      int i = (int)Element_[ba][i_];
	      int j = (int)Element_[ba][j_];
	      if (Math.abs(j - i) > elementsBetweenDiffGreat_){
	        elementsBetweenDiffGreat_ = Math.abs(j - i);
	      }
	    }
	    matrixWidthBand_ = (elementsBetweenDiffGreat_ +1) * numberOfLibertyDegree_;

	  } // end InitializeEBEs
  
  public FileDrop(final PrintStream out, final Component c,
          final Border dragBorder,
          final boolean recursive, final Listener listener) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 53 to the 0 allowed.}}

if (supportsDnD()) { // Make a drop listener
  dropListener = new DropTargetListener() {
      @Override
      public void dragEnter(final DropTargetDragEvent evt) {
          log(out, "FileDrop: dragEnter event.");

          // Is this an acceptable drag event?
          if (isDragOk(out, evt)) {
              // If it's a Swing component, set its border
              if (c instanceof JComponent) {
                  final JComponent jc = (JComponent) c;
                  normalBorder = jc.getBorder();
                  log(out, "FileDrop: normal border saved.");
                  jc.setBorder(dragBorder);
                  log(out, "FileDrop: drag border set.");
              } // end if: JComponent

              // Acknowledge that it's okay to enter
              // evt.acceptDrag(
              // DnDConstants.ACTION_COPY_OR_MOVE );
              evt.acceptDrag(DnDConstants.ACTION_COPY);
              log(out, "FileDrop: event accepted.");
          } // end if: drag ok
          else { // Reject the drag event
              evt.rejectDrag();
              log(out, "FileDrop: event rejected.");
          } // end else: drag not ok
      } // end dragEnter

      @Override
      public void dragOver(final DropTargetDragEvent evt) { // This
          // is
          // called
          // continually
          // as
          // long
          // as
          // the
          // mouse
          // is
          // over
          // the
          // drag
          // target.
      } // end dragOver

      @Override
      public void drop(final DropTargetDropEvent evt) {
          log(out, "FileDrop: drop event.");
          try { // Get whatever was dropped
              final Transferable tr = evt
                      .getTransferable();

              // Is it a file list?
              if (tr.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                  // Say we'll take it.
                  // evt.acceptDrop (
                  // DnDConstants.ACTION_COPY_OR_MOVE );
                  evt.acceptDrop(DnDConstants.ACTION_COPY);
                  log(out, "FileDrop: file list accepted.");

                  // Get a useful list
                  final java.util.List fileList = (java.util.List) tr
                          .getTransferData(DataFlavor.javaFileListFlavor);
                  final java.util.Iterator iterator = fileList
                          .iterator();

                  // Convert list to array
                  final File[] filesTemp = new File[fileList
                          .size()];
                  fileList.toArray(filesTemp);

                  // Alert listener to drop.
                  if (listener != null) {
                      listener.filesDropped(filesTemp);
                  }

                  // Mark that drop is completed.
                  evt.getDropTargetContext().dropComplete(true);
                  log(out, "FileDrop: drop complete.");
              } // end if: file list
              else // this section will check for a reader flavor.
              {
                  // Thanks, Nathan!
                  // BEGIN 2007-09-12 Nathan Blomquist -- Linux
                  // (KDE/Gnome) support added.
                  final DataFlavor[] flavors = tr
                          .getTransferDataFlavors();
                  boolean handled = false;
                  for (DataFlavor flavor : flavors) {
                      if (flavor.isRepresentationClassReader()) {
                          // Say we'll take it.
                          // evt.acceptDrop (
                          // DnDConstants.ACTION_COPY_OR_MOVE
                          // );
                          evt.acceptDrop(DnDConstants.ACTION_COPY);
                          log(out, "FileDrop: reader accepted.");

                          final Reader reader = flavor
                                  .getReaderForText(tr);

                          final BufferedReader br = new BufferedReader(
                                  reader);

                          if (listener != null) {
                              listener.filesDropped(createFileArray(
                                      br, out));
                          }

                          // Mark that drop is completed.
                          evt.getDropTargetContext().dropComplete(
                                  true);
                          log(out, "FileDrop: drop complete.");
                          handled = true;
                          break;
                      }
                  }
                  if (!handled) {
                      log(out,
                              "FileDrop: not a file list or reader - abort.");
                      evt.rejectDrop();
                  }
                  // END 2007-09-12 Nathan Blomquist -- Linux
                  // (KDE/Gnome) support added.
              } // end else: not a file list
          } // end try
          catch (final IOException io) {
              log(out, "FileDrop: IOException - abort:");
              BytecodeViewer.handleException(io);
              evt.rejectDrop();
          } // end catch IOException
          catch (final UnsupportedFlavorException ufe) {
              log(out,
                      "FileDrop: UnsupportedFlavorException - abort:");
              BytecodeViewer.handleException(
                      ufe);
              evt.rejectDrop();
          } // end catch: UnsupportedFlavorException
          finally {
              // If it's a Swing component, reset its border
              if (c instanceof JComponent) {
                  final JComponent jc = (JComponent) c;
                  jc.setBorder(normalBorder);
                  log(out, "FileDrop: normal border restored.");
              } // end if: JComponent
          } // end finally
      } // end drop

      @Override
      public void dragExit(final DropTargetEvent evt) {
          log(out, "FileDrop: dragExit event.");
          // If it's a Swing component, reset its border
          if (c instanceof JComponent) {
              final JComponent jc = (JComponent) c;
              jc.setBorder(normalBorder);
              log(out, "FileDrop: normal border restored.");
          } // end if: JComponent
      } // end dragExit

      @Override
      public void dropActionChanged(
              final DropTargetDragEvent evt) {
          log(out, "FileDrop: dropActionChanged event.");
          // Is this an acceptable drag event?
          if (isDragOk(out, evt)) { // evt.acceptDrag(
              // DnDConstants.ACTION_COPY_OR_MOVE
              // );
              evt.acceptDrag(DnDConstants.ACTION_COPY);
              log(out, "FileDrop: event accepted.");
          } // end if: drag ok
          else {
              evt.rejectDrag();
              log(out, "FileDrop: event rejected.");
          } // end else: drag not ok
      } // end dropActionChanged
  }; // end DropTargetListener

  // Make the component (and possibly children) drop targets
  makeDropTarget(out, c, recursive);
} // end if: supports dnd
else {
  log(out, "FileDrop: Drag and drop is not supported with this JVM");
} // end else: does not support DnD
} // end constructor

  private void processExtracted2At128(List<Object> reply, final Object firstObj, final byte[] resp) { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 16 to the 0 allowed.}}
	  if (Arrays.equals(MESSAGE.getRaw(), resp)) {
	          final byte[] bchannel = (byte[]) reply.get(1);
	          final byte[] bmesg = (byte[]) reply.get(2);
	          final String strchannel = (bchannel == null) 

	  ? null : SafeEncoder.encode(bchannel);
	          final String strmesg = (bmesg == null) 

	  ? null : SafeEncoder.encode(bmesg);
	          onMessage(strchannel, strmesg);
	        } else 

	  if (Arrays.equals(PMESSAGE.getRaw(), resp)) {
	          processExtracted1At128(reply);
	        } else 

	  if (Arrays.equals(PSUBSCRIBE.getRaw(), resp)) {
	          subscribedChannels = ((Long) reply.get(2)).intValue();
	          final byte[] bpattern = (byte[]) reply.get(1);
	          final String strpattern = (bpattern == null) 

	  ? null : SafeEncoder.encode(bpattern);
	          onPSubscribe(strpattern, subscribedChannels);
	        } else 

	  if (Arrays.equals(PUNSUBSCRIBE.getRaw(), resp)) {
	          subscribedChannels = ((Long) reply.get(2)).intValue();
	          final byte[] bpattern = (byte[]) reply.get(1);
	          final String strpattern = (bpattern == null) 

	  ? null : SafeEncoder.encode(bpattern);
	          onPUnsubscribe(strpattern, subscribedChannels);
	        } else 

	  if (Arrays.equals(PONG.getRaw(), resp)) {
	          final byte[] bpattern = (byte[]) reply.get(1);
	          final String strpattern = (bpattern == null) 

	  ? null : SafeEncoder.encode(bpattern);
	          onPong(strpattern);
	        } 

	  else {
	          throw new JedisException("Unknown message type: " + firstObj);
	        }
	    }
  
  public boolean equals(Object o) {
    while (false) {
    }
    return
      a
        || b
        && c;
  }

  public int hashCode() {
    while (false) {
    }
    if (
      a
        || b
        && c) {
      return 100;
    }
    return 42;
  }
  
  void attachListener() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 5 to the 0 allowed.}}
	    button.addActionListener(new ActionListener() {
	        @Override
	        public void actionPerformed(ActionEvent e) {  
	            if (isReady()) {                            // +1 (estructura) +1 (nivel 1) = 2
	                if (hasPermission()) {                  // +1 (estructura) +1 (nivel 2) = 3
	                    doAction();
	                }
	            }
	        }
	    });
	}
  
  public void attachTreeListeners() { // Noncompliant {{Refactor this method to reduce its Cognitive Complexity from 5 to the 0 allowed.}}
      tree.addMouseListener(new MouseAdapter()
      {
          @Override
          public void mouseReleased(MouseEvent e)
          {
              if (e.isMetaDown())
              {
                  ResourceTree tree = (ResourceTree) e.getSource();
                  TreePath selPath = ResourceListPane.this.tree.getClosestPathForLocation(e.getX(), e.getY());
                  
                  if (selPath == null)
                      return;
                  
                  showContextMenu(tree, selPath, e.getX(), e.getY());
              }
          }
      });
  }
}