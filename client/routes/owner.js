var request = require('request');

exports.id = function(req, res){
	console.log('Fetch gexf graph by Owner Id -> ' + req.params.id); 
	if (req.params.format.toLowerCase() === 'gexf') {
		req.pipe(request('http://localhost:7475/owner/gexf?ownerId=' + req.params.id)).pipe(res)
	} else {
		res.send({error: 'Unsupported filetype specified'});
	}
};

exports.addr = function(req, res){
	console.log('Fetch gexf graph by Address -> ' + req.params.addr);
	if (req.params.format.toLowerCase() === 'gexf') {
		req.pipe(request('http://localhost:7475/owner/gexf?addr=' + req.params.addr)).pipe(res)	
	} else {
		res.send({error: 'Unsupported filetype specified'});
	}	
};