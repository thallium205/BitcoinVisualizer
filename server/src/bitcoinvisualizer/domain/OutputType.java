package bitcoinvisualizer.domain;

/**
 * An output.  Addr2, Addr3 are anamolous and are rarely used.  All modern blocks in the chain will never have these values.
 * @author John
 *
 */
public class OutputType
{
	private String addr;
	private String addr2;
	private String addr3;
	private Long value;
	private Integer type;	
	private String addr_tag;
	
	private String addr_tag_link;
	
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
	public Integer getType()
	{
		return type;
	}
	public void setType(Integer type)
	{
		this.type = type;
	}
	public String getAddr2()
	{
		return addr2;
	}
	public void setAddr2(String addr2)
	{
		this.addr2 = addr2;
	}
	public String getAddr3()
	{
		return addr3;
	}
	public void setAddr3(String addr3)
	{
		this.addr3 = addr3;
	}
	public String getAddr_tag()
	{
		return addr_tag;
	}
	public void setAddr_tag(String addr_tag)
	{
		this.addr_tag = addr_tag;
	}
	public String getAddr_tag_link()
	{
		return addr_tag_link;
	}
	public void setAddr_tag_link(String addr_tag_link)
	{
		this.addr_tag_link = addr_tag_link;
	}
}
