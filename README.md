# BlockViewer.com

## Description
Block Viewer visualizes the Bitcoin block chain by building an ownership network on top of the underlying transaction network and presents a web-enabled user interface to display the visualization results. 

![BlockViewer](http://toolongdidntread.com/wp-content/uploads/2013/01/Screen-Shot-2013-01-25-at-9.32.00-AM-1024x572.png)

## Dependencies
### Server:
* Java
* Neo4j Enterprise
* Gephi Toolkit
* OpenVPN (For replication) 

### Client:
* Node.js
* MySQL
* OpenVPN (For replication)

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
6. Once the ownership network is constructed, the application exports each owner to a graph.
7. It also exports time-division subsections of the network by day, giving users the ability to look at the behavior of the ownership network by time-span instead of by owner.
8. The front end consumes the generated graphs, displaying it to the user.  The front-end server uses Neo4j replication to keep the cypher queries fast while the backend is busy, and uses the backend's mysql database to fetch the owner graphs.

Each component of the graph represents the following:
* **Node Label** - If an owner has been identified to a real world entity such as a bitcointalk.org username, nodes will be tagged with this information under Alias. Otherwise, they will be tagged with a unique owner identifier.
* **Node Size** - The amount of inbound/outbound transactions that have been sent to and from a particular owner will be represented with how large a node is relative to the other nodes in the current graph.
* **Node Color** - Hotter colors will represent recent activity sent from the owner while a cooler color will represent older activity.
* **Edge Color** - Hotter colors will represent recent transactions while cooler colors will represent older ones.
* **Edge Direction** - Following an edge in a clockwise direction will separate inbound vs outbound transactions.

## Usage
Several command line arguments are passed into blockviewer.  To let it build a full, application ready use as seen at blockviewer.com, the backend server would use:
```java 
java -jar -Xmx2048m BlockViewer.jar -dbPath ../graph.db/ -configPath ../neo4j.properties -validate false -low -high -scraper -exporter
```
where:
* **dbPath** = The path to the Neo4j graph database.
* **configPath** = The path to the Neo4j configuration settings (used to set-up master/slave replication)
* **validate** = Toggle the verifier which checks if the local json files from blockchain.info API form a complete blockchain.
* **low** = Builds the low level block chain structure.
* **high** = Builds the high level ownership structure
* **scraper** = Runs the scraper which attempts to associate bitcoin addresses to real world entities.
* **exporter** = Exports a time based analysis and owner network of the entire block chain to mysql

The client only needs to be passed:
```java
java -jar -Xmx2048m BlockViewer.jar -dbPath graph.db/ -configPath neo4j.properties -client
```
where
* **client** = Just enables the Neo4j embedded database, and given the config settings, receives data from the server.
BlockViewer's backend is fully recoverable (except during a download from the API TODO), which means it can be stopped at resumed where it left at any time during this process without any issue.

## Detailed Usage
In order to run the BlockViewer application that builds the graph, the following must happen:

Backend:
0) Java needs to be installed
1) You need to run the Neo4j coordinator application separately because this program is using the high availability database.  If you are running windows, you need to download and execute this program before starting the jar: /neo4j-enterprise-1.8-windows/neo4j-enterprise-1.8/bin/Neo4jCoordinator.bat This can be downloaded from the Neo4j website!
2) Once that is running, you need to seed the genesis block.  Put this file in the directory where the BlockViewer.jar resides: http://blockchain.info/rawblock/000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f  Save it as: "1.json".  You have to do this because of a known bug that I wish I had more time to fix, but if you are so inclined, please check out latestDiskBlockIndex() function in GraphBuilder.java
3) Finally, run the application itself: java -jar -Xmx6g BlockViewer.jar -dbPath ../graph.db/ -configPath ../neo4j.properties -validate false -low -high  (There are several more flags that can be set... an easy way to test that things are working is to use the -client flag so no building happens but just starts the database).
4) If you are going to be exporting the visualizations, you need to have a mysql database setup.  Also, look at the export code to determine what stuff you actually want exported (pdf, gexf, png, etc).  It loads it as a LONGBLOB into a mysql table.

Frontend:
If you are interested in running the frontend application that allows visualization in the browser, it requires a few environment variables to be set:
0) Nodejs needs to be installed and its dependencies (npm install)
// Since you are hosting a website, this makes it so you can run a website on port 80 without having to do it as root by running it on 8080
sudo iptables -A PREROUTING -t nat -i eth0 -p tcp --dport 80 -j REDIRECT --to-port 8080
// It has a logger from http://loggly.com/ if you are interested in loging frontend activity
export logsubdomain=???
export loguser=???
export logpass=???
export logtoken=???
// It requires a mysql server
export sqlhost=10.0.0.1
export sqluser=root
export sqlpass=???
export sqldatabase=blockviewer

Known Issue:
Gephi toolkit has a known problem when casting the ID's to Long datatypes.  I reported and have been working with them for some time in getting it resolved: https://github.com/gephi/gephi/issues/707 They just now got around to fixing it, but my dirty hack is being used in the meantime.
There is a memory leak when exporting graph visualizations.  It is occuring in the Neo4jImporter importDatabase function.  I modified the source code of the importer so it doesn't have to close the database connection and repoen it upon each import.  By keeping it open, when i clear the graph workspace, some kind of memory is not being released.  Over time, the memory fills up and eventually results in an OutOfMemoryError exception.  The unmodified, original importer (which also doesn't allow users to traverse through time which I added), can be found here -> https://github.com/gephi/gephi-plugins/blob/neo4j-plugin/Neo4jPlugin/src/org/gephi/neo4j/plugin/impl/Neo4jImporterImpl.java#L96
Relationships between the owner nodes are only being expressed with one transaction - even if there are more interactions between owners.  This process takes place in the relinkOwners function under GraphBuilder.java

## Issues
Issues are being tracked in Github.

## Interested? Ideas?
I'm here to answer any and all questions and ideas.  If you would like to contribute to this project, please let me know!  It's very much in its infancy, there is lots of room for improvement.

## License
MIT License

Author 2012 - John Russell

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
