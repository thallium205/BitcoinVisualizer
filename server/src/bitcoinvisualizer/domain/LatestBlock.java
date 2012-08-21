package bitcoinvisualizer.domain;

import java.util.ArrayList;

/**
 * A minimum information block that is returned from calling http://blockchain.info/latestblock
 * @author John
 *
 */
public class LatestBlock
{
	private String hash;
	private Long time;
	private Integer block_index;
	private Integer height;
	private ArrayList<Integer> txIndexes;
	
	public String getHash()
	{
		return hash;
	}
	public void setHash(String hash)
	{
		this.hash = hash;
	}
	public Long getTime()
	{
		return time;
	}
	public void setTime(Long time)
	{
		this.time = time;
	}
	public Integer getBlock_index()
	{
		return block_index;
	}
	public void setBlock_index(Integer block_index)
	{
		this.block_index = block_index;
	}
	public Integer getHeight()
	{
		return height;
	}
	public void setHeight(Integer height)
	{
		this.height = height;
	}
	public ArrayList<Integer> getTxIndexes()
	{
		return txIndexes;
	}
	public void setTxIndexes(ArrayList<Integer> txIndexes)
	{
		this.txIndexes = txIndexes;
	}	
}
