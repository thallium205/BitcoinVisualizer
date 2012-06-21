var application_root = __dirname,
    express = require("express"),
    path = require("path"),
    neo4j = require('neo4j');

// Database
var db = new neo4j.GraphDatabase('http://localhost:7474');

// Config
var app = express.createServer();
app.configure(function () {
  app.use(express.bodyParser());
  app.use(express.methodOverride());
  app.use(app.router);
  app.use(express.static(path.join(application_root, "public")));
  app.use(express.errorHandler({ dumpExceptions: true, showStack: true }));
});

// Homepage Route
app.get('/', function(req, res){
  res.render('index.jade', { title: 'My Site' });
});

// API Route
app.get('/api', function (req, res) {
  res.send('API is running');
});

// API - block/hash
app.get('/api/block/hash/:id', function (req, res) {
  db.query("START block = node:block_hashes(block_hash=\"" + req.params.id + "\") MATCH (block) -- (x) RETURN x", function callback(err, result) {
    if (err) {
        res.send(err);
    } else {
        res.send(result);    // if an object, inspects the object
    }
})});

// Launch server
app.listen(3000, function(){
  console.log("Express server listening on port %d in %s mode", app.address().port, app.settings.env);
});
