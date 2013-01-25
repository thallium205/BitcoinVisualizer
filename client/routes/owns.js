var request = require('request');
var loggly = require('loggly')
var config = { subdomain: process.env.logsubdomain,  auth: { username: process.env.loguser, password: process.env.logpass }};
var client = loggly.createClient(config);
var logToken = process.env.logtoken;

exports.id = function(req, res){
	client.log(logToken, 'Fetch addresses by Owner Id -> ' + req.params.id);
	var r = request({	url: 'http://localhost:7474/db/data/cypher',
						method: 'post',
						headers: {'X-Stream': true},
						body: {	query: 'START owner = node(' + req.params.id + ') MATCH owner - [:owns] -> owned RETURN owned.addr AS address', 
								params: {}},
						json: true
					});
	req.pipe(r).pipe(res);	
};

exports.addr = function(req, res){
	client.log(logToken, 'Fetch addresses by Address -> ' + req.params.addr);
	var r = request({	url: 'http://localhost:7474/db/data/cypher',
						method: 'post',
						headers: {'X-Stream': true},
						body: {	query: 'START addr = node:owned_addr_hashes(owned_addr_hash={addr}) MATCH addr <- [:owns] - owner - [:owns] -> owned RETURN owned.addr AS address', 
								params: {addr: req.params.addr}},
						json: true
					});
	req.pipe(r).pipe(res);	
};