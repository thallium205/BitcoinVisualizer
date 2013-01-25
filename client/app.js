var express = require('express')
  , routes = require('./routes')
  , owner = require('./routes/owner')
  , owns = require('./routes/owns')
  , trans = require('./routes/trans')
  , time = require('./routes/time')
  , http = require('http')
  , path = require('path');

var app = express();

app.configure(function(){
  app.set('port', process.env.PORT || 8080);
  app.set('views', __dirname + '/views');
  app.set('view engine', 'jade');
  app.use(express.favicon());
  app.use(express.logger('dev'));
  app.use(express.bodyParser());
  app.use(express.methodOverride());
  app.use(app.router);
  app.use(express.static(path.join(__dirname, 'public')));
});

app.configure('development', function(){
  app.use(express.errorHandler());
});

app.get('/', routes.index);

app.get('/owner/id/:id.:format', owner.id);
app.get('/owner/addr/:addr.:format', owner.addr);

app.get('/owns/id/:id', owns.id);
app.get('/owns/addr/:addr', owns.addr);

app.get('/trans/id/:id/in', trans.idIn);
app.get('/trans/id/:id/out', trans.idOut);
app.get('/trans/addr/:addr/in', trans.addrIn);
app.get('/trans/addr/:addr/out', trans.addrOut);

app.get('/time/day/:day.:format', time.day);

http.createServer(app).listen(app.get('port'), function(){
  console.log("Express server listening on port " + app.get('port'));
});