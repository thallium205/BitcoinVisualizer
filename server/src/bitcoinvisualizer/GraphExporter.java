package bitcoinvisualizer;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
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
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2Builder;
import org.gephi.layout.plugin.labelAdjust.LabelAdjust;
import org.gephi.layout.plugin.labelAdjust.LabelAdjustBuilder;
import org.gephi.neo4j.plugin.api.FilterDescription;
import org.gephi.neo4j.plugin.api.FilterOperator;
import org.gephi.neo4j.plugin.api.Neo4jImporter;
import org.gephi.neo4j.plugin.api.RelationshipDescription;
import org.gephi.neo4j.plugin.api.TraversalOrder;
import org.gephi.neo4j.plugin.impl.MaxNodesExceededException;
import org.gephi.neo4j.plugin.impl.Neo4jImporterImpl;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
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
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.openide.util.Lookup;

import bitcoinvisualizer.GraphBuilder.OwnerRelTypes;
import bitcoinvisualizer.scraper.Scraper.ScraperRelationships;

import com.google.common.collect.Iterables;

/**
 * Creates an information rich graph in .gexf format of the Bitcoin ownership network given the path to a Neo4j graph.db database.
 * 
 * @author John
 * 
 */
public class GraphExporter
{
	private static final Logger LOG = Logger.getLogger(GraphExporter.class.getName());
	private static final int cores = Runtime.getRuntime().availableProcessors();	
	public static final String OWNED_ADDRESS_HASH = "owned_addr_hashes";
	public static final String OWNED_ADDRESS_HASH_KEY = "owned_addr_hash";
	private static Index<Node> owned_addresses;

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

			final Date lastTime = new Date(((Long) graphDb.getNodeById((((Long) graphDb.getNodeById(0).getProperty("last_linked_owner_build_block_nodeId")))).getProperty("time")) * 1000);
			while (!to.after(lastTime))
			{
				Export(sqlDb, graphDb, null, from, to, threadCount);
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
	
	public static void ExportOwnerGraphsToMySql(final GraphDatabaseAPI graphDb)
	{
		int ownersProcessed = 0;

		try
		{
			Class.forName("com.mysql.jdbc.Driver");
			final Connection sqlDb = DriverManager.getConnection("jdbc:mysql://localhost:3306/blockviewer?user=root&password=webster");
			SetupMysql(sqlDb);
			
			owned_addresses = graphDb.index().forNodes(OWNED_ADDRESS_HASH);
			HashSet<Long> owners = new HashSet<Long>();
			owners.add(29792952L);
			owners.add(30601119L);
			for (Node node : owned_addresses.query("*:*"))
			{
				Long ownerId = node.getSingleRelationship(OwnerRelTypes.owns, Direction.INCOMING).getStartNode().getId();				
				if (owners.add(ownerId))
				{
					Export(sqlDb, graphDb, ownerId, null, null, cores > 1 ? cores - 1 : cores);
					ownersProcessed ++;
					LOG.info("Owners Processed: " + ownersProcessed);
				}						

			}
			
			sqlDb.close();
			LOG.info("Total Number of Owners Processed:" + owners.size());			
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

	private static synchronized void Export(final Connection sqlDb, final GraphDatabaseAPI graphDb, Long ownerId, final Date from, final Date to, final int threadCount)
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
			
			try
			{
				importer.importDatabase(graphDb, ownerId, TraversalOrder.BREADTH_FIRST, Integer.MAX_VALUE, relationshipDescription, Collections.<FilterDescription> emptyList(), edgeFilterDescription,
						true, true, Integer.MAX_VALUE);
			} 
			
			catch (MaxNodesExceededException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		// We export a one-hop graph from the address
		else
		{
			assert (from == null && to == null);
			
			// Before importing, we have to check to see if lastSeen is less than the "last_time_sent" value on the owner node.  
			// If it is, then we continue.  If not, then we return because the owner has already been processed.			
			try
			{
				final PreparedStatement statement = sqlDb.prepareStatement("SELECT ownerId, lastSeen FROM owner WHERE ownerId = (?) LIMIT 1");
				statement.setLong(1, ownerId);
				final ResultSet result = statement.executeQuery();
				if (result.first())
				{
					final Long lastSeen = (long) result.getDouble("lastSeen");
					if (lastSeen >= Long.parseLong(graphDb.getNodeById(ownerId).getProperty("last_time_sent", 0).toString()))
					{
						LOG.info("This owner is already at its latest state.  Skipping...");
						return;
					}					
				}
						
			} 
			
			catch (SQLException e)
			{
				LOG.log(Level.SEVERE, "A SQL error has occured.", e);
			}
			
			
			// Import by traversing one hop along the ownership network's "transfers" edges
			LOG.info("Importing Ownership Network from Neo4j Database Starting With Node: " + ownerId + " ...");
			
			// This is the mt gox node that crashes the box, even just counting. TODO
			if (ownerId == 29792952L)
			{
				return;
			}
			
			final Collection<RelationshipDescription> relationshipDescription = new ArrayList<RelationshipDescription>();
			relationshipDescription.add(new RelationshipDescription(GraphBuilder.OwnerRelTypes.transfers, Direction.BOTH));
			try
			{
				importer.importDatabase(graphDb, ownerId, TraversalOrder.BREADTH_FIRST, 1, relationshipDescription, 2500);
			} 
			
			catch (MaxNodesExceededException e)
			{
				LOG.warning("The graph is being skipped because it contains over 2,500 nodes.");
				return;
			}
			
		}

		// Grab the graph that was loaded from the importer
		final GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getModel();
		final DirectedGraph graph = graphModel.getDirectedGraph();
		final AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
		LOG.info("Graph Imported.  Nodes: " + graph.getNodeCount() + " Edges: " + graph.getEdgeCount());

		LOG.info("Setting graph labels and features...");
		if (isDateCompare)
		{
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
				LOG.info("Remove start node from graph as it does not satisfy filterable description.");
				// Find the start node and remove it from the graph
				for (org.gephi.graph.api.Node node : graphModel.getGraph().getNodes().toArray())
				{
					if (((Long) node.getAttributes().getValue("id")).toString().contains(ownerId.toString()))
					{
						graphModel.getGraph().removeNode(node);
						break;
					}
				}			
			}
			
		}
		
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

		// We set a degree attribute on each gephi node
		if (!isDateCompare)
		{
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
			
				// Scraped Alias
				ArrayList<AliasType> aliases = new ArrayList<AliasType>();
				for (Relationship rel : neoNode.getRelationships(ScraperRelationships.identifies, Direction.INCOMING))
				{
					if (rel.getProperty("time") instanceof String)
					{
						aliases.add(new AliasType((String) rel.getProperty("name"), (String) rel.getProperty("source"), (String) rel.getProperty("contributor"), (String) rel.getProperty("time")));
					}
					
					else
					{
						aliases.add(new AliasType((String) rel.getProperty("name"), (String) rel.getProperty("source"), (String) rel.getProperty("contributor"), ((Long) rel.getProperty("time")).toString()));
					}					
				}				
				StringBuilder builder = new StringBuilder();
				for (AliasType alias : aliases)
				{
					builder.append("Name: " + alias.getName() + " ");
					builder.append("Source: " + alias.getSource() + " ");
					builder.append("Contributor: " + alias.getContributor() + " ");
				}				
				node.getAttributes().setValue("Aliases", builder.toString());
	
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
		}	
		
		// Remove system columns
		attributeModel.getNodeTable().removeColumn(attributeModel.getNodeTable().getColumn("last_time_sent"));
		
		// Ranking
		final RankingController rankingController = Lookup.getDefault().lookup(RankingController.class);
		rankingController.setInterpolator(Interpolator.LOG2);
				
		// Node Size		
		final AbstractSizeTransformer nodeSizeTransformer = (AbstractSizeTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_SIZE);
		nodeSizeTransformer.setMinSize(3);
		nodeSizeTransformer.setMaxSize(20);
		
		if (isDateCompare)
		{
			if (graphModel.getGraph().getNodeCount() > 0)
			{
				// Centrality
				final AttributeColumn centralityColumn = attributeModel.getNodeTable().getColumn(GraphDistance.BETWEENNESS);
				final Ranking centralityRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, centralityColumn.getId());
				rankingController.transform(centralityRanking, nodeSizeTransformer);
			}			
		}

		else
		{	
			final AttributeColumn neo4jDegreeColumn = attributeModel.getNodeTable().getColumn("Total Transactions");
			final Ranking nodeSizeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, neo4jDegreeColumn.getId());
			rankingController.transform(nodeSizeRanking, nodeSizeTransformer);
		}

		// Node Color	
		final AbstractColorTransformer nodeColorTransformer = (AbstractColorTransformer) rankingController.getModel().getTransformer(Ranking.NODE_ELEMENT, Transformer.RENDERABLE_COLOR);
		final float[] nodeColorPositions = { 0f, 0.5f, 1f };
		final Color[] nodeColors = new Color[] { new Color(0x0000FF), new Color(0x00FF00), new Color(0xFF0000) };
		nodeColorTransformer.setLinearGradient(new LinearGradient(nodeColors, nodeColorPositions));
		
		if (isDateCompare)
		{
			final Ranking nodeDegreeRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, Ranking.DEGREE_RANKING);
			rankingController.transform(nodeDegreeRanking, nodeColorTransformer);
		}
		
		else
		{
			final AttributeColumn lastTimeSentColumn = attributeModel.getNodeTable().getColumn("Last Transfer Time");
			if (lastTimeSentColumn != null)
			{
				final Ranking nodeLastTimeSentRanking = rankingController.getModel().getRanking(Ranking.NODE_ELEMENT, lastTimeSentColumn.getId());
				rankingController.transform(nodeLastTimeSentRanking, nodeColorTransformer);
			}
		}

		// Node Label
		for (org.gephi.graph.api.Node node : graphModel.getGraph().getNodes())
		{
			Object label = node.getAttributes().getValue(PropertiesColumn.NODE_ID.getId());
			
			if (label instanceof String)
			{
				node.getNodeData().setLabel((String) label);
			} 
			
			else if (label instanceof Long)
			{
				node.getNodeData().setLabel(Long.toString((Long) label));
			} 
			
			else
			{
				throw new ClassCastException();
			}
		}
		
		final PreviewModel previewModel = Lookup.getDefault().lookup(PreviewController.class).getModel();
		previewModel.getProperties().putValue(PreviewProperty.SHOW_NODE_LABELS, Boolean.TRUE);
		previewModel.getProperties().putValue(PreviewProperty.NODE_LABEL_PROPORTIONAL_SIZE, Boolean.FALSE);

		// Edge Color
		final AttributeColumn edgeTimeSentColumn = attributeModel.getEdgeTable().getColumn("time");
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
		for (int i = 0; i < 100 && layout.canAlgo(); i++)
		{
			layout.goAlgo();
		}

		// We need to prevent graphs from overlapping, but we only do so once the graph is spatialized
		layout.setAdjustSizes(true);
		for (int i = 0; i < 10 && layout.canAlgo(); i++)
		{
			layout.goAlgo();
		}

		layout.endAlgo();

		// We perform a label adjust to prevent overlapping labels
		final LabelAdjust labelAdjustLayout = new LabelAdjust(new LabelAdjustBuilder());
		labelAdjustLayout.setGraphModel(graphModel);
		for (int i = 0; i < 10 && labelAdjustLayout.canAlgo(); i++)
		{
			labelAdjustLayout.goAlgo();
		}

		labelAdjustLayout.endAlgo();

		LOG.info("Graph layout algorithm complete.");

		// Export
		LOG.info("Begin Exporting Graph To Disk...");
		String response = null; // TODO uglyyyyyyyyy.  Create a wrapped output manager object
		final ExportController ec = Lookup.getDefault().lookup(ExportController.class);
		final StringWriter gexfWriter = new StringWriter();
		final ByteArrayOutputStream pdfOutputStream = new ByteArrayOutputStream();
		final ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();		

		LOG.info("Begin GEXF...");
		final org.gephi.io.exporter.spi.CharacterExporter gexfExporter = (CharacterExporter) ec.getExporter("gexf");
		gexfExporter.setWorkspace(graphModel.getWorkspace());
		ec.exportWriter(gexfWriter, gexfExporter);
		LOG.info("GEXF Complete.");
	
		if (isDateCompare)
		{
			LOG.info("Begin PDF...");
			final PDFExporter pdfExporter = (PDFExporter) ec.getExporter("pdf");
			ec.exportStream(pdfOutputStream, pdfExporter);
		
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
				LOG.info("Begin storing time graph to MySql...");
				final PreparedStatement ps = sqlDb.prepareStatement("INSERT INTO `day` (graphTime, gexf, pdf, png) VALUES (?, ?, ?, ?)");
				ps.setLong(1, from.getTime() / 1000);
				ps.setString(2, gexfWriter.toString());
				ps.setBytes(3, pdfOutputStream.toByteArray());
				ps.setBytes(4, pngOutputStream.toByteArray());
				ps.execute();
				LOG.info("Storing time graph to MySql complete.");
			}

			catch (SQLException e)
			{
				LOG.log(Level.SEVERE, "Unable to access MySQL database.", e);
				return;
			}	
		}
		
		else 
		{
			try
			{
				LOG.info("Begin storing owner graph to MySql...");
				final PreparedStatement ps = sqlDb.prepareStatement("INSERT INTO `owner` (ownerId, lastSeen, gexf) VALUES (?, ?, ?)");
				ps.setLong(1, ownerId);
				ps.setLong(2, Long.parseLong(graphDb.getNodeById(ownerId).getProperty("last_time_sent", 0).toString()));				 
				ps.setString(3, gexfWriter.toString());
				ps.execute();
				LOG.info("Storing owner graph to MySql complete.");
			}

			catch (SQLException e)
			{
				LOG.log(Level.SEVERE, "Unable to access MySQL database.", e);
				return;
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
	}

	/**
	 * Sets up a mysql database and builds the tables.
	 */
	private static void SetupMysql(Connection sqlDb)
	{
		try
		{
			final Statement statement = sqlDb.createStatement();
			statement.execute("CREATE TABLE IF NOT EXISTS `day` ( `graphTime` double NOT NULL, `gexf` longblob, `pdf` longblob, `png` longblob,  `time` timestamp NULL DEFAULT CURRENT_TIMESTAMP,  PRIMARY KEY (`graphTime`), UNIQUE KEY `graphtime_UNIQUE` (`graphTime`)) ENGINE=InnoDB DEFAULT CHARSET=utf8");
			statement.execute("CREATE TABLE IF NOT EXISTS `owner` ( `ownerId` int(11) NOT NULL, `lastSeen` double, `gexf` longblob, `time` timestamp NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (`ownerId`), UNIQUE KEY `ownerId_UNIQUE` (`ownerId`)) ENGINE=InnoDB DEFAULT CHARSET=utf8");
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

		public String getName()
		{
			return name;
		}

		public void setName(String name)
		{
			this.name = name;
		}

		public String getSource()
		{
			return source;
		}

		public void setSource(String source)
		{
			this.source = source;
		}

		public String getContributor()
		{
			return contributor;
		}

		public void setContributor(String contributor)
		{
			this.contributor = contributor;
		}

		public String getTime()
		{
			return time;
		}

		public void setTime(String time)
		{
			this.time = time;
		}		
	}
}
