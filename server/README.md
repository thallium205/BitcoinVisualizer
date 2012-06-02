# Bitcoin Blockchain to Neo4j
This program will download the raw json files (nearly 200,000 of them) from blockchain.info first, validate the consistency (optional, but highly recommended at first run) and try to fix any files that may be missing, then persist them to a running instance of neo4j that you specify given a URI. Transactions are indexed so redeeming inputs from existing outputs is a constant time operation. Any sub-branching blocks that deviate from the main chain will perform a breadth first traversal to find its parent block, but that is usually very fast since these chains are not very long.
## Usage
usage: java -jar blockchainneo4j.jar
 -pass <password>         Password of the neo4j instance.
 -uri <uri>               The uri to the neo4j instsance. Ex:
                          http://localhost:7474/db/data
 -user <username>         Username of the neo4j instance.
 -validate <true/false>   Toggle whether the local json files form a
                          complete blockchain.  Default: true.
                          Recommended.
##Structure
Blocks "succeed" one another.  Transaction are "from" blocks.  Transactions "send" money.  Transactions "receive" money.
<img src="https://github.com/thallium205/BlockchainNeo4J/raw/master/screen.png"/>