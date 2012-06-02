package bitcoinvisualizer.domain;

import com.sdicons.json.model.JSONValue;

/**
 * High level accessor of a block.  Contains both the parsed and raw JSON object returned from the API.
 * @author John
 *
 */
public class BlockJsonType
{
	private BlockType blockType;
	private JSONValue blockJson;
	
	public BlockJsonType(bitcoinvisualizer.domain.BlockType blockType2, JSONValue blockJson)
	{
		this.blockType = blockType2;
		this.blockJson = blockJson;
	}
	
	public BlockType getBlockType()
	{
		return blockType;
	}
	public JSONValue getBlockJson()
	{
		return blockJson;
	}
}
