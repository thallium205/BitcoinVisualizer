package bitcoinvisualizer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.Traverser;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.Traversal;

import bitcoinvisualizer.GraphBuilder.BlockchainRelationships;

public class GraphAnalyzer
{
	private static final Logger LOG = Logger.getLogger(GraphBuilder.class.getName());
	
	private static GraphDatabaseAPI graphDb;
	
	public static void BuildTransactionStatistics(final GraphDatabaseAPI graphDb) throws IOException 
	{
		LOG.info("Begin analyzing the block chain");		
		// For every block, we count the total number of transactions in it, and the total number of unredeemed transactions in it
		final Traverser blocks = graphDb.getReferenceNode().traverse(Order.BREADTH_FIRST, StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL_BUT_START_NODE, GraphBuilder.BlockchainRelationships.succeeds, Direction.INCOMING);
		final ArrayList<BlockStatisticType> blockchainStatistics = new ArrayList<BlockStatisticType>();
		for (final Node block : blocks) 
		{
			final BlockStatisticType statistic = new BlockStatisticType();
			statistic.setHash((String) block.getProperty("hash"));
			statistic.setHeight((Integer) block.getProperty("height"));
			statistic.setTime((Long) block.getProperty("time"));
			
			final TraversalDescription monies = Traversal.description()
					.relationships(BlockchainRelationships.from, Direction.INCOMING)
					.relationships(BlockchainRelationships.sent)
					.evaluator(Evaluators.atDepth(2));
			
			int unspentTransactions = 0;
			int totalSentTransactions = 0;
			Long unspentValue = 0L;
			Long totalSentValue = 0L;
			for (final Path moneyPath : monies.traverse(block))
			{
				final Node money = moneyPath.endNode();
				if (!money.hasRelationship(BlockchainRelationships.received, Direction.OUTGOING))
				{
					unspentTransactions ++;
					unspentValue = unspentValue + (Long) money.getProperty("value");
				}
				totalSentTransactions ++;
				totalSentValue = totalSentValue + (Long) money.getProperty("value");
			}
			
			statistic.setUnspentTransactions(unspentTransactions);
			statistic.setUnspentValue(unspentValue);
			statistic.setTotalSentTransactions(totalSentTransactions);
			statistic.setTotalSentValue(totalSentValue);
			blockchainStatistics.add(statistic);
			LOG.info("Block: " + statistic.getHeight());			
		}
		
		LOG.info("Writing statistics to disk...");	
		// Write the array to a csv on disk
		FileWriter fw = new FileWriter("BlockchainStatistics.csv");
		PrintWriter pw = new PrintWriter(fw);
		
		pw.print("Block Hash");
		pw.print(",");
		pw.print("Block Height");
		pw.print(",");
		pw.print("Block Time");
		pw.print(",");
		pw.print("Unspent Transactions");
		pw.print(",");
		pw.print("Unspent Value");
		pw.print(",");
		pw.print("Total Sent Transactions");
		pw.print(",");
		pw.print("Total Sent Value");

		pw.println();
		
		for (final BlockStatisticType statistic : blockchainStatistics)
		{
			pw.print(statistic.getHash());
			pw.print(",");
			pw.print(statistic.getHeight());
			pw.print(",");
			pw.print(statistic.getTime());
			pw.print(",");
			pw.print(statistic.getUnspentTransactions());
			pw.print(",");
			pw.print(statistic.getUnspentValue());
			pw.print(",");
			pw.print(statistic.getTotalSentTransactions());
			pw.print(",");
			pw.print(statistic.getTotalSentValue());
			pw.println();
		}
		
		pw.flush();
		pw.close();
		fw.close();
		LOG.info("Writing statistics to disk completed.");	
		
		// Stop the database
		graphDb.shutdown();
		LOG.info("Analyzing the block chain completed.");	
	}
	
	private static class BlockStatisticType
	{
		private String hash;
		private Integer height;
		private Long time;
		private Integer unspentTransactions;
		private Long unspentValue;
		private Integer totalSentTransactions;
		private Long totalSentValue;
		
		public String getHash()
		{
			return hash;
		}
		public void setHash(String hash)
		{
			this.hash = hash;
		}
		public Integer getHeight()
		{
			return height;
		}
		public void setHeight(Integer height)
		{
			this.height = height;
		}
		public Long getTime()
		{
			return time;
		}
		public void setTime(Long time)
		{
			this.time = time;
		}
		public Integer getUnspentTransactions()
		{
			return unspentTransactions;
		}
		public void setUnspentTransactions(Integer unspentTransactions)
		{
			this.unspentTransactions = unspentTransactions;
		}
		public Long getUnspentValue()
		{
			return unspentValue;
		}
		public void setUnspentValue(Long unspentValue)
		{
			this.unspentValue = unspentValue;
		}
		public Integer getTotalSentTransactions()
		{
			return totalSentTransactions;
		}
		public void setTotalSentTransactions(Integer totalSentTransactions)
		{
			this.totalSentTransactions = totalSentTransactions;
		}
		public Long getTotalSentValue()
		{
			return totalSentValue;
		}
		public void setTotalSentValue(Long totalSentValue)
		{
			this.totalSentValue = totalSentValue;
		}
	}
	
}
