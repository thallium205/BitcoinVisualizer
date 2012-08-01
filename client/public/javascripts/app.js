function app()
{
	var width = $(window).width(),
	height = $(window).height();	

	var svg = d3.select("body").append("svg")
		.attr("width", '100%')
		.attr("height", '89%')
		.attr("viewBox", "0 0 100% 100%");

	svg.append("svg:defs").selectAll("marker").data(["succeeds", "from", "received", "sent", "redeemed", "same_owner", "owns", "transfers"]).enter().append("svg:marker").attr("id", String).attr("viewBox", "0 -5 10 10").attr("refX", 25).attr("refY", -1.5).attr("markerWidth", 6).attr("markerHeight", 6).attr("orient", "auto").append("svg:path").attr("d", "M0,-5L10,0L0,5");

	var force = d3.layout.force()
		.gravity(.05)
		.distance(150)
		.charge(-400)
		.size([width, height]);

	// Data structures
	var nodes = {}; 
	var links = {};	
	var nodeReqs = []; // Used to determine when to select and unselect nodes from the graph

	var query = function(val)
	{	
		$("#btnSubmit").button('loading');
		if (val.length == 64)
		{		
			$.getJSON("http://localhost:3000/api/block/hash/" + val + ".json", function(graph)
			{				
				show(graph);
				$("#btnSubmit").button('complete');
			});		

			/*
			$.getJSON("http://localhost:3000/api/trans/" + val + ".json", function(graph)
			{				
				show(graph);
				$("#btnSubmit").button('complete');
			});	
			*/
		}

		else if (val.length > 30 && val.length < 36)
		{
			$.getJSON("http://localhost:3000/api/addr/" + val + ".json", function(graph)
			{				
				show(graph);
				$("#btnSubmit").button('complete');
			});	

			$.getJSON("http://localhost:3000/api/owns/" + val + ".json", function(graph)
			{				
				show(graph);
				$("#btnSubmit").button('complete');
			});				
		}

		else if (val.indexOf(".") != -1)
		{
			$.getJSON("http://localhost:3000/api/ipv4/" + val + ".json", function(graph)
			{				
				show(graph);
				$("#btnSubmit").button('complete');
			});
		}

		else
		{			
			alert("Invalid input");
			$("#btnSubmit").button('complete');
		}
	};
	
	// When a user clicks on a node
	var nodeQuery = function(index)
	{	
		var nodeIndex = $.inArray(index, nodeReqs);
		// Add the index to the node requests array if it hasnt been requested before
		if (nodeIndex === -1)
		{
			$("#btnSubmit").button('loading');
			nodeReqs.push(index);	
			$.getJSON("http://localhost:3000/api/node/" + index + ".json", function(graph)
			{
				show(graph);
				$("#btnSubmit").button('complete');
			});	
		}	
		
		// A user is unselecting a node they previously requested.  Lets remove it and all vertices/edges associated with it. TODO
		else
		{
			var node = null;
			// Find the node itself	
			for (n in nodes)
			{	
				if (nodes[n].name === index)
				{
					node = nodes[n];
					break;
				}
			}
			
			// Find the relationship to any perviously selected node
			/*
			var nodeRels = [];
			for (selfNode in userReqs)
			{				
				for (link in links)
				{
					if (links[link].source.name === selfNode.name || links[link].target.name === selfNode.name)
					{
						// We have found the relationship that connects
						nodeRels.push(links[link]);						
					}
				}
			}
			*/
			
			
			var nodesToDelete = [];
			nodesToDelete.push(node);
			
			while(nodesToDelete.length > 0)
			{
				var tempNode = nodesToDelete.pop();
				for (link in links)
				{
					if (links[link].source.name === tempNode.name || links[link].target.name === tempNode.name)
					{
						// This link is connected to the node
						for (n in nodes)
						{
							// We add a node to be removed
							if (nodes[n].name === links[link].target.name || nodes[n].name === links[link].source.name)
							{
								// We do not want to add a user selected node to the list of nodes to delete
								var userNode = false;
								for (nd in nodes)
								{
									if (nodes[nd].name === nodeReqs)
									{
										userNode = true;
									}
								}
								
								if (!userNode)
								{
									nodesToDelete.push(nodes[n]);	
									userNode = false;
								}
							}
						}
						
						delete links[link];						
					}
				}
				
				// Delete the nodes			
				for (n in nodes)
				{
					if (nodes[n].name === tempNode.name)
					{
						alert("deleting");
						delete nodes[n];
					}
				}				
			}		
			
			// Delete the node itself
			// delete nodes[node];
			
			// Remove the node from the node requests array
			nodeReqs.splice(nodeIndex, 1);	
			
			// Update the graph
			show(getEmptyGraph());
		}
	}

	var show = function(json)
	{		
		// We group relationships by type if their type size is greater than 10 then we combine them //
		// TODO - This wont work with the source or target are not the same!  This will happen when querying addresses.  >.<
		var succeeds = [];
		var from = [];
		var received = [];
		var sent = [];
		var redeemed = [];
		var same_owner = [];
		var owns = [];
		var transfers = [];
		var identifies = [];
		for (var edge in json.graph.edges.edge)
		{
			// Store each relationship type
			switch(json.graph.edges.edge[edge]['@'].label)
			{
				case 'succeeds':						
					succeeds.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;
				case 'from':
					from.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;
				case 'received':
					received.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;
				case 'sent':
					sent.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;
				case 'redeemed':
					redeemed.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;
				case 'same_owner':
					same_owner.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;					
				case 'owns':
					owns.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;					
				case 'transfers':
					transfers.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;	
				case 'identifies':
					identifies.push({"source": json.graph.edges.edge[edge]['@'].source, "target": json.graph.edges.edge[edge]['@'].target, "data":json.graph.edges.edge[edge]});
					break;	
			} 
		}	
		
		if (succeeds.length > 10)
		{
			json = groupJson(json, succeeds);
		}
		
		if (from.length > 10)
		{
			json = groupJson(json, from);
		}
		
		if (received.length > 10)
		{
			json = groupJson(json, received);
		}
		
		if (sent.length > 10)
		{
			json = groupJson(json, sent);
		}

		if (redeemed.length > 10)
		{
			json = groupJson(json, redeemed);
		}
		
		if (same_owner.length > 10)
		{
			json = groupJson(json, same_owner);
		}
		
		if (owns.length > 10)
		{
			json = groupJson(json, owns);
		}
		
		if (transfers.length > 10)
		{
			json = groupJson(json, transfers);
		}
		
		if (identifies.length > 10)
		{
			json = groupJson(json, identifies);
		}
		// End grouping //
		
		// Begin normal linking		
		for (var node in json.graph.nodes.node)
		{			
			if (!(json.graph.nodes.node[node]['@'].id in nodes))
			{
				nodes[json.graph.nodes.node[node]['@'].id] = {"name": json.graph.nodes.node[node]['@'].id, "data": json.graph.nodes.node[node]};
			}			
		}

		for (var edge in json.graph.edges.edge)
		{	
			links[json.graph.edges.edge[edge]['@'].id] = {"source": nodes[json.graph.edges.edge[edge]['@'].source], "target": nodes[json.graph.edges.edge[edge]['@'].target], "data":json.graph.edges.edge[edge]};				
		}	
		
		// Add the group node		
		force.nodes(d3.values(nodes))
			.links(d3.values(links))
			.start();	

		// Compute the data join	
		var link = svg.selectAll(".link")
			.data(force.links(), function(d)
			{
				// provides a unique index for data
				return d.data['@'].id;
			});

		// Add any incoming links
		link.enter().append("line");

		// Remove any outgoing links
		link.exit().remove();

		// Compute new attributes for entering and updating links
		link.attr("class", function(d)
		{
			return "link " + d.data['@'].label;
		})
		.attr("marker-end", function(d)
		{
			return "url(#" + d.data['@'].label + ")";
		});

		// Compute the data join
		var node = svg.selectAll(".node")
		.data(force.nodes(), function(d)
		{
			// provides a unique index for data
			return d.name
		});

		// Add any incoming nodes
		node.enter().append("g");

		// Remove any outgoing nodes
		node.exit().remove();

		// Compute new attributes for entering and updating nodes
		node.attr("class", "node")	
		//.each(function(d)
		//{
			// Popover content
		//	$(this).popover({'title': d.data['@'].label.charAt(0).toUpperCase() + d.data['@'].label.slice(1), 'content': function()
		//		{
		//			var content = [];
		//			for (var val in d.data.attvalues.attvalue)
		//			{
		//				content.push(json.graph.attributes[0].attribute[d.data.attvalues.attvalue[val]["@"].for]["@"].title + ": " + d.data.attvalues.attvalue[val]["@"].value);
		//			}
		//			return content.join("\n");
		//		}
		//	})
		//})
		
		// On node click
		.on("click", function(d)
		{
			// If it is a group node we have to list what the user wants to show!
			if (d.data['@'].label === 'group')
			{	
				$('#tblGroup tbody tr').click( function(e) 
				{
					alert("clicked");
					$(this).toggleClass('row_selected');
				});
				
				$('#tblGroup').dataTable( 
				{
					"aaData": [
						/* Reduced data set */
						[ "Trident", "Internet Explorer 4.0", "Win 95+", 4, "X" ],
						[ "Trident", "Internet Explorer 5.0", "Win 95+", 5, "C" ],
						[ "Trident", "Internet Explorer 5.5", "Win 95+", 5.5, "A" ],
						[ "Trident", "Internet Explorer 6.0", "Win 98+", 6, "A" ],
						[ "Trident", "Internet Explorer 7.0", "Win XP SP2+", 7, "A" ],
						[ "Gecko", "Firefox 1.5", "Win 98+ / OSX.2+", 1.8, "A" ],
						[ "Gecko", "Firefox 2", "Win 98+ / OSX.2+", 1.8, "A" ],
						[ "Gecko", "Firefox 3", "Win 2k+ / OSX.3+", 1.9, "A" ],
						[ "Webkit", "Safari 1.2", "OSX.3", 125.5, "A" ],
						[ "Webkit", "Safari 1.3", "OSX.3", 312.8, "A" ],
						[ "Webkit", "Safari 2.0", "OSX.4+", 419.3, "A" ],
						[ "Webkit", "Safari 3.0", "OSX.4+", 522.1, "A" ]
					],
					"aoColumns": [
						{ "sTitle": "Engine" },
						{ "sTitle": "Browser" },
						{ "sTitle": "Platform" },
						{ "sTitle": "Version" },
						{ "sTitle": "Grade"}
					]
				} );    

				$('#groupModal').modal('show');
			}
			
			else
			{			
				nodeQuery(d.name);
			}
		})
		.call(force.drag);		

		node.append("svg:path")
			.attr("d", function(d)
			{
				// Return shape based upon node type
				switch(d.data['@'].label)
				{
					case 'block':						
						return "M15.5,3.029l-10.8,6.235L4.7,21.735L15.5,27.971l10.8-6.235V9.265L15.5,3.029zM15.5,7.029l6.327,3.652L15.5,14.334l-6.326-3.652L15.5,7.029zM24.988,10.599L16,15.789v10.378c0,0.275-0.225,0.5-0.5,0.5s-0.5-0.225-0.5-0.5V15.786l-8.987-5.188c-0.239-0.138-0.321-0.444-0.183-0.683c0.138-0.238,0.444-0.321,0.683-0.183l8.988,5.189l8.988-5.189c0.238-0.138,0.545-0.055,0.684,0.184C25.309,10.155,25.227,10.461,24.988,10.599z";
						break;
					case 'transaction':
						return "M21.786,12.876l7.556-4.363l-7.556-4.363v2.598H2.813v3.5h18.973V12.876zM10.368,18.124l-7.556,4.362l7.556,4.362V24.25h18.974v-3.501H10.368V18.124z";
						break;
					case 'money':
						return "M16,1.466C7.973,1.466,1.466,7.973,1.466,16c0,8.027,6.507,14.534,14.534,14.534c8.027,0,14.534-6.507,14.534-14.534C30.534,7.973,24.027,1.466,16,1.466z M17.255,23.88v2.047h-1.958v-2.024c-3.213-0.44-4.621-3.08-4.621-3.08l2.002-1.673c0,0,1.276,2.223,3.586,2.223c1.276,0,2.244-0.683,2.244-1.849c0-2.729-7.349-2.398-7.349-7.459c0-2.2,1.738-3.785,4.137-4.159V5.859h1.958v2.046c1.672,0.22,3.652,1.1,3.652,2.993v1.452h-2.596v-0.704c0-0.726-0.925-1.21-1.959-1.21c-1.32,0-2.288,0.66-2.288,1.584c0,2.794,7.349,2.112,7.349,7.415C21.413,21.614,19.785,23.506,17.255,23.88z";
						break;
					case 'address':
						return "M28.516,7.167H3.482l12.517,7.108L28.516,7.167zM16.74,17.303C16.51,17.434,16.255,17.5,16,17.5s-0.51-0.066-0.741-0.197L2.5,10.06v14.773h27V10.06L16.74,17.303z";
						break;
					case 'owner':
						return "M20.771,12.364c0,0,0.849-3.51,0-4.699c-0.85-1.189-1.189-1.981-3.058-2.548s-1.188-0.454-2.547-0.396c-1.359,0.057-2.492,0.792-2.492,1.188c0,0-0.849,0.057-1.188,0.397c-0.34,0.34-0.906,1.924-0.906,2.321s0.283,3.058,0.566,3.624l-0.337,0.113c-0.283,3.283,1.132,3.68,1.132,3.68c0.509,3.058,1.019,1.756,1.019,2.548s-0.51,0.51-0.51,0.51s-0.452,1.245-1.584,1.698c-1.132,0.452-7.416,2.886-7.927,3.396c-0.511,0.511-0.453,2.888-0.453,2.888h26.947c0,0,0.059-2.377-0.452-2.888c-0.512-0.511-6.796-2.944-7.928-3.396c-1.132-0.453-1.584-1.698-1.584-1.698s-0.51,0.282-0.51-0.51s0.51,0.51,1.02-2.548c0,0,1.414-0.397,1.132-3.68H20.771z";
						break;
					case 'group':
						return "M28.625,26.75h-26.5V8.375h1.124c1.751,0,0.748-3.125,3-3.125c3.215,0,1.912,0,5.126,0c2.251,0,1.251,3.125,3.001,3.125h14.25V26.75z";
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
				// Return label based upon node type
				switch(d.data['@'].label)
				{
					case 'block':						
						return d.data.attvalues.attvalue[0]['@'].value;
						break;
					case 'transaction':
						return d.data.attvalues.attvalue[4]['@'].value;
						break;
					case 'money':
						return d.data.attvalues.attvalue[1]['@'].value;
						break;
					case 'address':
						return d.data.attvalues.attvalue['@'].value;
						break;
					case 'owner':
						// TODO.  We need to find the edgtes connected to this vertices witht the label of identifies and display that, else, owner
						return "owner";
						break;
					case 'group':
						return d.data['@'].group.nodes.length + " Transactions";  // TODO
						break;
					default:
						return 'unknown';
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
	
	// Helper functions
	// Selected row from the data table
	function fnGetSelected( oTableLocal )
	{
		return oTableLocal.$('tr.row_selected');
	}
	
	// Returns an empty graph object
	function getEmptyGraph()
	{
		var emptyGraph = {};
		emptyGraph.graph = {};
		emptyGraph.graph.edges = {};
		emptyGraph.graph.edges.edge = [];		
		emptyGraph.graph.nodes = {};
		emptyGraph.graph.nodes.node = [];
		return emptyGraph;
	}
	
	// Groups by link type
	function groupJson(json, arr)
	{
		var group = {};
		group.nodes  = [];
		group.links = arr;
		
		// We find the directionality.  This is to determine directionality since all these relationships must be pointing either to or from the exact same node.
		// We can make this assumption since we are only parsing newly added data to the graph which will be at most a length of one depth, not the entire graph!
		var isIncoming;
		var nodeId;
		if (arr[0].source === arr[1].source)
		{
			isIncoming = false;
			nodeId = arr[0].source;
		}
		
		else if (arr[0].target === arr[1].target)
		{
			isIncoming = true;
			nodeId = arr[0].target
		}
		
		// Create errors
		else
		{
			isIncoming = null;
			nodeId = null;
		}	
			
		// We collect all the nodes that are attached to the links and add them to the group.  Then we remove them
		for (var link in group.links)
		{
			for (var node in json.graph.nodes.node)
			{		
				// We do not delete the last node in the list as this is the node that the user has selected! (Kinda hackey TODO)
				if (parseInt(node) !== json.graph.nodes.node.length - 1 && (json.graph.nodes.node[node]['@'].id == group.links[link].source || json.graph.nodes.node[node]['@'].id == group.links[link].target))
				{
					// Add the node to the group
					group.nodes.push({"name": json.graph.nodes.node[node]['@'].id, "data": json.graph.nodes.node[node]});
					
					// Remove the node from the json blob
					delete json.graph.nodes.node[node];
					
					// Remove the actual link from the json blob.  We cannot use our link I think because its not the same object in memory so we have to traverse the original blob
					for (edgeToDelete in json.graph.edges.edge)
					{
						if (json.graph.edges.edge[edgeToDelete]['@'].id === group.links[link].data['@'].id)
						{
							delete json.graph.edges.edge[edgeToDelete];
							break;
						}
					}				
				}			
			}
		}
		
		// Create our group node
		var groupNode = {'name': Math.floor((Math.random()*1000000000)+10000000).toString(), 'data': {'@': {'label': 'group', 'group': group}}};
		
		// We need to reinsert the object back into the raw json because d3js does some kind of object memeory reference call and it considers our nodeToLink to be a different object
		// than what is pulled in the json object in normal linking
		if (isIncoming)
		{
			json.graph.edges.edge.push({'@': {'id': Math.floor((Math.random()*1000000000)+10000000), 'label': 'from', 'source': groupNode.name, 'target': nodeId}}); // TODO
		}
		
		else
		{
			json.graph.edges.edge.push({'@': {'id': Math.floor((Math.random()*1000000000)+10000000), 'label': 'from', 'source': nodeId, 'target': groupNode.name}}); // TODO
		}		
		
		// Insert our group node and link into the data structure
		nodes[groupNode.name] = groupNode;
	
		// Return the stripped JSON for further processing
		return json;
	}
	
	// Returns the neighbor nodes given a node
	/*
	function getNeighbors(var node)
	{
		var neighborNodes = [];
		for (link in links)
		{
			if (links[link].source.name === node.name || links[link].target.name === node.name)
			{
				// This link is connected to the node
				for (n in nodes)
				{
					if (nodes[n].name === links[link].target.name)
					{ 								
						neighborNodes.push(node[n]);
					}
				}
			}
		}
		
		return neighborNodes;
	}
	*/

	// Register listeners
	$("#search").submit(function()
	{
		query($("input:first").val());
		return false;
	});

	$("#btnSubmit").click(function() 
	{
		query($("input:first").val());
		return false;
	});
	
	$("#btnClear").click(function()
	{
		nodes = {};
		links = {};
		show(getEmptyGraph());
		return false;
	});
};