var request = require('request');
var mysql = require('mysql');
var connection = mysql.createConnection({
  host     : 'localhost',
  user     : 'root',
  password : 'webster',
  database : 'blockviewer'
});
connection.connect();

exports.id = function(req, res){
	console.log('Fetch gexf graph by Owner Id -> ' + req.params.id); 
	if (req.params.format.toLowerCase() === 'gexf') {	
		connection.query('SELECT gexf FROM owner WHERE ownerId = ? LIMIT 1', [req.params.id], function(e, r) {
			if (e) {
				console.log(e);
				res.send({error: 'An error occured with the database'}, 500);
				return;
			}
			if (r.length !== 1) {
				console.log('Result returned something other than 1.');
				res.send({error: 'Date not found.'}, 500);
				return;
			}
			res.send(r.pop().gexf);
		});
	} else {
		res.send({error: 'Unsupported filetype specified'}, 500);
	}
};

exports.addr = function(req, res){
	console.log('Fetch gexf graph by Address -> ' + req.params.addr);
	if (req.params.format.toLowerCase() === 'gexf') {		
		var r = request({	url: 'http://localhost:7474/db/data/cypher',
							method: 'post',
							body: {	query: 'START addr = node:owned_addr_hashes(owned_addr_hash={addr}) MATCH addr <- [:owns] - owner RETURN ID(owner) AS ownerId', 
									params: {addr: req.params.addr}},
							json: true
						}, 
						function (e, r, b) {
							if (e) {
								console.log(e);
								res.send({error: 'An error occured with the database'}, 500);
								return;
							}
							var idArray = b.data.pop();
							if (idArray.length > 0) {
								var ownerId = idArray.pop();								
								connection.query('SELECT gexf FROM owner WHERE ownerId = ? LIMIT 1', [ownerId], function(e, r) {
								if (e) {
									console.log(e);
									res.send({error: 'An error occured with the database'}, 500);
									return;
								}
								if (r.length !== 1) {
									console.log('Result returned something other than 1.');
									res.send({error: 'An owner has been identified, but a graph has not been calculated.  This is typically a result of owners who are too large to be graphed.'}, 500);
									return;
								}
								res.send(r.pop().gexf);	
								});								
							}
							else {
								res.send({error: 'No owner has been identified by this address... yet.'}, 500);
							}
						});			
	} else {
		res.send({error: 'Unsupported filetype specified'}, 500);
	}	
};