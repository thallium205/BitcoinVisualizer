package blockchainneo4j;

import java.io.File;

public class FetcherException extends Exception
{
	private static final long serialVersionUID = -579591032879848637L;
	
	private Integer failedBlockIndex;
	
	FetcherException()
	{
		super("Latest block fetch failed.");	
	}
	
	FetcherException(Integer blockIndex)
	{
		super("Block Fetch failed on Block: " + blockIndex);
		this.failedBlockIndex = blockIndex;
	}
	
	FetcherException(String blockHash)
	{
		super("Block Fetch failed on Block: " + blockHash);		
	}
	
	FetcherException (File file)
	{
		super("Block Fetch failed on File: " + file.getName());		
	}
	
	public Integer getFailedBlockIndex()
	{
		return failedBlockIndex;
	}
}
