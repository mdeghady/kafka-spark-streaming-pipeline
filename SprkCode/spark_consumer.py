# Spark imports
from pyspark.sql import SparkSession
import pyspark.sql.functions as f
from pyspark.sql.avro.functions import from_avro  # for deserialization
from cassandra.cluster import Cluster
from confluent_kafka.schema_registry import SchemaRegistryClient  # to get the schema from Schema Registry
import logging

KAFKA_BROKERS = "kafka1:29092,kafka2:29092,kafka3:29092"
SCHEMA_REGISTRY_URL = "http://schema-registry:8081"
KAFKA_TOPIC = "FakeUsers"
CASSANDRA_KEYSPACE = "spark_streams"
CASSANDRA_TABLE = "fake_users"


def create_spark_session():
    spark = None
    try:
        spark = SparkSession.builder.appName("StreamingPipeline") \
            .config('spark.jars.packages', "org.apache.spark:spark-sql-kafka-0-10_2.13-3.5.0") \
            .config('spark.cassandra.connection.host', 'cassandra_db') \
            .config('spark.cassandra.connection.port', '9042') \
            .getOrCreate()
        spark.sparkContext.setLogLevel("ERROR")
    except Exception as e:
        logging.error(f"Couldn't create Spark connection due to {e}")

    return spark


def get_schema():
    avro_schema = None
    schema_registry_config = {"url": "http://schema-registry:8081"}
    schema_registry_client = SchemaRegistryClient(schema_registry_config)

    try:
        avro_schema = schema_registry_client.get_schema(1)

    except Exception as e:
        logging.error(f"Couldn't get schema from schema registry {e}")
    return avro_schema


def get_data_from_kafka(spark_session):
    streamed_data = None

    try:
        streamed_data = spark_session \
            .readStream \
            .format("kafka") \
            .option("kafka.bootstrap.servers", KAFKA_BROKERS) \
            .option("subscribe", KAFKA_TOPIC) \
            .option("startingOffsets", "earliest") \
            .load() \
            .selectExpr("substring(value, 6) as avro_value")
        # Skip the first 5 bytes (reserved by schema registry encoding protocol)

    except Exception as e:
        logging.error(f"Couldn't connect to Kafka due to {e}")

    return streamed_data


def deserialize_data(dataframe, avro_schema):
    avro_deserializer_options = {"mode": "PERMISSIVE"}  # Corrupt records are processed as null result.
    # deserialize data as a struct type aliased users
    deserialized_df = dataframe.select(
        from_avro(f.col("avro_value"), avro_schema.schema_str, avro_deserializer_options).alias("users")
    ).select("users.*")  # retrieve data from users struct type

    print("pipeline schema \n")
    deserialized_df.printSchema()

    return deserialized_df


def create_cassandra_connection():
    connection = None
    try:
        cluster = Cluster(["cassandra_db"])
        connection = cluster.connect()
    except Exception as e:
        logging.error(f"Couldn't connect to Cassandra due to {e}")

    return connection


def create_keyspace(cassandra_connection):
    cassandra_connection.execute("""
        CREATE KEYSPACE IF NOT EXISTS spark_streams
        WITH replication = {'class': 'SimpleStrategy', 'replication_factor': '1'};
    """)

    print("Keyspace created successfully!")


def create_table(cassandra_connection):
    cassandra_connection.execute("""
    CREATE TABLE IF NOT EXISTS spark_streams.fake_users (
        id UUID PRIMARY KEY,
        first_name TEXT,
        last_name TEXT,
        gender TEXT,
        age INT,
        email TEXT,
        picture TEXT,
        city TEXT,
        country TEXT);
    """)

    print("Table created successfully!")


def ingest_data_to_cassandra(users_df):
    users_df \
        .writeStream \
        .format("org.apache.spark.sql.cassandra") \
        .option("checkpointLocation", "/tmp/checkpoint") \
        .option("keyspace", CASSANDRA_KEYSPACE) \
        .option("table", CASSANDRA_TABLE) \
        .outputMode("append") \
        .start() \
        .awaitTermination()


if __name__ == "__main__":
    # create spark connection
    spark_connection = create_spark_session()

    # get data from kafka source
    streamed_df = get_data_from_kafka(spark_connection)

    # get Avro schema from schema registry
    pipeline_schema = get_schema()

    # deserialize avro data
    deserialized_users_data = deserialize_data(streamed_df, pipeline_schema)

    # create Cassandra connection
    cassandra_connection = create_cassandra_connection()

    print("Is Streaming ? " , deserialized_users_data.isStreaming)

    if cassandra_connection is not None:
        # Create Cassandra Keyspace
        create_keyspace(cassandra_connection)

        # Create Cassandra Table
        create_table(cassandra_connection)

        # Start ingestion pipeline to cassandra
        ingest_data_to_cassandra(deserialized_users_data)
