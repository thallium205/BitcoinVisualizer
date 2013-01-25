var request = require('request');
var loggly = require('loggly')
var config = { subdomain: process.env.logsubdomain,  auth: { username: process.env.loguser, password: process.env.logpass }};
var client = loggly.createClient(config);
var logToken = process.env.logtoken;

exports.idIn = function(req, res){
	client.log(logToken, 'Fetch incoming transactions by Owner Id -> ' + req.params.id); 
	var r = request({	url: 'http://localhost:7474/db/data/cypher',
						method: 'post',
						headers: {'X-Stream': true},
						body: {	query: 'START owner = node(' + req.params.id + ') MATCH owner <- [from:transfers] - sender RETURN id(sender) AS sender, from.time? AS time, from.value AS amount, ID(owner) as receiver', 
								params: {}},
						json: true
					});
	req.pipe(r).pipe(res);	
};

exports.idOut = function(req, res){
	client.log(logToken, 'Fetch outgoing transactions by Owner Id -> ' + req.params.id); 
	var r = request({	url: 'http://localhost:7474/db/data/cypher',
						method: 'post',
						headers: {'X-Stream': true},
						body: {	query: 'START owner = node(' + req.params.id + ') MATCH owner - [to:transfers] -> receiver RETURN id(owner) AS sender, to.time? AS time, to.value AS amount, ID(receiver) as receiver', 
								params: {}},
						json: true
					});
	req.pipe(r).pipe(res);	
};

exports.addrIn = function(req, res){
	client.log(logToken, 'Fetch incoming transactions by Address -> ' + req.params.addr);
	var r = request({	url: 'http://localhost:7474/db/data/cypher',
						method: 'post',
						headers: {'X-Stream': true},
						body: {	query: 'START addr = node:owned_addr_hashes(owned_addr_hash={addr}) MATCH addr <- [:owns] - owner <- [from:transfers] - sender RETURN id(sender) AS sender, from.time? AS time, from.value AS amount, ID(owner) as receiver', 
								params: {addr: req.params.addr}},
						json: true
					});
	req.pipe(r).pipe(res);	
};

exports.addrOut = function(req, res){
	client.log(logToken, 'Fetch outgoing transactions by Address -> ' + req.params.addr);
	var r = request({	url: 'http://localhost:7474/db/data/cypher',
						method: 'post',
						headers: {'X-Stream': true},
						body: {	query: 'START addr = node:owned_addr_hashes(owned_addr_hash={addr}) MATCH addr <- [:owns] - owner - [to:transfers] -> receiver RETURN id(owner) AS sender, to.time? AS time, to.value AS amount, ID(receiver) as receiver', 
								params: {addr: req.params.addr}},
						json: true
					});
	req.pipe(r).pipe(res);	
};