# Bitcoin Blockchain to Neo4j
This application can download the entire bitcoin blockchain using the API provided by http://blockchain.info and store it to a neo4j database.  After each block is stored, it will wait 2 seconds before fetching the next one in order to comply with API rules.  If you need a solution that stores it into a relational database and at a much faster rate, consider -> https://github.com/thallium205/Bitcoin_Updater

## Usage
usage: java -jar blockchainneo4j.jar
 -pass <password>   Password of the neo4j instance.
 -uri <uri>         The uri to the neo4j instsance. Ex:
                    http://localhost:7474/db/data (required)
 -user <username>   Username of the neo4j instance.


##Structure
Blocks "succeed" one another.  Transaction are "from" blocks.  Transactions "send" money.  Transactions "receive" money.
<img src="https://github.com/thallium205/BlockchainNeo4J/raw/master/screen/screen.png"/>