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
});

app.configure('production', function(){
  app.use(express.errorHandler());
});

process.on("uncaughtException", function(err)
{
	console.log(err.message);
});

app.enable("jsonp callback");

// Homepage Route
app.get('/', function(req, res){
  res.render('index.jade', { title: 'Block Viewer' });
});

// FAQ Route
app.get('/faq', function (req, res) {
  res.render('faq.jade',  { title: 'Block Viewer' });
});

// API Route
app.get('/api', function (req, res) {
  res.render('api.jade',  { title: 'Block Viewer' });
});

// Query Route
app.get('/query', function (req, res) {
  res.render('query.jade',  { title: 'Block Viewer' });
});

// API

// Latest
app.get('/api/latest', function (req, res)
{
	db.getNodeById(0, function callback(err, result)
	{
		if (err)
		{
			return res.send(err);
		}
		
		res.send(result.data.last_linked_owner_link_block_nodeId.toString());		
	});
});

// Node
app.get('/api/node/:id.:format?', function (req, res) 
{		
	var 
	query = [
		'START node = node({nodeId})',
		'MATCH node - [rel] - neighbor ',
		'RETURN neighbor AS nodes, rel AS edges',
		'LIMIT 5000'].join('\n'),		
	selfQuery = [
		'START node = node({nodeId})',
		'RETURN node AS nodes'].join('\n'),
	params = {
		nodeId: parseInt(req.params.id, 10),
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
			sendResult(queryResult, selfResult, req, res);
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
			sendResult(queryResult, selfResult, req, res);
		}
	});	
});

// Block hash
app.get('/api/block/hash/:id.:format?', function (req, res) 
{		
	var 
	query = [
		'START node = node:block_hashes(block_hash={blockHash})',
		'MATCH node - [rel] - neighbor ',
		'RETURN neighbor AS nodes, rel AS edges',
		'LIMIT 5000'].join('\n'),		
	selfQuery = [
		'START node = node:block_hashes(block_hash={blockHash})',
		'RETURN node AS nodes'].join('\n'),
	params = {
		blockHash: req.params.id,
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
			sendResult(queryResult, selfResult, req, res);
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
			sendResult(queryResult, selfResult, req, res);
		}
	});	
});

// Block height
app.get('/api/block/height/:id.:format?', function (req, res) 
{		
	var 
	query = [
		'START node = node:block_heights(block_height={blockHeight})',
		'MATCH node - [rel] - neighbor ',
		'RETURN neighbor AS nodes, rel AS edges',
		'LIMIT 5000'].join('\n'),		
	selfQuery = [
		'START node = node:block_heights(block_height={blockHeight})',
		'RETURN node AS nodes'].join('\n'),
	params = {
		blockHeight: req.params.id,
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
			sendResult(queryResult, selfResult, req, res);
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
			sendResult(queryResult, selfResult, req, res);
		}
	});	
});

// Transaction hash
app.get('/api/trans/:id.:format?', function (req, res) 
{		
	var 
	query = [
		'START node = node:tx_hashes(tx_hash={tranHash})',
		'MATCH node - [rel] - neighbor ',
		'RETURN neighbor AS nodes, rel AS edges',
		'LIMIT 5000'].join('\n'),		
	selfQuery = [
		'START node = node:tx_hashes(tx_hash={tranHash})',
		'RETURN node AS nodes'].join('\n'),
	params = {
		tranHash: req.params.id,
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
			sendResult(queryResult, selfResult, req, res);
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
			sendResult(queryResult, selfResult, req, res);
		}
	});	
});

// Address
app.get('/api/addr/:id.:format?', function (req, res) 
{		
	var 
	query = [
		'START node = node:addr_hashes(addr_hash={addr})',
		'MATCH node - [rel] - neighbor ',
		'RETURN neighbor AS nodes, rel AS edges',
		'LIMIT 5000'].join('\n'),		
	selfQuery = [
		'START node = node:addr_hashes(addr_hash={addr})',
		'RETURN node AS nodes'].join('\n'),
	params = {
		addr: req.params.id,
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
			sendResult(queryResult, selfResult, req, res);
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
			sendResult(queryResult, selfResult, req, res);
		}
	});	
});

// Owner
app.get('/api/owns/:id.:format?', function (req, res) 
{		
	var 
	query = [
		'START node = node:owned_addr_hashes(owned_addr_hash={owner})',
		'MATCH node - [rel] - neighbor ',
		'RETURN neighbor AS nodes, rel AS edges',
		'LIMIT 5000'].join('\n'),		
	selfQuery = [
		'START node = node:owned_addr_hashes(owned_addr_hash={owner})',
		'RETURN node AS nodes'].join('\n'),
	params = {
		owner: req.params.id,
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
			sendResult(queryResult, selfResult, req, res);
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
			sendResult(queryResult, selfResult, req, res);
		}
	});	
});

// IPv4 Address
app.get('/api/ipv4/:id.:format?', function (req, res) 
{		
	var 
	query = [
		'START node = node:ipv4_addrs(ipv4_addr={ip})',
		'MATCH node - [rel] - neighbor ',
		'RETURN neighbor AS nodes, rel AS edges',
		'LIMIT 5000'].join('\n'),		
	selfQuery = [
		'START node = node:ipv4_addrs(ipv4_addr={ip})',
		'RETURN node AS nodes'].join('\n'),
	params = {
		ip: req.params.id,
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
			sendResult(queryResult, selfResult, req, res);
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
			sendResult(queryResult, selfResult, req, res);
		}
	});	
});

function sendResult(queryResult, selfResult, req, res)
{
	if (queryResult != null && selfResult != null)
	{	
		for (i = 0; i < selfResult.length; i++)
		{
			queryResult.push(selfResult[i]);
		}	
						
		if (req.params.format == 'json')
		{
			sendJson(queryResult, res, req);				
		}
		
		else
		{
			sendXml(queryResult, res, req);
		}
	}	
}

// Format database response to gexf
function sendXml(result, res, req)
{
	res.setHeader('content-type', 'application/gexf+xml');
	res.send(toGexf(result));
}

// Format database response to gexf json
function sendJson(result, res, req)
{	
	var parser = new xml2js.Parser();		
	parser.parseString(toGexf(result), function (err, result) 
	{	
		if (err)
		{
			return res.send(err);
		}
		
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
		
		// Begin attributes
		xml.BeginNode("attributes");
		xml.Attrib("class", "node");
	
		// Get all the unique node and edge attributes	(todo - use a hashset?)

		for (obj in result)
		{	
			if (result[obj].nodes !== undefined)
			{
				for (prop in result[obj].nodes.data)
				{
					nodeAttr[prop] = "";
				}	
			}

			if (result[obj].edges !== undefined)
			{
				for (prop in result[obj].edges.data)
				{				
					edgeAttr[prop] = "";
				}
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
		for (obj in result)
		{
			if (result[obj].nodes !== undefined)
			{
				xml.BeginNode("node");
				xml.Attrib("id", result[obj].nodes.self.match(/\/node\/(.*)/)[1].toString());
				// Determine what kind of node this is
				if (result[obj].nodes.data.hasOwnProperty("block_index"))
				{
					xml.Attrib("label", "block");
				}
				
				else if (result[obj].nodes.data.hasOwnProperty("tx_index"))
				{
					xml.Attrib("label", "transaction");
				}
				
				else if (result[obj].nodes.data.hasOwnProperty("n") && result[obj].nodes.data.hasOwnProperty("value") && result[obj].nodes.data.hasOwnProperty("addr") && result[obj].nodes.data.hasOwnProperty("type"))
				{
					xml.Attrib("label", "money");
				}
				
				else if (result[obj].nodes.data.hasOwnProperty("addr"))
				{
					xml.Attrib("label", "address");				
				}
				
				else
				{
					xml.Attrib("label", "owner");
				}
				
				xml.BeginNode("attvalues");
				
				for (prop in result[obj].nodes.data)
				{
					xml.BeginNode("attvalue");
					xml.Attrib("for", nodeAttr[prop].toString());
					xml.Attrib("value", result[obj].nodes.data[prop].toString());
					xml.EndNode();				
				}			
				xml.EndNode();
				
				xml.EndNode();	
			}	
		}		
		xml.EndNode();		
		
		// Begin edges
		xml.BeginNode("edges");
		for (obj in result)
		{
			if (result[obj].edges !== undefined)
			{
				xml.BeginNode("edge");
				xml.Attrib("id", result[obj].edges.self.match(/\/relationship\/(.*)/)[1].toString());
				xml.Attrib("label", result[obj].edges.type);
				
				if (result[obj].edges.type === 'same_owner')
				{
					xml.Attrib("type", "undirected");
				}
				
				else
				{
					xml.Attrib("type", "directed");
				}
				xml.Attrib("source", result[obj].edges.start['_data'].self.match(/\/node\/(.*)/)[1].toString());
				xml.Attrib("target", result[obj].edges.end['_data'].self.match(/\/node\/(.*)/)[1].toString());
				xml.BeginNode("attvalues");
				for (prop in result[obj].edges.data)
				{
					xml.BeginNode("attvalue");
					xml.Attrib("for", edgeAttr[prop].toString());
					xml.Attrib("value", result[obj].edges.data[prop].toString());
					xml.EndNode();	
				}
				xml.EndNode();
				xml.EndNode();
			}			
		}
		
		xml.EndNode();	
		xml.EndNode();
		xml.EndNode();

		return xml.ToString();
	}
}

// Launch server
app.listen(process.env.PORT || 8080);
console.log("Express server listening on port %d in %s mode", app.address().port, app.settings.env);
