# BlockViewer.com

## Description
Block Viewer visualizes the Bitcoin block chain by building an ownership network on top of the underlying network of transactions and presenting a web-enabled user interface to display the visualization results. 

## Dependencies
### Server:
Java 1.8
Neo4j Enterprise
Gephi Toolkit
OpenVPN (For replication) 

### Client:
Node.js
MySQL
OpenVPN (For replication)

## Methodology
In order to create what is seen at http://blockviewer.com, Block Viewer performs the following:
1. It downloads the entire raw Bitcoin block chain from blockchain.info API.  
2. It constructs the low level graph structure by linking outputs to their redeemed inputs and associating them to their respective block:
![Low Level Chain](http://toolongdidntread.com/wp-content/uploads/2012/04/screen.png)
3. It finds transactions that redeemed several addresses and creates a "same_owner" edge between them.  Then, traverses each same_owner cluster and creates an owner vertices with owns edges pointing to each redeemed address:
![Owner Identification](http://toolongdidntread.com/wp-content/uploads/2012/05/Connected-Component.png)
4. It finds related transactions between owners and creates a transfers edge between owners, creating the high level owner network:
![Ownership Network](http://i.imgur.com/hfOxS.png)
5. Using each owner's identified addresses, it scrapes several websites (including BitcoinTalk and Bitcoin-OTC) and sees if an owner has been explicitly identified.  If so, it creates an "identifies" edge between the address and owner, as seen in the screenshot in step 3.
6. Once the ownership network is constructed, the application exports each owner to a graph, where each component of the graph represents the following:
   * Node Label - If an owner has been identified to a real world entity such as a bitcointalk.org username, nodes will be tagged with this information under Alias. Otherwise, they will be tagged with a unique owner identifier.
   * Node Size - The amount of inbound/outbound transactions that have been sent to and from a particular owner will be represented with how large a node is relative to the other nodes in the current graph.
   * Node Color - Hotter colors will represent recent activity sent from the owner while a cooler color will represent older activity.
   * Edge Color - Hotter colors will represent recent transactions while cooler colors will represent older ones.
   * Edge Direction - Following an edge in a clockwise direction will separate inbound vs outbound transactions.
7. It also exports time-division subsections of the network by day, giving users the ability to look at the behavior of the ownership network by time-span instead of by owner.
8. The front end consumes the generated graphs, displaying it to the user.  The front-end server uses Neo4j replication to keep the cypher queries fast, and uses the backend's mysql database to fetch the owner graphs

## Usage
Several command line arguments are passed into blockviewer.  To let it build a full, application ready use as seen at blockviewer.com, the backend server would use:
```java java -jar -Xmx2048m BlockViewer.jar -dbPath ../graph.db/ -configPath ../neo4j.properties -validate false -low -high -scraper -exporter
where:
* dbPath = The path to the Neo4j graph database.
* configPath = The path to the Neo4j configuration settings (used to set-up master/slave replication)
* validate = Toggle the verifier which checks if the local json files from blockchain.info API form a complete blockchain.
* low = Builds the low level block chain structure.
* high = Builds the high level ownership structure
* scraper = Runs the scraper which attempts to associate bitcoin addresses to real world entities.
* exporter = Exports a time based analysis and owner network of the entire block chain to mysql
The client only needs to be passed:
```java java -jar -Xmx2048m BlockViewer.jar -dbPath graph.db/ -configPath neo4j.properties -client
where
* client = Just enables the Neo4j embedded database, and given the config settings, receives data from the server.
BlockViewer's backend is fully recoverable (except during a download from the API TODO), which means it can be stopped at resumed where it left at any time during this process without any issue.


## Issues
Issues are being tracked in Github.

## Interested? Ideas?
I'm here to answer any and all questions and ideas!  If you would like to contribute to this project, please let me know!  It's very much in its infancy, there is lots of room for improvement.
