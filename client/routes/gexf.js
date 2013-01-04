var request = require('request');

exports.id = function(req, res){
	var id = req.params.id;	
	console.log(id);
	req.pipe(request('http://localhost:7475/owner/gexf?ownerId=' + id)).pipe(res)	
};
exports.addr = function(req, res){
	var addr = req.params.addr;
	req.pipe(request('http://localhost:7475/owner/gexf?addr=' + addr)).pipe(res)	
};