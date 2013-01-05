var request = require('request');

exports.id = function(req, res){
	req.pipe(request('http://localhost:7475/owner/gexf?ownerId=' + req.params.id)).pipe(res)	
};
exports.addr = function(req, res){
	req.pipe(request('http://localhost:7475/owner/gexf?addr=' + req.params.addr)).pipe(res)	
};