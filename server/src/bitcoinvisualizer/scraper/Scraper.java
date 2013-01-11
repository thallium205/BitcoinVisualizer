package bitcoinvisualizer.scraper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.GraphDatabaseAPI;

import bitcoinvisualizer.GraphBuilder;
import bitcoinvisualizer.GraphBuilder.OwnerRelTypes;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;
import com.sdicons.json.mapper.JSONMapper;
import com.sdicons.json.model.JSONArray;
import com.sdicons.json.model.JSONValue;
import com.sdicons.json.parser.JSONParser;

/**
 * Attempts to associate bitcoin addresses to real-world identieis and stores them in a simple sqllite database.
 * 
 * @author John
 * 
 */
public class Scraper
{

	private static final Logger LOG = Logger.getLogger(Scraper.class.getName());
	
	public static enum ScraperRelationships implements RelationshipType
	{
		identifies
	}

	public static void BitcoinTalkProfiles(final GraphDatabaseAPI graphDb, final Index<Node> owned_addresses) throws IOException
	{
		// User profile page
		String url = "https://bitcointalk.org/index.php?action=profile;u=";

		// Import the security certificate
		System.setProperty("javax.net.ssl.trustStore", "cert/bitcointalk2013.jks");

		// Get latest member user id, set that as upper bound
		int last_internet_userId = getLatestUid();

		if (last_internet_userId != -1)
		{
			for (int i = (Integer) graphDb.getReferenceNode().getProperty("last_bitcointalk_userId_scraped", 0); i < last_internet_userId; i++)
			{
				LOG.info("Begin scraping user: " + i);
				Document profile = null; 
				try 
				{
					profile = Jsoup.connect(url + i).get();
				} 
				
				catch (SocketTimeoutException e)
				{
					LOG.log(Level.WARNING, "Jsoup connect function timed out.  Retrying...", e);
				}
				
				if (profile == null)
				{
					i--;
					continue;
				}
				
				Elements elements = profile.getElementsByClass("signature");
				String signature = elements.text();
				String[] words = signature.trim().split(" ");
				for (String word : words)
				{
					if (word.length() > 28)
					{
						String addr = null;
						if (word.indexOf("1") != -1)
						{
							addr = word.substring(word.indexOf("1"));
						}

						else if (word.indexOf("3") != -1)
						{
							addr = word.substring(word.indexOf("3"));
						}

						if (addr != null && addr.length() > 28)
						{
							// Check to see if we have an owner entity associated with this address.  If we don't, then lucky you!
							Transaction tx = graphDb.beginTx();	
							try
							{
								identifyAddress(owned_addresses, new Address(NetworkParameters.prodNet(), addr), getName(profile), url + i, "thallium205");
								graphDb.getReferenceNode().setProperty("last_bitcointalk_userId_scraped", i);
								tx.success();
							}

							catch (AddressFormatException e)
							{
								tx.failure();
								LOG.log(Level.INFO, addr + " is not a valid Bitcoin address.  Skipping.", e);
							}
							
							finally
							{
								tx.finish();
							}							
						}
					}
				}
				LOG.info("User: " + i + " completed.");
			}
		}
	}
	
	public static void BitcoinOtcDatabase(final GraphDatabaseAPI graphDb, final Index<Node> owned_addresses) throws Exception
	{
		final URL BITCOIN_OTC_URL_DB = new URL("http://bitcoin-otc.com/viewgpg.php?outformat=json");
		URLConnection connection = BITCOIN_OTC_URL_DB.openConnection();
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));			
		JSONArray json = (JSONArray) new JSONParser(reader).nextValue();
		ArrayList<JSONValue> jsonUsers = (ArrayList<JSONValue>) json.getValue();	
		for (JSONValue userJson : jsonUsers)
		{
			BitcoinOtcUserType user = (BitcoinOtcUserType) JSONMapper.toJava(userJson, BitcoinOtcUserType.class);
			if (user.getBitcoinaddress() != null && user.getNick() != null)
			{
				// That was easy.
				Transaction tx = graphDb.beginTx();	
				try
				{
					identifyAddress(owned_addresses, new Address(NetworkParameters.prodNet(), user.getBitcoinaddress()), user.getNick(), BITCOIN_OTC_URL_DB.toString(), "thallium205");
					tx.success();
				} 
				
				catch (AddressFormatException e)
				{
					tx.failure();
					LOG.log(Level.INFO, user.getBitcoinaddress() + " is not a valid Bitcoin address.  Skipping.", e);							
				}
				
				finally 
				{
					tx.finish();
				}									
			}
		}					
	}
	
	private static void identifyAddress(final Index<Node> owned_addresses, Address address, String name, String source, String contributor)
	{
		Date time = new Date();
		for (Node ownedAddr : owned_addresses.query(GraphBuilder.OWNED_ADDRESS_HASH_KEY, address.toString()))
		{
			if (ownedAddr.hasRelationship(ScraperRelationships.identifies, Direction.OUTGOING))
			{
				LOG.info("This address already has an identifiying relationship associated with it.  Skipping...");
				break;
			}
																					
			// For each ownedAddr, we find the owner node and make am identifiying relationship to it			
			Node owner = ownedAddr.getSingleRelationship(OwnerRelTypes.owns,  Direction.INCOMING).getStartNode();																		
			// Set the name to the owner node as well as the relationship.
			if (owner.hasProperty("name"))
			{
				final String ownerName = (String) owner.getProperty("name");
				owner.setProperty("name", ownerName + "," + name);
			}
			
			else
			{
				owner.setProperty("name", name);
			}							
			
			Relationship relationship = ownedAddr.createRelationshipTo(owner, ScraperRelationships.identifies);				
			relationship.setProperty("name", name);
			relationship.setProperty("source", source);
			relationship.setProperty("contributor", contributor);
			relationship.setProperty("time", time.getTime());
			
			LOG.info("Owner added:\nAddress: " + address.toString() + "\nName: " + name + "\nSource: " + source + "\nTime: " + time.toString());			
		}
	}

	private static int getLatestUid() throws IOException
	{
		Document homePage = Jsoup.connect("https://bitcointalk.org/index.php").get();
		Elements elements = homePage.getElementsByClass("middletext");

		for (Element element : elements)
		{
			if (element.text().indexOf("Latest Member") != -1)
			{
				String url = element.select("a").attr("href");
				return Integer.parseInt(url.substring(url.lastIndexOf("=") + 1));
			}
		}
		return -1;
	}

	private static String getName(Document profile)
	{
		Elements elements = profile.getElementsByClass("windowbg");

		for (Element element : elements)
		{
			Elements els = element.getElementsByTag("td");

			for (Element e : els)
			{
				if (e.hasText() && e.text().equals("Name:"))
				{
					return e.nextElementSibling().text();
				}
			}
		}
		return null;
	}
}
