package blockchainneo4j.domain;

import blockchainneo4j.PrevOut;

/**
 * An input abstraction created from the blockchain.info API.
 * @author John
 *
 */
public class InputType
{
	private PrevOut prev_out;

	public PrevOut getPrev_out()
	{
		return prev_out;
	}

	public void setPrev_out(PrevOut prev_out)
	{
		this.prev_out = prev_out;
	}	
}
