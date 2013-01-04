package bitcoinvisualizer.api;

import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Logger;

import org.neo4j.kernel.GraphDatabaseAPI;

import bitcoinvisualizer.GraphExporter;

public class GraphExporterNodejsApi extends NanoHTTPD
{
	private static final Logger LOG = Logger.getLogger(GraphExporter.class.getName());
	final GraphDatabaseAPI graphDb;

	public GraphExporterNodejsApi(final GraphDatabaseAPI graphDb) throws IOException
	{			
		super(7475, new File("."));
		this.graphDb = graphDb;
		LOG.info("API Server Running on Port 7475.");
	}
	
	public Response serve( String uri, String method, Properties header, Properties parms, Properties files )
	{	
		if (method.contains("GET"))
		{
			if (uri.contains("/owner"))
			{
				if (parms.containsKey("addr"))
				{
					if (uri.contains("/owner/gexf"))
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_DEFAULT_BINARY, GraphExporter.GetOwnerByAddress(graphDb, parms.getProperty("addr"), GraphExporter.ExportType.GEXF));
					}
					
					else if (uri.contains("/owner/pdf"))
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_HTML, GraphExporter.GetOwnerByAddress(graphDb, parms.getProperty("addr"), GraphExporter.ExportType.PDF));	
					}
					
					else if (uri.contains("/owner/png"))
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_HTML, GraphExporter.GetOwnerByAddress(graphDb, parms.getProperty("addr"), GraphExporter.ExportType.PNG));
					}
					
					else 
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_PLAINTEXT, "{\"error\": \"Only GEXF, PDF, and PNG file types are supported.\"}");
					}
				}
				
				else if (parms.containsKey("ownerId"))
				{
					if (uri.contains("/owner/gexf"))
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_DEFAULT_BINARY, GraphExporter.GetOwnerById(graphDb, Long.parseLong(parms.getProperty("ownerId")), GraphExporter.ExportType.GEXF));
					}
					
					else if (uri.contains("/owner/pdf"))
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_DEFAULT_BINARY, GraphExporter.GetOwnerById(graphDb, Long.parseLong(parms.getProperty("ownerId")), GraphExporter.ExportType.PDF));					}
					
					else if (uri.contains("/owner/png"))
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_DEFAULT_BINARY, GraphExporter.GetOwnerById(graphDb, Long.parseLong(parms.getProperty("ownerId")), GraphExporter.ExportType.PNG));					}
					
					else 
					{
						return new NanoHTTPD.Response( HTTP_OK, MIME_PLAINTEXT, "{\"error\": \"Only GEXF, PDF, and PNG file types are supported.\"}");
					}
				}
				else
				{
					return new NanoHTTPD.Response( HTTP_OK, MIME_PLAINTEXT, "{\"error\": \"Owner requires an address or owner id.\"}");
				}
			}
			else
			{
				return new NanoHTTPD.Response( HTTP_OK, MIME_PLAINTEXT, "{\"error\": \"The owner endpoint is only available.\"}");
			}
		}
		else
		{
			return new NanoHTTPD.Response( HTTP_OK, MIME_PLAINTEXT, "{\"error\": \"GETs only.\"}");
		}
	}
}