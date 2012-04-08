package blockchainneo4j;

import java.io.IOException;
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

import com.sdicons.json.mapper.MapperException;

import antlr.RecognitionException;
import antlr.TokenStreamException;

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
			String path = line.getOptionValue("uri");
			Integer threads = Integer.parseInt(line.getOptionValue("threads"));
			String user = line.getOptionValue("user");
			String pass = line.getOptionValue("pass");			

			if (user != null && pass != null)
			{
				Database db = new Database(path, user, pass);
				//db.persistVertices(threads);
				db.persistEdges();
			}
			
			else
			{			
				Database db = new Database(path);
				//db.persistVertices(threads);
				db.persistEdges();
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
		Option uri = OptionBuilder.hasArg().withArgName("uri").withDescription("The uri to the neo4j instsance. Ex: http://localhost:7474/db/data").isRequired().create("uri");
		Option threads = OptionBuilder.hasArg().withArgName("num").withDescription("How many threads to spawn to query the API.").isRequired().create("threads");
		Option user = OptionBuilder.hasArg().withArgName("username").withDescription("Username of the neo4j instance.").create("user");
		Option pass = OptionBuilder.hasArg().withArgName("password").withDescription("Password of the neo4j instance.").create("pass");

		Options options = new Options();
		options.addOption(uri);
		options.addOption(threads);
		options.addOption(user);
		options.addOption(pass);
		return options;
	}

}
