package bitcoinvisualizer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.HighlyAvailableGraphDatabase;
import org.neo4j.kernel.Traversal;
import org.neo4j.kernel.impl.traversal.TraversalDescriptionImpl;
import org.neo4j.server.WrappingNeoServerBootstrapper;

import bitcoinvisualizer.domain.BlockJsonType;
import bitcoinvisualizer.domain.BlockType;
import bitcoinvisualizer.domain.InputType;
import bitcoinvisualizer.domain.LatestBlock;
import bitcoinvisualizer.domain.OutputType;
import bitcoinvisualizer.domain.TransactionType;
import bitcoinvisualizer.scraper.Scraper;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;


/**
 * Provides functionality to fetch and create the low-level block chain and the high level abstractions.
 * 
 * @author John
 * 
 */
public class GraphBuilder
{
	private static final Logger LOG = Logger.getLogger(GraphBuilder.class.getName());

	private static GraphDatabaseAPI graphDb;
	private static WrappingNeoServerBootstrapper srv;
	private static Thread shutdownThread;
	private static boolean isStarted = false;

	// Index block hash
	public static final String BLOCK_HASH = "block_hashes";
	public static final String BLOCK_HASH_KEY = "block_hash";
	private static Index<Node> block_hashes;

	// Index block height
	public static final String BLOCK_HEIGHT = "block_heights";
	public static final String BLOCK_HEIGHT_KEY = "block_height";
	private static Index<Node> block_heights;

	// Index Transaction hashes
	public static final String TRANSACTION_HASH = "tx_hashes";
	public static final String TRANSACTION_HASH_KEY = "tx_hash";
	private static Index<Node> tx_hashes;

	// Index Transaction index
	public static final String TRANSACTION_INDEX = "tx_indexes";
	public static final String TRANSACTION_INDEX_KEY = "tx_index";
	private static Index<Node> tx_indexes;

	// Address hash
	public static final String ADDRESS_HASH = "addr_hashes";
	public static final String ADDRESS_HASH_KEY = "addr_hash";
	private static Index<Node> addresses;

	// Owner address hash
	public static final String OWNED_ADDRESS_HASH = "owned_addr_hashes";
	public static final String OWNED_ADDRESS_HASH_KEY = "owned_addr_hash";
	private static Index<Node> owned_addresses;

	// Index IPV4 address
	public static final String IPV4 = "ipv4_addrs";
	public static final String IPV4_KEY = "ipv4_addr";
	static Index<Node> ipv4_addrs;
	
	// Reference node indexes
	private static final String LAST_BLOCK_HASH = "last_block_hash";
	private static final String LAST_LINKED_TRANSACTION_BLOCK_NODEID = "last_linked_transaction_block_nodeId";
	private static final String LAST_LINKED_OWNER_BUILD_BLOCK_NODEID = "last_linked_owner_build_block_nodeId";
	private static final String LAST_LINKED_OWNER_LINK_BLOCK_NODEID = "last_linked_owner_link_block_nodeId";
	private static final String LAST_TIME_SENT = "last_time_sent";
	/**
	 * Represents the basic relationships of the low-level model.
	 * 
	 * @author John
	 * 
	 */
	public static enum BlockchainRelationships implements RelationshipType
	{
		succeeds, from, received, sent
	}

	/**
	 * Represents high-level relationships of addresses.
	 * 
	 * @author jdmarble
	 * 
	 */
	public static enum AddressRelTypes implements RelationshipType
	{
		redeemed, same_owner;
	}

	/**
	 * Represents high-level relationships of owners.
	 * 
	 * @author jdmarble
	 * 
	 */
	public static enum OwnerRelTypes implements RelationshipType
	{
		owns, transfers;
	}

	/**
	 * Starts the embedded neo4j instance
	 * 
	 * @param dbPath
	 *            - The path where the data will be saved
	 * @throws IOException 
	 */
	public static void StartDatabase(final String dbPath, final String configPath) throws IOException
	{
		LOG.info("Starting database...");		
		// graphDb = new HighlyAvailableGraphDatabase(dbPath, MapUtil.load(FileUtils.getFile(configPath)));
		graphDb = new EmbeddedGraphDatabase (dbPath);
		srv = new WrappingNeoServerBootstrapper(graphDb);
		srv.start();

		// Load indexes
		block_hashes = graphDb.index().forNodes(BLOCK_HASH);
		block_heights = graphDb.index().forNodes(BLOCK_HEIGHT);
		tx_hashes = graphDb.index().forNodes(TRANSACTION_HASH);
		tx_indexes = graphDb.index().forNodes(TRANSACTION_INDEX);
		addresses = graphDb.index().forNodes(ADDRESS_HASH);
		owned_addresses = graphDb.index().forNodes(OWNED_ADDRESS_HASH);
		ipv4_addrs = graphDb.index().forNodes(IPV4);

		// Register shutdown hook
		shutdownThread = new Thread()
		{
			@Override
			public void run()
			{
				LOG.info("Stopping database...");
				srv.stop();
				graphDb.shutdown();
				isStarted = false;
				LOG.info("Database stopped.");
			}
		};
		
		isStarted = true;
		LOG.info("Database started.");
	}

	/**
	 * Stops the embedded neo4j instance
	 */
	public static void StopDatabase()
	{
		LOG.info("Stopping database...");
		srv.stop();
		graphDb.shutdown();
		Runtime.getRuntime().removeShutdownHook(shutdownThread);
		isStarted = false;
		LOG.info("Database stopped.");
	}
	
	/**
	 * Returns if the database is already started or not
	 * @return
	 */
	public static boolean IsStarted()
	{
		return isStarted;
	}

	/**
	 * Stores blocks, transactions, and input/output nodes to the database. Blocks will be given a relationship of "succeeds" to the previous categorized block to the database. Transactions will be
	 * given a relationship of "from" to its owner Block. Input/Output nodes (called Money nodes) will either be given "sent" or "received" relationships based upon what they represent. This function
	 * maintains a local json repository by downloading it directly from the API. Then, it persists the json blocks to the datastore from these local json files.
	 * 
	 * @author John
	 * @throws Exception 
	 * 
	 */
	public static void DownloadAndSaveBlockChain(boolean doValidate) throws Exception
	{
		LOG.info("Begin building block chain...");
		// We find the latest block from the internet
		LatestBlock latestInternetBlock;
		
		try
		{
			latestInternetBlock = Fetcher.GetLatest();
		} 
		
		catch (Exception e)
		{
			throw e;
		}
		LOG.info("Latest block from the internet is: " + latestInternetBlock.getHeight());

		// The latest block index that exists in the bitcoin blockchain
		final int latestRemoteBlockIndex = latestInternetBlock.getBlock_index();

		// The latest block index we have on disk before downloadChain() is called.
		final int latestLocalBlockIndex = latestDiskBlockIndex();

		// The most recent database block index that has been persisted to neo4j
		int lastDatabaseBlockIndex;

		// We fetch the difference
		downloadChainFromInternet(latestLocalBlockIndex, latestInternetBlock);

		// We now find the latest block persisted to the database
		LOG.info("Finding the last block saved in the datastore...");
		Node latestDatabaseBlock = getLatestDatabaseBlockNodeByHash(LAST_BLOCK_HASH);
		if (latestDatabaseBlock.hasProperty("block_index"))
			lastDatabaseBlockIndex = (Integer) latestDatabaseBlock.getProperty("block_index");
		else
			lastDatabaseBlockIndex = 0;
		LOG.info("Latest database block index is: " + lastDatabaseBlockIndex);

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
		while (lastDatabaseBlockIndex < latestRemoteBlockIndex)
		{
			// Begin transaction
			tx = graphDb.beginTx();
			try
			{
				currentBlock = getNextBlockFromDisk(lastDatabaseBlockIndex, latestRemoteBlockIndex);
				LOG.info("Persisting Block: " + currentBlock.getHeight());

				// We save the block node and set it to the latestDatabaseBlock
				latestDatabaseBlock = persistBlockNode(currentBlock, latestDatabaseBlock);
				
				// Update the metadata node of the last database block
				graphDb.getReferenceNode().setProperty(LAST_BLOCK_HASH, currentBlock.getHash());
				
				// Update the previous block node to the current one and repeat
				lastDatabaseBlockIndex = currentBlock.getBlock_index();
				
				tx.success();
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
				tx.finish();
			}
		}

		LOG.info("Building block chain completed.");
	}

	/**
	 * Builds the high level abstraction of the block chain.
	 * 
	 * @author jdmarble
	 * @throws FetcherException 
	 * @throws IOException 
	 */
	public static void BuildHighLevelGraph() throws FetcherException, IOException
	{	
		LOG.info("Begin building high level graph...");
		
		// Add addresses to redeemed transfers and link those addresses together when the owner is the same.		
		LOG.info("Checking to make sure the latest block used to link addresses is still a part of the main chain...");		
		
		// Check to make sure all 3 components of the high-level builder have resolved on the same block.  If they haven't, then we skip this step	
		// This step is required because the last block we originally thought was part of the main chain can become an orphan block later.
		if (graphDb.getReferenceNode().hasProperty(LAST_LINKED_TRANSACTION_BLOCK_NODEID) && graphDb.getReferenceNode().hasProperty(LAST_LINKED_OWNER_BUILD_BLOCK_NODEID) && graphDb.getReferenceNode().hasProperty(LAST_LINKED_OWNER_LINK_BLOCK_NODEID))
		{			
			final Object lastLinkedBlockNodeId = graphDb.getReferenceNode().getProperty(LAST_LINKED_TRANSACTION_BLOCK_NODEID);
			final Object lastLinkedOwnerBlockNodeId = graphDb.getReferenceNode().getProperty(LAST_LINKED_OWNER_BUILD_BLOCK_NODEID);
			final Object lastLinkedOwnerLinkBlockNodeId =  graphDb.getReferenceNode().getProperty(LAST_LINKED_OWNER_LINK_BLOCK_NODEID);
			
			if (lastLinkedBlockNodeId.equals(lastLinkedOwnerBlockNodeId) && lastLinkedOwnerBlockNodeId.equals(lastLinkedOwnerLinkBlockNodeId))
			{
				final Transaction tx = graphDb.beginTx();
				checkAndFixIfLastPersistedHighLevelBlockIsOrpahn();
				tx.success();
				tx.finish();
			}
			
			else
			{
				LOG.warning("All 3 components of the high-level builder do not match.  This could be the result of an unsucessful high-level graph build.  Continuing...");
			}
		}
		
		else
		{
			LOG.info("The reference block does not have high level property information stored.  This is usually the result of a first-time run before any data has been added.");
		}
		
			
		LOG.info("Latest high level orphan block check completed.");

		
		LOG.info("Linking addresses...");
		for (final Node transaction : getLatestTransactions(getLatestDatabaseBlockNodeByNodeId(LAST_LINKED_TRANSACTION_BLOCK_NODEID).getId()))
		{
			final Transaction tx = graphDb.beginTx();
			try
			{
				LOG.info("Processing transaction: " + (String) transaction.getProperty("hash"));
				linkAddresses(transaction);
				// We need to get the block node id from this transaction.  There is only one block to a transaction
				graphDb.getReferenceNode().setProperty(LAST_LINKED_TRANSACTION_BLOCK_NODEID, transaction.traverse(Order.BREADTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, BlockchainRelationships.from, Direction.OUTGOING).iterator().next().getId());
				tx.success();
			}

			catch (Exception e)
			{
				LOG.log(Level.SEVERE, "Link address error.", e);
				tx.failure();
				return;
			}

			finally
			{
				tx.finish();
			}
		}
		LOG.info("Linking addresses completed.");		

		
		
		
		// Go through all addresses and create an owner for each connected component
		// NOTE: ALL addresses must be created and linked BEFORE this pass.
		LOG.info("Begin building owners for all linked addresses...");
		
		for (final Node block : getLatestMainBlocks(getLatestDatabaseBlockNodeByNodeId(LAST_LINKED_OWNER_BUILD_BLOCK_NODEID).getId()))
		{
			final Transaction tx = graphDb.beginTx();
			try
			{
				LOG.info("Processing block: " + (Integer) block.getProperty("height"));
				createOwners(block);
				graphDb.getReferenceNode().setProperty(LAST_LINKED_OWNER_BUILD_BLOCK_NODEID, block.getId());
				tx.success();
			}

			catch (Exception e)
			{
				LOG.log(Level.SEVERE, "Building owners error.", e);
				tx.failure();
				return;
			}

			finally
			{
				tx.finish();
			}			
		}
		LOG.info("Building owners completed.");

		// Link the owners for the highest level of the abstraction
		LOG.info("Begin linking owners...");	
		HashSet<Long> owners = new HashSet<Long>();
		for (final Node block : getLatestMainBlocks(getLatestDatabaseBlockNodeByNodeId(LAST_LINKED_OWNER_LINK_BLOCK_NODEID).getId()))
		{		
			try
			{
				for (final Node owner : getOwners(block))
				{
					LOG.info("Processing owner: " + owner.getId());
					
					// For each run, we update all our owners.
					if (owners.add(owner.getId()))
					{
						relinkOwners(block, owner);
					}											
				}
			}

			catch (Exception e)
			{
				LOG.log(Level.SEVERE, "Linking owners error.", e);				
				return;
			}
		}
		LOG.info("Linking owners completed.");
		LOG.info("Building high level graph completed.");
		
	}
	
	/**
	 * Scrapes the internet, associating bitcoin addresses to entities.  The primary method this function uses is to scrape the signatures of bitcointalk.org users.
	 */
	public static void Scrape()
	{
		LOG.info("Begin scraping...");
		
		try
		{
			LOG.info("Scraping BitcoinTalk.org User Profiles...");
			Scraper.BitcoinTalkProfiles(graphDb, owned_addresses);
			LOG.info("Finished scraping BitcoinTalk.org User Profiles...");
	
			LOG.info("Scraping Bitcoin-OTC.com User Database...");			
			Scraper.BitcoinOtcDatabase(graphDb, owned_addresses);
			LOG.info("Finished scraping Bitcoin-OTC.org User Database...");
		} 
		
		catch (Exception e)
		{
			LOG.log(Level.SEVERE, "Scraping error.  Aborting...", e);
		}
		
		LOG.info("Scraping complete.");		
	}
	
	/**
	 * Checks to see if the last block processed by the high level graph builder is still part of the main chain.  If it isnt, this function will remove all high level data from each block in the orphaned chain.
	 * @throws IOException 
	 * @throws FetcherException 
	 */
	private static void checkAndFixIfLastPersistedHighLevelBlockIsOrpahn() throws IOException, FetcherException
	{		
		final Node lastHighLevelBlockProcessed = graphDb.getNodeById(getLatestDatabaseBlockNodeByNodeId(LAST_LINKED_TRANSACTION_BLOCK_NODEID).getId());
		final BlockJsonType rawBlockFromInternet = Fetcher.GetBlock((String) lastHighLevelBlockProcessed.getProperty("hash"));
		
		if ((Boolean) lastHighLevelBlockProcessed.getProperty("main_chain") != rawBlockFromInternet.getBlockType().getMain_chain())
		{
			LOG.info("Block: " + (Integer) lastHighLevelBlockProcessed.getProperty("height") + " has become an orphan.  Begin deleting high level information from it."); 
			// The block we at one point thought was part of the main chain is actually an orphan block.  We need to 	
			// 1) Delete the high-level graph data we built in our last run for each of these orphan blocks 
			removeHighLevelDataFromBlock(lastHighLevelBlockProcessed);
			// 2) Overwrite our stale data on disk
			LOG.info("Overwriting JSON store with the latest information from the API.");
			FileUtils.writeStringToFile(new File(rawBlockFromInternet.getBlockType().getBlock_index() + ".json"), rawBlockFromInternet.getBlockJson().render(true));
			lastHighLevelBlockProcessed.setProperty("main_chain", false);
			// 3) Go backward down the chain until our data and the API's data agrees.		
			for (Node block : lastHighLevelBlockProcessed.traverse(org.neo4j.graphdb.Traverser.Order.BREADTH_FIRST, StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL_BUT_START_NODE, BlockchainRelationships.succeeds, Direction.OUTGOING))
			{
				final BlockJsonType nextPreviosRawBlockFromInternet = Fetcher.GetBlock((String) block.getProperty("hash"));
				if ((Boolean)block.getProperty("main_chain") == nextPreviosRawBlockFromInternet.getBlockType().getMain_chain())
				{
					LOG.info("Block: " + (Integer) block.getProperty("height") + " is not an orphan.  Local database has reached an agreement with the block chain.");
					// Our data agrees with the API.  This is the last block we have processed that is in the main chain.  Update our values:
					graphDb.getReferenceNode().setProperty(LAST_BLOCK_HASH, block.getProperty("hash"));
					graphDb.getReferenceNode().setProperty(LAST_LINKED_TRANSACTION_BLOCK_NODEID, block.getId());
					graphDb.getReferenceNode().setProperty(LAST_LINKED_OWNER_BUILD_BLOCK_NODEID, block.getId());
					graphDb.getReferenceNode().setProperty(LAST_LINKED_OWNER_LINK_BLOCK_NODEID, block.getId());
					
					if (isBlockchainComplete())
					{
						LOG.info("Orphan block successfully removed.");
					}
					else
					{
						LOG.warning("There may be a problem with the low-level JSON store.  Run validation again.");
					}
					
					break;					
				}
				
				else
				{
					LOG.info("Block: " + (String) block.getProperty("height") + " is also an orphan.  Begin deleting high level information from it also."); 
					removeHighLevelDataFromBlock(lastHighLevelBlockProcessed);
					FileUtils.writeStringToFile(new File(nextPreviosRawBlockFromInternet.getBlockType().getBlock_index() + ".json"), nextPreviosRawBlockFromInternet.getBlockJson().render(true));
					block.setProperty("main_chain", false);
				}
				
			}			
		}		
	}
	
	/**
	 * Removes all high level information from a block.
	 * @param block
	 */	
	private static void removeHighLevelDataFromBlock(Node block)
	{		
		HashSet<Node> redeemedNodes = new HashSet<Node>(); 
		HashSet<Node> ownerNodes = new HashSet<Node>(); 
		 
		for (Node transaction : getTransactions(block))
		{
			for (Node input : getInputs(transaction))
			{
				for (Node redeemer : input.traverse(Order.BREADTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, AddressRelTypes.redeemed, Direction.INCOMING))
				{
					redeemedNodes.add(redeemer);
					
					for(Node owner : redeemer.traverse(Order.BREADTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, OwnerRelTypes.owns, Direction.INCOMING))
					{
						ownerNodes.add(owner);
					}
				}				
			}
		}
		
		// We get the inputs of each transaction in each block and remove the "redeemed" edge from it.		
		for (Node transaction : getTransactions(block))
		{
			for (Node input : getInputs(transaction))
			{
				input.getSingleRelationship(AddressRelTypes.redeemed, Direction.INCOMING).delete();
			}
		}
		
		// Then we check the redeemers.  If they have no other "redeemed" edges to it, then we delete it and all its other edges.
		for (Node redeemer : redeemedNodes)
		{
			if (!redeemer.hasRelationship(AddressRelTypes.redeemed, Direction.BOTH))
			{
				for (Relationship rel : redeemer.getRelationships())
				{
					rel.delete();
				}
				
				LOG.info("Redeemer: " + redeemer.getId() + " deleted.");
				redeemer.delete();
				
			}
		}
		
		// Then we check the owners.  If they have no "owns" edges attached to it (from the previous redeemers deletion), then we can safely delete it and all its relationships since its a bunk owner created solely from a garbage orphan block.
		for (Node owner : ownerNodes)
		{
			if (!owner.hasRelationship(OwnerRelTypes.owns, Direction.BOTH))
			{
				for (Relationship rel : owner.getRelationships())
				{
					rel.delete();
				}
				
				LOG.info("Owner: " + owner.getId() + " deleted.");
				owner.delete();
			}
		}			
	}

	/**
	 * Fetches the latest block index stored on disk.
	 * 
	 * @return - The latest block index on disk.
	 * @author John
	 */
	private static int latestDiskBlockIndex()
	{
		LOG.info("Finding latest block on disk...");
		Collection<File> files = Fetcher.getFolderContents(".");
		ArrayList<Integer> fileNames = new ArrayList<Integer>();
		for (File file : files)
		{
			if (file.getName().endsWith(".json"))
				fileNames.add(Integer.parseInt(file.getName().split(".json")[0]));
		}
		Collections.sort(fileNames);
		LOG.info("Latest block on disk: " + (fileNames.size() - 1) + ".json");
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
	 * @author John
	 */
	private static void downloadChainFromInternet(int latestLocalBlockIndex, LatestBlock latestBlock)
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
	 * Queries the local database to find the latest stored block index.
	 * 
	 * @return The latest block node stored from the datastore.
	 * @author John
	 */
	private static Node getLatestDatabaseBlockNodeByHash(final String property)
	{
		if (graphDb.getReferenceNode().hasProperty(property))
		{
			return block_hashes.query(BLOCK_HASH_KEY, (String) graphDb.getReferenceNode().getProperty(property)).getSingle();
		}

		else
		{
			return graphDb.getReferenceNode();
		}
	}
	
	private static Node getLatestDatabaseBlockNodeByNodeId(String property)
	{
		if (graphDb.getReferenceNode().hasProperty(property))
		{
			if (graphDb.getReferenceNode().getProperty(property) instanceof Long)
			{
				return graphDb.getNodeById((Long) graphDb.getReferenceNode().getProperty(property));
			}
			else
			{
				return graphDb.getNodeById((Integer) graphDb.getReferenceNode().getProperty(property));
			}
		}

		else
		{
			return graphDb.getReferenceNode();
		}
	}


	/**
	 * Scans the disk to make sure that the blockchain and that there are no missing blocks.
	 * 
	 * @return If the blockchain is complete.
	 * @author John
	 */
	private static boolean isBlockchainComplete()
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
	 * @author John
	 */
	private static BlockType getNextBlockFromDisk(int lastDatabaseBlockIndex, final int lastBlockDownloaded) throws FetcherException
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
	 * @author John
	 * @throws Exception 
	 */
	private static Node persistBlockNode(BlockType currentBlock, Node previousBlock) throws Exception
	{
		Node block = getLatestDatabaseBlockNodeByHash(currentBlock.getHash());
		if (block.getId() != 0)
		{
			LOG.severe("This block has already been added to the database.  Skipping...");
			throw new Exception("Block: " + currentBlock.getHash() + " has already been added to the low-level database.  Aborting...");
		}
		
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

		// In the event that the previous block of the current
		// block node is not equal to the last block node's hash,
		// then we have to traverse the graph and find the relationship.
		// This occurs when a block is not part of the main chain, and
		// is instead branching off (orphan block).
		if (previousBlock.hasProperty("hash") && !((String) currentBlockNode.getProperty("prev_block")).contains((String) previousBlock.getProperty("hash")))
		{
			boolean blockFound = false;
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
					blockFound = true;
					break;
				}
			}
			
			if (!blockFound)
			{
				LOG.severe("Unable to find the previous block to block: " + currentBlockNode.getProperty("hash") + ".  Aborting!  Run block validation to ensure chain consistency.");
				throw new Exception("Unable to find the previous block to block: " + currentBlockNode.getProperty("hash") + ".  Aborting!  Run block validation to ensure chain consistency.");
			
			}
		}

		// The previous block of the current block node is equal.
		else
		{
			currentBlockNode.createRelationshipTo(previousBlock, BlockchainRelationships.succeeds);
		}

		// Store indexes
		if (currentBlockNode.hasProperty("hash"))
			block_hashes.add(currentBlockNode, BLOCK_HASH_KEY, currentBlockNode.getProperty("hash"));
		if (currentBlockNode.hasProperty("height"))
			block_heights.add(currentBlockNode, BLOCK_HEIGHT_KEY, currentBlockNode.getProperty("height"));
		if (currentBlockNode.hasProperty("relayed_by"))
			ipv4_addrs.add(currentBlockNode, IPV4_KEY, currentBlockNode.getProperty("relayed_by"));

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
	 * @author John
	 */
	private static void persistTransaction(Node block, TransactionType tran)
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

		// Store indexes
		// Used for redeeming inputs
		if (tranNode.hasProperty("tx_index"))
			tx_indexes.add(tranNode, TRANSACTION_INDEX_KEY, tranNode.getProperty("tx_index"));
		if (tranNode.hasProperty("hash"))
			tx_hashes.add(tranNode, TRANSACTION_HASH_KEY, tranNode.getProperty("hash"));
		if (tranNode.hasProperty("relayed_by"))
			ipv4_addrs.add(tranNode, IPV4_KEY, tranNode.getProperty("relayed_by"));

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
	 * @author John
	 */
	private static void persistOutput(OutputType output, Node transactionNode, int index)
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

			// Create index
			if (outNode.hasProperty("addr"))
				addresses.add(outNode, ADDRESS_HASH_KEY, outNode.getProperty("addr"));
		}
	}

	/**
	 * Saves and redeems an input to the database.
	 * 
	 * @param input
	 *            - The input to save
	 * @param transacstionNode
	 *            - The transaction the input belongs to
	 * @author John
	 */
	private static void persistInput(InputType input, Node transacstionNode)
	{
		PrevOut prevOut = input.getPrev_out();

		// Sometimes old blockchain data is bunk, so we ignore these anomalies
		if (prevOut == null)
			return;
		
		// We redeem the output transaction from a previous transaction using an index.
		// Note: There are very rare transactions that redeem from multiple blocks, such as: bc75d4718ba30e978c1a2d9fc2c3bfaa2e4b891dd186cda196b00363a557770a
		// As a result, we must get an iterable and go through each transaction node
		for (Node transactionNode : tx_indexes.query(TRANSACTION_INDEX_KEY, prevOut.getTx_index()))
		{
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

	/**
	 * A lazy Iterable of all transactions from the given block.
	 * 
	 * @param block
	 *            a node in the block chain to get transactions from.
	 * @return all transactions from the given block.
	 * @author jdmarble
	 */
	private static Iterable<Node> getTransactions(final Node block)
	{
		return block.traverse(org.neo4j.graphdb.Traverser.Order.BREADTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, BlockchainRelationships.from, Direction.INCOMING);
	}

	/**
	 * A lazy Iterable of all transactions from the given blocks.
	 * 
	 * @param blocks
	 *            nodes in the block chain to get transactions from.
	 * @return all transactions from the given blocks.
	 * @author jdmarble
	 */
	private static Iterable<Node> getLatestTransactions(final Iterable<Node> blocks)
	{
		final Iterable<Iterable<Node>> transactionGroups = FluentIterable.from(blocks).transform(new GetTransactions());
		return Iterables.concat(transactionGroups);
	}

	/**
	 * A lazy Iterable of all transactions from the main block chain.
	 * 
	 * @return all transactions from the main blocks.
	 * @author jdmarble
	 */
	private static Iterable<Node> getLatestTransactions(long blockNodeId)
	{
		return getLatestTransactions(getLatestMainBlocks(blockNodeId));
	}

	/**
	 * Wraps {@link Transactions.getTransactions(Node)} in a function so it can be transformed lazily.
	 * 
	 * @author jdmarble
	 */
	private static class GetTransactions implements Function<Node, Iterable<Node>>
	{
		public Iterable<Node> apply(final Node block)
		{
			return getTransactions(block);
		}
	}

	public static enum TransferProperties
	{
		addr, value;
	}

	/**
	 * Creates redeemed addresses to a transfer.
	 * 
	 * @param transaction
	 * @param graphDb
	 * @author jdmarble
	 */
	private static void linkAddresses(final Node transaction)
	{
		// Bake the traverser so it can be iterated over multiple times.
		final Iterable<Node> transfers = ImmutableList.copyOf(getInputs(transaction));
		final ImmutableList.Builder<Node> addressBldr = ImmutableList.builder();

		for (final Node transfer : transfers)
		{
			final Iterator<Relationship> existingRedeemers = transfer.getRelationships(Direction.INCOMING, AddressRelTypes.redeemed).iterator();
			if (!existingRedeemers.hasNext())
			{
				final String address = (String) transfer.getProperty(TransferProperties.addr.toString(), null);
				if (address != null)
				{
					final Node addressNode = getAddress(address);
					addressNode.createRelationshipTo(transfer, AddressRelTypes.redeemed);
					addressBldr.add(addressNode);
				}
			}
		}
		final List<Node> addresses = addressBldr.build();

		if (addresses.size() > 1)
		{
			final Node addr1 = addresses.get(0);
			for (int i = 1; i < addresses.size(); ++i)
			{
				final Node addr2 = addresses.get(i);
				addr1.createRelationshipTo(addr2, AddressRelTypes.same_owner);
			}
		}
	}

	/**
	 * Gets the node that belongs to a given address.
	 * 
	 * @param address
	 * @return
	 * @author jdmarble
	 */
	private static Node getAddress(final String address)
	{
		final Node existing = owned_addresses.get(OWNED_ADDRESS_HASH_KEY, address).getSingle();		
		
		final Node result;
		if (existing != null)
		{
			result = existing;
		}

		else
		{
			final Node newNode = graphDb.createNode();
			newNode.setProperty(TransferProperties.addr.toString(), address);

			// Atomic add to the index.
			final Node indexedNode = owned_addresses.putIfAbsent(newNode, OWNED_ADDRESS_HASH_KEY, address);
			if (indexedNode == null)
			{
				// Nobody else tried to add that address at the same time.
				result = newNode;
			}

			else
			{
				// Somebody else added a node with the same address.
				result = indexedNode;
				// Don't need this one anymore.
				newNode.delete();
			}
		}
		return result;
	}

	/**
	 * Returns all the inputs from a given transaction
	 * 
	 * @param transaction
	 * @return
	 * @author jdmarble
	 */
	private static Iterable<Node> getInputs(final Node transaction)
	{
		return transaction.traverse(org.neo4j.graphdb.Traverser.Order.BREADTH_FIRST, StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, BlockchainRelationships.received,
				Direction.INCOMING);
	}

	/**
	 * Creates an owner if one doesn't exist and links it to the other addresses that have been identified as being owned by the same owner as this address.
	 * CYPHER Equivalent:
	 * 
	 * 	START block = node(blockId) 
		MATCH	block <- [from:from] - transaction <- [received:received] - incomingPayment <- [redeemed:redeemed] - addressRedeemer <- [owns:owns] - owner
		RETURN 	owns, owner
	 */
	private static void createOwners(final Node block)
	{
		// All addresses that redeemed an input at a transaction in this block.
		final TraversalDescription redeemedTraversal = Traversal.description()
				.relationships(BlockchainRelationships.from, Direction.INCOMING)
				.relationships(BlockchainRelationships.received, Direction.INCOMING)
				.relationships(AddressRelTypes.redeemed, Direction.INCOMING)
				.evaluator(Evaluators.returnWhereLastRelationshipTypeIs(AddressRelTypes.redeemed));

		for (final Path toAddress : redeemedTraversal.traverse(block))
		{
			final Node address = toAddress.endNode();
			// Does an owner node already own this address?
			final Iterator<Relationship> owns = address.getRelationships(Direction.INCOMING, OwnerRelTypes.owns).iterator();
			if (!owns.hasNext())
			{
				// This address does not have an owner				
				// We need to check to see if ANY owner has previously redeedemd ANY of the addresses in this sub graph.  Simply checking the last address so far wont work.  If ANY of these addresses has an owner,
				// then we take that owner and add its owns edges to the new addresses				
				
				// Find the owner in the same_owner subgraph if it exists
				final TraversalDescription ownerTraversal = Traversal.description()
						.relationships(AddressRelTypes.same_owner, Direction.BOTH)
						.relationships(OwnerRelTypes.owns, Direction.INCOMING)
						.evaluator(Evaluators.returnWhereLastRelationshipTypeIs(OwnerRelTypes.owns));
				
				Node owner = null;
				for (final Path sameOwnerNode : ownerTraversal.traverse(address))
				{
					// This network of addresses does in fact have a single owner
					owner = sameOwnerNode.endNode();
					break;
				}
				
				if (owner == null)
				{
					// This network has not been redeemed by owners at all
					owner = graphDb.createNode();
					owner.setProperty("id", owner.getId()); // This is needed to index nodes by Gephi as the traversal doesn't pull node id's at the moment 
				}	
				
				final Iterable<Node> addresses = address.traverse(org.neo4j.graphdb.Traverser.Order.BREADTH_FIRST, StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL, AddressRelTypes.same_owner,	Direction.BOTH);
				for (final Node owned : addresses)
				{
					// Do not create duplicate edges of the same type either...
					if (!owned.hasRelationship(OwnerRelTypes.owns, Direction.INCOMING))
					{
						owner.createRelationshipTo(owned, OwnerRelTypes.owns);
					}
				}				
			}			
		}
	}

	/**
	 * A lazy Iterable of all "main" blocks in the database from the given nodeId.
	 * 
	 * These blocks are those in the longest chain.
	 * 
	 * @param graphDb
	 *            the database to get blocks from.
	 * @return all main blocks in the database in breadth first order.
	 * @author jdmarble
	 */
	private static Iterable<Node> getLatestMainBlocks(long nodeId)
	{
		final Iterable<Node> blocks = getLatestBlocks(nodeId);

		return FluentIterable.from(blocks).filter(new Predicate<Node>()
		{
			public boolean apply(final Node block)
			{
				// METHOD A
				// If this is the first run, we ping the API and check to make sure this block is still, in fact, part of the main chain.  
				// If it is, then we return true and build the high level graph
				// If it is not and it has changed, we go backwards, checking each node against the API until we find a block we both agree.  This will be 
				// the responsibility of the calling function, and not this function persay.
				return (Boolean) block.getProperty("main_chain", false);				 
			}
		});
	}

	/**
	 * A lazy Iterable of all the blocks after the one given in nodeId. 
	 * 
	 * @param graphDb
	 *            the database to get blocks from.
	 * @return all blocks in the database in breadth first order from the given nodeId.
	 * @author jdmarble
	 */
	private static Iterable<Node> getLatestBlocks(long nodeId)
	{
		final Node block = graphDb.getNodeById(nodeId);
		return block.traverse(org.neo4j.graphdb.Traverser.Order.BREADTH_FIRST, StopEvaluator.END_OF_GRAPH, ReturnableEvaluator.ALL_BUT_START_NODE, BlockchainRelationships.succeeds, Direction.INCOMING);
	}

	/**
	 * A lazy Iterable of all owners that have redeemed transfers at a block.
	 * 
	 * @param block
	 *            a node in the block chain to get owners from.
	 * @return all owners from the given block.
	 * @author jdmarble
	 */
	private static Iterable<Node> getOwners(final Node block)
	{
		final TraversalDescription td = Traversal.description()
				.relationships(BlockchainRelationships.from, Direction.INCOMING)
				.relationships(BlockchainRelationships.received, Direction.INCOMING)
				.relationships(AddressRelTypes.redeemed, Direction.INCOMING)
				.relationships(OwnerRelTypes.owns, Direction.INCOMING)
				.evaluator(Evaluators.atDepth(4))
				.evaluator(Evaluators.returnWhereLastRelationshipTypeIs(OwnerRelTypes.owns));
		
		return td.traverse(block).nodes();
	}

	/**
	 * 
	 * @param owner
	 * @author jdmarble
	 */
	private static void relinkOwners(final Node block, final Node owner)
	{		
		// Remove all outgoing transfers relationships from this owner
		Transaction deleteTx = graphDb.beginTx();
		int count = 0;
		for (Relationship transfer : owner.getRelationships(OwnerRelTypes.transfers, Direction.OUTGOING))
		{
			if (count > 1000)
			{
				deleteTx.success();
				deleteTx.finish();	
				deleteTx = graphDb.beginTx();
				count = 0;
			}
			
			transfer.delete();
			count ++;
		}
		deleteTx.success();
		deleteTx.finish();	
		
		// All addresses that redeemed an input at a transaction in this block.
		final TraversalDescription td = Traversal.description()
				.relationships(OwnerRelTypes.owns, Direction.OUTGOING)
				.relationships(AddressRelTypes.redeemed, Direction.OUTGOING)
				.relationships(BlockchainRelationships.received, Direction.OUTGOING)
				.relationships(BlockchainRelationships.sent, Direction.OUTGOING)
				.relationships(AddressRelTypes.redeemed, Direction.INCOMING)
				.relationships(OwnerRelTypes.owns, Direction.INCOMING)
				.evaluator(Evaluators.atDepth(6))
				.evaluator(Evaluators.returnWhereLastRelationshipTypeIs(OwnerRelTypes.owns));

		count = 0;
		
		final ArrayList<Long> sentTimes = new ArrayList<Long>();
		Transaction createTx = graphDb.beginTx();
		for (final Path btcTransfer : td.traverse(owner))
		{
			if (count > 1000)
			{
				createTx.success();
				createTx.finish();
				createTx = graphDb.beginTx();
				count = 0;
			}
			
			final List<Node> nodes = ImmutableList.copyOf(btcTransfer.nodes());
			final long value = ((Number) nodes.get(4).getProperty("value", 0)).longValue();
			final long time = (Long) nodes.get(3).getSingleRelationship(BlockchainRelationships.from, Direction.OUTGOING).getEndNode().getProperty("time");
				
			final Relationship transfer = owner.createRelationshipTo(btcTransfer.endNode(), OwnerRelTypes.transfers);
			transfer.setProperty("value", value);
			transfer.setProperty("time", time);
			sentTimes.add(time);
			count ++;
		}
		
		if (!sentTimes.isEmpty())
		{
			owner.setProperty(LAST_TIME_SENT, Collections.max(sentTimes));	
		}
		
		else
		{
			owner.setProperty(LAST_TIME_SENT, (long) 0);
		}
			
		
		graphDb.getReferenceNode().setProperty(LAST_LINKED_OWNER_LINK_BLOCK_NODEID, block.getId());
		createTx.success();
		createTx.finish();
	}
}