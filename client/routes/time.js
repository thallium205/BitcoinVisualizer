var mysql = require('mysql');
var connection = mysql.createConnection({
  host     : 'localhost',
  user     : 'root',
  password : 'webster',
  database : 'blockviewer'
});
connection.connect();	

exports.day = function(req, res){	
	if (req.params.format.toLowerCase() === 'gexf') {
		connection.query('SELECT gexf FROM day WHERE graphTime <= ? ORDER BY graphTime desc LIMIT 1', [req.params.day], function(e, r) {
			if (e) {
				console.log(e);
				es.send({error: 'An error occured with the database'});
				return;
			}
			if (r.length !== 1) {
				console.log('Result returned something other than 1.');
				es.send({error: 'Date not found.'});
				return;
			}
			res.send(r.pop().gexf);
		});	
	} else {
		res.send({error: 'Unsupported filetype specified'});
	}
};