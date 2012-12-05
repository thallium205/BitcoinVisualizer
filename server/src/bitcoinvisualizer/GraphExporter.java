package bitcoinvisualizer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.logging.Logger;

import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.neo4j.plugin.api.Neo4jImporter;
import org.gephi.neo4j.plugin.api.RelationshipDescription;
import org.gephi.neo4j.plugin.api.TraversalOrder;
import org.gephi.neo4j.plugin.impl.Neo4jImporterImpl;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.openide.util.Lookup;

/**
 * Creates an information rich graph in .gexf format of the Bitcoin ownership network given the path to a Neo4j graph.db database. 
 * @author John
 *
 */
public class GraphExporter
{
	private static final Logger LOG = Logger.getLogger(GraphExporter.class.getName());
	
	private static GraphDatabaseAPI graphDb;
	// Owner address hash
	public static final String OWNED_ADDRESS_HASH = "owned_addr_hashes";
	public static final String OWNED_ADDRESS_HASH_KEY = "owned_addr_hash";
	private static Index<Node> owned_addresses;
	
	public static void BuildGexfFromNeo4j(String dbPath, String gexfPath, int threadCount)
	{		
		LOG.info("Begin Building GEXF from Neo4j");
		LOG.info("Fetching graph...");
		// Get the database and the owner index 
		graphDb = new EmbeddedGraphDatabase (dbPath);
		owned_addresses = graphDb.index().forNodes(OWNED_ADDRESS_HASH);
		
		// Find an owner node id by a known address.  This will be the start location of the graph traversal
		final long ownerId = owned_addresses.query(OWNED_ADDRESS_HASH_KEY, "1DkyBEKt5S2GDtv7aQw6rQepAvnsRyHoYM").getSingle().traverse(Order.DEPTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, GraphBuilder.OwnerRelTypes.owns, Direction.INCOMING).iterator().next().getId();
		
		graphDb.shutdown();
				
		// Import by traversing the entire ownership network along the "transfers" edges
		LOG.info("Importing Ownership Network from Neo4j Database...");
		final Collection<RelationshipDescription> relationshipDescription = new ArrayList<RelationshipDescription>();
		relationshipDescription.add(new RelationshipDescription(GraphBuilder.OwnerRelTypes.transfers, Direction.BOTH));
		final Neo4jImporter importer = new Neo4jImporterImpl();		
		importer.importDatabase(graphDb, ownerId, TraversalOrder.BREADTH_FIRST, Integer.MAX_VALUE, relationshipDescription);
		
		// Grab the graph that was loaded from the importer		
		final ProjectController projectController = Lookup.getDefault().lookup(ProjectController.class);
		final Workspace workspace = projectController.getCurrentWorkspace();
		final GraphModel graph = Lookup.getDefault().lookup(GraphController.class).getModel(workspace);	
		LOG.info("Graph Imported.  Nodes: " + graph.getDirectedGraph().getNodeCount() + "Edges: " + graph.getDirectedGraph().getEdgeCount());
		
		// Set graph options
		
		// Set graph query		
		
		// Nodes (size and color)
		
		// Edges (label and color)
		
		// Layout
		final ForceAtlas2 layout = new ForceAtlas2(new ForceAtlas2Builder());
		layout.setGraphModel(graph);
		layout.setLinLogMode(true);		
		layout.setEdgeWeightInfluence(1.0);	
		layout.setScalingRatio(10.0);
		layout.setGravity(1.0);
		layout.setBarnesHutOptimize(true);
		layout.setThreadsCount(threadCount);
		
		
		
		// Statistics
		
		// Export
		
		

	}
	
	public static void BuildSeadragonFromGexf(String gexfPath, String seadragonPath)
	{
		
	}
}
