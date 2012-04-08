package blockchainneo4j;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.neo4j.rest.graphdb.RestAPI;
import org.neo4j.rest.graphdb.entity.RestNode;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.DynamicRelationshipType;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.index.UniqueFactory;
import org.neo4j.graphdb.traversal.Evaluator;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.kernel.impl.traversal.TraversalDescriptionImpl;

import blockchainneo4j.domain.BlockType;
import blockchainneo4j.domain.InputType;
import blockchainneo4j.domain.LatestBlock;
import blockchainneo4j.domain.OutputType;
import blockchainneo4j.domain.PrevOut;
import blockchainneo4j.domain.TransactionType;

public class Database
{
	private static final Logger LOG = Logger.getLogger(Database.class.getName());

	RestAPI restApi;
	ConcurrentLinkedQueue<Integer> blockIndexes = new ConcurrentLinkedQueue<Integer>();

	enum BitcoinRelationships implements RelationshipType
	{
		succeeds, from, received, sent, to_be_determined
	}

	public Database(String uri)
	{
		restApi = new RestAPI(uri);
	}

	public Database(String uri, String user, String pass)
	{
		restApi = new RestAPI(uri, user, pass);
	}

	public void persistVertices(int numOfThreads)
	{
		// Last block in the chain that has a relationship from the database
	//	Node lastBlock = getLatestLocalBlockNode();
		
		// Get the last block's index.  Will be null if nothing exists in the database.
	//	int lastBlockIndex;
	//	if (lastBlock != null)
	//		lastBlockIndex = (Integer) lastBlock.getProperty("block_index");
	//	else
	//		lastBlockIndex = 1;

		// Fetch the latest block index from the API
		LatestBlock latestBlock = Fetcher.GetLatest();
		if (latestBlock == null)
		{
			LOG.severe("Unable to retreive the latest block index.  Aborting.");
			return;
		}

		// Populate our queue of what blocks we need to download
		for (int i = 1; i < latestBlock.getBlock_index(); i++)
			blockIndexes.offer(i);

		// While the queue has block indexes in it, we will launch a thread
		// to evaluate an index in the queue.
		ExecutorService threadExecutor = Executors.newFixedThreadPool(numOfThreads);
		for (int i = 0; i < numOfThreads; i++)
		{
			threadExecutor.execute(new DatabaseThread(restApi, blockIndexes, restApi.getReferenceNode()));
		}

		threadExecutor.shutdown();

	}

	/**
	 * Creates relationships between Blocks and previous Blocks. Also creates
	 * relationships between Money and Transactions.
	 */
	public void persistEdges()
	{
		// Create all block relationships
		// Get the latest block added (it will not have a relationship most
		// likely)
		Node lastBlock = persistBlockEdges(getLatestLocalBlockNode());

	}

	/**
	 * Stores blocks, transactions, and input/output nodes to the database.
	 * Blocks will be given a temporary relationship of "to_be_determined" to
	 * the latest categorized block to the database. This relationship will be
	 * deleted and replaced with the correct order of blocks when persistEdges()
	 * is called. Transactions will be given a relationship of "from" to its
	 * owner Block. Input/Output nodes (called Money nodes) will either be given
	 * "sent" or "received" relationships based upon what they represent.
	 * 
	 * @author John
	 * 
	 */
	static class DatabaseThread implements Runnable
	{
		RestAPI restApi;
		ConcurrentLinkedQueue<Integer> queue;
		Node parentNode;

		DatabaseThread(RestAPI restApi, ConcurrentLinkedQueue<Integer> queue, Node parentNode)
		{
			this.restApi = restApi;
			this.queue = queue;
			this.parentNode = parentNode;
		}

		@Override
		public void run()
		{
			BlockType block = null;

			while (!queue.isEmpty())
			{
				try
				{
					// Get the block index to download
					Integer index = queue.poll();

					// If null, we have downloaded all the blocks. Exit.
					if (index == null)
						return;

					// Download the block
					block = Fetcher.GetBlock(index);

					// Persist block node
					Map<String, Object> blockProps = new HashMap<String, Object>();
					RestNode blockNode = null;
					blockProps.put("hash", block.getHash());
					blockProps.put("ver", block.getVer());
					blockProps.put("prev_block", block.getPrev_block());
					blockProps.put("mrkl_root", block.getMrkl_root());
					blockProps.put("time", block.getTime());
					blockProps.put("bits", block.getBits());
					blockProps.put("nonce", block.getNonce());
					blockProps.put("n_tx", block.getN_tx());
					blockProps.put("size", block.getSize());
					blockProps.put("block_index", block.getBlock_index());
					blockProps.put("main_chain", block.getMain_chain());
					blockProps.put("height", block.getHeight());
					blockProps.put("received_time", block.getReceived_time());
					blockProps.put("relayed_by", block.getRelayed_by());
					blockNode = restApi.createNode(blockProps);

					// Create a temporary relationship of this block to the
					// parentBlock
					restApi.createRelationship(parentNode, blockNode, BitcoinRelationships.to_be_determined, null);

					// Persist transaction nodes
					// The transaction node properties
					Map<String, Object> tranProps;
					// The transaction object
					TransactionType tran;
					// The transaction node
					RestNode tranNode = null;
					// The relationship properties between transaction and block
					Map<String, Object> fromRelation;
					for (Iterator<TransactionType> tranIter = block.getTx().iterator(); tranIter.hasNext();)
					{
						tranProps = new HashMap<String, Object>();
						tran = tranIter.next();
						tranProps.put("hash", tran.getHash());
						tranProps.put("ver", tran.getVer());
						tranProps.put("vin_sz", tran.getVin_sz());
						tranProps.put("vout_sz", tran.getVout_sz());
						tranProps.put("size", tran.getSize());
						tranProps.put("relayed_by", tran.getRelayed_by());
						tranProps.put("tx_index", tran.getTx_index());
						tranNode = restApi.createNode(tranProps);

						fromRelation = new HashMap<String, Object>();
						fromRelation.put("block_hash", block.getHash());
						restApi.createRelationship(tranNode, blockNode, BitcoinRelationships.from, fromRelation);

						// Persist Money nodes
						Map<String, Object> moneyProps; // The money node
														// properties
						PrevOut prevOut; // The input object
						RestNode moneyNode = null; // The money node
						// The relationship properties between input and
						// transaction
						Map<String, Object> receivedRelation;
						for (Iterator<InputType> inputIter = tran.getInputs().iterator(); inputIter.hasNext();)
						{
							moneyProps = new HashMap<String, Object>();
							prevOut = inputIter.next().getPrev_out();
							if (prevOut == null) // If money was created
								continue;
							moneyProps.put("type", prevOut.getType());
							moneyProps.put("addr", prevOut.getAddr());
							moneyProps.put("value", prevOut.getValue());
							moneyProps.put("n", prevOut.getN());
							moneyNode = restApi.createNode(moneyProps);

							receivedRelation = new HashMap<String, Object>();
							receivedRelation.put("tx_index", prevOut.getTx_index());
							restApi.createRelationship(moneyNode, tranNode, BitcoinRelationships.received, receivedRelation);
						}

						// The output object
						OutputType output;
						// The relationship properties between transaction and
						// output
						Map<String, Object> sentRelation;
						// The location of an output within a transaction.
						int n = 0;
						for (Iterator<OutputType> outputIter = tran.getOut().iterator(); outputIter.hasNext();)
						{
							moneyProps = new HashMap<String, Object>();
							output = outputIter.next();
							moneyProps.put("type", output.getType());
							moneyProps.put("addr", output.getAddr());
							moneyProps.put("value", output.getValue());
							moneyProps.put("n", n);
							moneyNode = restApi.createNode(moneyProps);

							sentRelation = new HashMap<String, Object>();
							sentRelation.put("n", n);
							restApi.createRelationship(tranNode, moneyNode, BitcoinRelationships.sent, sentRelation);
							n++;
						}
					}
				}

				catch (FetcherException e)
				{
					LOG.log(Level.WARNING, "FethcerThread failed.  Download on block: " + e.getFailedBlockIndex() + " will try again later.", e);

					// Add the failed block index back to the queue to try it
					// again later in another thread
					queue.offer(e.getFailedBlockIndex());

					// Backoff timeout to slow down the speed by which we are
					// receiving information from the API.
					try
					{
						Thread.sleep(2000);
					}

					catch (InterruptedException e1)
					{
						LOG.log(Level.SEVERE, "Thread.sleep() was interrupted.  Aborting...", e1);
					}
				}

				// A one second wait period for the API
				try
				{
					Thread.sleep(1000);
				}

				catch (InterruptedException e)
				{
					LOG.log(Level.SEVERE, "Thread.sleep() was interrupted.  Aborting...", e);
				}
			}
		}
	}

	/**
	 * Queries the local database to find the latest stored block index. Used to
	 * set a lower bound on what Blocks to fetch from the API.
	 * 
	 * @return The next block index the local datastore needs to store.
	 */
	private Node getLatestLocalBlockNode()
	{
		Node referenceNode = restApi.getNodeById(1); // Genesis block TODO
		TraversalDescription td = new TraversalDescriptionImpl();
		td = td.depthFirst().relationships(BitcoinRelationships.succeeds, Direction.INCOMING);

		Traverser traverser = td.traverse(referenceNode);
		for (Iterator<Path> iter = traverser.iterator(); iter.hasNext();)
		{
			Node node = iter.next().endNode();
			if (!node.hasRelationship(BitcoinRelationships.succeeds, Direction.INCOMING))
				return node;
		}
		return null;
	}

	/**
	 * Creates relationships with all blocks that do not have one yet.
	 * 
	 * @param lastNode
	 *            - the last node in the database that has a relationship.
	 * @return Newest lastNode in the database. It will be the latest block in
	 *         the blockchain.
	 */
	private Node persistBlockEdges(Node lastNode)
	{
		Map<String, Object> succeedsProps = new HashMap<String, Object>();
		int lastIndex = (Integer) lastNode.getProperty("block_index");
		int currentIndex;
		boolean nodeFound = true;

		// Traverse all the nodes that have to_be_determined relationships to
		// them.
		TraversalDescription td = new TraversalDescriptionImpl();
		td = td.depthFirst().relationships(BitcoinRelationships.to_be_determined);

		while (nodeFound)
		{
			nodeFound = false;
			Iterable<Node> traverser = td.traverse(restApi.getReferenceNode()).nodes();
			for (Iterator<Node> iter = traverser.iterator(); iter.hasNext();)
			{
				Node currentNode = iter.next();
				// Have we found a block node?
				if (currentNode.hasProperty("block_index"))
				{
					currentIndex = (Integer) currentNode.getProperty("block_index");

					// If the block index is one greater than the last block's
					// index, then we found the succeeding block
					if (currentIndex == lastIndex + 1)
					{
						// We have found the block that succeeds this one, we
						// create
						// a relationship
						succeedsProps.put("prev_block", lastNode.getProperty("hash"));
						restApi.createRelationship(currentNode, lastNode, BitcoinRelationships.succeeds, succeedsProps);

						// We set the lastNode to the currentNode, and repeat
						// the
						// process until there are no more nodes
						lastNode = currentNode;
						lastIndex = currentIndex;
						succeedsProps.clear();
						nodeFound = true;
						break;
					}
				}
			}
		}

		return lastNode;
	}
}

/*
 * //BlockType block = Fetcher.GetBlock(
 * "0000000000000bae09a7a393a8acded75aa67e46cb81f7acaa5ad94f9eacd103");
 * //System.out.println(block.getHash());
 */