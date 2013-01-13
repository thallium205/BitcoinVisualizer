var request = require('request');

var active = [];

exports.id = function(req, res){
	console.log('Fetch gexf graph by Owner Id -> ' + req.params.id); 
	active.push(new Date().getTime());
	if (req.params.format.toLowerCase() === 'gexf') {
		req.pipe(request('http://localhost:7475/owner/gexf?ownerId=' + req.params.id)).pipe(res)
	} else {
		res.send({error: 'Unsupported filetype specified'});
	}
};

exports.addr = function(req, res){
	console.log('Fetch gexf graph by Address -> ' + req.params.addr);
	active.push(new Date().getTime());
	if (req.params.format.toLowerCase() === 'gexf') {
		req.pipe(request('http://localhost:7475/owner/gexf?addr=' + req.params.addr)).pipe(res)	
	} else {
		res.send({error: 'Unsupported filetype specified'});
	}	
};

exports.status = function(req, res){
	console.log('Fetch status');
	res.send(JSON.stringify(active, null, 4));
};
