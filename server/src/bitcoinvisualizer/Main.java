package bitcoinvisualizer;

import java.io.IOException;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;


public class Main
{
	private static final Logger LOG = Logger.getLogger(Main.class.getName());

	public static void main(String[] args)
	{
		// Print options
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("java -jar blockchainneo4j.jar", getOptions());

		// Parse options
		CommandLineParser parser = new GnuParser();
		try
		{
			// Get values
			CommandLine line = parser.parse(getOptions(), args);
			
			if (line.hasOption("path"))
			{
				boolean validate = true;
				if (line.hasOption("validate"))
					validate = Boolean.parseBoolean(line.getOptionValue("validate"));
				GraphBuilder.StartDatabase(line.getOptionValue("path"));
				GraphBuilder.DownloadAndSaveBlockChain(validate);
				GraphBuilder.BuildHighLevelGraph();	
				GraphBuilder.StopDatabase(); // Debug.
				LOG.info("Completed.");
			}
			
			// If the user activated the scraper
			else if (line.hasOption("scraper"))
			{
				try
				{
					Scraper scraper = new Scraper("scraper.sql");
					scraper.bitcoinTalkProfiles();
				}

				catch (ClassNotFoundException e)
				{
					LOG.log(Level.SEVERE, "Unable to start the scraper.", e);
				}

				catch (SQLException e)
				{
					LOG.log(Level.SEVERE, "Unable to start the scraper.", e);
				}

				catch (IOException e)
				{
					LOG.log(Level.SEVERE, "Unable to scrape the website.", e);
				}
			}
			
			else
			{
				LOG.info("No options selected.");
			}
		}

		catch (ParseException e)
		{
			LOG.log(Level.SEVERE, "Parsing failed.  Reason: " + e.getMessage(), e);
		}
	}

	/**
	 * Creates the options menu
	 * 
	 * @return
	 */
	@SuppressWarnings("static-access")
	private static Options getOptions()
	{
		// Define options
		Option dbPath = OptionBuilder.hasArg().withArgName("path").withDescription("The path to the neo4j graph.db directory. Ex: /home/user/Documents/neo4j/graph.db").isRequired().create("path");
		Option validate = OptionBuilder.hasArg().withArgName("true/false")
				.withDescription("Toggle the verifier which checks if the local json files form a complete blockchain.  Default: true.  Recommended.").create("validate");
		Option scraper = OptionBuilder.withArgName("scraper").withDescription("Runs the scraper which attempts to associate bitcoin addresses to real world entities.").create("scraper");
		Options options = new Options();
		options.addOption(dbPath);
		options.addOption(validate);
		options.addOption(scraper);
		return options;
	}
}
