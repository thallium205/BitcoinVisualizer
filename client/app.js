var xmlwriter = require('./util/xmlwriter.js');

var application_root = __dirname,
    express = require("express"),
    path = require("path"),
    neo4j = require('neo4j');

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
	params = {
		blockId: req.params.id,
		};
		
	db.query(query, params, function callback(err, result)
	{
		if (err)
		{
			res.send(err);
		}
		
		else
		{	
			if (req.params.format == 'xml')
			{
				sendXml(result, res);
			}
			else
			{
				sendJson(result, res);
			}
		}
	});
});

// Format database response to gexf
function sendXml(result, res)
{
	if (result != null)
	{	
		// Improvised hashset for unique node and edge attributes since different types of nodes are in each result
		var nodeAttr = {}; 
		var edgeAttr = {};
		
		var xml = new xmlwriter.xmlwriter;	
		xml.BeginNode("gexf");
		xml.Attrib("xmlns", "http://www.gexf.net/1.2draft");
		xml.Attrib("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
		xml.Attrib("xsi:schemaLocation", "http://www.gexf.net/1.2draft http://www.gexf.net/1.2draft/gexf.xsd");
		xml.Attrib("version", "1.2");
		xml.BeginNode("meta");
		xml.Attrib("lastmodifieddate", "2009-03-20");
		xml.BeginNode("creator");
		xml.WriteString("BlockViewer.com");
		xml.EndNode();
		xml.BeginNode("description");
		xml.WriteString("Bitcoin Blockchain");
		xml.EndNode();
		xml.EndNode();
		xml.BeginNode("graph")
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
			xml.Attrib("label", "TODO");
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
		res.send(xml.ToString());
	}
}

// Format database response to gexf json
function sendJson(result, res)
{	
	if (result != null)
	{	
		var json = { "gexf": {"xmlns": "http://www.gexf.net/1.2draft", "xmlns:xsi": "http://www.w3.org/2001/XMLSchema-instance", "xsi:schemaLocation": "http://www.gexf.net/1.2draft http://www.gexf.net/1.2draft/gexf.xsd", "version": "1.2", "meta": {"lastmodifieddate": new Date(), "creator": "BlockViewer.com", "description": "Bitcoin Blockchain"}}};

		// Define attributes
		json.gexf.graph = { "defaultedgetype": "directed" };
		json.gexf.graph.attributes = [];
		
		// Define nodes/edges
		json.gexf.graph.attributes[0] = {"class": "node"};
		json.gexf.graph.attributes[0].attribute = [];		
		json.gexf.graph.attributes[1] = {"class": "edge"};
		json.gexf.graph.attributes[1].attribute = [];
		json.gexf.graph.nodes = [];
		json.gexf.graph.nodes.node = {};
		json.gexf.graph.edges = [];
		json.gexf.graph.edges.edge = {};
		
		var attributes = {}; // Used to get unique attributes
		
		// Get all the unique node attributes	(todo - use a hashset?)
		for (node in result[0].nodes)
		{				
			for (prop in result[0].nodes[node].data)
			{
				attributes[prop] = "";
			}	
		}
		
		// Bind each unique node attribute to a numeric id
		var i = 0;
		for (attr in attributes)
		{
			if (attributes.hasOwnProperty(attr))
			{ 
				attributes[attr] = i;
				json.gexf.graph.attributes[0].attribute[i] = {"id": i, "title": attr, "type": "string"};
				i++;
			}
		}		
		
		// Define the nodes and bind their attribute values to the node attribute list
		for (node in result[0].nodes)
		{		
			var n = {"id": result[0].nodes[node].self.match(/\/node\/(.*)/)[1], "label": "somenodevalue"};
			n.attvalues = [];
			for (prop in result[0].nodes[node].data)
			{
				var attvalue = {"for": attributes[prop], "value": result[0].nodes[node].data[prop]};
				n.attvalues.push({"attvalue": attvalue});
			}
			json.gexf.graph.nodes.push({"node": n});
		}
		
		// Get all the unique edge attributes	(todo - use a hashset?)		
		attributes = {};
		for (edge in result[0].edges)
		{				
			for (prop in result[0].edges[edge].data)
			{
				attributes[prop] = "";
			}	
		}
		
		// Bind each unique edge attribute to a numeric id
		i = 0;
		for (attr in attributes)
		{
			if (attributes.hasOwnProperty(attr))
			{ 
				attributes[attr] = i;
				json.gexf.graph.attributes[1].attribute[i] = {"id": i, "title": attr, "type": "string"};
				i++;
			}
		}

		// Define the edges and bind their attribute values to the edge attribute list
		for (edge in result[0].edges)
		{
			var e = {"id": result[0].edges[edge].self.match(/\/relationship\/(.*)/)[1], "type": result[0].edges[edge].type, "source": result[0].edges[edge].start.match(/\/node\/(.*)/)[1], "target": result[0].edges[edge].end.match(/\/node\/(.*)/)[1]};
			e.attvalues = [];
			for (prop in result[0].edges[edge].data)
			{
				var attvalue = {"for": attributes[prop], "value": result[0].edges[edge].data[prop]};
				e.attvalues.push({"attvalue": attvalue});
			}
			json.gexf.graph.edges.push({"edge": e});     
		}
		
		res.send(JSON.stringify(json, null, 4));		
	}	
}

// Launch server
app.listen(3000, function(){
  console.log("Express server listening on port %d in %s mode", app.address().port, app.settings.env);
});
