package bitcoinvisualizer;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.data.properties.PropertiesColumn;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.io.exporter.preview.PDFExporter;
import org.gephi.io.exporter.spi.CharacterExporter;
import org.gephi.io.exporter.spi.Exporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerFactory;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.layout.plugin.openord.OpenOrdLayout;
import org.gephi.layout.plugin.openord.OpenOrdLayoutBuilder;
import org.gephi.neo4j.plugin.api.FilterDescription;
import org.gephi.neo4j.plugin.api.FilterOperator;
import org.gephi.neo4j.plugin.api.Neo4jImporter;
import org.gephi.neo4j.plugin.api.RelationshipDescription;
import org.gephi.neo4j.plugin.api.TraversalOrder;
import org.gephi.neo4j.plugin.impl.Neo4jImporterImpl;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.project.api.ProjectController;
import org.gephi.project.api.Workspace;
import org.gephi.ranking.api.Interpolator;
import org.gephi.ranking.api.Ranking;
import org.gephi.ranking.api.RankingController;
import org.gephi.ranking.api.Transformer;
import org.gephi.ranking.plugin.transformer.AbstractColorTransformer;
import org.gephi.ranking.plugin.transformer.AbstractColorTransformer.LinearGradient;
import org.gephi.ranking.plugin.transformer.AbstractSizeTransformer;
import org.gephi.statistics.plugin.ClusteringCoefficient;
import org.gephi.statistics.plugin.ConnectedComponents;
import org.gephi.statistics.plugin.Degree;
import org.gephi.statistics.plugin.EigenvectorCentrality;
import org.gephi.statistics.plugin.GraphDensity;
import org.gephi.statistics.plugin.GraphDistance;
import org.gephi.statistics.plugin.Hits;
import org.gephi.statistics.plugin.Modularity;
import org.gephi.statistics.plugin.PageRank;
import org.gephi.statistics.plugin.WeightedDegree;
import org.gephi.statistics.plugin.dynamic.DynamicClusteringCoefficient;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.openide.util.Lookup;

import bitcoinvisualizer.GraphBuilder.OwnerRelTypes;

import com.google.common.collect.Iterables;
import com.itextpdf.text.PageSize;

/**
 * Creates an information rich graph in .gexf format of the Bitcoin ownership network given the path to a Neo4j graph.db database. 
 * @author John
 *
 */
public class GraphExporter
{
	private static final Logger LOG = Logger.getLogger(GraphExporter.class.getName());
	public static final String OWNED_ADDRESS_HASH = "owned_addr_hashes";
	public static final String OWNED_ADDRESS_HASH_KEY = "owned_addr_hash";
	private static Index<Node> owned_addresses;
	
	public static void ExportBetweenTwoDates(final GraphDatabaseAPI graphDb, final String outputPath, final int threadCount, final Date from, final Date to)
	{	
		Export(graphDb, outputPath, threadCount, null, from, to);
	}
	
	public static void ExportAtAddress(final GraphDatabaseAPI graphDb, final String outputPath, final int threadCount, final String address)
	{
		Export(graphDb, outputPath, threadCount, address, null, null);
	}
	
	private static void Export(final GraphDatabaseAPI graphDb, final String outputpath, final int threadCount, final String address, final Date from, final Date to)
	{
		boolean isDateCompare;
		LOG.info("Begin Building GEXF from Neo4j");
		owned_addresses = graphDb.index().forNodes(OWNED_ADDRESS_HASH);
		
		if (address == null)
			isDateCompare = true;
		else
			isDateCompare = false;
		
		// We export the entire graph between the two dates
		if (isDateCompare)
		{
			assert(from != null && to != null);			
			// Find an owner node id by a known address.  This will be the start location of the graph traversal		
			final long ownerId = owned_addresses.query(OWNED_ADDRESS_HASH_KEY, "1DkyBEKt5S2GDtv7aQw6rQepAvnsRyHoYM").getSingle().traverse(Order.DEPTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, GraphBuilder.OwnerRelTypes.owns, Direction.INCOMING).iterator().next().getId();
			
			// Import by traversing the entire ownership network along the "transfers" edges
			LOG.info("Building Ownership Network from Neo4j Database From: " + from.toString() + " To: " + to.toString() + " Starting With Node: " + ownerId + " ...");
			final Collection<RelationshipDescription> relationshipDescription = new ArrayList<RelationshipDescription>();
			relationshipDescription.add(new RelationshipDescription(GraphBuilder.OwnerRelTypes.transfers, Direction.BOTH));
			final Collection<FilterDescription> edgeFilterDescription = new ArrayList<FilterDescription>();
			edgeFilterDescription.add(new FilterDescription("time", FilterOperator.GREATER_OR_EQUALS, Long.toString(from.getTime() / 1000)));
			edgeFilterDescription.add(new FilterDescription("time", FilterOperator.LESS, Long.toString(to.getTime() / 1000)));
			final Neo4jImporter importer = new Neo4jImporterImpl();	
			importer.importDatabase(graphDb, ownerId, TraversalOrder.BREADTH_FIRST, Integer.MAX_VALUE, relationshipDescription, Collections.<FilterDescription>emptyList(), edgeFilterDescription, true, true);			
		}
		
		// We export a one-hop graph from the address
		else 
		{
			assert(from == null && to == null);			
			// Find an owner node id by the given address		
			final long ownerId = owned_addresses.query(OWNED_ADDRESS_HASH_KEY, address).getSingle().traverse(Order.DEPTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, GraphBuilder.OwnerRelTypes.owns, Direction.INCOMING).iterator().next().getId();
			
			// Import by traversing one hop along the ownership network's "transfers" edges
			LOG.info("Importing Ownership Network from Neo4j Database Starting With Node: " + ownerId + " ...");
			final Collection<RelationshipDescription> relationshipDescription = new ArrayList<RelationshipDescription>();
			relationshipDescription.add(new RelationshipDescription(GraphBuilder.OwnerRelTypes.transfers, Direction.BOTH));
			final Neo4jImporter importer = new Neo4jImporterImpl();	
			importer.importDatabase(graphDb, ownerId, TraversalOrder.BREADTH_FIRST, 1, relationshipDescription);		
		}
		
		
		// Grab the graph that was loaded from the importer			
		final GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
		final DirectedGraph graph = graphModel.getDirectedGraph();
		AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
		LOG.info("Graph Imported.  Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());	
		
		LOG.info("Setting graph labels and features...");
		// Ranking
		final RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);
		// rankingController.setInterpolator(Interpolator.newBezierInterpolator(0, 1, 0, 1)); TODO
		
		// Node Size (Gephi can be used to build degree ranking on time span queries, but neo4j needs to be queried to find node degree on address specific queries)
		final Ranking nodeDegreeRanking;
		final AbstractSizeTransformer nodeSizeTransformer = (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
		
		if (isDateCompare)
		{
			nodeDegreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);			
		}
		
		else
		{
			// We set a degree attribute on each gephi node
			for (org.gephi.graph.api.Node node : graphModel.getGraph().getNodes())
			{
				node.getAttributes().setValue("globaldegree", Iterables.size(graphDb.getNodeById((Long) node.getAttributes().getValue(PropertiesColumn.NODE_ID.getId())).getRelationships(OwnerRelTypes.transfers)));
			}
			
			final AttributeColumn neo4jDegreeColumn = attributeModel.getNodeTable().getColumn("globaldegree");
			nodeDegreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, neo4jDegreeColumn.getId());
		}
		
		nodeSizeTransformer.setMinSize(3);
		nodeSizeTransformer.setMaxSize(20);
		rankingController.transform(nodeDegreeRanking, nodeSizeTransformer);
		
		// Node Color
		final AttributeColumn lastTimeSentColumn = attributeModel.getNodeTable().getColumn("last_time_sent");
		final Ranking nodeLastTimeSentRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, lastTimeSentColumn.getId());
		final AbstractColorTransformer nodeColorTransformer = (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
		final float[] nodeColorPositions = {0f, 0.5f, 1f};
		final Color[] nodeColors = new Color[]{new Color(0x0000FF), new Color(0x00FF00), new Color(0xFF0000)};
		nodeColorTransformer.setLinearGradient(new LinearGradient(nodeColors, nodeColorPositions));
		rankingController.transform(nodeLastTimeSentRanking, nodeColorTransformer);		
		
		// Node Label
		for (org.gephi.graph.api.Node node : graphModel.getGraph().getNodes())
		{					
			node.getNodeData().setLabel(Long.toString((Long) node.getAttributes().getValue(PropertiesColumn.NODE_ID.getId())));
		}	
		PreviewModel previewModel = Lookup.getDefault().lookup(PreviewController.class).getModel();
		previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
		previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.FALSE);
		//previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, previewModel.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
		
		// Edge Color
		final AttributeColumn edgeTimeSentColumn = attributeModel.getEdgeTable().getColumn("value");
		final Ranking edgeTimeSentRanking = rankingController.getModel().getRanking(Ranking.EDGE_ELEMENT, edgeTimeSentColumn.getId());
		final AbstractColorTransformer edgeColorTransformer = (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.EDGE_ELEMENT, Transformer.RENDERABLE_COLOR);
		final float[] edgeColorPositions = {0f, 0.5f, 1f};
		final Color[] edgeColors = new Color[]{new Color(0x0000FF) ,new Color(0x00FF00), new Color(0xFF0000)};
		edgeColorTransformer.setLinearGradient(new LinearGradient(edgeColors, edgeColorPositions));
		rankingController.transform(edgeTimeSentRanking, edgeColorTransformer);		
		
		// Edge Label
		for (org.gephi.graph.api.Edge edge : graphModel.getGraph().getEdges())
		{					
			edge.getEdgeData().setLabel(Double.toString(((Long) graphDb.getRelationshipById((Long) edge.getAttributes().getValue(PropertiesColumn.EDGE_ID.getId())).getProperty("value")).doubleValue() / 100000000));		
		}		
		previewModel.getProperties().putValue(PreviewProperty.SHOW_EDGE_LABELS, Boolean.TRUE);
		//previewModel.getProperties().putValue(PreviewProperty.EDGE_LABEL_FONT, previewModel.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
		//previewModel.getProperties().putValue(PreviewProperty.ARROW_SIZE, 20f);
		
		
		
		LOG.info("Setting graph labels and features complete.");
		
		// Graph Layout (Maybe use an auto layout?)		
		/*
		LOG.info("Begin graph layout algorithm (OpenORD)...");
		final OpenOrdLayout layout = new OpenOrdLayout(new OpenOrdLayoutBuilder());		
		layout.setGraphModel(graphModel);	
		layout.resetPropertiesValues();
		layout.setLiquidStage(25);
		layout.setExpansionStage(25);		
		layout.setCooldownStage(25);		
		layout.setCrunchStage(10);
		layout.setSimmerStage(15);		
		layout.setEdgeCut(.8f);
		layout.setNumThreads(1);
		layout.setNumIterations(750);
		layout.setRealTime(.2f);		
		layout.setRandSeed(new Random().nextLong());		
		layout.initAlgo();		
		for (int i = 0; i < 750 && layout.canAlgo(); i++) 
		{
			// LOG.info("Status: " + i + "/100");
			layout.goAlgo();
		}
		layout.endAlgo();
		LOG.info("Graph layout algorithm complete.");
		*/
		
		LOG.info("Begin graph layout algorithm (Force Atlas 2)...");
		final ForceAtlas2 layout = new ForceAtlas2(new ForceAtlas2Builder());		
		layout.setGraphModel(graphModel);	
		layout.resetPropertiesValues();
		layout.setLinLogMode(true);
		layout.setThreadsCount(threadCount);
		layout.initAlgo();		
		for (int i = 0; i < 5000 && layout.canAlgo(); i++) 
		{
			layout.goAlgo();
		}
		
		// We need to prevent graphs from overlapping, but we only do so once the graph is spatialized
		layout.setAdjustSizes(true);
		for (int i = 0; i < 500 && layout.canAlgo(); i++) 
		{
			layout.goAlgo();
		}
		
		layout.endAlgo();
		LOG.info("Graph layout algorithm complete.");
		
		// Statistics
		if (isDateCompare)
		{
			// Clustering Coefficient
			LOG.info("Begin Calculating Statistics...");
			LOG.info("Begin Clustering Coefficient...");
			ClusteringCoefficient clusteringCoefficient = new ClusteringCoefficient();
			clusteringCoefficient.setDirected(true);
			clusteringCoefficient.execute(graphModel, attributeModel);
			LOG.info("Clustering Coefficient Complete.");
			
			// Connected Components
			LOG.info("Begin Connected Components...");
			ConnectedComponents connectedComponents = new ConnectedComponents();
			connectedComponents.setDirected(true);
			connectedComponents.execute(graphModel, attributeModel);
			LOG.info("Connected Components Complete.");
			
			// Degree
			LOG.info("Begin Degree...");
			Degree degree = new Degree();
			degree.execute(graphModel, attributeModel);			
			LOG.info("Connected Degree.");
			
			// Eigenvector Centrality
			LOG.info("Begin Eigenvector Centrality...");
			EigenvectorCentrality eigenvector = new EigenvectorCentrality();
			eigenvector.setDirected(true);
			eigenvector.setNumRuns(100); // TODO
			eigenvector.execute(graphModel, attributeModel);
			LOG.info("Eigenvector Centrality Complete.");
			
			// Graph Density
			LOG.info("Begin Graph Density...");
			GraphDensity graphDensity = new GraphDensity();
			graphDensity.setDirected(true);
			graphDensity.execute(graphModel, attributeModel);
			LOG.info("Graph Density Complete.");
			
			// Graph Distance
			LOG.info("Begin Graph Distance...");
			GraphDistance graphDistance = new GraphDistance();
			graphDistance.setDirected(true);
			graphDistance.execute(graphModel, attributeModel);
			LOG.info("Graph Distance Complete.");
			
			// Hits
			LOG.info("Begin HITS...");
			Hits hits = new Hits();
			hits.setUndirected(false);
			hits.setEpsilon(.001);
			hits.execute(graphModel, attributeModel);
			LOG.info("HITS Complete.");
			
			// Modularity
			LOG.info("Begin Modularity...");
			Modularity modularity = new Modularity();
			modularity.setRandom(true);
			modularity.setUseWeight(true);
			modularity.setResolution(1.0);
			modularity.execute(graphModel, attributeModel);
			LOG.info("Modularity Complete.");
			
			// Page Rank
			LOG.info("Begin Page Rank...");
			PageRank pageRank = new PageRank();
			pageRank.setDirected(true);
			pageRank.setProbability(.85);
			pageRank.setEpsilon(.001);		
			pageRank.execute(graphModel, attributeModel);
			LOG.info("Page Rank Complete.");
			
			// Weighted Degree
			LOG.info("Begin Weighted Degree...");
			WeightedDegree weightedDegree = new WeightedDegree();
			weightedDegree.execute(graphModel, attributeModel);
			LOG.info("Weighted Degree Complete.");
			LOG.info("Calculating Statistics Complete.");
		}
		
		// Export
		LOG.info("Begin Exporting Graph To Disk...");
		LOG.info("Begin PDF...");
		final ExportController ec = Lookup.getDefault().lookup(ExportController.class);
		try 
		{
			ec.exportFile(new File("C:\\graph.pdf"));
		}
		
		catch (IOException e) 
		{
			LOG.log(Level.SEVERE, "Exporting PDF Failed.", e);
			return;
		}
		
		final PDFExporter pdfExporter = (PDFExporter) ec.getExporter("pdf");
		pdfExporter.setPageSize(PageSize.A0);
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ec.exportStream(baos, pdfExporter);
		final byte[] pdf = baos.toByteArray();
		LOG.info("PDF Complete.");
		
		LOG.info("Begin GEXF...");
		final org.gephi.io.exporter.spi.GraphExporter exporter = (org.gephi.io.exporter.spi.GraphExporter) ec.getExporter("gexf");
		exporter.setExportVisible(true); 
		exporter.setWorkspace(graphModel.getWorkspace());
		try 
		{
			ec.exportFile(new File("C:\\graph.gexf"), exporter);
		}
		
		catch (IOException e)
		{
			LOG.log(Level.SEVERE, "Exporting GEXF Failed.", e);
			return;
		}	
		LOG.info("GEXF Complete.");
		LOG.info("Exporting Graph To Disk Completed.");		
	}
}
