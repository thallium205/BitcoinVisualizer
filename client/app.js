var xmlutils = require('./util/xml');

var application_root = __dirname,
    express = require("express"),
    path = require("path"),
    neo4j = require('neo4j'),
	xml2js = require('xml2js');

// Database
var db = new neo4j.GraphDatabase('http://localhost:7474');

// Config
var app = express.createServer();
app.configure(function () {
  app.use(express.bodyParser());
  app.use(express.methodOverride());
  app.use(app.router);
  app.use(express.static(path.join(application_root, "public")));
  app.use(express.errorHandler({ dumpExceptions: true, showStack: true }));
});

// Homepage Route
app.get('/', function(req, res){
  res.render('index.jade', { title: 'Dynamic Data from the server LAWL' });
});

// API Route
app.get('/api', function (req, res) {
  res.send('API is running');
});

// API - block/hash
app.get('/api/block/hash/:id.:format?', function (req, res) 
{
	
		
	var 
	query = [
		'START node = node:block_hashes(block_hash={blockId})',
		'MATCH node - [rel] - neighbor ',
		'RETURN COLLECT(neighbor) AS nodes, COLLECT(rel) AS edges'].join('\n'),		
	selfQuery = [
		'START node = node:block_hashes(block_hash={blockId})',
		'RETURN COLLECT(node) AS node'].join('\n'),
	params = {
		blockId: req.params.id,
		},
	queryResult = null,
	selfResult = null;
	
	
		
	db.query(query, params, function callback(err, result)
	{
		if (err)
		{
			res.send(err);
		}
		
		else
		{			
			queryResult = result;
			
			if (queryResult != null && selfResult != null)
			{	
				// Combine the queries
				queryResult[0].nodes.push(selfResult[0].node[0]);				
				if (req.params.format == 'json')
				{
					sendJson(queryResult, res);				
				}
				else
				{
					sendXml(queryResult, res);
				}
			}
		}
	});
	
	db.query(selfQuery, params, function callback(err, result)
	{
		if (err)
		{
			res.send(err);
		}
		
		else
		{	
			selfResult = result;
			
			if (queryResult != null && selfResult != null)
			{	
				// Combine the queries
				queryResult[0].nodes.push(selfResult[0].node[0]);				
				if (req.params.format == 'json')
				{
					sendJson(queryResult, res);				
				}
				else
				{
					sendXml(queryResult, res);
				}
			}
		}
	});
	
});

// Format database response to gexf
function sendXml(result, res)
{
	res.send(toGexf(result));
}

// Format database response to gexf json
function sendJson(result, res)
{	
	var parser = new xml2js.Parser();		
		parser.parseString(toGexf(result), function (err, result) 
		{
			res.send(JSON.stringify(result, null, 4));
		});
}

function toGexf(result)
{
	if (result != null)
	{	
		// Improvised hashset for unique node and edge attributes since different types of nodes are in each result
		var nodeAttr = {}; 
		var edgeAttr = {};
		
		var xml = new xmlutils.writer;		
		xml.BeginNode("gexf");
		xml.Attrib("xmlns", "http://www.gexf.net/1.2draft");
		xml.Attrib("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		xml.Attrib("xsi:schemaLocation", "http://www.gexf.net/1.2draft http://www.gexf.net/1.2draft/gexf.xsd");
		xml.Attrib("version", "1.2");
		xml.BeginNode("meta");
		xml.Attrib("lastmodifieddate", new Date().toISOString());
		xml.BeginNode("creator");
		xml.WriteString("BlockViewer.com");
		xml.EndNode();
		xml.BeginNode("description");
		xml.WriteString("Bitcoin Blockchain");
		xml.EndNode();
		xml.EndNode();
		xml.BeginNode("graph");
		xml.Attrib("defaultedgetype", "directed");
		xml.BeginNode("attributes");
		xml.Attrib("class", "node");
		// Begin node attributes loop
		
		// Get all the unique node attributes	(todo - use a hashset?)
		for (node in result[0].nodes)
		{				
			for (prop in result[0].nodes[node].data)
			{
				nodeAttr[prop] = "";
			}	
		}		
		
		// Bind each unique node attribute to a numeric id		
		var i = 0;
		for (attr in nodeAttr)
		{
			if (nodeAttr.hasOwnProperty(attr))
			{ 		
				nodeAttr[attr] = i;				
				xml.BeginNode("attribute");
				xml.Attrib("id", i.toString());
				xml.Attrib("title", attr);
				xml.Attrib("type", "string");
				xml.EndNode();
				i++;
			}
		}
		xml.EndNode();
		xml.BeginNode("attributes");
		xml.Attrib("class", "edge");
		// Begin edge attributes loop
		
		// Get all the unique edge attributes	(todo - use a hashset?)		
		for (edge in result[0].edges)
		{				
			for (prop in result[0].edges[edge].data)
			{
				edgeAttr[prop] = "";
			}	
		}
		
		// Bind each unique edge attribute to a numeric id
		i = 0;
		for (attr in edgeAttr)
		{
			if (edgeAttr.hasOwnProperty(attr))
			{ 
				edgeAttr[attr] = i;
				xml.BeginNode("attribute");
				xml.Attrib("id", i.toString());
				xml.Attrib("title", attr);
				xml.Attrib("type", "string");
				xml.EndNode();
				i++;
			}
		}		
		xml.EndNode();
		
		// Begin nodes
		xml.BeginNode("nodes");		
		// Define the nodes and bind their attribute values to the node attribute list
		for (node in result[0].nodes)
		{		
			xml.BeginNode("node");
			xml.Attrib("id", result[0].nodes[node].self.match(/\/node\/(.*)/)[1].toString());
			
			// Determine what kind of node this is
			if (result[0].nodes[node].data.hasOwnProperty("block_index"))
			{
				xml.Attrib("label", "block");
			}
			
			else if (result[0].nodes[node].data.hasOwnProperty("tx_index"))
			{
				xml.Attrib("label", "transaction");
			}
			
			else
			{
				xml.Attrib("label", "unknown");
			}			
			
			xml.BeginNode("attvalues");
			for (prop in result[0].nodes[node].data)
			{		
				xml.BeginNode("attvalue");
				xml.Attrib("for", nodeAttr[prop].toString());
				xml.Attrib("value", result[0].nodes[node].data[prop].toString());
				xml.EndNode();				
			}
			xml.EndNode();
			xml.EndNode();
		}		
		xml.EndNode();
		
		// Begin edges
		xml.BeginNode("edges");
		// Define the edges and bind their attribute values to the edge attribute list
		for (edge in result[0].edges)
		{
			xml.BeginNode("edge");
			xml.Attrib("id", result[0].edges[edge].self.match(/\/relationship\/(.*)/)[1].toString());
			xml.Attrib("label", result[0].edges[edge].type);
			// xml.Attrib("type", "directed");  // TODO
			xml.Attrib("source", result[0].edges[edge].start.match(/\/node\/(.*)/)[1].toString());
			xml.Attrib("target", result[0].edges[edge].end.match(/\/node\/(.*)/)[1].toString());
			xml.BeginNode("attvalues");
			for (prop in result[0].edges[edge].data)
			{
				xml.BeginNode("attvalue");
				xml.Attrib("for", edgeAttr[prop].toString());
				xml.Attrib("value", result[0].edges[edge].data[prop]);
				xml.EndNode();	
			}
			xml.EndNode();
			xml.EndNode();
		}
		
		xml.EndNode();	
		xml.EndNode();
		xml.EndNode();

		return xml.ToString();
	}
}

// Launch server
app.listen(3000, function(){
  console.log("Express server listening on port %d in %s mode", app.address().port, app.settings.env);
});
