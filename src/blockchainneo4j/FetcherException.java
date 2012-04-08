package blockchainneo4j;

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
	
	public Integer getFailedBlockIndex()
	{
		return failedBlockIndex;
	}
}
