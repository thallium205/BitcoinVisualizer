package bitcoinvisualizer;

import java.io.IOException;
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
import org.neo4j.graphdb.ReturnableEvaluator;
import org.neo4j.graphdb.StopEvaluator;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.Traverser.Order;
import org.neo4j.graphdb.index.Index;
import org.neo4j.kernel.GraphDatabaseAPI;

import bitcoinvisualizer.GraphBuilder.OwnerRelTypes;

import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.NetworkParameters;

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
		String btcAddress;
		String name;
		String source;
		String contributor = "thallium205";
		Date time = new Date();

		// User profile page
		String url = "https://bitcointalk.org/index.php?action=profile;u=";

		// Import the security certificate
		System.setProperty("javax.net.ssl.trustStore", "cert/bitcointalk.jks");

		// Get latest member user id, set that as upper bound
		int last_internet_userId = getLatestUid();

		if (last_internet_userId != -1)
		{
			for (int i = graphDb.getReferenceNode().hasProperty("last_userId_scraped") ? (Integer) graphDb.getReferenceNode().getProperty("last_userId_scraped") : 0; i < last_internet_userId; i++)
			{
				LOG.info("Begin scraping user: " + i);
				Document profile = Jsoup.connect(url + i).get();
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
							try
							{
								Address address = new Address(NetworkParameters.prodNet(), addr);

								// The address is valid. Add the information associated with it to the database
								btcAddress = address.toString();
								name = getName(profile);
								source = url + i;							
								
								// Check to see if we have an owner entity associated with this address.  If we don't, then lucky you!
								for (Node ownedAddr : owned_addresses.query(GraphBuilder.OWNED_ADDRESS_HASH_KEY, btcAddress))
								{
									if (ownedAddr.hasRelationship(ScraperRelationships.identifies, Direction.OUTGOING))
									{
										LOG.info("This address already has an identifiying relationship associated with it.  Skipping...");
										break;
									}
																											
									// For each ownedAddr, we find the owner node and make am identifiying relationship to it						
									Iterable<Node> owners = ownedAddr.traverse(Order.BREADTH_FIRST,  StopEvaluator.DEPTH_ONE, ReturnableEvaluator.ALL_BUT_START_NODE, OwnerRelTypes.owns, Direction.INCOMING);						
									for (Node owner : owners)
									{
										Transaction tx = graphDb.beginTx();										
										Relationship relationship = ownedAddr.createRelationshipTo(owner, ScraperRelationships.identifies);
										relationship.setProperty("name", name);
										relationship.setProperty("source", source);
										relationship.setProperty("contributor", contributor);
										relationship.setProperty("time", time.toString());
										graphDb.getReferenceNode().setProperty("last_userId_scraped", i);
										tx.success();
										tx.finish();
										LOG.info("Owner added:\nAddress: " + btcAddress + "\nName: " + name + "\nSource: " + source + "\nTime: " + time.toString());
									}
								}
							}

							catch (AddressFormatException e)
							{
								LOG.log(Level.INFO, addr + " is not a valid Bitcoin address.  Skipping.", e);
							}
						}
					}
				}
				LOG.info("User: " + i + " completed.");
			}
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
