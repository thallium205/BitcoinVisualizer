package blockchainneo4j;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

	String filePath;
	Connection conn;

	/**
	 * The path of the database to be modified or created.
	 * 
	 * @param filePath
	 * @throws ClassNotFoundException
	 */
	public Scraper(String filePath) throws ClassNotFoundException, SQLException
	{
		Class.forName("org.sqlite.JDBC");
		this.filePath = filePath;

		this.filePath = filePath;
		if (!new File(filePath).exists())
		{
			LOG.info("Scraper database not found.  Creating one...");
			conn = DriverManager.getConnection("jdbc:sqlite:" + filePath);
			buildDatabase();
			LOG.info("Created.");
		}

		else
		{
			LOG.info("Database found.  Connecting...");
			conn = DriverManager.getConnection("jdbc:sqlite:" + filePath);
			LOG.info("Connected.");
		}
	}

	public void bitcoinTalkProfiles() throws IOException, SQLException
	{
		// User profile page
		String url = "https://bitcointalk.org/index.php?action=profile;u=";

		// Import the security certificate
		System.setProperty("javax.net.ssl.trustStore", "cert/bitcointalk.jks");

		// Get latest member user id, set that as upper bound
		//int latestUid = getLatestUid();
		
		//if (latestUid != -1)
		//{

		for (int i = 224; i < 225; i++)
		{
			Document profile = Jsoup.connect(url + i).get();

			// Get an array of all the addresses mentioned in the signature
			Pattern p = Pattern.compile("/^([a-zA-Z0-9_-]){5,50}$/");
			String text = profile.text();
			Matcher m = p.matcher(profile.text());
			List<String> addresses = new ArrayList<String>();
			while (m.find())
			{
				addresses.add(m.group());
			}

			for (String address : addresses)
			{
				try
				{
					Address validAddress = new Address(NetworkParameters.prodNet(), address);
					
					// The address is valid.  Add the information associated with it to the database
					String query = "INSERT OR REPLACE INTO Address (address)";
					PreparedStatement statement = conn.prepareStatement(query);
					
				}

				catch (AddressFormatException e1)
				{
					LOG.log(Level.INFO, address + " is not a valid Bitcoin address.  Skipping.", e1);
				}				
			}
		}
	}

	private int getLatestUid() throws IOException
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

	private void buildDatabase() throws SQLException
	{
		Statement statement = conn.createStatement();
		statement.executeUpdate("CREATE TABLE Address (address TEXT, PRIMARY KEY(address))");
		statement.executeUpdate("CREATE TABLE Association (id INTEGER, url TEXT, raw TEXT, entity TEXT, FOREIGN KEY (Address_address) REFERENCES Address(address), PRIMARY KEY (id))");
	}
}
