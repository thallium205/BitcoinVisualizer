var request = require('request');

exports.id = function(req, res){
	console.log('Fetch addresses by Owner Id -> ' + req.params.id);
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
	console.log('Fetch addresses by Address -> ' + req.params.addr);
	var r = request({	url: 'http://localhost:7474/db/data/cypher',
						method: 'post',
						headers: {'X-Stream': true},
						body: {	query: 'START addr = node:owned_addr_hashes(owned_addr_hash={addr}) MATCH addr <- [:owns] - owner - [:owns] -> owned RETURN owned.addr AS address', 
								params: {addr: req.params.addr}},
						json: true
					});
	req.pipe(r).pipe(res);	
};