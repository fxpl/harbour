CASSANDRA="."
$CASSANDRA/bin/cqlsh -e "DESCRIBE KEYSPACE ycsb;" &> /dev/null
if [ $? -ne 0 ]; then
  $CASSANDRA/bin/cqlsh -e "
  create keyspace ycsb WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor': 1 };
  USE ycsb;
  create table usertable (
    y_id varchar primary key,
    field0 varchar,
    field1 varchar,
    field2 varchar,
    field3 varchar,
    field4 varchar,
    field5 varchar,
    field6 varchar,
    field7 varchar,
    field8 varchar,
    field9 varchar);" &> /dev/null
  if [ $? -eq 0 ]; then
    echo "Initalised db with keyspace 'ycsb'."
  else
    echo "Something went wrong during initalisation"
  fi
else
  echo "Already initalised db with keyspace 'ycsb'."
  read -p "Do you want to drop the keyspace 'ycsb'? (yes/no): " choice
  if [ "$choice" = "yes" ]; then
    $CASSANDRA/bin/cqlsh -e "DROP KEYSPACE ycsb;" &> /dev/null
    if [ $? -eq 0 ]; then
      echo "Keyspace 'ycsb' dropped successfully."
    else
      echo "Failed to drop keyspace 'foobar'."
    fi
  fi
fi
