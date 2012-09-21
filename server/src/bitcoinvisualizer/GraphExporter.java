package bitcoinvisualizer;

import org.gephi.neo4j.plugin.api.Neo4jImporter;
import org.gephi.neo4j.plugin.impl.Neo4jImporterImpl;

/**
 * Creates an information rich graph in .gexf format of the Bitcoin ownership network given the path to a Neo4j graph.db database. 
 * @author John
 *
 */
public class GraphExporter
{
	public static void BuildGexfFromNeo4j(String dbPath, String gexfPath)
	{
		// Import
		Neo4jImporter importer = new Neo4jImporterImpl();
		// importer.importDatabase(arg0)
	}
	
	public static void BuildSeadragonFromGexf(String gexfPath, String seadragonPath)
	{
		
	}
}
