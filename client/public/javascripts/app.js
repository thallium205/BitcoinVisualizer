function app()
{
	var width = $(window).width(),
	height = $(window).height();	
		
	var svg = d3.select("body").append("svg")
		.attr("width", width)
		.attr("height", height);
		
	svg.append("svg:defs").selectAll("marker")
		.data(["succeeds", "from"])
		.enter().append("svg:marker")
		.attr("id", String)
		.attr("viewBox", "0 -5 10 10")
		.attr("refX", 15)
		.attr("refY", -1.5)
		.attr("markerWidth", 12)
		.attr("markerHeight", 12)
		.attr("orient", "auto")
		.append("svg:path")
		.attr("d", "M0,-5L10,0L0,5");
		
	var force = d3.layout.force()
		.gravity(.05)
		.distance(300)
		.charge(-400)
		.size([width, height]);	
	
	// Register search listener
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
			nodes[json.graph.nodes.node[node]['@'].id] = {"name":json.graph.nodes.node[node]['@'].id, "data":json.graph.nodes.node[node]};
		}
			
		for (var edge in json.graph.edges.edge)
		{		
			links.push({"source": nodes[json.graph.edges.edge[edge]['@'].source], "target": nodes[json.graph.edges.edge[edge]['@'].target], "data":json.graph.edges.edge[edge]});				
		}
			
		force.nodes(d3.values(nodes))
			.links(links)
			.start();
			
		var link = svg.selectAll(".link")
			.data(force.links())
			.enter().append("line")
			.attr("class", function(d)
			{
				return "link " + d.data['@'].label;
			})
			.attr("marker-end", function(d)
			{
				return "url(#" + d.data['@'].label + ")";
			});

		var node = svg.selectAll(".node")
		.data(force.nodes(), function(d)
		{
			return d.name;
		})
		.enter().append("g")
		.attr("class", "node")
		.call(force.drag);
		

		node.append("svg:path")
			.attr("d", function(d)
			{
				switch(d.data['@'].label)
				{
					case 'block':						
						return "M15.5,3.029l-10.8,6.235L4.7,21.735L15.5,27.971l10.8-6.235V9.265L15.5,3.029zM15.5,7.029l6.327,3.652L15.5,14.334l-6.326-3.652L15.5,7.029zM24.988,10.599L16,15.789v10.378c0,0.275-0.225,0.5-0.5,0.5s-0.5-0.225-0.5-0.5V15.786l-8.987-5.188c-0.239-0.138-0.321-0.444-0.183-0.683c0.138-0.238,0.444-0.321,0.683-0.183l8.988,5.189l8.988-5.189c0.238-0.138,0.545-0.055,0.684,0.184C25.309,10.155,25.227,10.461,24.988,10.599z";
						break;
					case 'transaction':
						return "M21.786,12.876l7.556-4.363l-7.556-4.363v2.598H2.813v3.5h18.973V12.876zM10.368,18.124l-7.556,4.362l7.556,4.362V24.25h18.974v-3.501H10.368V18.124z";
						break;
					default:
						return "M26.711,14.086L16.914,4.29c-0.778-0.778-2.051-0.778-2.829,0L4.29,14.086c-0.778,0.778-0.778,2.05,0,2.829l9.796,9.796c0.778,0.777,2.051,0.777,2.829,0l9.797-9.797C27.488,16.136,27.488,14.864,26.711,14.086zM14.702,8.981c0.22-0.238,0.501-0.357,0.844-0.357s0.624,0.118,0.844,0.353c0.221,0.235,0.33,0.531,0.33,0.885c0,0.306-0.101,1.333-0.303,3.082c-0.201,1.749-0.379,3.439-0.531,5.072H15.17c-0.135-1.633-0.301-3.323-0.5-5.072c-0.198-1.749-0.298-2.776-0.298-3.082C14.372,9.513,14.482,9.22,14.702,8.981zM16.431,21.799c-0.247,0.241-0.542,0.362-0.885,0.362s-0.638-0.121-0.885-0.362c-0.248-0.241-0.372-0.533-0.372-0.876s0.124-0.638,0.372-0.885c0.247-0.248,0.542-0.372,0.885-0.372s0.638,0.124,0.885,0.372c0.248,0.247,0.372,0.542,0.372,0.885S16.679,21.558,16.431,21.799z";
				}					
			})
			.style("fill", "#000")
			.style("stroke", "none");

		node.append("text")
			.attr("dx", 12)
			.attr("dy", ".35em")
			.text(function(d) 
			{ 
				switch(d.data['@'].label)
				{					
					case 'block':						
						return d.data.attvalues.attvalue[0]['@'].value;
						break;
					case 'transaction':
						return d.data.attvalues.attvalue[4]['@'].value;
						break;
					default:
						return 'Unknown';
				}

			});	
				
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
	
	$("#node").on("hover", function(event)
	{
		alert("hover time");
	});
};