package blockchainneo4j.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * The fundamental datastructure of bitcoin.
 * @author John
 *
 */
public class BlockType
{
	private String hash;
	private Integer ver;
	private String prev_block;
	private String mrkl_root;
	private Long time;
	private Long bits;
	private Long nonce;
	private Integer n_tx;
	private Integer size;
	private Integer block_index;
	private Boolean main_chain;
	private Integer height;
	private Long received_time;
	private String relayed_by;
	
	ArrayList<TransactionType> tx = new ArrayList<TransactionType>();	
	
	public String getHash()
	{
		return hash;
	}
	public void setHash(String hash)
	{
		this.hash = hash;
	}
	public Integer getVer()
	{
		return ver;
	}
	public void setVer(Integer ver)
	{
		this.ver = ver;
	}
	public String getPrev_block()
	{
		return prev_block;
	}
	public void setPrev_block(String prev_block)
	{
		this.prev_block = prev_block;
	}
	public String getMrkl_root()
	{
		return mrkl_root;
	}
	public void setMrkl_root(String mrkl_root)
	{
		this.mrkl_root = mrkl_root;
	}
	public Long getTime()
	{
		return time;
	}
	public void setTime(Long time)
	{
		this.time = time;
	}
	public Long getBits()
	{
		return bits;
	}
	public void setBits(Long bits)
	{
		this.bits = bits;
	}
	public Long getNonce()
	{
		return nonce;
	}
	public void setNonce(Long nonce)
	{
		this.nonce = nonce;
	}
	public Integer getN_tx()
	{
		return n_tx;
	}
	public void setN_tx(Integer n_tx)
	{
		this.n_tx = n_tx;
	}
	public Integer getSize()
	{
		return size;
	}
	public void setSize(Integer size)
	{
		this.size = size;
	}
	public Integer getBlock_index()
	{
		return block_index;
	}
	public void setBlock_index(Integer block_index)
	{
		this.block_index = block_index;
	}
	public Boolean getMain_chain()
	{
		return main_chain;
	}
	public void setMain_chain(Boolean main_chain)
	{
		this.main_chain = main_chain;
	}
	public Integer getHeight()
	{
		return height;
	}
	public void setHeight(Integer height)
	{
		this.height = height;
	}
	public Long getReceived_time()
	{
		return received_time;
	}
	public void setReceived_time(Long received_time)
	{
		this.received_time = received_time;
	}
	public String getRelayed_by()
	{
		return relayed_by;
	}
	public void setRelayed_by(String relayed_by)
	{
		this.relayed_by = relayed_by;
	}	
	public ArrayList<TransactionType> getTx()
	{		
		return tx;
	}	
	public void setTx(ArrayList<TransactionType> tx)
	{
		this.tx = tx;
	}
	public ArrayList<TransactionType> getAscTx()
	{
		Collections.sort(tx, new Comparator<TransactionType>() {
			@Override
			public int compare(TransactionType t1, TransactionType t2)
			{
				return t1.getTx_index() - t2.getTx_index();
			}	
		});
		return tx;
	}
}
