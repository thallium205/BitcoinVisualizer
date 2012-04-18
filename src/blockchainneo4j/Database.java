package blockchainneo4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.neo4j.rest.graphdb.RestAPI;
import org.neo4j.rest.graphdb.entity.RestNode;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
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
 * Stores the basic underlying structure of the bitcoin blockchcain to neo4j. The relationships are: Blocks "succeed" one another. Transaction are "from" blocks. Transactions "send" money.
 * Transactions "receive" money.
 * 
 * @author John
 * 
 */
public class Database
{
	private static final Logger LOG = Logger.getLogger(Database.class.getName());

	RestAPI restApi;

	/**
	 * Represents the basic relationships of the model.
	 * 
	 * @author John
	 * 
	 */
	enum BitcoinRelationships implements RelationshipType
	{
		succeeds, from, received, sent
	}

	public Database(String uri)
	{
		restApi = new RestAPI(uri);
	}

	public Database(String uri, String user, String pass)
	{
		restApi = new RestAPI(uri, user, pass);
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
	public void downloadBlockChain(boolean doValidate)
	{
		// The latest block index we have on index before downloadChain() is
		// called.
		final int LATEST_DISK_BLOCK_INDEX;
		// The latest block index that exists in the bitcoin blockchain
		final int LATEST_INTERNET_BLOCK_INDEX;
		// The most recent database block index that has been persisted to neo4j
		int lastDatabaseBlockIndex;

		// We find the latest block from the internet
		LatestBlock latestInternetBlock = Fetcher.GetLatest();
		LATEST_INTERNET_BLOCK_INDEX = latestInternetBlock.getBlock_index();

		// We find the latest block on disk
		Collection<File> files = Fetcher.getFolderContents(".");
		ArrayList<Integer> fileNames = new ArrayList<Integer>();
		for (File file : files)
		{
			if (file.getName().endsWith(".json"))
				fileNames.add(Integer.parseInt(file.getName().split(".json")[0]));
		}
		Collections.sort(fileNames);
		LATEST_DISK_BLOCK_INDEX = fileNames.get(fileNames.size() - 1);

		// We fetch the difference
		downloadChain(LATEST_DISK_BLOCK_INDEX, latestInternetBlock);

		// We now find the latest block persisted to the database
		Node latestDatabaseBlock = getLatestLocalBlockNode();
		if (latestDatabaseBlock.hasProperty("block_index"))
			lastDatabaseBlockIndex = (Integer) latestDatabaseBlock.getProperty("block_index");
		else
			lastDatabaseBlockIndex = 0;

		// We persist the difference

		if (doValidate)
		{
			if (isBlockchainComplete())
			{
				// Yay.
			} else
			{
				LOG.severe("Integrity test failed. Aborting... Try again.");
				return;
			}
		}

		else
		{
			LOG.info("Begin persistance...");
			// Begin persistence
			BlockType currentBlock = null;
			RestNode currentBlockNode = null;
			while (lastDatabaseBlockIndex < LATEST_INTERNET_BLOCK_INDEX)
			{
				// Load the next block from disk. Because indexes don't go in
				// order, we need to find it from disk.
				try
				{
					for (int i = lastDatabaseBlockIndex; i < LATEST_INTERNET_BLOCK_INDEX; i++)
					{
						if (FileUtils.getFile((i + 1) + ".json").exists())
						{
							currentBlock = Fetcher.GetBlock(FileUtils.getFile((i + 1) + ".json")).getBlockType();
							break;
						}
					}
				}

				catch (FetcherException e)
				{
					LOG.log(Level.SEVERE, "Corrupted block on disk.  Reason:  " + e.getMessage() + " Aborting...", e);
					return;
				}

				LOG.info("Persisting Block: " + currentBlock.getBlock_index());

				// Persist a new block node
				Map<String, Object> blockProps = new HashMap<String, Object>();
				blockProps.put("hash", currentBlock.getHash());
				blockProps.put("ver", currentBlock.getVer());
				blockProps.put("prev_block", currentBlock.getPrev_block());
				blockProps.put("mrkl_root", currentBlock.getMrkl_root());
				blockProps.put("time", currentBlock.getTime());
				blockProps.put("bits", currentBlock.getBits());
				blockProps.put("nonce", currentBlock.getNonce());
				blockProps.put("n_tx", currentBlock.getN_tx());
				blockProps.put("size", currentBlock.getSize());
				blockProps.put("block_index", currentBlock.getBlock_index());
				blockProps.put("main_chain", currentBlock.getMain_chain());
				blockProps.put("height", currentBlock.getHeight());
				blockProps.put("received_time", currentBlock.getReceived_time());
				blockProps.put("relayed_by", currentBlock.getRelayed_by());
				currentBlockNode = restApi.createNode(blockProps);

				// Create a relationship of this block to the parentBlock

				// In the unlikely event that the previous block of the current
				// block node is not equal to the last block node's hash,
				// then we have to traverse the graph and find the relationship.
				// This occurs when a block is not part of the main chain, and
				// is instead branching off.
				if (latestDatabaseBlock.hasProperty("hash") && !((String) currentBlockNode.getProperty("prev_block")).contains((String) latestDatabaseBlock.getProperty("hash")))
				{
					TraversalDescription td = new TraversalDescriptionImpl();
					td = td.depthFirst().relationships(BitcoinRelationships.succeeds);
					Iterable<Node> nodeTraversal = td.traverse(latestDatabaseBlock).nodes();

					for (Iterator<Node> iter = nodeTraversal.iterator(); iter.hasNext();)
					{
						Node blockNode = iter.next();
						// We check to see if its hash is equal to the current
						// block nodes previous_block hash
						if (blockNode.hasProperty("hash") && ((String) blockNode.getProperty("hash")).contains(((String) currentBlockNode.getProperty("prev_block"))))
						{
							// We have found the block. Create a relationship
							restApi.createRelationship(currentBlockNode, blockNode, BitcoinRelationships.succeeds, null);
						}
					}
				}

				else
				{
					restApi.createRelationship(currentBlockNode, latestDatabaseBlock, BitcoinRelationships.succeeds, null);
				}

				// Persist transaction nodes
				// The transaction node properties
				Map<String, Object> tranProps;
				// The transaction object
				TransactionType tran;
				// The transaction node
				RestNode tranNode = null;
				// The relationship properties between transaction and block
				Map<String, Object> fromRelation;
				for (Iterator<TransactionType> tranIter = currentBlock.getTx().iterator(); tranIter.hasNext();)
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
					fromRelation.put("block_hash", currentBlock.getHash());
					restApi.createRelationship(tranNode, currentBlockNode, BitcoinRelationships.from, fromRelation);

					// Persist Money nodes
					// The money node properties
					Map<String, Object> moneyProps;
					// The money node
					RestNode outNode = null;
					// The output object
					OutputType output;
					// The relationship properties between transaction and output
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
						outNode = restApi.createNode(moneyProps);

						sentRelation = new HashMap<String, Object>();
						sentRelation.put("to_addr", output.getAddr());
						sentRelation.put("n", n);
						restApi.createRelationship(tranNode, outNode, BitcoinRelationships.sent, sentRelation);
						n++;
					}

					// The relationship properties between input and transaction
					Map<String, Object> receivedRelation;
					// The input object
					PrevOut prevOut;
					for (Iterator<InputType> inputIter = tran.getInputs().iterator(); inputIter.hasNext();)
					{
						moneyProps = new HashMap<String, Object>();
						prevOut = inputIter.next().getPrev_out();
						if (prevOut == null)
							continue;
						moneyProps.put("type", prevOut.getType());
						moneyProps.put("addr", prevOut.getAddr());
						moneyProps.put("value", prevOut.getValue());
						moneyProps.put("n", prevOut.getN());

						// We need to reedeem an output transaction. Because the chain is being built sequentially from early to later, this is possible.
						TraversalDescription td = new TraversalDescriptionImpl();
						td = td.breadthFirst().relationships(BitcoinRelationships.succeeds).relationships(BitcoinRelationships.from);
						Iterable<Node> nodeTraversal = td.traverse(tranNode).nodes();

						boolean isFound = false;
						for (Iterator<Node> iter = nodeTraversal.iterator(); iter.hasNext();)
						{
							Node transactionNode = iter.next();
							// We grab the transaction the money node we are looking for belongs to
							if (transactionNode.hasProperty("tx_index"))
							{
								int transactionIndex = (Integer) transactionNode.getProperty("tx_index");
								if (transactionIndex == prevOut.getTx_index())
								{
									// We have found the transaction node. Now we find the corresponding money node by looking at "sent" transactions
									Iterable<Relationship> moneyNodeRelationships = transactionNode.getRelationships(BitcoinRelationships.sent, Direction.OUTGOING);
									for (Iterator<Relationship> moneyIter = moneyNodeRelationships.iterator(); moneyIter.hasNext();)
									{
										// For each sent transaction, we get the nodes attached to it
										Node[] moneyNodes = moneyIter.next().getNodes();
										for (int i = 0; i < moneyNodes.length; i++)
										{
											// Is this the money node we're looking for!?
											if (moneyNodes[i].hasProperty("addr") && moneyNodes[i].hasProperty("n") && ((String) moneyNodes[i].getProperty("addr")).contains(prevOut.getAddr())
													&& ((Integer) moneyNodes[i].getProperty("n") == prevOut.getN()))
											{

												// We have found the money node that reedemed this one. Create the relationship.
												receivedRelation = new HashMap<String, Object>();
												receivedRelation.put("tx_index", prevOut.getTx_index());
												restApi.createRelationship(moneyNodes[i], tranNode, BitcoinRelationships.received, receivedRelation);
												isFound = true;
												break;
											}

										}

										if (isFound)
											break;
										else
										{
											LOG.severe("Unable to redeem transaction: " + transactionNode.getProperty("tx_index"));
											// Should abort application in later release!											
										}
										
										
									}
								}
							}
							if (isFound)
								break;
						}
					}
				}

				// Update the previous block node to the current one and repeat
				lastDatabaseBlockIndex = currentBlock.getBlock_index();
				latestDatabaseBlock = currentBlockNode;
			}
		}
	}

	/**
	 * Queries the local database to find the latest stored block index. Used to set a lower bound on what Blocks to fetch from the API.
	 * 
	 * @return The next block index the local datastore needs to store.
	 */
	private Node getLatestLocalBlockNode()
	{
		Node referenceNode = restApi.getReferenceNode();
		TraversalDescription td = new TraversalDescriptionImpl();
		td = td.depthFirst().relationships(BitcoinRelationships.succeeds, Direction.INCOMING);

		Traverser traverser = td.traverse(referenceNode);
		for (Iterator<Path> iter = traverser.iterator(); iter.hasNext();)
		{
			Node node = iter.next().endNode();
			if (!node.hasRelationship(BitcoinRelationships.succeeds, Direction.INCOMING))
				return node;
		}
		return referenceNode;
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
	private void downloadChain(int latestLocalBlockIndex, LatestBlock latestBlock)
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
				LOG.info((latestLocalBlockIndex - lastBlock.getBlockType().getBlock_index()) + " blocks left.");
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
					// TODO Auto-generated catch block
					e1.printStackTrace();
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
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}

		LOG.info("Blockchain download complete.");
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

		// We are going to populate a hashtable binding addresses (key) to index
		// numbers (value).
		// This will allow in-memory verification while keeping memory
		// consumption at a minimum
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
}