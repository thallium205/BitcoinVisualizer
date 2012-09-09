package bitcoinvisualizer.scraper;

public class BitcoinOtcUserType
{
	private Long id;
	private String keyid;
	private String fingerprint;
	private String bitcoinaddress;
	private String registered_at;
	private String nick;
	
	public Long getId()
	{
		return id;
	}
	public void setId(Long id)
	{
		this.id = id;
	}
	public String getKeyid()
	{
		return keyid;
	}
	public void setKeyid(String keyid)
	{
		this.keyid = keyid;
	}
	public String getFingerprint()
	{
		return fingerprint;
	}
	public void setFingerprint(String fingerprint)
	{
		this.fingerprint = fingerprint;
	}
	public String getBitcoinaddress()
	{
		return bitcoinaddress;
	}
	public void setBitcoinaddress(String bitcoinaddress)
	{
		this.bitcoinaddress = bitcoinaddress;
	}
	public String getRegistered_at()
	{
		return registered_at;
	}
	public void setRegistered_at(String registered_at)
	{
		this.registered_at = registered_at;
	}
	public String getNick()
	{
		return nick;
	}
	public void setNick(String nick)
	{
		this.nick = nick;
	}
}
