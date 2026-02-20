package test.neo.reducecognitivecomplexity.graphs;


import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.junit.jupiter.api.Test;

import neo.reducecognitivecomplexity.core.graphs.Utils;

class getDistanceBetweenVerticesTest {
	private SimpleDirectedGraph<Integer, DefaultEdge> graph = new SimpleDirectedGraph<>(DefaultEdge.class);
	
	// 1 -> 2 -> 3 -> 4 -> 5 -> 20
	// 1 ---------------------> 20
	public getDistanceBetweenVerticesTest () {		
		graph.addVertex(1);
		graph.addVertex(2);
		graph.addVertex(20);
		graph.addVertex(3);
		graph.addVertex(4);
		graph.addVertex(5);
		
		graph.addEdge(1, 2);
		graph.addEdge(1, 20);
		graph.addEdge(2, 3);
		graph.addEdge(3, 4);
		graph.addEdge(4, 5);
		graph.addEdge(5, 20);
	}
	
	@Test
	void testDistance4() {					
		assert(Utils.getDistanceBetweenVertices(graph, 2, 20) == 4);		
	}
	
	@Test
	void testDistance3() {					
		assert(Utils.getDistanceBetweenVertices(graph, 2, 5) == 3);		
	}

	@Test
	void testDistance2() {							
		assert(Utils.getDistanceBetweenVertices(graph, 1, 3) == 2);		
	}
	
	@Test
	void testDistance1A() {							
		assert(Utils.getDistanceBetweenVertices(graph, 1, 2) == 1);
	}
	
	@Test
	void testDistance1B() {							
		assert(Utils.getDistanceBetweenVertices(graph, 1, 20) == 1);
	}
	
	@Test
	void testDistance1C() {							
		assert(Utils.getDistanceBetweenVertices(graph, 5, 20) == 1);
	}
	
	@Test
	void testDistance0() {							
		assert(Utils.getDistanceBetweenVertices(graph, 1, 1) == 0);
	}
	
	@Test
	void testIncorrectParameters() {							
		assert(Utils.getDistanceBetweenVertices(graph, 5, 1) == -1);
	}
}
