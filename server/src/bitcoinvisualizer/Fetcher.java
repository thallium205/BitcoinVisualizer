package bitcoinvisualizer;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.CanReadFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;

import bitcoinvisualizer.domain.BlockJsonType;
import bitcoinvisualizer.domain.BlockType;
import bitcoinvisualizer.domain.LatestBlock;

import com.sdicons.json.mapper.JSONMapper;
import com.sdicons.json.mapper.MapperException;
import com.sdicons.json.model.JSONValue;
import com.sdicons.json.parser.JSONParser;

import antlr.RecognitionException;
import antlr.TokenStreamException;

public class Fetcher
{
	private static final String BLOCKCHAININFOAPI = "http://blockchain.info/rawblock/";
	private static final String SECRET = "?api_code=lpho39f1";
	private static final String BLOCKCHAININFOLATEST = "http://blockchain.info/latestblock";
	private static final Logger LOG = Logger.getLogger(Fetcher.class.getName());	

	/**
	 * Downloads a block by block index.
	 * 
	 * @param value -  Index value of the block.
	 * @return BlockJsonType
	 * @throws FetcherException
	 * @throws IOException
	 * @throws RecognitionException
	 * @throws TokenStreamException
	 * @throws MapperException
	 */
	public static BlockJsonType GetBlock(Integer index) throws FetcherException
	{
		LOG.info("Downloading block: " + index);
		URL url;
		try
		{
			url = new URL(BLOCKCHAININFOAPI + index + SECRET);
			URLConnection connection = url.openConnection();

			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

			JSONValue json = new JSONParser(reader).nextValue();
			return new BlockJsonType((BlockType) JSONMapper.toJava(json, BlockType.class), json);
		}

		catch (MalformedURLException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(index);
		}

		catch (IOException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(index);
		}

		catch (TokenStreamException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(index);
		}

		catch (RecognitionException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(index);
		}

		catch (MapperException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(index);
		}
	}
	
	/**
	 * Downloads a block by block hash.
	 * 
	 * @param blockHash - Hash value of the block.
	 * @return BlockJsonType
	 * @throws FetcherException
	 * @throws IOException
	 * @throws RecognitionException
	 * @throws TokenStreamException
	 * @throws MapperException
	 */
	public static BlockJsonType GetBlock(String blockHash) throws FetcherException
	{

		LOG.info("Downloading block: " + blockHash);
		URL url;
		try
		{
			url = new URL(BLOCKCHAININFOAPI + blockHash + SECRET);
			URLConnection connection = url.openConnection();

			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

			JSONValue json = new JSONParser(reader).nextValue();
			return new BlockJsonType((BlockType) JSONMapper.toJava(json, BlockType.class), json);
		}

		catch (MalformedURLException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(blockHash);
		}

		catch (IOException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(blockHash);
		}

		catch (TokenStreamException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(blockHash);
		}

		catch (RecognitionException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(blockHash);
		}

		catch (MapperException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(blockHash);
		}
	}
	
	/**
	 * Converts a JSON file from disk to a BlockJsonType
	 * @param file - a .json file from disk.
	 * @return 
	 * @throws FetcherException
	 */
	public static BlockJsonType GetBlock(File file) throws FetcherException
	{
		try
		{				
			JSONValue json = new JSONParser(new ByteArrayInputStream(FileUtils.readFileToByteArray(file))).nextValue();
			return new BlockJsonType((BlockType) JSONMapper.toJava(json, BlockType.class), json);		
		} 
		
		catch (IOException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(file);
		} 
		
		catch (TokenStreamException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(file);
		} 
		
		catch (RecognitionException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(file);
		} 
		
		catch (MapperException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed.  Reason: " + e.getMessage(), e);
			throw new FetcherException(file);
		}
	}

	/**
	 * Downloads the latest block from the API.  
	 * @return
	 */
	public static LatestBlock GetLatest()
	{
		LOG.info("Downloading latest block value from the internet...");
		URL url;
		try
		{
			url = new URL(BLOCKCHAININFOLATEST);
			URLConnection connection = url.openConnection();

			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));

			JSONValue json = new JSONParser(reader).nextValue();
			return (LatestBlock) JSONMapper.toJava(json, LatestBlock.class);
		}

		catch (MalformedURLException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed to retrieve latest block value. Aborting.  Reason: " + e.getMessage(), e);
		}

		catch (IOException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed to retrieve latest block value. Aborting.  Reason: " + e.getMessage(), e);
		}

		catch (TokenStreamException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed to retrieve latest block value. Aborting.  Reason: " + e.getMessage(), e);
		}

		catch (RecognitionException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed to retrieve latest block value. Aborting.  Reason: " + e.getMessage(), e);
		}

		catch (MapperException e)
		{
			LOG.log(Level.SEVERE, "Fetcher failed to retrieve latest block value. Aborting.  Reason: " + e.getMessage(), e);
		}
		return null;
	}

	/**
	 * Returns all the files in a directory.
	 * 
	 * @param dir
	 *            - Path to the directory that contains the text documents to be
	 *            parsed.
	 * @return A collection of File Objects
	 */
	public static Collection<File> getFolderContents(String dir)
	{
		// Collect all readable documents
		File file = new File(dir);
		Collection<File> files = FileUtils.listFiles(file, CanReadFileFilter.CAN_READ, DirectoryFileFilter.DIRECTORY);
		return files;
	}
}
