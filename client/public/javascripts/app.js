function app()
{
	var width = $(window).width(),
	height = $(window).height();	
		
	var svg = d3.select("body").append("svg")
		.attr("width", width)
		.attr("height", height);
		
	var force = d3.layout.force()
		.gravity(.05)
		.distance(100)
		.charge(-100)
		.size([width, height]);	
	
	$("#search").submit(function()
	{
		var val = $("input:first").val();
		
		if (val.length == 64)
		{
			$.getJSON("http://localhost:3000/api/block/hash/" + val + ".json", function(graph)
			{
				show(graph);
			});			
		}
		
		else
		{
			alert("die");
		}
		
		return false;	
	});	

	var show = function(json)
	{			
		var nodes = {}; 
		var links = [];	

		/*
		nodes[1] = {"name": 1};
		nodes[2] = {"name": 2};			
		links.push({"source": nodes[1], "target": nodes[2]});
		*/			
			
		for (var node in json.graph.nodes.node)
		{
			nodes[json.graph.nodes.node[node]['@'].id] = {"name":json.graph.nodes.node[node]['@'].id};				
		}
			
		for (var edge in json.graph.edges.edge)
		{			
			links.push({"source": nodes[json.graph.edges.edge[edge]['@'].source], "target": nodes[json.graph.edges.edge[edge]['@'].target]});				
		}
			
		force
			.nodes(d3.values(nodes))
			.links(links)
			.start();
			
		var link = svg.selectAll(".link")
			.data(force.links())
			.enter().append("line")
			.attr("class", "link");			

		var node = svg.selectAll(".node")
		.data(force.nodes())
		.enter().append("g")
		.attr("class", "node")
		.call(force.drag);

		node.append("image")
			.attr("xlink:href", "https://github.com/favicon.ico")
			.attr("x", -8)
			.attr("y", -8)
			.attr("width", 16)
			.attr("height", 16);

		node.append("text")
			.attr("dx", 12)
			.attr("dy", ".35em")
			.text(function(d) { return d.name });				
				
		force.on("tick", function()
		{
			link
				.attr("x1", function(d) { return d.source.x; })
				.attr("y1", function(d) { return d.source.y; })
				.attr("x2", function(d) { return d.target.x; })
				.attr("y2", function(d) { return d.target.y; });
					
			node
				.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
		});
	};
};