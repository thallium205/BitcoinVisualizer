package blockchainneo4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sdicons.json.mapper.JSONMapper;
import com.sdicons.json.mapper.MapperException;
import com.sdicons.json.model.JSONValue;
import com.sdicons.json.parser.JSONParser;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import blockchainneo4j.domain.BlockType;
import blockchainneo4j.domain.LatestBlock;

public class Fetcher
{
	private static final String BLOCKCHAININFOAPI = "http://blockchain.info/rawblock/";	
	private static final String BLOCKCHAININFOLATEST = "http://blockchain.info/latestblock";
	private static final Logger LOG = Logger.getLogger(Fetcher.class.getName());
	
	/**
	 * Downloads a block by block index.
	 * @param value - Index value of the block.
	 * @return BlockType
	 * @throws FetcherException 
	 * @throws IOException
	 * @throws RecognitionException 
	 * @throws TokenStreamException 
	 * @throws MapperException 
	 */
	public static BlockType GetBlock(Integer index) throws FetcherException
	{
		LOG.info("Downloading block: " + index);
		URL url;
		try
		{
			url = new URL(BLOCKCHAININFOAPI + index);
			URLConnection connection = url.openConnection();

			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			
			JSONValue json = new JSONParser(reader).nextValue();
			return (BlockType)JSONMapper.toJava(json, BlockType.class);	
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
	
	public static LatestBlock GetLatest()
	{
		LOG.info("Downloading latest block value.");
		URL url;
		try
		{
			url = new URL(BLOCKCHAININFOLATEST);
			URLConnection connection = url.openConnection();

			BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			
			JSONValue json = new JSONParser(reader).nextValue();
			return (LatestBlock)JSONMapper.toJava(json, LatestBlock.class);	
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
}
