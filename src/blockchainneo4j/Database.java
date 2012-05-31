package blockchainneo4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.graphdb.traversal.Traverser;
import org.neo4j.kernel.impl.traversal.TraversalDescriptionImpl;

import blockchainneo4j.domain.BlockJsonType;
import blockchainneo4j.domain.BlockType;
import blockchainneo4j.domain.InputType;
import blockchainneo4j.domain.LatestBlock;
import blockchainneo4j.domain.OutputType;
import blockchainneo4j.domain.PrevOut;
import blockchainneo4j.domain.TransactionType;

/**
 * Provides functionality to fetch the low-level block chain and the high level abstraction.
 * 
 * @author John
 * 
 */
public class Database
{
	private static final Logger LOG = Logger.getLogger(Database.class.getName());

	private static final String TRANSACTION_INDEX = "transactions";
	private static final String TRANSACTION_INDEX_KEY = "tx_index";

	GraphDatabaseService graphDb;
	Index<Node> transactionsIndex;

	/**
	 * Represents the basic relationships of the model.
	 * 
	 * @author John
	 * 
	 */
	enum BlockchainRelationships implements RelationshipType
	{
		succeeds, from, received, sent
	}

	public Database(final String dbPath, final String dbConfig)
	{
		graphDb = new GraphDatabaseFactory().newEmbeddedDatabaseBuilder(dbPath).loadPropertiesFromFile(dbConfig + "neo4j.properties").newGraphDatabase();
		transactionsIndex = graphDb.index().forNodes(TRANSACTION_INDEX);
		
		// Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread() 
        {
            @Override
            public void run() 
            {
                graphDb.shutdown();
            }
        });
	}

	/**
	 * Stores blocks, transactions, and input/output nodes to the database. Blocks will be given a relationship of "succeeds" to the previous categorized block to the database. Transactions will be
	 * given a relationship of "from" to its owner Block. Input/Output nodes (called Money nodes) will either be given "sent" or "received" relationships based upon what they represent. This function
	 * maintains a local json repository by downloading it directly from the API. Then, it persists the json blocks to the datastore from these local json files.
	 * 
	 * 
	 * @author John
	 * 
	 */
	public void downloadAndSaveBlockChain(boolean doValidate)
	{
		// We find the latest block from the internet
		LatestBlock latestInternetBlock = Fetcher.GetLatest();
		
		// The latest block index that exists in the bitcoin blockchain
		final int latestRemotBlockIndex = latestInternetBlock.getBlock_index();
		
		// The latest block index we have on disk before downloadChain() is called.
		final int latestLocalBlockIndex = latestDiskBlockIndex();
		
		// The most recent database block index that has been persisted to neo4j
		int lastDatabaseBlockIndex;

		// We fetch the difference
		downloadChainFromInternet(latestLocalBlockIndex, latestInternetBlock);

		// We now find the latest block persisted to the database
		Node latestDatabaseBlock = getLatestDatabaseBlockNode();
		if (latestDatabaseBlock.hasProperty("block_index"))
			lastDatabaseBlockIndex = (Integer) latestDatabaseBlock.getProperty("block_index");
		else
			lastDatabaseBlockIndex = 0;

		// We persist the difference
		if (doValidate)
		{
			LOG.info("Starting blockchain validation...");
			if (isBlockchainComplete())
			{
				LOG.info("Integrity test succeeded.");
			}

			else
			{
				LOG.severe("Integrity test failed. Aborting... Try again.");
				return;
			}
		}

		else
		{
			LOG.info("Skipping blockchain validation...");
		}

		LOG.info("Begin persistance...");
		// Begin persistence
		BlockType currentBlock = null;
		Transaction tx;
		while (lastDatabaseBlockIndex < latestRemotBlockIndex)
		{
			// Begin transaction
			LOG.info("Persisting Block: " + currentBlock.getBlock_index());
			tx = graphDb.beginTx();
			try
			{
				currentBlock = getNextBlockFromDisk(lastDatabaseBlockIndex, latestRemotBlockIndex);
				
				// We save the block node and set it to the latestDatabaseBlock
				latestDatabaseBlock = persistBlockNode(currentBlock, latestDatabaseBlock);				
				tx.success();
				
				// Update the previous block node to the current one and repeat
				lastDatabaseBlockIndex = currentBlock.getBlock_index();
			}

			catch (FetcherException e)
			{
				LOG.log(Level.SEVERE, "Corrupted block on disk.  Reason:  " + e.getMessage() + " Aborting...", e);
				tx.failure();
				return;
			}

			catch (Exception e)
			{
				LOG.log(Level.SEVERE, "Error in persistence of block: " + currentBlock.getBlock_index() + " Aborting...", e);
				tx.failure();
				return;
			}

			finally
			{
				LOG.info("Block persistence completed.");
				tx.finish();
			}
		}
		
		// Shutdown the database
		graphDb.shutdown();
	}

	public void buildHighLevelGraph()
	{

	}

	private int latestDiskBlockIndex()
	{
		Collection<File> files = Fetcher.getFolderContents(".");
		ArrayList<Integer> fileNames = new ArrayList<Integer>();
		for (File file : files)
		{
			if (file.getName().endsWith(".json"))
				fileNames.add(Integer.parseInt(file.getName().split(".json")[0]));
		}
		Collections.sort(fileNames);
		return fileNames.get(fileNames.size() - 1);
	}

	/**
	 * Downloads the blocks between the two block indexes (inclusive). Will overwrite any file with the same name.
	 * 
	 * @param latestLocalBlockIndex
	 *            - The last block we have
	 * @param latestBlock
	 *            - The last block from the api
	 * @throws FetcherException
	 */
	private void downloadChainFromInternet(int latestLocalBlockIndex, LatestBlock latestBlock)
	{
		// Fetch the latest block given the last remote block index
		BlockJsonType lastBlock;
		try
		{
			lastBlock = Fetcher.GetBlock(latestBlock.getBlock_index());
		}

		catch (FetcherException e1)
		{
			LOG.log(Level.SEVERE, "Unable to download the latest block from the API.  Aborting...", e1);
			return;
		}

		// Keep downloading until we find the last block
		while (lastBlock.getBlockType().getBlock_index() != latestLocalBlockIndex)
		{
			try
			{
				FileUtils.writeStringToFile(new File(lastBlock.getBlockType().getBlock_index() + ".json"), lastBlock.getBlockJson().render(true));
				lastBlock = Fetcher.GetBlock(lastBlock.getBlockType().getPrev_block());
				LOG.info((Math.abs(latestLocalBlockIndex - lastBlock.getBlockType().getBlock_index())) + " blocks left.");
			}

			catch (IOException e)
			{
				LOG.warning("Error downloading block: " + lastBlock.getBlockType().getPrev_block());
				LOG.warning("5 minute backoff timer begin.");
				try
				{
					Thread.sleep(300000);
				}

				catch (InterruptedException e1)
				{
					LOG.log(Level.SEVERE, "Sleep thread timer interrupted", e1);
				}
			}

			catch (FetcherException e)
			{
				LOG.warning(e.getMessage());
				LOG.warning("5 minute backoff timer begin.");
				try
				{
					Thread.sleep(300000);
				}

				catch (InterruptedException e1)
				{
					LOG.log(Level.SEVERE, "Sleep thread timer interrupted", e1);
				}
			}
		}

		LOG.info("Blockchain download complete.");
	}

	/**
	 * Queries the local database to find the latest stored block index. Used to set a lower bound on what Blocks to fetch from the API.
	 * 
	 * @return The latest block node stored from the datastore.
	 */
	private Node getLatestDatabaseBlockNode()
	{
		// 9505925
		Node referenceNode = graphDb.getReferenceNode();
		TraversalDescription td = new TraversalDescriptionImpl();
		td = td.depthFirst().relationships(BlockchainRelationships.succeeds, Direction.INCOMING);

		Traverser traverser = td.traverse(referenceNode);
		Node node;
		for (Path path : traverser)
		{
			node = path.endNode();
			if (!node.hasRelationship(BlockchainRelationships.succeeds, Direction.INCOMING) && (Boolean) node.getProperty("main_chain"))
				return node;
		}

		return referenceNode;
	}

	/**
	 * Scans the disk to make sure that the blockchain and that there are no missing blocks.
	 * 
	 * @return
	 */
	private boolean isBlockchainComplete()
	{
		boolean isComplete = true;

		LOG.info("Verifying local blockchain integrity.");

		// We are going to populate a hashtable binding addresses (key) to index numbers (value). This will allow in-memory verification while keeping memory consumption at a minimum
		Hashtable<String, Integer> blocks = new Hashtable<String, Integer>();
		File fileBlock;
		BlockType block;

		LOG.info("Collecting blocks...");
		Collection<File> files = Fetcher.getFolderContents(".");
		for (Iterator<File> iter = files.iterator(); iter.hasNext();)
		{
			fileBlock = iter.next();
			if (fileBlock.getName().endsWith("json"))
			{
				try
				{
					block = Fetcher.GetBlock(fileBlock).getBlockType();
					blocks.put(block.getHash(), block.getBlock_index());
				}

				catch (FetcherException e)
				{
					LOG.severe("Unable to parse block.  Reason: " + e.getMessage());
					return false;
				}
			}
		}

		// For every file, we check to see if it's prev_block value exists in
		// the hash table. If it does, we go to the next file
		LOG.info("Verifying integrity...");
		files = Fetcher.getFolderContents(".");
		for (Iterator<File> iter = files.iterator(); iter.hasNext();)
		{
			fileBlock = iter.next();
			if (fileBlock.getName().endsWith("json"))
			{
				try
				{
					block = Fetcher.GetBlock(fileBlock).getBlockType();
					if (block.getPrev_block().length() != 0)
					{
						Integer prevBlock = blocks.get(block.getPrev_block());
						if (prevBlock != null)
						{
							// blocks.remove(block.getPrev_block()); - We don't
							// remove the block because there is no guarentee of
							// order in FileUtils.listFiles in Fetcher
						}

						else
						{
							LOG.severe("Error.  Could not find the prev_block (" + block.getPrev_block() + ") on block index: " + block.getBlock_index() + " Downloading missing block...");

							BlockJsonType blockToWrite = Fetcher.GetBlock(block.getPrev_block());
							FileUtils.writeStringToFile(new File(blockToWrite.getBlockType().getBlock_index() + ".json"), blockToWrite.getBlockJson().render(true));
							isComplete = false;
						}
					}

					else
					{
						// The genesis block does not contain a prev_block.
					}

				}

				catch (FetcherException e)
				{
					LOG.severe(e.getMessage());
					return false;
				}

				catch (IOException e)
				{
					LOG.severe(e.getMessage());
					return false;
				}

			}
		}

		LOG.info("Integrity test complete.");
		return isComplete;
	}

	/**
	 * Fetches the next block that needs to be processed from disk.
	 * 
	 * @param lastDatabaseBlockIndex
	 *            - The last block that was persisted to the database.
	 * @param lastBlockDownloaded
	 *            - The last block we have.
	 * @return - The next block to process.
	 * @throws FetcherException
	 */
	private BlockType getNextBlockFromDisk(int lastDatabaseBlockIndex, final int lastBlockDownloaded) throws FetcherException
	{
		// Load the next block from disk. Because indexes don't go in
		// order, we need to find it from disk.
		for (int i = lastDatabaseBlockIndex; i < lastBlockDownloaded; i++)
		{
			if (FileUtils.getFile((i + 1) + ".json").exists())
			{
				return Fetcher.GetBlock(FileUtils.getFile((i + 1) + ".json")).getBlockType();
			}
		}
		return null;
	}

	/**
	 * Saves a block to the database.
	 * 
	 * @param currentBlock
	 *            - The block to save
	 * @param previousBlock
	 *            - The block preceding the currentBlock
	 * @return - Returns the neo4j block node entity.
	 */
	private Node persistBlockNode(BlockType currentBlock, Node previousBlock)
	{
		// Persist a new block node
		Node currentBlockNode = graphDb.createNode();
		currentBlockNode.setProperty("hash", currentBlock.getHash());
		currentBlockNode.setProperty("ver", currentBlock.getVer());
		currentBlockNode.setProperty("prev_block", currentBlock.getPrev_block());
		currentBlockNode.setProperty("mrkl_root", currentBlock.getMrkl_root());
		currentBlockNode.setProperty("time", currentBlock.getTime());
		currentBlockNode.setProperty("bits", currentBlock.getBits());
		currentBlockNode.setProperty("nonce", currentBlock.getNonce());
		currentBlockNode.setProperty("n_tx", currentBlock.getN_tx());
		currentBlockNode.setProperty("size", currentBlock.getSize());
		currentBlockNode.setProperty("block_index", currentBlock.getBlock_index());
		currentBlockNode.setProperty("main_chain", currentBlock.getMain_chain());
		currentBlockNode.setProperty("height", currentBlock.getHeight());
		currentBlockNode.setProperty("received_time", currentBlock.getReceived_time());
		currentBlockNode.setProperty("relayed_by", currentBlock.getRelayed_by());

		// Create a relationship of this block to the parentBlock

		// In the unlikely event that the previous block of the current
		// block node is not equal to the last block node's hash,
		// then we have to traverse the graph and find the relationship.
		// This occurs when a block is not part of the main chain, and
		// is instead branching off.
		if (previousBlock.hasProperty("hash") && !((String) currentBlockNode.getProperty("prev_block")).contains((String) previousBlock.getProperty("hash")))
		{
			TraversalDescription td = new TraversalDescriptionImpl();
			td = td.breadthFirst().relationships(BlockchainRelationships.succeeds);
			Iterable<Node> nodeTraversal = td.traverse(previousBlock).nodes();
			for (Node blockNode : nodeTraversal)
			{
				// We check to see if its hash is equal to the current block nodes previous_block hash
				if (blockNode.hasProperty("hash") && ((String) blockNode.getProperty("hash")).contains(((String) currentBlockNode.getProperty("prev_block"))))
				{
					// We have found the block. Create a relationship
					currentBlockNode.createRelationshipTo(blockNode, BlockchainRelationships.succeeds);
					break;
				}
			}
		}

		// The previous block of the current block node is equal.
		else
		{
			currentBlockNode.createRelationshipTo(previousBlock, BlockchainRelationships.succeeds);
		}

		// We save each transaction in the block.
		// We get an ascending order of transactions. The API does not print them in ascending order which is necessary as inputs may try to redeem outputs that
		// dont exist yet within the same block!
		for (TransactionType tran : currentBlock.getAscTx())
		{
			persistTransaction(currentBlockNode, tran);
		}

		return currentBlockNode;
	}

	/**
	 * Saves a transaction in a block.
	 * 
	 * @param currentBlockNode
	 *            - The block that holds the transaction
	 * @param tran
	 *            - The transaction to save
	 */
	private void persistTransaction(Node block, TransactionType tran)
	{
		// Persist transaction
		Node tranNode = graphDb.createNode();
		tranNode.setProperty("hash", tran.getHash());
		tranNode.setProperty("ver", tran.getVer());
		tranNode.setProperty("vin_sz", tran.getVin_sz());
		tranNode.setProperty("vout_sz", tran.getVout_sz());
		tranNode.setProperty("size", tran.getSize());
		tranNode.setProperty("relayed_by", tran.getRelayed_by());
		tranNode.setProperty("tx_index", tran.getTx_index());

		// Create the relationship
		tranNode.createRelationshipTo(block, BlockchainRelationships.from);

		// Create the index
		transactionsIndex.add(tranNode, TRANSACTION_INDEX_KEY, tran.getTx_index());

		// Persist outbound transactions
		// The location of an output within a transaction.
		int n = 0;
		for (OutputType output : tran.getOut())
		{
			persistOutput(output, tranNode, n);
			n++;
		}

		// Persist inbound transactions
		for (InputType input : tran.getInputs())
		{
			persistInput(input, tranNode);
		}
	}

	/**
	 * Saves an output to the database.
	 * 
	 * @param output
	 *            - The outbound transaction to save.
	 * @param transactionNode
	 *            - The transaction that owns the output
	 * @param index
	 *            - The index location of where the output transaction is in the transaction
	 */
	private void persistOutput(OutputType output, Node transactionNode, int index)
	{
		// We need to make sure this is a valid outbound transaction
		if (output.getType() != -1 && output.getAddr() != null)
		{
			// This is a valid outbound transaction. Some outbound transactions do not have outbound addresses, which messes up everything
			Node outNode = graphDb.createNode();
			outNode.setProperty("type", output.getType());
			outNode.setProperty("addr", output.getAddr());
			outNode.setProperty("value", output.getValue());
			outNode.setProperty("n", index);

			// Create relationship
			transactionNode.createRelationshipTo(outNode, BlockchainRelationships.sent);
		}
	}

	/**
	 * Saves and redeems an input to the database.
	 * 
	 * @param input
	 *            - The input to save
	 * @param transacstionNode
	 *            - The transaction the input belongs to
	 */
	private void persistInput(InputType input, Node transacstionNode)
	{
		PrevOut prevOut = input.getPrev_out();

		// Sometimes old blockchain data is bunk, so we ignore these anomalies
		if (prevOut == null)
			return;

		// We redeem the output transaction from a previous transaction using an index.
		Node transactionNode = transactionsIndex.query("tx_index", prevOut.getTx_index()).getSingle();

		if (transactionNode != null)
		{
			// We have found the transaction node. Now we find the corresponding money node by looking at "sent" transactions
			Iterable<Relationship> moneyNodeRelationships = transactionNode.getRelationships(BlockchainRelationships.sent, Direction.OUTGOING);
			for (Iterator<Relationship> moneyIter = moneyNodeRelationships.iterator(); moneyIter.hasNext();)
			{
				// For each sent transaction, we get the nodes attached to it. There will only ever be 2 to iterate over, and in very rare cases, 3 or 4 more nodes for "weird"
				// transactions.
				Node[] moneyNodes = moneyIter.next().getNodes();
				for (int i = 0; i < moneyNodes.length; i++)
				{
					// Is this the money node we're looking for!?
					if (moneyNodes[i].hasProperty("addr") && moneyNodes[i].hasProperty("n") && ((String) moneyNodes[i].getProperty("addr")).contains(prevOut.getAddr())
							&& ((Integer) moneyNodes[i].getProperty("n") == prevOut.getN()))
					{
						// We have found the money node that redeemed this one. Create the relationship.
						moneyNodes[i].createRelationshipTo(transacstionNode, BlockchainRelationships.received);
						break;
					}
				}
			}
		}
	}
}