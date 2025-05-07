# Elasticsearch Vector Search

This Java program allows you to search for documents in Elasticsearch using a hybrid approach combining vector similarity and classic text search. 
It takes text queries, encodes them into vector representations using an external service, performs both KNN (K-Nearest Neighbors) and 
classic text searches in Elasticsearch, and then combines the results using Reciprocal Rank Fusion (RRF) to find the most relevant documents.

## Prerequisites

- Java 24 or higher
- Maven

## Setup

1. Run Image-Text Encoder service
Make sure the Image-Text Encoder service is running on port 5555. This service is used to encode the text query into a vector representation.
See [Image-Text Encoder README](./../searcher/image-text-encoder/README.md) for details on how to build and run the Docker image.
 
2. Run Elasticsearch
Make sure the Elasticsearch instance with a `catalogue` index that has vector embeddings.
* Run https://github.com/elastic/start-local
* Obtain an API key: `curl -X POST -u elastic:<password-from-elastic-start-local.env> -H "Content-Type: application/json" -d '{"name":"test","role_descriptors":{"role_name":{"cluster":["all"],"index":[{"names":["*"],"privileges":["all"]}]}}}' "http://localhost:9200/_security/api_key"`
* Set the required environment variables:
```bash
export ES_URL=<your_elasticsearch_url>
export ES_API_KEY=<your_elasticsearch_api_key>
```
* Run `data_import.sh`

## Running the Program

### From the command line
You can run the program directly from the command line using Maven:

```bash
mvn compile
mvn exec:java -Dexec.mainClass="org.przybyl.EnterpriseySearcher"
```

### From IntelliJ IDEA
You can run the program directly from IntelliJ IDEA:
* Edit Run configurations for `EnterpriseySearcher` to add Environment variables: `ES_URL=<your_elasticsearch_url>;ES_API_KEY=<your_elasticsearch_api_key>`.
* Click the Run button in the gutter in the `EnterpriseySearcher` class.
* You may also need to set Language Level to "24 (Preview)".

## How It Works

1. The program processes a list of text queries
2. For each query, it sends the text to the Image-Text Encoder service to get a vector representation
3. It connects to Elasticsearch using the provided credentials
4. It performs both a KNN search and a classic text search
5. It combines the results using Reciprocal Rank Fusion (RRF) algorithm
6. It displays the results, including the filename, price, and path

## Example Output

```
orange: [apple.jpg (12.99 USD), fruit-basket.jpg (24.50 EUR), orange-tree.jpg (8.75 USD), citrus-collection.jpg (15.00 USD), orange-juice.jpg (3.99 USD)]
apple: [apple.jpg (12.99 USD), green-apple.jpg (9.50 USD), fruit-basket.jpg (24.50 EUR), apple-tree.jpg (18.25 USD), apple-pie.jpg (7.99 USD)]
Heckscheibenwaschanlage: [car-parts.jpg (45.00 EUR), windshield-wiper.jpg (12.75 EUR), auto-accessories.jpg (89.99 EUR), german-car.jpg (35000.00 EUR), repair-manual.jpg (29.99 USD)]
```

## Elasticsearch Index Structure

The program expects an Elasticsearch index called `catalogue` with the following structure:

```json
{
  "mappings": {
    "properties": {
      "@timestamp": {
        "type": "date"
      },
      "embedding": {
        "type": "dense_vector",
        "dims": 512,
        "index": true,
        "similarity": "cosine",
        "index_options": {
          "type": "int8_hnsw",
          "m": 16,
          "ef_construction": 100
        }
      },
      "filename": {
        "type": "keyword"
      },
      "full-path": {
        "type": "keyword"
      },
      "price": {
        "properties": {
          "currency": {
            "type": "keyword"
          },
          "value": {
            "type": "scaled_float",
            "scaling_factor": 100.0
          }
        }
      }
    }
  }
}
```
