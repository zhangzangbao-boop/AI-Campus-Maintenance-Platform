# Local Embedding Model

Place the ONNX embedding model files for `paraphrase-multilingual-MiniLM-L12-v2` in this directory:

- `model.onnx`
- `tokenizer.json`

The project launch scripts resolve these paths from the project root and expose them to `qiyun-ai-service` as:

- `EMBEDDING_MODEL_PATH`
- `EMBEDDING_TOKENIZER_PATH`

If either file is missing, RAG will continue with deterministic fallback embeddings, but the page may still show that the real embedding model is unavailable.
