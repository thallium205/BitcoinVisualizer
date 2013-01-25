var mysql = require('mysql');
var loggly = require('loggly')
var config = { subdomain: process.env.logsubdomain,  auth: { username: process.env.loguser, password: process.env.logpass }};
var client = loggly.createClient(config);
var logToken = process.env.logtoken;

var connection = mysql.createConnection({
  host     : process.env.sqlhost,
  user     : process.env.sqluser,
  password : process.env.sqlpass,
  database : process.env.sqldatabase
});
connection.connect();	

exports.day = function(req, res){	
	client.log(logToken, 'Fetch gexf day graph by Unix Time -> ' + req.params.day); 
	if (req.params.format.toLowerCase() === 'gexf') {
		connection.query('SELECT gexf FROM day WHERE graphTime <= ? ORDER BY graphTime desc LIMIT 1', [req.params.day], function(e, r) {
			if (e) {
				client.log(logToken, e);
				res.send({error: 'An error occured with the database'}, 500);
				return;
			}
			if (r.length !== 1) {
				client.log(logToken, 'Result returned something other than 1.');
				res.send({error: 'Date not found.'}, 500);
				return;
			}
			res.send(r.pop().gexf);
		});	
	} else {
		res.send({error: 'Unsupported filetype specified'}, 500);
	}
};