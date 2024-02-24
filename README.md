# Project

This project uses the Spring AI project to create a LLM RAG application.

Special thanks to Josh Long for his video and code that helped me get started with this project.
https://github.com/coffee-software-show/llm-rag-with-spring-ai

## Pre-requisites
Install Ollama with the Llama2 model.

https://ollama.com/

## Usage
1. run Ollama with the Llama2 model
2. run the supplied docker-compose.yml to start the pgvector database

### Pgvector PostgresQL Database
This application uses a pgvector database to store the RAG data. The database is started using the supplied docker-compose.yml file.

