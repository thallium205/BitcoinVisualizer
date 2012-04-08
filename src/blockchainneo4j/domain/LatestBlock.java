package blockchainneo4j.domain;

import java.util.ArrayList;

public class LatestBlock
{
	String hash;
	Long time;
	Integer block_index;
	Integer height;
	ArrayList<Integer> txIndexes;
	
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
