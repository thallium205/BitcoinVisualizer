package bitcoinvisualizer;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.gephi.io.exporter.preview.PNGExporter;
import org.gephi.io.exporter.spi.CharacterExporter;
import org.gephi.io.exporter.spi.Exporter;
import org.gephi.io.importer.api.Container;
import org.gephi.io.importer.api.ContainerFactory;
import org.gephi.layout.plugin.force.StepDisplacement;
import org.gephi.layout.plugin.force.yifanHu.YifanHuLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.layout.plugin.labelAdjust.LabelAdjust;
import org.gephi.layout.plugin.labelAdjust.LabelAdjustBuilder;
import org.gephi.layout.plugin.openord.OpenOrdLayout;
import org.gephi.layout.plugin.openord.OpenOrdLayoutBuilder;
import org.gephi.neo4j.plugin.api.FilterDescription;
import org.gephi.neo4j.plugin.api.FilterOperator;
import org.gephi.neo4j.plugin.api.Neo4jImporter;
import org.gephi.neo4j.plugin.api.RelationshipDescription;
import org.gephi.neo4j.plugin.api.TraversalOrder;
import org.gephi.neo4j.plugin.impl.Neo4jImporterImpl;
import org.gephi.preview.PreviewModelImpl;
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
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.openide.util.Lookup;

import bitcoinvisualizer.GraphBuilder.OwnerRelTypes;
import bitcoinvisualizer.scraper.Scraper.ScraperRelationships;

import com.google.common.collect.Iterables;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Rectangle;

/**
 * Creates an information rich graph in .gexf format of the Bitcoin ownership network given the path to a Neo4j graph.db database.
 * 
 * @author John
 * 
 */
public class GraphExporter
{
	private static final Logger LOG = Logger.getLogger(GraphExporter.class.getName());
	public static final String OWNED_ADDRESS_HASH = "owned_addr_hashes";
	public static final String OWNED_ADDRESS_HASH_KEY = "owned_addr_hash";
	private static Index<Node> owned_addresses;
	public enum ExportType { GEXF, PDF, PNG }	

	public static void ExportTimeAnalysisGraphsToMySql(final GraphDatabaseAPI graphDb, final int threadCount)
	{
		try
		{
			Class.forName("com.mysql.jdbc.Driver");
			final Connection sqlDb = DriverManager.getConnection("jdbc:mysql://localhost:3306/blockviewer?user=root&password=webster");
			SetupMysql(sqlDb);
			
			Double lastDay = null;
			Date from = null;
			Date to = null;
			final Statement statement = sqlDb.createStatement();
			ResultSet rs = statement.executeQuery("SELECT MAX(`graphTime`) FROM `day`");
			Calendar cal = Calendar.getInstance();
			while (rs.next())
			{
				lastDay = rs.getDouble(1);
			}

			if (lastDay != null && lastDay != 0)
			{				
				// We add 1 day to the largest time value 
				from = new Date((long) (lastDay * 1000));
				cal.setTime(from);
				cal.add(Calendar.DATE, 1);
				from = cal.getTime();
			} 
			
			else
			{
				from = new Date(1231006505L * 1000);
			}

			
			cal.setTime(from);
			cal.add(Calendar.DATE, 1);
			to = cal.getTime();

			final Date lastTime = new Date(((Long) graphDb.getNodeById((((Long) graphDb.getNodeById(0).getProperty("last_linked_owner_build_block_nodeId")))).getProperty("received_time")) * 1000);
			while (!to.after(lastTime))
			{
				Export(sqlDb, graphDb, null, from, to, null, threadCount);
				from = to;
				cal.setTime(to);
				cal.add(Calendar.DATE, 1);
				to = cal.getTime();
			}
		} 
		
		catch (ClassNotFoundException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		} 
		
		catch (SQLException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}

	public static String GetOwnerByAddress(final GraphDatabaseAPI graphDb, final String address, final ExportType exportType)
	{
		owned_addresses = graphDb.index().forNodes(OWNED_ADDRESS_HASH); 
		final long ownerId = owned_addresses.query(OWNED_ADDRESS_HASH_KEY, address).getSingle().getSingleRelationship(OwnerRelTypes.owns, Direction.INCOMING).getStartNode().getId();		
		return Export(null, graphDb, ownerId, null, null, exportType, 1);
	}

	private static String Export(final Connection sqlDb, final GraphDatabaseAPI graphDb, Long ownerId, final Date from, final Date to, final ExportType exportType, final int threadCount)
	{
		boolean isDateCompare;
		LOG.info("Begin Building GEXF from Neo4j");

		if (ownerId == null)
		{
			isDateCompare = true;
			ownerId = 29784508L; // Arbitrary start node
		}

		else
		{
			isDateCompare = false;
		}

		// We export the entire graph between the two dates
		final Neo4jImporter importer = new Neo4jImporterImpl();
		if (isDateCompare)
		{
			assert (from != null && to != null);
			// Import by traversing the entire ownership network along the "transfers" edges
			LOG.info("Building Ownership Network from Neo4j Database From: " + from.toString() + " To: " + to.toString() + " ...");
			final Collection<RelationshipDescription> relationshipDescription = new ArrayList<RelationshipDescription>();
			relationshipDescription.add(new RelationshipDescription(GraphBuilder.OwnerRelTypes.transfers, Direction.BOTH));
			final Collection<FilterDescription> edgeFilterDescription = new ArrayList<FilterDescription>();
			edgeFilterDescription.add(new FilterDescription("time", FilterOperator.GREATER_OR_EQUALS, Long.toString(from.getTime() / 1000)));
			edgeFilterDescription.add(new FilterDescription("time", FilterOperator.LESS, Long.toString(to.getTime() / 1000)));
			importer.importDatabase(graphDb, ownerId, TraversalOrder.BREADTH_FIRST, Integer.MAX_VALUE, relationshipDescription, Collections.<FilterDescription> emptyList(), edgeFilterDescription,
					true, true);
		}

		// We export a one-hop graph from the address
		else
		{
			assert (from == null && to == null);
			// Import by traversing one hop along the ownership network's "transfers" edges
			LOG.info("Importing Ownership Network from Neo4j Database Starting With Node: " + ownerId + " ...");
			final Collection<RelationshipDescription> relationshipDescription = new ArrayList<RelationshipDescription>();
			relationshipDescription.add(new RelationshipDescription(GraphBuilder.OwnerRelTypes.transfers, Direction.BOTH));
			importer.importDatabase(graphDb, ownerId, TraversalOrder.BREADTH_FIRST, 1, relationshipDescription);
		}

		// Grab the graph that was loaded from the importer
		final GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
		final DirectedGraph graph = graphModel.getDirectedGraph();
		AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
		LOG.info("Graph Imported.  Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

		if (!isDateCompare && graph.getNodeCount() > 100000)
		{
			LOG.warning("The graph is being skipped because it contains over 100,000 nodes.");
			return null;
		}

		LOG.info("Setting graph labels and features...");
		// Ranking
		final RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);
		// rankingController.setInterpolator(Interpolator.newBezierInterpolator(0, 1, 0, 1));
		rankingController.setInterpolator(Interpolator.LOG2);

		// Node Size (Gephi can be used to build degree ranking on time span queries, but neo4j needs to be queried to find node degree on address specific queries)
		final Ranking nodeDegreeRanking;
		final AbstractSizeTransformer nodeSizeTransformer = (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);

		if (isDateCompare)
		{
			// TODO
			// This is a bit of a hack. The start point node is always added to the graph. Before we actually add it, we need to ensure that it satisfies the filter description, if it doesnt, we
			// remove it.
			boolean startNodeIsValid = false;
			for (Relationship rel : graphDb.getNodeById(ownerId).getRelationships(OwnerRelTypes.transfers, Direction.BOTH))
			{
				final Long time = (Long) rel.getProperty("time");
				if ((time >= from.getTime() / 1000) && (time < to.getTime() / 1000))
				{
					startNodeIsValid = true;
					break;
				}
			}

			if (!startNodeIsValid)
			{
				LOG.info("Removing Starting Node From Graph");
				graphModel.getGraph().removeNode(graphModel.getGraph().getNode(1));
			}
		}

		// We set a degree attribute on each gephi node
		for (org.gephi.graph.api.Node node : graphModel.getGraph().getNodes())
		{
			Object label = node.getAttributes().getValue(PropertiesColumn.NODE_ID.getId());
			Node neoNode = null;

			if (label instanceof String)
			{
				neoNode = graphDb.getNodeById(Long.parseLong((String) label));
			}

			else if (label instanceof Long)
			{
				neoNode = graphDb.getNodeById((Long) label);
			}

			else
			{
				throw new ClassCastException();
			}

			// Calculate interesting statistics about each node. TODO - A lot more opportunity is available right here!

			// Scraped Alias
			ArrayList<AliasType> aliases = new ArrayList<AliasType>();
			for (Relationship rel : neoNode.getRelationships(ScraperRelationships.identifies, Direction.INCOMING))
			{
				aliases.add(new AliasType((String) rel.getProperty("name"), (String) rel.getProperty("source"), (String) rel.getProperty("contributor"), (String) rel.getProperty("time")));
			}
			node.getAttributes().setValue("Aliases", aliases.toString());

			// Get Node Addresses
			node.getAttributes().setValue("Total Owned Addresses", Iterables.size(neoNode.getRelationships(OwnerRelTypes.owns, Direction.OUTGOING))); // TODO - Storing the actual addresses values
																																						// makes each gexf file too large!

			// Incoming Transactions
			node.getAttributes().setValue("Total Incoming Transactions", Iterables.size(neoNode.getRelationships(OwnerRelTypes.transfers, Direction.INCOMING)));

			// Outgoing Transactions
			node.getAttributes().setValue("Total Outgoing Transactions", Iterables.size(neoNode.getRelationships(OwnerRelTypes.transfers, Direction.OUTGOING)));

			// Get Node Degree
			node.getAttributes().setValue("Total Transactions", Iterables.size(neoNode.getRelationships(OwnerRelTypes.transfers, Direction.BOTH)));

			// Total Incoming Amount
			Long totalIncomingAmount = 0L;
			for (Relationship rel : neoNode.getRelationships(OwnerRelTypes.transfers, Direction.INCOMING))
			{
				totalIncomingAmount += (Long) rel.getProperty("value");
			}
			node.getAttributes().setValue("Total Incoming Amount", totalIncomingAmount.doubleValue() / 100000000);

			// Total Outgoing Amount
			Long totalOutgoingAmount = 0L;
			for (Relationship rel : neoNode.getRelationships(OwnerRelTypes.transfers, Direction.OUTGOING))
			{
				totalOutgoingAmount += (Long) rel.getProperty("value");
			}
			node.getAttributes().setValue("Total Outgoing Amount", totalOutgoingAmount.doubleValue() / 100000000);

			// Current Balance
			// node.getAttributes().setValue("Current Balance", (Double) node.getAttributes().getValue("Total Incoming Amount") - (Double) node.getAttributes().getValue("Total Outgoing Amount"));

			// First Time & Last Time Sent
			final ArrayList<Long> times = new ArrayList<Long>();
			for (Relationship rel : neoNode.getRelationships(OwnerRelTypes.transfers, Direction.BOTH))
			{
				times.add((Long) rel.getProperty("time"));
			}

			if (times.size() > 0)
			{
				node.getAttributes().setValue("First Transfer Time", Collections.min(times));
				node.getAttributes().setValue("Last Transfer Time", Collections.max(times));
			}

			// Date
			node.getAttributes().setValue("Last Block Calculated", graphDb.getNodeById(((Long) graphDb.getNodeById(0).getProperty("last_linked_owner_build_block_nodeId"))).getProperty("height"));
		}

		if (isDateCompare)
		{
			nodeDegreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
		}

		else
		{
			final AttributeColumn neo4jDegreeColumn = attributeModel.getNodeTable().getColumn("Total Transactions");
			nodeDegreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, neo4jDegreeColumn.getId());
		}

		// Remove system columns
		attributeModel.getNodeTable().removeColumn(attributeModel.getNodeTable().getColumn("last_time_sent"));

		nodeSizeTransformer.setMinSize(3);
		nodeSizeTransformer.setMaxSize(20);
		rankingController.transform(nodeDegreeRanking, nodeSizeTransformer);

		// Node Color
		final AttributeColumn lastTimeSentColumn = attributeModel.getNodeTable().getColumn("Last Transfer Time");
		if (lastTimeSentColumn != null)
		{
			final Ranking nodeLastTimeSentRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, lastTimeSentColumn.getId());
			final AbstractColorTransformer nodeColorTransformer = (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
			final float[] nodeColorPositions = { 0f, 0.5f, 1f };
			final Color[] nodeColors = new Color[] { new Color(0x0000FF), new Color(0x00FF00), new Color(0xFF0000) };
			nodeColorTransformer.setLinearGradient(new LinearGradient(nodeColors, nodeColorPositions));
			rankingController.transform(nodeLastTimeSentRanking, nodeColorTransformer);
		}

		// Node Label
		for (org.gephi.graph.api.Node node : graphModel.getGraph().getNodes())
		{
			Object label = node.getAttributes().getValue(PropertiesColumn.NODE_ID.getId());
			if (label instanceof String)
			{
				node.getNodeData().setLabel((String) label);
			} else if (label instanceof Long)
			{
				node.getNodeData().setLabel(Long.toString((Long) label));
			} else
			{
				throw new ClassCastException();
			}
		}
		PreviewModel previewModel = Lookup.getDefault().lookup(PreviewController.class).getModel();
		previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
		previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.FALSE);
		// previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_FONT, previewModel.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));

		// Edge Color
		final AttributeColumn edgeTimeSentColumn = attributeModel.getEdgeTable().getColumn("value");
		if (edgeTimeSentColumn != null)
		{
			final Ranking edgeTimeSentRanking = rankingController.getModel().getRanking(Ranking.EDGE_ELEMENT, edgeTimeSentColumn.getId());
			final AbstractColorTransformer edgeColorTransformer = (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.EDGE_ELEMENT, Transformer.RENDERABLE_COLOR);
			final float[] edgeColorPositions = { 0f, 0.5f, 1f };
			final Color[] edgeColors = new Color[] { new Color(0x0000FF), new Color(0x00FF00), new Color(0xFF0000) };
			edgeColorTransformer.setLinearGradient(new LinearGradient(edgeColors, edgeColorPositions));
			rankingController.transform(edgeTimeSentRanking, edgeColorTransformer);
		}

		// Edge Label
		for (org.gephi.graph.api.Edge edge : graphModel.getGraph().getEdges())
		{
			Object label = edge.getAttributes().getValue(PropertiesColumn.EDGE_ID.getId());
			if (label instanceof String)
			{
				edge.getEdgeData().setLabel(Double.toString(((Long) graphDb.getRelationshipById(Long.parseLong((String) label)).getProperty("value")).doubleValue() / 100000000));
			}

			else if (label instanceof Long)
			{
				edge.getEdgeData().setLabel(Double.toString(((Long) graphDb.getRelationshipById((Long) label).getProperty("value")).doubleValue() / 100000000));
			}

			else
			{
				throw new ClassCastException();
			}
		}
		previewModel.getProperties().putValue(PreviewProperty.SHOW_EDGE_LABELS, Boolean.TRUE);
		previewModel.getProperties().putValue(PreviewProperty.EDGE_CURVED, Boolean.TRUE);
		// previewModel.getProperties().putValue(PreviewProperty.EDGE_LABEL_FONT, previewModel.getProperties().getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(8));
		// previewModel.getProperties().putValue(PreviewProperty.ARROW_SIZE, 20f);

		LOG.info("Setting graph labels and features complete.");

		// Graph Layout
		LOG.info("Begin graph layout algorithm (Force Atlas 2 and Label Adjust)...");
		final ForceAtlas2 layout = new ForceAtlas2(new ForceAtlas2Builder());
		layout.setGraphModel(graphModel);
		layout.resetPropertiesValues();
		layout.setLinLogMode(true);
		layout.setThreadsCount(threadCount);
		layout.initAlgo();
		for (int i = 0; i < 1000 && layout.canAlgo(); i++)
		{
			layout.goAlgo();
		}

		// We need to prevent graphs from overlapping, but we only do so once the graph is spatialized
		layout.setAdjustSizes(true);
		for (int i = 0; i < 100 && layout.canAlgo(); i++)
		{
			layout.goAlgo();
		}

		layout.endAlgo();

		// We perform a label adjust to prevent overlapping labels
		final LabelAdjust labelAdjustLayout = new LabelAdjust(new LabelAdjustBuilder());
		labelAdjustLayout.setGraphModel(graphModel);
		for (int i = 0; i < 100 && labelAdjustLayout.canAlgo(); i++)
		{
			labelAdjustLayout.goAlgo();
		}

		labelAdjustLayout.endAlgo();

		LOG.info("Graph layout algorithm complete.");

		// Statistics
		if (isDateCompare && graphModel.getGraph().getNodeCount() > 0)
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
		String response = null; // TODO uglyyyyyyyyy.  Create a wrapped output manager object
		final ExportController ec = Lookup.getDefault().lookup(ExportController.class);
		final StringWriter gexfWriter = new StringWriter();
		final ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
		final ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
		
		if (isDateCompare || exportType == ExportType.GEXF)
		{
			LOG.info("Begin GEXF...");
			final org.gephi.io.exporter.spi.CharacterExporter gexfExporter = (CharacterExporter) ec.getExporter("gexf");
			gexfExporter.setWorkspace(graphModel.getWorkspace());
			ec.exportWriter(gexfWriter, gexfExporter);
			LOG.info("GEXF Complete.");
		}

		if (isDateCompare || exportType == ExportType.PDF)
		{
			LOG.info("Begin PDF...");
			final PDFExporter pdfExporter = (PDFExporter) ec.getExporter("pdf");
			ec.exportStream(pdfOutputStream, pdfExporter);
			LOG.info("PDF Complete.");
		}


		if (isDateCompare || exportType == ExportType.PNG)
		{
			LOG.info("Begin PNG...");
			final PNGExporter pngExporter = (PNGExporter) ec.getExporter("png");
			pngExporter.setTransparentBackground(true);
			ec.exportStream(pngOutputStream, pngExporter);
			LOG.info("PNG Complete.");
		}		

		if (isDateCompare)
		{
			try
			{
				LOG.info("Begin storing graph to MySql...");
				final PreparedStatement ps = sqlDb.prepareStatement("INSERT INTO `day` (graphTime, gexf, pdf, png) VALUES (?, ?, ?, ?)");
				ps.setLong(1, from.getTime() / 1000);
				ps.setString(2, gexfWriter.toString());
				ps.setBytes(3, pdfOutputStream.toByteArray());
				ps.setBytes(4, pngOutputStream.toByteArray());
				ps.execute();
				LOG.info("Storing graph to MySql complete.");
			}

			catch (SQLException e)
			{
				LOG.log(Level.SEVERE, "Unable to access MySQL database.", e);
				return null;
			}
		}

		else			
		{
			// TODO - UGLY UGLY UGLY
			LOG.info("Exporting to memory...");			
			
			if (exportType == ExportType.GEXF)
			{
				response = gexfWriter.toString();
			}
			
			else if (exportType == ExportType.PDF)
			{
				response = pdfOutputStream.toString();
			}
			
			else if (exportType == ExportType.PNG)
			{
				response = pngOutputStream.toString();
			}
			
			else
			{
				LOG.severe("No export type defined for export.");
				response = null;
			}
			
		}

		try
		{
			pdfOutputStream.flush();
			pdfOutputStream.close();
			pngOutputStream.flush();
			pngOutputStream.close();
		}

		catch (IOException e)
		{
			LOG.log(Level.WARNING, "Unable to close the output stream on PNG export.", e);
		}


		LOG.info("Exporting Graph To Disk Completed.");
		return response;
	}
		


	/**
	 * Sets up a mysql database and builds the tables.
	 */
	private static void SetupMysql(Connection sqlDb)
	{
		try
		{
			final Statement statement = sqlDb.createStatement();
			statement
					.execute("CREATE TABLE IF NOT EXISTS `owner` ( `ownerId` int(11) NOT NULL, `gexf` longblob, `pdf` longblob, `png` longblob, `time` timestamp NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`ownerId`), UNIQUE KEY `ownerId_UNIQUE` (`ownerId`)) ENGINE=InnoDB DEFAULT CHARSET=utf8");
			statement
					.execute("CREATE TABLE IF NOT EXISTS `day` ( `graphTime` double NOT NULL, `gexf` longblob, `pdf` longblob, `png` longblob,  `time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,  PRIMARY KEY (`graphTime`), UNIQUE KEY `graphtime_UNIQUE` (`graphTime`)) ENGINE=InnoDB DEFAULT CHARSET=utf8");
		}

		catch (SQLException e)
		{
			LOG.log(Level.SEVERE, "Unable to access MySQL database.", e);
		}
	}

	private static class AliasType
	{
		private String name;
		private String source;
		private String contributor;
		private String time;

		AliasType(String name, String source, String contributor, String time)
		{
			this.name = name;
			this.source = source;
			this.contributor = contributor;
			this.time = time;
		}
	}
}
