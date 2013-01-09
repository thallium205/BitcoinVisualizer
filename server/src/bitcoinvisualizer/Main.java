package bitcoinvisualizer;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import org.apache.commons.io.FileUtils;
import org.neo4j.helpers.collection.MapUtil;
import org.neo4j.kernel.GraphDatabaseAPI;
import org.neo4j.kernel.HighlyAvailableGraphDatabase;
import org.neo4j.server.WrappingNeoServerBootstrapper;

import bitcoinvisualizer.api.GraphExporterNodejsApi;

public class Main
{
	private static final java.util.logging.Logger LOG = Logger.getLogger(Main.class.getName());
	private static GraphDatabaseAPI graphDb;
	private static WrappingNeoServerBootstrapper srv;
	private static Thread shutdownThread;
	private static GraphExporterNodejsApi api = null;
	private static GraphExporter graphExporter = new GraphExporter();
	

	public static void main(String[] args)
	{
		
		
		CommandLine line = null;
		boolean hasError = false;

		while (!hasError)
		{
			// Print options
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("java -jar blockchainneo4j.jar", getOptions());			
			CommandLineParser parser = new GnuParser();		
			try
			{
				line = parser.parse(getOptions(), args);
			} 
			
			catch (ParseException e)
			{
				hasError = true;
				LOG.log(Level.SEVERE, "Unable to parse options.", e);
			}
			
			// Start Neo4j
			try
			{
				graphDb = new HighlyAvailableGraphDatabase((line.getOptionValue("dbPath")), MapUtil.load(FileUtils.getFile(line.getOptionValue("configPath"))));			
				srv = new WrappingNeoServerBootstrapper(graphDb);
				srv.start();
				shutdownThread = new Thread()
				{
					@Override
					public void run()
					{
						LOG.info("Stopping database...");
						srv.stop();
						graphDb.shutdown();
						LOG.info("Database stopped.");
					}
				};
			} 
			catch (Exception e)
			{
				hasError = true;
				LOG.log(Level.SEVERE, "Unable to start Neo4j.", e);
			}
			
			try
			{				
				// Get values
				line = parser.parse(getOptions(), args);
				boolean validate = true;
				if (line.hasOption("validate"))
					validate = Boolean.parseBoolean(line.getOptionValue("validate"));
				
				if (!GraphBuilder.IsStarted())
				{
					GraphBuilder.StartDatabase(graphDb);
				}

				if (!line.hasOption("client"))
				{
					try
					{						
						if (line.hasOption("low"))
						{
							GraphBuilder.DownloadAndSaveBlockChain(validate);
						}
						
						if (line.hasOption("high"))
						{
							GraphBuilder.BuildHighLevelGraph();
						}

						// If the user activated the scraper
						if (line.hasOption("scraper"))
						{
							GraphBuilder.Scrape();
						}
						
						if (line.hasOption("timeanalysis"))
						{							
							int cores = Runtime.getRuntime().availableProcessors();			
							graphExporter.ExportTimeAnalysisGraphsToMySql(graphDb, cores > 1 ? cores - 1 : cores);
						}
						
					} 
					
					catch (Exception e)
					{
						LOG.log(Level.WARNING, "Graph Build Failed.  Skipping, but not shutting down database.", e);
					}
				}
				
				// If the user activated the exporter server
				if (line.hasOption("api"))
				{
					if (api == null)
					{
						 api = new GraphExporterNodejsApi(graphDb);
					}
				}
				
			}

			catch (ParseException e)
			{
				LOG.log(Level.SEVERE, "Parsing failed.  Reason: " + e.getMessage(), e);
				hasError = true;
				// GraphBuilder.StopDatabase();
			} 
			
			catch (IOException e)
			{
				LOG.log(Level.SEVERE, "Input/Output Failed.  Reason: " + e.getMessage(), e);
				hasError = true;
			}

			
			try
			{
				if (line.hasOption("time"))
				{
					LOG.info("Sleeping for: " + line.getOptionValue("time") + " ms");					
					Thread.sleep(Long.parseLong(line.getOptionValue("time")));					
				}
				else
				{
					LOG.info("Sleeping for default time of 6 hours since no time was specified.");
					Thread.sleep(21600000);
				}				
			}

			catch (InterruptedException e)
			{
				LOG.log(Level.SEVERE, "Thread sleep failed.  Reason: " + e.getMessage(), e);
				// GraphBuilder.StopDatabase();
			}
			
		}
		
		LOG.info("Stopping database...");
		srv.stop();
		graphDb.shutdown();
		Runtime.getRuntime().removeShutdownHook(shutdownThread);
		LOG.info("Database stopped.");		
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
		Option dbPath = OptionBuilder.hasArg().withArgName("dbPath").withDescription("The path to the neo4j graph.db directory. Ex: /home/user/neo4j/graph.db/").isRequired().create("dbPath");
		Option configPath = OptionBuilder.hasArg().withArgName("configPath").withDescription("The path to the neo4jconfig file. Ex: /home/user/neo4j.properties").isRequired().create("configPath");
		Option validate = OptionBuilder.hasArg().withArgName("true/false")
				.withDescription("Toggle the verifier which checks if the local json files form a complete blockchain.  Default: true.  Recommended.").create("validate");
		Option scraper = OptionBuilder.withArgName("scraper").withDescription("Runs the scraper which attempts to associate bitcoin addresses to real world entities.").create("scraper");
		Option low = OptionBuilder.withArgName("low").withDescription("Builds the low level block chain structure.").create("low");
		Option high = OptionBuilder.withArgName("high").withDescription("Builds the high level data structure.").create("high");
		Option client = OptionBuilder.withArgName("client").withDescription("Will only run the database service and not attempt to build the blockchain.").create("client");
		Option api = OptionBuilder.withArgName("api").withDescription("Creates the exporter server to communicate with an external web server.").create("api");
		Option timeAnalysis = OptionBuilder.withArgName("timeanalysis").withDescription("Exports a time based analysis of the entire block chain to mysql").create("timeanalysis");
		Option time = OptionBuilder.hasArg().withArgName("time").withDescription("The amount of time the program will wait before rebuilding an updated version of the graph again.").create("time");
		Option statistics = OptionBuilder.withArgName("statistics").withDescription("Prints a CSV file in the current directory with some statistics on the block chain.").create("statistics");
		
		Options options = new Options();
		options.addOption(dbPath);
		options.addOption(configPath);
		options.addOption(validate);
		options.addOption(scraper);
		options.addOption(low);
		options.addOption(high);
		options.addOption(client);
		options.addOption(api);
		options.addOption(timeAnalysis);
		options.addOption(time);
		options.addOption(statistics);
		return options;
	}
}
