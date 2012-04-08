package blockchainneo4j.domain;

public class PrevOut
{
	private Integer type;
	private String addr;
	private Long value;
	private Integer tx_index;
	private Integer n;
	
	
	public Integer getType()
	{
		return type;
	}
	public void setType(Integer type)
	{
		this.type = type;
	}
	public String getAddr()
	{
		return addr;
	}
	public void setAddr(String addr)
	{
		this.addr = addr;
	}
	public Long getValue()
	{
		return value;
	}
	public void setValue(Long value)
	{
		this.value = value;
	}
	public Integer getTx_index()
	{
		return tx_index;
	}
	public void setTx_index(Integer tx_index)
	{
		this.tx_index = tx_index;
	}
	public Integer getN()
	{
		return n;
	}
	public void setN(Integer n)
	{
		this.n = n;
	}
}
