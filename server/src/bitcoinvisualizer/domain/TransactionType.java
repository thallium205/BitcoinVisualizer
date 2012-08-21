package bitcoinvisualizer.domain;

import java.util.ArrayList;

/**
 * Summary and aggregate information regarding containing input and output transactions.
 * @author John
 *
 */
public class TransactionType
{
	private String hash;
	private Integer ver;
	private Integer vin_sz;
	private Integer vout_sz;
	private Integer size;
	private String relayed_by;
	private Integer tx_index;
	private Long time;
	
	private ArrayList<InputType> inputs;
	private ArrayList<OutputType> out;	
	
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
	public Integer getVin_sz()
	{
		return vin_sz;
	}
	public void setVin_sz(Integer vin_sz)
	{
		this.vin_sz = vin_sz;
	}
	public Integer getVout_sz()
	{
		return vout_sz;
	}
	public void setVout_sz(Integer vout_sz)
	{
		this.vout_sz = vout_sz;
	}
	public Integer getSize()
	{
		return size;
	}
	public void setSize(Integer size)
	{
		this.size = size;
	}
	public String getRelayed_by()
	{
		return relayed_by;
	}
	public void setRelayed_by(String relayed_by)
	{
		this.relayed_by = relayed_by;
	}
	public Integer getTx_index()
	{
		return tx_index;
	}
	public void setTx_index(Integer tx_index)
	{
		this.tx_index = tx_index;
	}
	public ArrayList<InputType> getInputs()
	{
		return inputs;
	}
	public void setInputs(ArrayList<InputType> inputs)
	{
		this.inputs = inputs;
	}
	public ArrayList<OutputType> getOut()
	{
		return out;
	}
	public void setOut(ArrayList<OutputType> out)
	{
		this.out = out;
	}
	public Long getTime()
	{
		return time;
	}
	public void setTime(Long time)
	{
		this.time = time;
	}
}
